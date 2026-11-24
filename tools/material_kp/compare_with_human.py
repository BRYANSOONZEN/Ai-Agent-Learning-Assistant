"""
知识点识别的准确率（第 3 步，最后一层）：和人工列出的知识点清单对比。

  python compare_with_human.py --human 人工知识点.txt --draft 04_知识点草稿_第1次.json --out 结果.json

人工清单的格式（每行一个知识点，| 后面可选填课件页码，帮助对上）：
  自动化测试的定义与局限 | P4,P5,P6
  智能测试的三大特性 | P9

对上的规则：名称的字重合度 ≥ 0.4，或者页码大部分相同（和 extract_kps.py 判断稳定性的规则一样）。
  查准率 = 模型列出的知识点里，人也列了的 ÷ 模型列出的
  查全率 = 人列出的知识点里，模型也列了的 ÷ 人列出的
两个都对不上的会分别列出来，方便看是模型多列了、漏列了，还是只是叫法不同（这种情况由人判断后手动改清单再跑）。
"""
import argparse
import json
import sys

from extract_kps import SAME_KP_MIN, same_kp


def load_human(path):
    kps = []
    for line in open(path, encoding='utf-8'):
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        name, _, pages = line.partition('|')
        kps.append({'name': name.strip(), 'sources': [p.strip() for p in pages.split(',') if p.strip()]})
    return kps


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--human', required=True)
    ap.add_argument('--draft', required=True)
    ap.add_argument('--out', required=True)
    a = ap.parse_args()
    sys.stdout.reconfigure(encoding='utf-8')
    human = load_human(a.human)
    model = json.load(open(a.draft, encoding='utf-8'))['knowledge_points']

    def best(kp, pool):
        return max(((same_kp(kp, k)[0], k['name']) for k in pool), default=(0, None))

    model_rows = [{'模型的': k['name'], '最像的人工': best(k, human)[1], '分数': round(best(k, human)[0], 2),
                   '对上': best(k, human)[0] >= SAME_KP_MIN} for k in model]
    human_rows = [{'人工的': k['name'], '最像的模型': best(k, model)[1], '分数': round(best(k, model)[0], 2),
                   '对上': best(k, model)[0] >= SAME_KP_MIN} for k in human]
    precision = sum(r['对上'] for r in model_rows) / len(model_rows) if model_rows else None
    recall = sum(r['对上'] for r in human_rows) / len(human_rows) if human_rows else None
    out = {'人工知识点数': len(human), '模型知识点数': len(model), '查准率': precision, '查全率': recall,
           '模型多列的': [r['模型的'] for r in model_rows if not r['对上']],
           '模型漏列的': [r['人工的'] for r in human_rows if not r['对上']],
           '明细': {'模型 → 人工': model_rows, '人工 → 模型': human_rows}}
    json.dump(out, open(a.out, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    print(f'人工 {len(human)} 个，模型 {len(model)} 个；查准率 {precision:.0%}，查全率 {recall:.0%}')
    print('模型多列的：', out['模型多列的'] or '无')
    print('模型漏列的：', out['模型漏列的'] or '无')


if __name__ == '__main__':
    main()
