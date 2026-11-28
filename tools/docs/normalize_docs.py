# -*- coding: utf-8 -*-
"""
把 docs/提交材料 下 7 份文档的开头统一成提交格式：标题、文档信息表、修订记录、说明、目录。
可以重复运行：标记行 <!-- 文档信息 开始/结束 --> 之间的内容每次重写，正文不动。
用法：python tools/docs/normalize_docs.py
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
DOCS = os.path.join(HERE, '..', '..', 'docs', '提交材料')
DATE = '2026-11-28'
REPO = 'GitHub：BRYANSOONZEN/Ai-Agent-Learning-Assistant，分支 feature/ai-learning-studywise'

DOCS_INFO = [
    ('01-需求分析.md', 'SE-G2-01', '需求分析说明书'),
    ('02-组件设计.md', 'SE-G2-02', '组件设计说明书'),
    ('03-接口设计.md', 'SE-G2-03', '接口设计说明书'),
    ('04-数据设计.md', 'SE-G2-04', '数据设计说明书'),
    ('05-界面设计.md', 'SE-G2-05', '界面设计说明书'),
    ('06-测试说明.md', 'SE-G2-06', '测试报告'),
    ('07-数据库说明（MySQL）.md', 'SE-G2-07', '数据库说明书（MySQL）'),
]
BEGIN, END = '<!-- 文档信息 开始 -->', '<!-- 文档信息 结束 -->'


def split_header(text):
    """返回 (原来开头的说明行, 正文)。正文从第一个 '## ' 二级标题开始"""
    if BEGIN in text:
        notes = re.search(r'<!-- 说明 -->\n(.*?)\n<!-- 说明完 -->', text, re.S)
        body = text[text.index(END) + len(END):].lstrip('\n')
        return (notes.group(1).split('\n') if notes else []), body
    head, body = text.split('\n## ', 1)
    body = '## ' + body
    notes = []
    for line in head.split('\n'):
        line = line.strip()
        if line.startswith('>'):
            line = line.lstrip('> ').strip()
            if line and '第 2 组' not in line:
                notes.append(line)
    # 把被拆成两行的命令接回去
    merged = []
    for n in notes:
        if merged and n.startswith('`') and merged[-1].endswith('：'):
            merged[-1] += n
        else:
            merged.append(n)
    return merged, body


def toc(body):
    items = []
    for line in body.split('\n'):
        m = re.match(r'^(#{2,3}) (.+)$', line)
        if m:
            items.append(('' if m.group(1) == '##' else '  ') + '- ' + m.group(2).strip())
    return '\n'.join(items)


def main():
    for name, code, title in DOCS_INFO:
        p = os.path.join(DOCS, name)
        text = open(p, encoding='utf-8').read()
        notes, body = split_header(text)
        head = [
            '# 软件工程智能学习平台 · %s' % title, '',
            BEGIN, '',
            '| 项目 | 内容 |', '|---|---|',
            '| 项目名称 | 软件工程智能学习平台 |',
            '| 文档名称 | %s |' % title,
            '| 文档编号 | %s |' % code,
            '| 版本 | V1.0 |',
            '| 日期 | %s |' % DATE,
            '| 编写 | 第 2 组 |',
            '| 代码仓库 | %s |' % REPO, '',
            '**修订记录**', '',
            '| 版本 | 日期 | 说明 |', '|---|---|---|',
            '| V1.0 | %s | 按提交时的代码整理，首次提交 |' % DATE, '',
        ]
        if notes:
            head += ['**说明**', '', '<!-- 说明 -->'] + notes + ['<!-- 说明完 -->', '']
        head += ['**目录**', '', toc(body), '', END, '', '---', '']
        # 说明行在页面上显示成列表
        out = '\n'.join(head)
        out = re.sub(r'<!-- 说明 -->\n(.*?)\n<!-- 说明完 -->',
                     lambda m: '<!-- 说明 -->\n' + '\n'.join(l if l.startswith('- ') else '- ' + l
                                                           for l in m.group(1).split('\n')) + '\n<!-- 说明完 -->',
                     out, flags=re.S)
        open(p, 'w', encoding='utf-8', newline='\n').write(out + '\n' + body)
        print('ok', name)


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main()
