"""
视频同音字纠正（VideoTermFixer）的效果评估：用第 6 章的真实数据跑一次，和人工确认的识别错误对照。

  python -X utf8 asr_term_fix_eval.py

做法和后端一样：
  - 提示词：backend/src/main/resources/prompts/asr_term_fix_v1.md；课件文字、视频文字各取前 12000 字
  - 程序核对（和 VideoTermFixer.accept 一样）：都是 3 个字以上的汉字、等长、只差一个字；
    正确写法在课件里、错写法不在课件里、错写法在视频里
结果写到 验证结果/第6章-视频同音字纠正.json：模型给了哪些、程序采用了哪些、每条改了几处、
对照“人工确认的识别错误.txt”算改对了几处、误改了几处，以及这次调用的 token、耗时、费用。
API key 从 backend/src/main/resources/application-local.yml 读，不打印。
"""
import json
import os
import re
import sys
import time
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', '..')
RES = os.path.join(ROOT, 'backend', 'src', 'main', 'resources')
OUT = os.path.join(HERE, '验证结果', '第6章-视频同音字纠正.json')
MAX = 12000
PRICE_IN, PRICE_OUT = 2.2, 8.7  # 元 / 百万 token，和 application.yml 一样
HAN = re.compile(r'[一-鿿]{3,}')


def api_key():
    text = open(os.path.join(RES, 'application-local.yml'), encoding='utf-8').read()
    m = re.search(r'api-key:\s*(\S+)', text)
    if not m:
        sys.exit('application-local.yml 里没有 llm.api-key')
    return m.group(1).strip('"\'')


def accept(frm, to, courseware, video):
    if not (HAN.fullmatch(frm) and HAN.fullmatch(to)) or len(frm) != len(to):
        return False
    diff = sum(a != b for a, b in zip(frm, to))
    return diff == 1 and to in courseware and frm not in courseware and frm in video


def main():
    v = os.path.join(HERE, '验证结果')
    cw = json.load(open(os.path.join(v, '第6章-课件文字-第2版.json'), encoding='utf-8'))
    courseware = '\n'.join(s['text'] for s in cw['segments'])
    video = ''
    for n in ('6-1', '6-2', '6-3', '6-4'):
        d = json.load(open(os.path.join(v, '第6章-视频文字', n + '.json'), encoding='utf-8'))
        video += '\n'.join(s['text'] for s in d['segments']) + '\n'
    confirmed = {}
    for line in open(os.path.join(v, '第6章-视频文字', '人工确认的识别错误.txt'), encoding='utf-8'):
        if line.strip() and not line.startswith('#'):
            right, wrong = line.rstrip('\n').split('\t')
            confirmed[wrong] = right

    prompt = open(os.path.join(RES, 'prompts', 'asr_term_fix_v1.md'), encoding='utf-8').read()
    prompt = prompt.replace('{{courseware}}', courseware[:MAX]).replace('{{video}}', video[:MAX])
    body = {'model': 'deepseek-flash', 'messages': [{'role': 'user', 'content': prompt}], 'temperature': 0.0,
            'max_tokens': 800, 'response_format': {'type': 'json_object'}, 'thinking': {'type': 'disabled'}}
    req = urllib.request.Request('https://api.deepseek.com/chat/completions', data=json.dumps(body).encode(),
                                 headers={'Content-Type': 'application/json', 'Authorization': 'Bearer ' + api_key()})
    t0 = time.time()
    resp = json.loads(urllib.request.urlopen(req, timeout=120).read())
    sec = time.time() - t0
    usage = resp.get('usage', {})
    fixes = json.loads(resp['choices'][0]['message']['content']).get('fixes', [])

    rows, right, wrong_fix = [], 0, 0
    for f in fixes:
        frm, to = f.get('from', '').strip(), f.get('to', '').strip()
        ok = accept(frm, to, courseware, video)
        n = video.count(frm) if ok else 0
        is_error = confirmed.get(frm) == to
        if ok:
            right += n if is_error else 0
            wrong_fix += 0 if is_error else n
        rows.append({'from': frm, 'to': to, '程序采用': ok, '替换处数': n, '人工确认是识别错误': is_error})
    cn_errors = {w: r for w, r in confirmed.items() if HAN.fullmatch(w)}
    total_cn = sum(video.count(w) for w in cn_errors)
    result = {
        '时间': time.strftime('%Y-%m-%d %H:%M'), '课件字数': len(courseware), '视频字数': len(video),
        '模型给出': rows,
        '人工确认的中文识别错误（处）': total_cn,
        '改对（处）': right, '误改（处）': wrong_fix,
        '用时（秒）': round(sec, 1),
        'token': {'输入': usage.get('prompt_tokens'), '输出': usage.get('completion_tokens')},
        '费用（元）': round((usage.get('prompt_tokens', 0) * PRICE_IN + usage.get('completion_tokens', 0) * PRICE_OUT) / 1e6, 4),
    }
    json.dump(result, open(OUT, 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
