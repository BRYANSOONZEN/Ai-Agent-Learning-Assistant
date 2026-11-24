"""
视频转文字的准确率（第 2 步）：用课件里的专业词检查识别得对不对。

  python asr_term_check.py --terms 专业词.txt --video-json 6-1.json 6-2.json ... --out 结果.json

做法：
  - 专业词清单来自同一章的课件（课件里写对了的词，视频里讲到时也应该是这几个字）。
  - 对每个专业词，在视频文字里找“完全一样”的（识别对了），和“疑似错写”：
      中文：只差一个字（测试用例 → 测试用力）；两个字的词误报太多，不找。
      英文：首字母相同、编辑距离不超过一半长度（DeepSeek → deeps，Cursor → cosor）。
  - 疑似错写附上前后文，由人确认哪些是真的识别错误（--confirmed 清单），准确率只按确认过的算。

专业词识别正确率 = 识别对的次数 ÷（识别对的次数 + 识别错的次数）

这是自动的粗略检查，只看专业词。整体的字错误率要人工校对一段再算（见 asr_cer.py）。
"""
import argparse
import json
import re
import sys


def load_terms(path):
    return [t.strip() for t in open(path, encoding='utf-8') if t.strip() and not t.startswith('#')]


def near_cn(term, text):
    """只差一个字的写法（长度相同、恰好一个位置不同），附上前后文。两个字的词误报太多，不找"""
    n, out = len(term), []
    if n < 3:
        return out
    for i in range(len(text) - n + 1):
        w = text[i:i + n]
        if w != term and sum(x != y for x, y in zip(w, term)) == 1 and re.fullmatch(r'[一-鿿]+', w):
            out.append((w, text[max(0, i - 8):i + n + 8]))
    return out


def edit(a, b):
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def near_en(term, text):
    """首字母相同、编辑距离不超过一半长度的英文写法（DeepSeek → deeps，Cursor → cosor）"""
    t, out = term.lower().replace(' ', ''), []
    for m in re.finditer(r'[A-Za-z]+', text):
        lw = m.group().lower()
        if lw != t and len(lw) >= 3 and lw[0] == t[0] and edit(lw, t) <= max(2, len(t) // 2):
            out.append((m.group(), text[max(0, m.start() - 8):m.end() + 8]))
    return out


def load_confirmed(path):
    """人工确认过的识别错误：每行“正确词<Tab>错写”"""
    if not path:
        return None
    pairs = set()
    for line in open(path, encoding='utf-8'):
        if line.strip() and not line.startswith('#'):
            right, wrong = line.rstrip('\n').split('\t')
            pairs.add((right.strip(), wrong.strip()))
    return pairs


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--terms', required=True)
    ap.add_argument('--video-json', nargs='+', required=True)
    ap.add_argument('--out', required=True)
    ap.add_argument('--confirmed', help='人工确认过的识别错误清单；不给就只列出疑似错误')
    a = ap.parse_args()
    sys.stdout.reconfigure(encoding='utf-8')
    terms = load_terms(a.terms)
    text = ''
    for p in a.video_json:
        d = json.load(open(p, encoding='utf-8'))
        text += ''.join(s['text'] for s in d['segments'])
    confirmed = load_confirmed(a.confirmed)
    rows, right, wrong, suspects = [], 0, 0, []
    for t in terms:
        en = bool(re.fullmatch(r'[A-Za-z ]+', t))
        ok = len(re.findall(re.escape(t), text, re.I)) if en else text.count(t)
        cands = near_en(t, text) if en else near_cn(t, text)
        suspects += [{'专业词': t, '疑似错写': w, '前后文': ctx} for w, ctx in cands]
        bad = [w for w, _ in cands if confirmed is not None and (t, w) in confirmed]
        right += ok
        wrong += len(bad)
        if ok or bad:
            rows.append({'专业词': t, '识别对': ok, '识别错': len(bad), '错成': sorted(set(bad)),
                         '找错': '两字词不找错' if not en and len(t) < 3 else '是'})
    rate = right / (right + wrong) if right + wrong else None
    result = {'专业词数': len(terms), '视频里出现过的': len(rows), '识别对': right,
              '识别错（人工确认）': wrong if confirmed is not None else '未确认',
              '专业词识别正确率': round(rate, 3) if confirmed is not None and rate is not None else '未确认',
              '明细': rows, '疑似错误（待人工确认）': suspects}
    json.dump(result, open(a.out, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    print(f'专业词 {len(terms)} 个，视频里出现 {len(rows)} 个；识别对 {right} 次；疑似错误 {len(suspects)} 处')
    if confirmed is None:
        for x in suspects:
            print(f"  {x['专业词']} → {x['疑似错写']}　…{x['前后文']}…")
        return
    print(f'人工确认的识别错误 {wrong} 次；专业词识别正确率 {rate:.1%}')
    for r in rows:
        if r['识别错']:
            print(f"  {r['专业词']}：对 {r['识别对']}，错 {r['识别错']}（{'、'.join(r['错成'])}）")


if __name__ == '__main__':
    main()
