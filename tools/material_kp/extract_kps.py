"""
一章的资料（课件 PDF 的文字 + 讲课视频转出的文字）→ 大模型识别知识点 → 知识点蓝图草稿。

  python extract_kps.py --chapter 6 --title 智能测试生成与优化 \
      --pdf-json ch6_pdf.json --video-json 6-1.json 6-2.json ... --runs 3 --out-dir 验证结果/第6章

每一步都存一份记录（out-dir 里）：
  01_资料片段.json      交给模型的每一段：编号、来源、位置、文字
  02_提示词.txt         发给模型的完整内容
  03_模型原始回复_第N次.txt
  04_知识点草稿_第N次.json   解析后的结果
  05_出处核对_第N次.json     程序自动核对：引用的编号存不存在、要点和引用的资料对不对得上
  06_稳定性.json        同样的输入跑几次，知识点能不能对上
  记录.md               每一步的数字汇总（字数、token、耗时、核对结果）

准确率分三层看：
  - 出处有效率：引用的编号都存在的要点 ÷ 全部要点（程序算，应为 100%）
  - 出处支持率：要点和它引用的资料文字对得上的 ÷ 全部要点（程序按字的重合算，是粗略的自动检查）
  - 和人工清单对比的查准率、查全率：另写 compare_with_human.py，需要人先列出这一章的知识点
API key：读环境变量 LLM_API_KEY；没有就读 backend 的 application-local.yml。不会打印 key。
"""
import argparse
import json
import os
import re
import sys
import time
import urllib.request

BASE_URL = 'https://api.deepseek.com'
MODEL = 'deepseek-flash'   # 和 application.yml 一致
HERE = os.path.dirname(os.path.abspath(__file__))
LOCAL_YML = os.path.join(HERE, '..', '..', 'backend', 'src', 'main', 'resources', 'application-local.yml')
PROMPT_FILE = os.path.join(HERE, 'kp_prompt_v1.md')
MAX_MATERIAL_CHARS = 40000   # 超过就要分批（一章课件 + 视频一般 1～2 万字）
SUPPORT_MIN = 0.3            # 要点里的字词，至少这么多比例能在引用的资料里找到，才算“对得上”
SAME_KP_MIN = 0.4            # 两次结果里的知识点名称，字的重合度达到这个值算同一个知识点


# ------------------------------------------------------------------ 资料片段

def load_segments(pdf_json, video_jsons, image_json=None):
    segs = []
    if pdf_json:
        d = json.load(open(pdf_json, encoding='utf-8'))
        for s in d['segments']:
            segs.append({'id': s['id'], 'source': 'pdf', 'file': d['file'], 'where': f"第 {s['page']} 页",
                         'text': s['text']})
    if image_json:   # 课件里的图片（image_to_text.py 的结果），放在同一页的文字后面
        imgs = json.load(open(image_json, encoding='utf-8'))
        order = {s['id']: i for i, s in enumerate(segs)}
        for im in imgs:
            segs.append({'id': im['id'], 'source': 'image', 'file': '课件图片', 'where': f"第 {im['page']} 页的图",
                         'text': ' '.join(im['text'].split())})
        segs.sort(key=lambda s: (int(re.match(r'P(\d+)', s['id']).group(1)) if s['id'].startswith('P') else 10 ** 6,
                                 s['source'] == 'image', s['id']))
    for n, path in enumerate(video_jsons or [], 1):
        d = json.load(open(path, encoding='utf-8'))
        for c in d['chunks']:
            segs.append({'id': f'V{n}-{c["id"]:02d}', 'source': 'video', 'file': d['source'],
                         'where': f"{c['at']}～{mmss(c['end'])}", 'text': c['text']})
    return segs


def mmss(t):
    return f'{int(t) // 60:02d}:{int(t) % 60:02d}'


def material_text(segs):
    lines = []
    for s in segs:
        head = (f"[{s['id']}]" if s['source'] == 'pdf' else f"[{s['id']} 课件第 {s['id'][1:].split('-')[0]} 页的图]"
                if s['source'] == 'image' else f"[{s['id']} {s['file']} {s['where']}]")
        lines.append(f'{head} {s["text"]}')
    return '\n'.join(lines)


# ------------------------------------------------------------------ 调模型

def api_key():
    key = os.environ.get('LLM_API_KEY')
    if not key and os.path.exists(LOCAL_YML):
        m = re.search(r'^\s*api-key:\s*(\S+)', open(LOCAL_YML, encoding='utf-8').read(), re.M)
        key = m.group(1).strip('"\'') if m else None
    if not key:
        sys.exit('没有 API key：设置环境变量 LLM_API_KEY，或在 application-local.yml 里配置 llm.api-key')
    return key


def call_model(prompt, key):
    body = json.dumps({'model': MODEL, 'messages': [{'role': 'user', 'content': prompt}],
                       'temperature': 0.2, 'max_tokens': 16384,   # 8192 时加了图片后连续两次被截断（模型的“思考”也算输出）
                      
                       'response_format': {'type': 'json_object'}}).encode()
    req = urllib.request.Request(BASE_URL + '/chat/completions', data=body, method='POST',
                                 headers={'Content-Type': 'application/json', 'Authorization': 'Bearer ' + key})
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=300) as r:
        out = json.loads(r.read())
    choice = out['choices'][0]
    # finish_reason：stop = 正常写完；length = 写到输出上限被截断
    return choice['message']['content'].strip(), out.get('usage', {}), time.time() - t0, choice.get('finish_reason')


# ------------------------------------------------------------------ 核对

def grams(text):
    """用来比较的字词：汉字取相邻两个字，英文取整个单词（小写）"""
    t = re.sub(r'[，。、；：“”‘’（）()《》\[\]【】!！?？,.;:\s•\-—/]+', ' ', text)
    out = set()
    for part in t.split():
        if re.fullmatch(r'[A-Za-z0-9]+', part):
            out.add(part.lower())
        else:
            han = re.findall(r'[一-鿿]', part)
            out.update(a + b for a, b in zip(han, han[1:]))
    return out


def support(claim, texts):
    """要点里的字词，有多少比例能在引用的资料里找到"""
    g = grams(claim)
    if not g:
        return 0.0
    pool = set().union(*[grams(t) for t in texts]) if texts else set()
    return len(g & pool) / len(g)


def check(blueprint, segs):
    by_id = {s['id']: s for s in segs}
    report, total, valid, supported = [], 0, 0, 0
    for kp in blueprint.get('knowledge_points', []):
        items = []
        for p in kp.get('key_points', []):
            total += 1
            srcs = p.get('sources', [])
            missing = [i for i in srcs if i not in by_id]
            ok_ids = bool(srcs) and not missing
            valid += ok_ids
            sc = support(p.get('text', ''), [by_id[i]['text'] for i in srcs if i in by_id])
            good = ok_ids and sc >= SUPPORT_MIN
            supported += good
            items.append({'要点': p.get('text'), '出处': srcs, '不存在的编号': missing,
                          '支持度': round(sc, 2), '对得上': good})
        report.append({'知识点': kp.get('name'), '重要性': kp.get('importance'),
                       '要点数': len(kp.get('key_points', [])), '误解数': len(kp.get('misconceptions', [])),
                       '要点核对': items})
    return {'知识点数': len(report), '要点数': total,
            '出处有效率': round(valid / total, 3) if total else None,
            '出处支持率': round(supported / total, 3) if total else None,
            '明细': report}


def same(a, b):
    ga, gb = grams(a), grams(b)
    return len(ga & gb) / len(ga | gb) if ga | gb else 0


def same_kp(a, b):
    """两次结果里的两个知识点是不是同一个：名称相近，或者引用的资料大部分相同（取较大的那个）"""
    sa, sb = set(a.get('sources', [])), set(b.get('sources', []))
    src = len(sa & sb) / len(sa | sb) if sa | sb else 0
    return max(same(a['name'], b['name']), src), round(same(a['name'], b['name']), 2), round(src, 2)


def stability(runs):
    """以第 1 次的知识点为基准，看后面每一次能不能找到名称相近的知识点"""
    rows = []
    for kp in runs[0].get('knowledge_points', []):
        hits = []
        for r in runs[1:]:
            best = max(((same_kp(kp, k), k['name']) for k in r.get('knowledge_points', [])),
                       default=((0, 0, 0), None))
            (score, by_name, by_src), name = best
            hits.append({'最像的': name, '名称重合度': by_name, '出处重合度': by_src, '对上': score >= SAME_KP_MIN})
        rows.append({'第 1 次的知识点': kp['name'], '其他几次': hits})
    matched = sum(h['对上'] for r in rows for h in r['其他几次'])
    pairs = sum(len(r['其他几次']) for r in rows)
    return {'每次的知识点数': [len(r.get('knowledge_points', [])) for r in runs],
            '一致率': round(matched / pairs, 3) if pairs else None, '明细': rows}


# ------------------------------------------------------------------ 主流程

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--course', default='智能软件工程')
    ap.add_argument('--chapter', required=True)
    ap.add_argument('--title', required=True)
    ap.add_argument('--pdf-json')
    ap.add_argument('--video-json', nargs='*')
    ap.add_argument('--prompt', default=PROMPT_FILE, help='提示词文件，默认 kp_prompt_v1.md；有图片时用 kp_prompt_v2.md')
    ap.add_argument('--image-json', help='课件图片的文字描述（image_to_text.py 的 图片描述.json）')
    ap.add_argument('--runs', type=int, default=1, help='同样的输入跑几次，用来看稳定性')
    ap.add_argument('--out-dir', required=True)
    a = ap.parse_args()
    sys.stdout.reconfigure(encoding='utf-8')
    os.makedirs(a.out_dir, exist_ok=True)
    log = [f'# 第 {a.chapter} 章「{a.title}」知识点识别记录', '',
           f'- 时间：{time.strftime("%Y-%m-%d %H:%M")}', f'- 模型：{MODEL}，提示词：{os.path.basename(a.prompt)}', '']

    def save(name, data):
        with open(os.path.join(a.out_dir, name), 'w', encoding='utf-8') as f:
            f.write(data if isinstance(data, str) else json.dumps(data, ensure_ascii=False, indent=1))

    # 第 1 步：资料片段
    segs = load_segments(a.pdf_json, a.video_json, a.image_json)
    save('01_资料片段.json', segs)
    material = material_text(segs)
    pdf_n = sum(s['source'] == 'pdf' for s in segs)
    img_n = sum(s['source'] == 'image' for s in segs)
    vid_n = sum(s['source'] == 'video' for s in segs)
    log += ['## 第 1 步：资料片段', '',
            f'- 课件文字 {pdf_n} 段，课件图片 {img_n} 段，视频 {vid_n} 段，共 {len(material)} 字',
            f'- 上限 {MAX_MATERIAL_CHARS} 字：{"未超过，一次交给模型" if len(material) <= MAX_MATERIAL_CHARS else "超过了，需要分批"}', '']
    print(log[-3]); print(log[-2])
    if len(material) > MAX_MATERIAL_CHARS:
        save('记录.md', '\n'.join(log))
        sys.exit('资料太长，需要分批（还没实现）')

    # 第 2 步：提示词
    prompt = open(a.prompt, encoding='utf-8').read()
    for k, v in {'course': a.course, 'chapter': a.chapter, 'title': a.title, 'material': material}.items():
        prompt = prompt.replace('{{' + k + '}}', str(v))
    save('02_提示词.txt', prompt)
    log += ['## 第 2 步：提示词', '', f'- {len(prompt)} 字，见 02_提示词.txt', '']

    # 第 3～5 步：调模型、解析、核对（跑 runs 次）
    key = api_key()
    runs = []
    log += ['## 第 3～5 步：调模型、解析、核对出处', '',
            '| 第几次 | 耗时 | 输入 token | 输出 token | 知识点 | 要点 | 出处有效率 | 出处支持率 |', '|---|---|---|---|---|---|---|---|']
    failures = []
    for i in range(1, a.runs + 1):
        bp = None
        for attempt in (1, 2):   # 回复被截断或不是合法 JSON 时重试一次，两次都记下来
            text, usage, sec, finish = call_model(prompt, key)
            tag = f'第{i}次' if attempt == 1 else f'第{i}次-重试'
            save(f'03_模型原始回复_{tag}.txt', text)
            try:
                bp = json.loads(text)
                break
            except json.JSONDecodeError as e:
                why = '写到输出上限被截断' if finish == 'length' else f'不是合法 JSON（{e}）'
                failures.append(f'{tag}：{why}，回复 {len(text)} 字，输出 token {usage.get("completion_tokens")}')
                print(f'{tag}：{why}' + ('，重试' if attempt == 1 else ''))
        if bp is None:
            log.append(f'| {i} | {sec:.0f} 秒 | — | — | 两次都失败 | | | |')
            continue
        save(f'04_知识点草稿_第{i}次.json', bp)
        c = check(bp, segs)
        save(f'05_出处核对_第{i}次.json', c)
        runs.append(bp)
        row = (f"| {i} | {sec:.0f} 秒 | {usage.get('prompt_tokens')} | {usage.get('completion_tokens')} | "
               f"{c['知识点数']} | {c['要点数']} | {c['出处有效率']:.0%} | {c['出处支持率']:.0%} |")
        log.append(row)
        print(f"第 {i} 次：{c['知识点数']} 个知识点、{c['要点数']} 条要点；出处有效率 {c['出处有效率']:.0%}，"
              f"出处支持率 {c['出处支持率']:.0%}；{sec:.0f} 秒")
    log.append('')
    if failures:
        log += ['失败和重试：', ''] + [f'- {f}' for f in failures] + ['']

    # 第 6 步：稳定性
    if len(runs) >= 2:
        st = stability(runs)
        save('06_稳定性.json', st)
        log += ['## 第 6 步：稳定性', '', f"- 每次的知识点数：{st['每次的知识点数']}",
                f"- 以第 1 次为基准，其他几次能对上的比例：{st['一致率']:.0%}"
                "（名称相近或引用的资料大部分相同，算同一个知识点）", '']
        print(f"稳定性：每次 {st['每次的知识点数']} 个，一致率 {st['一致率']:.0%}")

    # 第 1 次的结果写成可读的清单，方便人看和对照
    if runs:
        log += ['## 第 1 次的知识点草稿', '']
        for n, kp in enumerate(runs[0]['knowledge_points'], 1):
            log.append(f"### {n}. {kp['name']}（{kp.get('importance', '')}）")
            log.append(f"{kp.get('summary', '')}  出处：{'、'.join(kp.get('sources', []))}")
            log.append('')
            for p in kp.get('key_points', []):
                log.append(f"- {p['text']}（{'、'.join(p.get('sources', []))}）")
            for m in kp.get('misconceptions', []):
                log.append(f'- 误解：{m}')
            log.append('')
    save('记录.md', '\n'.join(log))
    print('记录写在', a.out_dir)


if __name__ == '__main__':
    main()
