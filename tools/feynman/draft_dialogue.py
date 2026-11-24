"""
费曼式 AI 对话：给一个知识点起草对话（原型，先看效果，再接进平台）。

  python draft_dialogue.py --kp kp.json --pdf-json 课件文字.json --video-json 6-1.json 6-2.json ... --out-dir 验证结果/xxx

输入：一个已确认的知识点（名称、说明、要点和出处、常见误解），以及资料原文（按出处取出来给模型参考）。
输出草稿（老师看、老师改）：场景、小林的开场白、带着的误解、每条要点的判断标准和三级引导、考一考小林、复述要点。
每一步存档：01 发给模型的提示词、02 原始回复、03 草稿 JSON、记录.md（耗时、token、费用、程序检查）。
"""
import argparse
import json
import os
import re
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'material_kp'))
from extract_kps import api_key, call_model  # noqa: E402  和识别知识点用同一个模型、同样的调用方式

PRICE_IN, PRICE_OUT = 2.2, 8.7   # 元 / 百万 token，和 application.yml 一致


def mmss(t):
    return f'{int(t) // 60:02d}:{int(t) % 60:02d}'


def load_material(pdf_json, video_jsons):
    """出处编号 → (位置说明, 原文)。D1-P7 = 课件第 7 页；V2-02 = 第 2 个视频第 2 段"""
    out = {}
    if pdf_json:
        for s in json.load(open(pdf_json, encoding='utf-8'))['segments']:
            out[f"D1-{s['id']}"] = (f"课件第 {s['page']} 页", s['text'])
    for n, p in enumerate(video_jsons or [], 1):
        d = json.load(open(p, encoding='utf-8'))
        name = d['source'].replace('.mp4', '').split()[0]
        for c in d['chunks']:
            out[f"V{n}-{c['id']:02d}"] = (f"视频 {name} 的 {mmss(c['start'])}", c['text'])
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--kp', required=True, help='一个知识点的 JSON（kp-status 接口里的一项）')
    ap.add_argument('--course', default='智能软件工程')
    ap.add_argument('--pdf-json')
    ap.add_argument('--video-json', nargs='*')
    ap.add_argument('--used-scenes', default='无')
    ap.add_argument('--out-dir', required=True)
    a = ap.parse_args()
    sys.stdout.reconfigure(encoding='utf-8')
    os.makedirs(a.out_dir, exist_ok=True)
    kp = json.load(open(a.kp, encoding='utf-8'))
    mat = load_material(a.pdf_json, a.video_json)

    sources = []
    for p in kp['keyPoints']:
        for s in p['sources']:
            if s not in sources:
                sources.append(s)
    material = '\n'.join(f'[{mat[s][0]}] {mat[s][1]}' for s in sources if s in mat)
    points = '\n'.join(f"{i}. {p['text']}（资料位置：{'、'.join(mat[s][0] for s in p['sources'] if s in mat)}）"
                       for i, p in enumerate(kp['keyPoints'], 1))
    prompt = open(os.path.join(HERE, 'draft_prompt_v1.md'), encoding='utf-8').read()
    for k, v in {'course': a.course, 'name': kp['name'], 'summary': kp['summary'], 'points': points,
                 'misconceptions': '\n'.join(f'- {m}' for m in kp['misconceptions']),
                 'material': material, 'used_scenes': a.used_scenes}.items():
        prompt = prompt.replace('{{' + k + '}}', v)
    open(os.path.join(a.out_dir, '01_提示词.txt'), 'w', encoding='utf-8').write(prompt)

    key = api_key()
    text, usage, sec, finish = call_model(prompt, key)
    open(os.path.join(a.out_dir, '02_模型原始回复.txt'), 'w', encoding='utf-8').write(text)
    draft = json.loads(text)
    json.dump(draft, open(os.path.join(a.out_dir, '03_对话草稿.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)

    # 程序检查：每条要点都有三级引导和判断标准；引导问题里没有直接说出要点里的关键词组
    checks = []
    n_points = len(kp['keyPoints'])
    got = {p.get('no') for p in draft.get('key_points', [])}
    checks.append(('每条要点都有草稿', got == set(range(1, n_points + 1)), f'{len(got)}/{n_points}'))
    missing = [p.get('no') for p in draft.get('key_points', [])
               if not all(p.get(f) for f in ('L1', 'L2', 'L3', 'understood_if', 'partial_if', 'pointer'))]
    checks.append(('三级引导和判断标准都写了', not missing, f'缺的要点：{missing}' if missing else '齐全'))
    long_lines = [s for s in [draft['scene'].get('opening', ''), draft['misconception_probe'].get('line', '')]
                  + [p.get(f, '') for p in draft['key_points'] for f in ('L1', 'L2', 'L3')] if len(s) > 80]
    checks.append(('每句不超过 80 字', not long_lines, f'超长 {len(long_lines)} 句'))
    leaks = []
    for p in draft['key_points']:
        ref = kp['keyPoints'][p['no'] - 1]['text']
        terms = set(re.findall(r'[一-鿿]{4,}', ref))
        for f in ('L1', 'L2', 'L3'):
            hit = [t for t in terms if t in p.get(f, '')]
            if hit:
                leaks.append(f"要点{p['no']} {f}：{hit}")
    checks.append(('引导问题没有照抄要点原文（4 字以上的词组）', not leaks, '；'.join(leaks) or '无'))
    cost = ((usage.get('prompt_tokens') or 0) * PRICE_IN + (usage.get('completion_tokens') or 0) * PRICE_OUT) / 1e6

    log = [f"# 费曼对话草稿：{kp['name']}", '', f"- 时间：{time.strftime('%Y-%m-%d %H:%M')}",
           f'- 耗时 {sec:.0f} 秒；输入 {usage.get("prompt_tokens")}、输出 {usage.get("completion_tokens")} token；'
           f'约 {cost:.3f} 元；结束原因 {finish}', '', '## 程序检查', '', '| 检查 | 结果 | 说明 |', '|---|---|---|']
    log += [f"| {c} | {'通过' if ok else '没通过'} | {d} |" for c, ok, d in checks]
    open(os.path.join(a.out_dir, '记录.md'), 'w', encoding='utf-8').write('\n'.join(log) + '\n')
    print('\n'.join(log))
    print(json.dumps(draft, ensure_ascii=False, indent=1))


if __name__ == '__main__':
    main()
