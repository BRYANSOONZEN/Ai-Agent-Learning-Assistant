# -*- coding: utf-8 -*-
"""
把提交材料的 Markdown 转成 Word（.docx）：
  1. 解析本项目文档用到的 Markdown（标题、段落、表格、列表、引用、代码块、粗体、行内代码、链接、图片）；
  2. mermaid 图交给 render_mermaid.mjs 渲染成 PNG；
  3. 写成带样式的 HTML，再用本机 Word 打开、把图片嵌进文档、插入自动目录、A4 排版，另存为 .docx。
用法：python tools/docs/md2docx.py docs/提交材料/01-需求分析.md [输出.docx]
"""
import html
import json
import os
import re
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))

CSS = """
body { font-family: "宋体", SimSun, serif; font-size: 12pt; line-height: 1.5; }
h1 { font-family: "黑体", SimHei, sans-serif; font-size: 22pt; text-align: center; margin: 24pt 0 18pt; }
h2 { font-family: "黑体", SimHei, sans-serif; font-size: 16pt; margin: 18pt 0 8pt; }
h3 { font-family: "黑体", SimHei, sans-serif; font-size: 14pt; margin: 12pt 0 6pt; }
h4 { font-family: "黑体", SimHei, sans-serif; font-size: 12pt; margin: 10pt 0 4pt; }
p { margin: 4pt 0; text-align: justify; }
table { border-collapse: collapse; width: 100%; margin: 6pt 0 10pt; }
th, td { border: 1px solid #000; padding: 3pt 5pt; font-size: 10.5pt; vertical-align: top; }
th { background: #D9D9D9; font-family: "黑体", SimHei, sans-serif; font-weight: normal; }
code { font-family: Consolas, monospace; font-size: 10pt; }
pre { font-family: Consolas, monospace; font-size: 9.5pt; background: #F2F2F2; padding: 6pt; }
blockquote { margin: 4pt 0 4pt 18pt; color: #333; }
.fig { text-align: center; margin: 8pt 0; }
.caption { text-align: center; font-size: 10.5pt; margin-bottom: 10pt; }
"""


def inline(t):
    t = html.escape(t, quote=False)
    t = re.sub(r'`([^`]+)`', r'<code>\1</code>', t)
    t = re.sub(r'\*\*([^*]+)\*\*', r'<b>\1</b>', t)
    t = re.sub(r'!\[([^\]]*)\]\(([^)]+)\)', r'<img src="\2" alt="\1">', t)
    t = re.sub(r'\[([^\]]+)\]\(([^)]+)\)', r'\1', t)  # 链接只留文字
    return t


def cells(line):
    s = line.strip()
    if s.startswith('|'):
        s = s[1:]
    if s.endswith('|'):
        s = s[:-1]
    parts = re.split(r'(?<!\\)\|', s)
    return [p.strip().replace('\\|', '|') for p in parts]


def convert(md, src_dir, img_dir):
    lines = md.split('\n')
    out, figs = [], []
    i = 0
    n_fig = 0
    in_header = False
    while i < len(lines):
        ln = lines[i]
        s = ln.strip()
        # 文档开头的“文档信息 / 目录”区：换成 Word 的自动目录
        if s == '<!-- 文档信息 开始 -->':
            in_header = True
            i += 1
            continue
        if s == '<!-- 文档信息 结束 -->':
            in_header = False
            out.append('<p class="toc-title" style="text-align:center;font-family:黑体;font-size:16pt">目　录</p>')
            out.append('<p>[[TOC]]</p>')
            out.append('<br style="page-break-before:always" clear="all">')
            i += 1
            continue
        if in_header or s.startswith('<!--') or s == '---':
            i += 1
            continue
        if s.startswith('```'):
            lang = s[3:].strip()
            body = []
            i += 1
            while i < len(lines) and not lines[i].strip().startswith('```'):
                body.append(lines[i])
                i += 1
            i += 1
            if lang == 'mermaid':
                n_fig += 1
                name = 'fig%d' % n_fig
                figs.append({'name': name, 'code': '\n'.join(body)})
                out.append('<p class="fig"><img src="%s" data-fig="%s"></p>' % (os.path.join(img_dir, name + '.png').replace('\\', '/'), name))
            else:
                out.append('<pre>%s</pre>' % html.escape('\n'.join(body)))
            continue
        m = re.match(r'^(#{1,4})\s+(.*)$', s)
        if m:
            lv = len(m.group(1))
            text = m.group(2)
            if lv == 1 and ' · ' in text:  # 文档标题：项目名一行、文档名一行
                a1, a2 = text.split(' · ', 1)
                out.append('<h1>%s<br>%s</h1>' % (inline(a1), inline(a2)))
            else:
                out.append('<h%d>%s</h%d>' % (lv, inline(text), lv))
            i += 1
            continue
        if s.startswith('|') and i + 1 < len(lines) and re.match(r'^\|?\s*:?-{3,}', lines[i + 1].strip()):
            head = cells(s)
            i += 2
            rows = []
            while i < len(lines) and lines[i].strip().startswith('|'):
                rows.append(cells(lines[i]))
                i += 1
            # 列宽：按显示宽度算（汉字算 2、字母数字算 1）。短列（编号、优先级、标签）给刚好放得下的宽度，
            # 长文字列分剩下的宽度；一行大约 84 个半角宽
            def dw(x):
                return sum(2 if ord(ch) > 127 else 1 for ch in re.sub(r'[`*]', '', x))
            widest = [max(dw(r[k]) if k < len(r) else 0 for r in [head] + rows) for k in range(len(head))]
            fixed = {k: w + 3 for k, w in enumerate(widest) if w <= 18}
            flex = {k: min(w, 80) for k, w in enumerate(widest) if k not in fixed}
            room = max(84 - sum(fixed.values()), 24 * max(1, len(flex)))
            units = [fixed[k] if k in fixed else room * flex[k] / sum(flex.values()) for k in range(len(head))]
            total = sum(units)
            pct = [100 * u / total for u in units]
            def cell(tag, k, c):
                nw = ' nowrap' if k in fixed else ''
                return '<%s width="%.0f%%"%s style="width:%.1f%%">%s</%s>' % (tag, pct[k], nw, pct[k], inline(c), tag)
            t = ['<table width="100%" style="width:100%">',
                 '<tr>' + ''.join(cell('th', k, c) for k, c in enumerate(head)) + '</tr>']
            for r in rows:
                t.append('<tr>' + ''.join(cell('td', k, c) for k, c in enumerate(r)) + '</tr>')
            t.append('</table>')
            out.append('\n'.join(t))
            continue
        if re.match(r'^(\s*)[-*] ', ln):
            items = []
            while i < len(lines) and re.match(r'^(\s*)[-*] ', lines[i]):
                items.append(inline(re.sub(r'^\s*[-*] ', '', lines[i])))
                i += 1
            out.append('<ul>' + ''.join('<li>%s</li>' % x for x in items) + '</ul>')
            continue
        if re.match(r'^\d+\. ', s):
            items = []
            while i < len(lines) and re.match(r'^\d+\. ', lines[i].strip()):
                items.append(inline(re.sub(r'^\d+\. ', '', lines[i].strip())))
                i += 1
            out.append('<ol>' + ''.join('<li>%s</li>' % x for x in items) + '</ol>')
            continue
        if s.startswith('>'):
            out.append('<blockquote><p>%s</p></blockquote>' % inline(s.lstrip('> ')))
            i += 1
            continue
        if not s:
            i += 1
            continue
        # 普通段落：连续的非空行合成一段
        para = [s]
        i += 1
        while i < len(lines) and lines[i].strip() and not re.match(r'^(#|\||```|>|[-*] |\d+\. |<!--|---)', lines[i].strip()):
            para.append(lines[i].strip())
            i += 1
        out.append('<p>%s</p>' % inline(''.join(para)))
    return '\n'.join(out), figs


def main(src, dst):
    src = os.path.abspath(src)
    md = open(src, encoding='utf-8').read()
    work = tempfile.mkdtemp(prefix='md2docx-')
    img_dir = os.path.join(work, 'img')
    body, figs = convert(md, os.path.dirname(src), img_dir)
    if figs:
        fj = os.path.join(work, 'figs.json')
        json.dump(figs, open(fj, 'w', encoding='utf-8'), ensure_ascii=False)
        subprocess.run(['node', os.path.join(HERE, 'render_mermaid.mjs'), fj, img_dir], check=True)
        # 图片按实际大小（2 倍图的一半）显示，最宽 600 像素（约 16 厘米）
        from PIL import Image
        def size(m):
            p = m.group(1)
            w, h = Image.open(p).size
            k = min(1.0, 600 / (w / 2), 820 / (h / 2))  # 最宽约 16 厘米、最高约 22 厘米（一页以内）
            return '<img src="%s" width="%d" height="%d"' % (p, int(w / 2 * k), int(h / 2 * k))
        body = re.sub(r'<img src="([^"]+\.png)" data-fig="[^"]+"', size, body)
    # 相对路径的图片（界面截图）换成绝对路径
    body = re.sub(r'<img src="(?!/|[A-Za-z]:)([^"]+)"',
                  lambda m: '<img src="%s"' % os.path.join(os.path.dirname(src), m.group(1)).replace('\\', '/'), body)
    page = os.path.join(work, 'doc.html')
    open(page, 'w', encoding='utf-8').write(
        '<!doctype html><html><head><meta charset="utf-8"><style>%s</style></head><body>%s</body></html>' % (CSS, body))
    dst = os.path.abspath(dst)
    subprocess.run(['powershell', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', os.path.join(HERE, 'html2docx.ps1'),
                    page, dst], check=True)
    print('Word 文档 →', dst)


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    src = sys.argv[1]
    dst = sys.argv[2] if len(sys.argv) > 2 else os.path.splitext(src)[0] + '.docx'
    main(src, dst)
