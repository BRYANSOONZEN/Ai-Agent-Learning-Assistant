"""
把开发用的 H2 数据库（backend/data）里的数据搬到 MySQL：读 H2 导出的 SQL（SCRIPT SIMPLE COLUMNS），
转成 MySQL 能执行的 INSERT，写到一个 .sql 文件。表结构不在这里建：MySQL 的表由后端第一次启动时按
schema.sql 建好（和 H2 同一份建表语句）。

用法：
  1. 导出 H2（后端可以开着，库开了 AUTO_SERVER）：
     java -cp h2-2.3.232.jar org.h2.tools.Shell -url "jdbc:h2:file:<backend>/data/platform;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;AUTO_SERVER=TRUE"
          -user sa -sql "SCRIPT SIMPLE COLUMNS TO 'h2dump.sql' CHARSET 'UTF-8'"
  2. python h2_to_mysql.py h2dump.sql mysql_cols.tsv out.sql
     mysql_cols.tsv：MySQL 里每张表的列（information_schema 查出来，表名<Tab>逗号分隔的列名）
  3. 在 MySQL 里执行 out.sql（文件开头会清空要导入的表、设 NO_BACKSLASH_ESCAPES）

转换做的事：
  - "public"."表" / "列" → `表` / `列`
  - H2 的 U&'...\\XXXX...' Unicode 转义 → 普通字符串（中文原样写出）
  - MySQL 里没有的表（已删掉的旧表 alert、intervention）跳过；MySQL 里没有的列连同它的值一起去掉
"""
import re
import sys


def statements(text):
    """按分号切语句，引号里的分号不算"""
    out, buf, q, i = [], [], False, 0
    while i < len(text):
        c = text[i]
        buf.append(c)
        if c == "'":
            if q and i + 1 < len(text) and text[i + 1] == "'":
                buf.append("'")
                i += 1
            else:
                q = not q
        elif c == ';' and not q:
            out.append(''.join(buf)[:-1].strip())
            buf = []
        i += 1
    if ''.join(buf).strip():
        out.append(''.join(buf).strip())
    return out


def split_top(s):
    """按顶层逗号切值列表（引号、括号里的逗号不算）"""
    parts, buf, q, depth, i = [], [], False, 0, 0
    while i < len(s):
        c = s[i]
        if c == "'":
            buf.append(c)
            if q and i + 1 < len(s) and s[i + 1] == "'":
                buf.append("'")
                i += 1
            else:
                q = not q
        elif not q and c == '(':
            depth += 1
            buf.append(c)
        elif not q and c == ')':
            depth -= 1
            buf.append(c)
        elif not q and depth == 0 and c == ',':
            parts.append(''.join(buf).strip())
            buf = []
        else:
            buf.append(c)
        i += 1
    parts.append(''.join(buf).strip())
    return parts


def unescape_u(lit):
    """U&'abc\\4e2d' → 'abc中'（H2 用 \\XXXX、\\+XXXXXX 表示字符，\\\\ 表示反斜杠）"""
    body = lit[3:-1]
    out, i = [], 0
    while i < len(body):
        c = body[i]
        if c == '\\':
            if body[i + 1] == '\\':
                out.append('\\')
                i += 2
            elif body[i + 1] == '+':
                out.append(chr(int(body[i + 2:i + 8], 16)))
                i += 8
            else:
                out.append(chr(int(body[i + 1:i + 5], 16)))
                i += 5
        else:
            out.append(c)
            i += 1
    return "'" + ''.join(out) + "'"


def value(v):
    if v.startswith("U&'"):
        return unescape_u(v)
    return v


INSERT = re.compile(r'^INSERT INTO "public"\."(\w+)"\((.*?)\) VALUES\((.*)\)$', re.S)


def main(dump, cols_file, out_file):
    mysql_cols = {}
    for line in open(cols_file, encoding='utf-8'):
        t, c = line.rstrip('\n').split('\t')
        mysql_cols[t] = c.split(',')
    rows, skipped_tables, dropped = {}, set(), {}
    for st in statements(open(dump, encoding='utf-8').read()):
        m = INSERT.match(st)
        if not m:
            continue
        table = m.group(1)
        if table not in mysql_cols:
            skipped_tables.add(table)
            continue
        cols = [c.strip().strip('"') for c in m.group(2).split(',')]
        vals = split_top(m.group(3))
        assert len(cols) == len(vals), (table, len(cols), len(vals))
        keep = [(c, value(v)) for c, v in zip(cols, vals) if c in mysql_cols[table]]
        for c in cols:
            if c not in mysql_cols[table]:
                dropped.setdefault(table, set()).add(c)
        rows.setdefault(table, []).append(
            'INSERT INTO `%s` (%s) VALUES (%s);' % (table, ', '.join('`%s`' % c for c, _ in keep),
                                                    ', '.join(v for _, v in keep)))
    with open(out_file, 'w', encoding='utf-8', newline='\n') as f:
        f.write("-- 由 tools/db/h2_to_mysql.py 从 H2 开发库转出来的数据\n")
        f.write("SET NAMES utf8mb4;\nSET SESSION sql_mode = CONCAT(@@sql_mode, ',NO_BACKSLASH_ESCAPES');\n")
        f.write("SET FOREIGN_KEY_CHECKS = 0;\n")
        for t in rows:
            f.write('TRUNCATE TABLE `%s`;\n' % t)
        for t, lines in rows.items():
            f.write('\n-- %s：%d 行\n' % (t, len(lines)))
            f.write('\n'.join(lines) + '\n')
        f.write("SET FOREIGN_KEY_CHECKS = 1;\n")
    print('表：', {t: len(v) for t, v in rows.items()})
    print('跳过的表（MySQL 里没有）：', sorted(skipped_tables))
    print('去掉的列（MySQL 里没有）：', {t: sorted(c) for t, c in dropped.items()})


if __name__ == '__main__':
    main(*sys.argv[1:4])
