"""
PDF 课件里的图片 → 文字描述（第 1 步的补充：页面文字取不到图里的内容）。

  python image_to_text.py 课件.pdf --pages 13 --out-dir 验证结果/第6章-图片

做法：
  1. 找图：每页的图片里，去掉每页都重复出现的装饰（校徽、底栏，出现超过 3 页的）和太小的图标（不到页面 5%）。
  2. 看图：把图片发给能看图的大模型（DeepSeek），要求只写图里真正有的文字和箭头关系，不编造，不重复页面上已有的文字。
  3. 每张图存档：原图、发出的提示词、模型原始回复、token、耗时（记录.md 汇总）。

结果编号为 P13-图1，之后和页面文字一起交给 extract_kps.py。
API key：读环境变量 LLM_API_KEY；没有就读 backend 的 application-local.yml。不会打印 key。
"""
import argparse
import base64
import json
import os
import sys
import time
import urllib.error
import urllib.request
from collections import Counter

import pymupdf

from extract_kps import BASE_URL, api_key

MODEL = 'deepseek-flash'
MIN_AREA = 0.05          # 不到页面 5% 的图标不处理
REPEAT_PAGES = 3         # 出现在 3 页以上的图（校徽、底栏）当装饰

PROMPT = """这是大学课件第 {page} 页里的一张图。页面上已经能读到的文字如下（不用重复）：
{page_text}

请把这张图里的内容写成文字，给后面整理知识点用：
1. 只写图里真正有的文字和关系，照原样抄写图里的字，不要改写；被遮挡或看不清的写“看不清”，不要猜，不要补充图里没有的内容。
2. 如果是流程图：按箭头顺序写出每一步；有分支的写清楚“什么条件 → 走到哪一步”。
3. 如果是软件截图：写出截图里能看到的关键文字和它说明了什么，界面上的按钮、菜单不用逐个列。
4. 如果是示意图：写出图里的元素和它们之间的关系。
5. 第一行写图的类型（流程图 / 截图 / 示意图 / 其他），然后写内容，不超过 300 字。"""


def find_images(doc):
    seen = Counter(im[0] for p in doc for im in p.get_images(full=True))
    area = doc[0].rect.width * doc[0].rect.height
    found = []
    for no, p in enumerate(doc, 1):
        n = 0
        for info in p.get_image_info(xrefs=True):
            x0, y0, x1, y1 = info['bbox']
            if seen.get(info['xref'], 0) > REPEAT_PAGES or (x1 - x0) * (y1 - y0) / area < MIN_AREA:
                continue
            n += 1
            found.append({'id': f'P{no}-图{n}', 'page': no, 'bbox': [round(v) for v in info['bbox']],
                          'xref': info['xref']})
    return found


def crop_png(page, bbox, dpi=150):
    """按图在页面上的位置截下来（比直接取原图稳：有的图是几张拼起来的）"""
    return page.get_pixmap(dpi=dpi, clip=pymupdf.Rect(bbox)).tobytes('png')


def look(png, prompt, key, model):
    url = 'data:image/png;base64,' + base64.b64encode(png).decode()
    # max_tokens 4096：2048 时 P30 被截断（模型的“思考”也算输出）
    body = json.dumps({'model': model, 'temperature': 0.1, 'max_tokens': 4096, 'messages': [{
        'role': 'user', 'content': [{'type': 'image_url', 'image_url': {'url': url}},
                                    {'type': 'text', 'text': prompt}]}]}).encode()
    req = urllib.request.Request(BASE_URL + '/chat/completions', data=body, method='POST',
                                 headers={'Content-Type': 'application/json', 'Authorization': 'Bearer ' + key})
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=120) as r:
        out = json.loads(r.read())
    c = out['choices'][0]
    return c['message']['content'].strip(), out.get('usage', {}), time.time() - t0, c.get('finish_reason')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('pdf')
    ap.add_argument('--pages', help='只处理这些页，逗号分隔；不给就处理全部')
    ap.add_argument('--model', default=MODEL)
    ap.add_argument('--out-dir', required=True)
    a = ap.parse_args()
    sys.stdout.reconfigure(encoding='utf-8')
    os.makedirs(a.out_dir, exist_ok=True)
    doc = pymupdf.open(a.pdf)
    images = find_images(doc)
    if a.pages:
        want = {int(x) for x in a.pages.split(',')}
        images = [im for im in images if im['page'] in want]
    key = api_key()
    log = [f'# 课件图片 → 文字（{os.path.basename(a.pdf)}）', '', f'- 时间：{time.strftime("%Y-%m-%d %H:%M")}',
           f'- 模型：{a.model}', '', '| 图 | 位置 | 耗时 | 输入 token | 输出 token | 结束原因 |', '|---|---|---|---|---|---|']
    results = []
    for im in images:
        page = doc[im['page'] - 1]
        png = crop_png(page, im['bbox'])
        name = im['id']
        open(os.path.join(a.out_dir, f'{name}.png'), 'wb').write(png)
        prompt = PROMPT.format(page=im['page'], page_text=' '.join(page.get_text().split())[:800])
        open(os.path.join(a.out_dir, f'{name}-提示词.txt'), 'w', encoding='utf-8').write(prompt)
        try:
            text, usage, sec, finish = look(png, prompt, key, a.model)
            if finish == 'length':   # 写到输出上限被截断：重试一次，两次都记下来
                open(os.path.join(a.out_dir, f'{name}-模型回复-截断.txt'), 'w', encoding='utf-8').write(text)
                log.append(f"| {name} | {im['bbox']} | {sec:.0f} 秒 | {usage.get('prompt_tokens')} | "
                           f"{usage.get('completion_tokens')} | length（截断，重试） |")
                text, usage, sec, finish = look(png, prompt, key, a.model)
        except urllib.error.HTTPError as e:
            msg = e.read().decode('utf-8', 'replace')[:300]
            log.append(f'| {name} | {im["bbox"]} | — | — | — | 调用失败：HTTP {e.code} {msg} |')
            print(f'{name}：调用失败 HTTP {e.code}：{msg}')
            continue
        open(os.path.join(a.out_dir, f'{name}-模型回复.txt'), 'w', encoding='utf-8').write(text)
        results.append({**im, 'text': text})
        log.append(f"| {name} | {im['bbox']} | {sec:.0f} 秒 | {usage.get('prompt_tokens')} | "
                   f"{usage.get('completion_tokens')} | {finish} |")
        print(f'{name}：{sec:.0f} 秒，输入 {usage.get("prompt_tokens")} token\n{text}\n')
    json.dump(results, open(os.path.join(a.out_dir, '图片描述.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    open(os.path.join(a.out_dir, '记录.md'), 'w', encoding='utf-8').write('\n'.join(log) + '\n')


if __name__ == '__main__':
    main()
