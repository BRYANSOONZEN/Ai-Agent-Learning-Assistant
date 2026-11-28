# -*- coding: utf-8 -*-
"""
把讲稿（md）变成剪映能导入的字幕（srt）。
讲稿格式：**m:ss 标题** 开一段，下面一行或几行 “> 台词”。每段从这个时间开口，到下一段开头为止。
台词按标点切成一条条字幕（每条不超过 MAX 个字），按语速（每秒 CPS 个字）往后排；排不下就按比例压缩。
用法：python tools/demo/make_srt.py 讲稿.md 输出.srt
"""
import re
import sys

MAX = 20      # 每条字幕一般不超过几个字（剪映一行大约放得下）
HARD = 28     # 没有逗号可以断时，最长放到几个字
CPS = 4.2     # 语速：每秒几个字
GAP = 0.15    # 两条字幕之间空一点


def parse(md):
    segs = []
    cur = None
    for line in md.split('\n'):
        m = re.match(r'^\*\*(\d+):(\d{2})', line.strip())
        if m:
            cur = {'t': int(m.group(1)) * 60 + int(m.group(2)), 'text': ''}
            segs.append(cur)
            continue
        if line.startswith('>') and cur is not None:
            cur['text'] += line.lstrip('> ').strip()
        elif line.startswith('**（插入') and segs:
            cur = None  # 补图那段没有时间，接在最后一段后面
            segs.append({'t': None, 'text': ''})
            cur = segs[-1]
    return [s for s in segs if s['text']]


def chunks(text):
    # 先按句末标点切，再按逗号切，最后硬切
    parts = re.findall(r'[^。！？；]+[。！？；]?', text)
    out = []
    for p in parts:
        p = p.strip()
        while len(p) > MAX:
            cut = max((p.rfind(c, 0, MAX + 1) for c in '，、：'), default=-1)
            if cut < 6:
                # 前 MAX 个字里没有逗号：往后找第一个逗号，不超过 HARD 就在那里断；整句不超过 HARD 就不断
                nxt = min([i for i in (p.find(c, MAX) for c in '，、：') if i != -1] or [len(p)])
                if nxt < HARD:
                    cut = nxt
                elif len(p) <= HARD:
                    break
                else:
                    cut = MAX - 1
            out.append(p[:cut + 1])
            p = p[cut + 1:]
        if p:
            out.append(p)
    # 去掉每条末尾的逗号、句号（字幕习惯）
    return [re.sub(r'[，。；、]$', '', c).strip() for c in out if c.strip('，。 ')]


def ts(sec):
    ms = int(round(sec * 1000))
    h, ms = divmod(ms, 3600000)
    m, ms = divmod(ms, 60000)
    s, ms = divmod(ms, 1000)
    return '%02d:%02d:%02d,%03d' % (h, m, s, ms)


def main(src, dst):
    segs = parse(open(src, encoding='utf-8').read())
    lines = []
    for i, sg in enumerate(segs):
        start = sg['t']
        if start is None:  # 补图：接在上一段说完之后
            start = lines[-1][1] + 0.5 if lines else 0
        nxt = next((s['t'] for s in segs[i + 1:] if s['t'] is not None), None)
        cs = chunks(sg['text'])
        need = sum(len(c) / CPS + GAP for c in cs)
        room = (nxt - start - 0.3) if nxt is not None else need
        k = min(1.0, room / need) if need > 0 else 1.0
        t = start
        for c in cs:
            d = max(1.0, len(c) / CPS * k)
            lines.append((t, t + d, c))
            t += d + GAP * k
    with open(dst, 'w', encoding='utf-8-sig', newline='\r\n') as f:
        for n, (a, b, c) in enumerate(lines, 1):
            f.write('%d\n%s --> %s\n%s\n\n' % (n, ts(a), ts(b), c))
    print('%d 条字幕 → %s' % (len(lines), dst))


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main(sys.argv[1], sys.argv[2])
