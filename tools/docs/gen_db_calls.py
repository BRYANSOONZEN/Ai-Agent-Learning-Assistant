# -*- coding: utf-8 -*-
"""
生成《数据库说明》第八节：每个功能调用数据库的实测记录。
数据来自 backend/target/sql-trace.json（SqlTraceIT 把 42 个功能真实走一遍，截下 MyBatis 打印的每条 SQL）；
调用链（Controller 方法 → 调了哪些 Service）从后端源码里找。
用法：python tools/docs/gen_db_calls.py   （由 build_docs.py 调用）
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', '..')
SRC = os.path.join(ROOT, 'backend', 'src', 'main', 'java', 'com', 'se', 'platform')
TRACE = os.path.join(ROOT, 'backend', 'target', 'sql-trace.json')
MAP = re.compile(r'@(Get|Post|Put|Delete)Mapping(?:\((?:value\s*=\s*)?"([^"]*)"[^)]*\)|\(\)|)')


def controllers():
    """(方法, 路径模板) → (类名.方法名, [调用的 Service.方法])"""
    out = {}
    for dp, _, fs in os.walk(SRC):
        for f in fs:
            if not f.endswith('Controller.java'):
                continue
            s = open(os.path.join(dp, f), encoding='utf-8').read()
            cls = f[:-5]
            m = re.search(r'@RequestMapping\("([^"]*)"\)', s)
            prefix = m.group(1) if m else ''
            fields = dict((name, typ) for typ, name in re.findall(r'private (?:final )?(\w+) (\w+);', s))
            lines = s.split('\n')
            for i, l in enumerate(lines):
                mm = MAP.search(l.strip())
                if not mm or not l.strip().startswith('@') or 'RequestMapping' in l:
                    continue
                j = i + 1
                while j < len(lines) and 'public ' not in lines[j]:
                    j += 1
                name = re.search(r'(\w+)\(', lines[j].split('public ', 1)[1]).group(1)
                body, depth, k = [], 0, j
                while k < len(lines):
                    body.append(lines[k])
                    depth += lines[k].count('{') - lines[k].count('}')
                    if depth <= 0 and '{' in ''.join(body):
                        break
                    k += 1
                calls = []
                for recv, meth in re.findall(r'\b(\w+)\.(\w+)\(', '\n'.join(body)):
                    typ = fields.get(recv)
                    if typ and not typ.endswith('Mapper'):
                        c = typ + '.' + meth
                        if c not in calls:
                            calls.append(c)
                out[(mm.group(1).upper(), prefix + (mm.group(2) or ''))] = (cls + '.' + name, calls)
    return out


def kind(sql):
    w = sql.split()[0].upper()
    return '读' if w == 'SELECT' else '写'


def tables(sql):
    return [t.lower() for t in re.findall(r'(?:FROM|JOIN|INTO|UPDATE)\s+`?(\w+)`?', sql, re.I)
            if t.upper() not in ('SELECT',)]


def short(sql, n=160):
    sql = re.sub(r'\s+', ' ', sql)
    sql = re.sub(r'SELECT (?!COUNT)[\w`,\s.()]+? FROM', 'SELECT … FROM', sql, count=1)
    sql = sql.replace('|', '\\|')
    return sql if len(sql) <= n else sql[:n] + '…'


def main():
    if not os.path.exists(TRACE):
        print('（没有 backend/target/sql-trace.json：先运行 `mvnw test -Dtest=SqlTraceIT -Dsurefire.failIfNoSpecifiedTests=false`）')
        return
    flows = json.load(open(TRACE, encoding='utf-8'))
    print('\n---\n\n## 八、每个功能调用数据库的实测记录（自动生成）\n')
    ctrl = controllers()
    print('下面是 **%d 个功能** 实际执行的全部 SQL，按功能的使用顺序排列。记录方法：`SqlTraceIT` 用内存库和演示数据把这些功能'
          '真实走一遍，截下 MyBatis 打印的每一条 SQL（大模型和资料处理服务用假的，只为了让流程走通，不影响 SQL）；'
          'MySQL 上执行的是同样的 SQL。`?` 是参数占位，值由 JDBC 单独传；`SELECT … FROM` 省略了列名（就是这张表的全部列）；'
          '连续重复的同一条 SQL 合并写“×次数”（例如交卷时每道题各存一行）。\n' % len(flows))

    # 总表：功能 × 读写了哪些表
    print('### 8.1 总表：每个功能读、写了哪些表\n')
    print('| # | 功能 | 谁用 | 接口 | SQL 条数 | 读的表 | 写的表 |\n|---|---|---|---|---|---|---|')
    for i, f in enumerate(flows, 1):
        r, w = [], []
        for s in f['sql']:
            for t in tables(s):
                (r if kind(s) == '读' else w).append(t)
        r = sorted(set(r))
        w = sorted(set(w))
        print('| %d | %s | %s | `%s %s` | %d | %s | %s |' % (i, f['title'], f['actor'], f['method'], f['path'], len(f['sql']),
                                                         '、'.join(r) or '—', '、'.join(w) or '—'))
    print()

    # 每个功能的明细
    print('### 8.2 每个功能的调用链和 SQL\n')
    group = None
    for i, f in enumerate(flows, 1):
        if f['group'] != group:
            group = f['group']
            print('#### %s\n' % group)
        c, calls = ctrl.get((f['method'], f['path']), ('（没找到对应的 Controller 方法）', []))
        print('**%d. %s**（%s）\n' % (i, f['title'], f['actor']))
        print('- 接口：`%s %s`' % (f['method'], f['path']))
        print('- 调用链：`%s`%s' % (c, (' → ' + '、'.join('`%s`' % x for x in calls)) if calls else ''))
        print('- 共执行 %d 条 SQL：\n' % len(f['sql']))
        if not f['sql']:
            print('（没有访问数据库）\n')
            continue
        print('| 序 | 读 / 写 | 表 | SQL |\n|---|---|---|---|')
        rows, prev, cnt = [], None, 0
        for s in f['sql'] + [None]:
            if s == prev:
                cnt += 1
                continue
            if prev is not None:
                rows.append((prev, cnt))
            prev, cnt = s, 1
        for n, (s, cnt) in enumerate(rows, 1):
            print('| %d | %s | %s | `%s`%s |' % (n, kind(s), '、'.join(dict.fromkeys(tables(s))) or '—', short(s),
                                              ' ×%d' % cnt if cnt > 1 else ''))
        print()


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main()
