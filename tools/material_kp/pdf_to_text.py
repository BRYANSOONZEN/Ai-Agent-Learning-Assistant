"""
PDF 课件 → 按页的文字（模块一：资料处理）。

  python pdf_to_text.py 课件.pdf --out 结果.json

输出：[{"id": "P4", "source": "pdf", "file": "...", "page": 4, "text": "..."}, ...]
  - 每页一段，id 用来让大模型标出处（P4 = 第 4 页）。
  - 几乎没有字的页（封面、纯图片页、章节过渡页）不要，但会在 skipped 里记下来。

清理：
  - 第一行是页面标题，后面加“：”和正文隔开；只有数字的行（页码、左上角的章节号）去掉。
  - 去掉每页开头的页码（课件每页左上角的数字会被当成正文）。
  - 两个大写字母被拆开的合并（“U I” → “UI”）。
  - 合并被拆开的字母（“D e e p S e e k” → “DeepSeek”，PDF 里常见的字距问题）。
  - 去掉自动换行在两个汉字之间留下的空格（“系统 化” → “系统化”）。
  - 合并多余的空白。
图片里的文字（流程图、截图）取不出来；需要时以后再加 OCR。
"""
import argparse
import json
import os
import re
import sys

import pymupdf

MIN_CHARS = 30   # 少于这么多字的页当作封面、过渡页或纯图片页


def clean(text, page):
    t = ' '.join(text.split())
    # 页首的页码：第 4 页的文字以 "4 " 开头
    t = re.sub(rf'^{page}\s+', '', t)
    # 被拆开的英文字母或数字：三个以上单字符用空格隔开（D e e p S e e k、C I / C D）
    t = re.sub(r'(?<![A-Za-z0-9])((?:[A-Za-z0-9/] ){2,}[A-Za-z0-9/])(?![A-Za-z0-9])',
               lambda m: m.group(1).replace(' ', ''), t)
    # 两个大写字母被拆开（U I → UI）
    t = re.sub(r'(?<![A-Za-z])([A-Z]) ([A-Z])(?![A-Za-z])', r'\1\2', t)
    # 课件里一行写不下自动换行，两个汉字之间会多出空格（“系统 化”）
    t = re.sub(r'(?<=[一-鿿，。、；：）]) (?=[一-鿿，。、；：（])', '', t)
    return t.strip()


def page_text(page, no):
    """按 PDF 自带的阅读顺序取（多栏也是一栏读完再读下一栏）。
    只有数字的行（页码、左上角的章节号 1、3.1）不要；第一行是页面标题，后面加“：”和正文隔开。
    （试过按文字块的位置重新排序，多栏页面会左右交错，反而更乱，所以不排序）"""
    lines = [ln.strip() for ln in page.get_text().splitlines()]
    lines = [ln for ln in lines if ln and not re.fullmatch(r'[\d.]+', ln)]
    if not lines:
        return ''
    title, body = clean(lines[0], no), clean(' '.join(lines[1:]), no)
    return title + ('：' + body if body else '')


def pdf_pages(path):
    doc = pymupdf.open(path)
    name = os.path.basename(path)
    pages, skipped = [], []
    for i, p in enumerate(doc, 1):
        t = page_text(p, i)
        if len(t) < MIN_CHARS:
            skipped.append(i)
            continue
        pages.append({'id': f'P{i}', 'source': 'pdf', 'file': name, 'page': i, 'text': t})
    return pages, skipped, len(doc)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('pdf')
    ap.add_argument('--out', required=True)
    a = ap.parse_args()
    sys.stdout.reconfigure(encoding='utf-8')
    pages, skipped, total = pdf_pages(a.pdf)
    with open(a.out, 'w', encoding='utf-8') as f:
        json.dump({'file': os.path.basename(a.pdf), 'pages': total, 'skipped': skipped, 'segments': pages},
                  f, ensure_ascii=False, indent=1)
    chars = sum(len(p['text']) for p in pages)
    print(f'{total} 页，取到 {len(pages)} 页、{chars} 字；跳过 {len(skipped)} 页（{skipped}）')


if __name__ == '__main__':
    main()
