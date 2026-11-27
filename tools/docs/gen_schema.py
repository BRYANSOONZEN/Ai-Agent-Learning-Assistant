# -*- coding: utf-8 -*-
"""从 backend/src/main/resources/schema.sql 生成表结构清单（Markdown），保证《数据设计》和代码一致。
每张表：表上面的说明注释、每一列（类型、约束、注释）、索引和唯一约束。
用法：python tools/docs/gen_schema.py > 输出.md"""
import os
import re
import sys

SQL = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "backend", "src", "main", "resources", "schema.sql")


def parse(text):
    lines = text.split("\n")
    tables = []
    i = 0
    while i < len(lines):
        m = re.match(r"CREATE TABLE `?(\w+)`?\s*\(\s*(?:--\s*(.*))?$", lines[i].strip())
        if not m:
            i += 1
            continue
        name, inline = m.group(1), (m.group(2) or "").strip()
        # 表上面的说明：往上找连续的 -- 注释行，去掉分隔线
        notes = []
        j = i - 1
        while j >= 0 and lines[j].strip().upper().startswith("DROP TABLE"):
            j -= 1
        while j >= 0 and lines[j].strip().startswith("--"):
            t = lines[j].strip()[2:].strip()
            if t and not set(t) <= set("-="):
                notes.insert(0, t)
            j -= 1
        if inline:
            notes.insert(0, inline)
        cols, keys = [], []
        i += 1
        while i < len(lines) and not lines[i].strip().startswith(");"):
            raw = lines[i].strip()
            i += 1
            if not raw:
                continue
            if raw.startswith("--"):
                if cols:  # 接在上一列后面的续行注释
                    cols[-1][3] += " " + raw[2:].strip()
                continue
            body, _, comment = raw.partition("--")
            body = body.strip().rstrip(",")
            comment = comment.strip()
            if re.match(r"(PRIMARY KEY|CONSTRAINT|UNIQUE|KEY|INDEX)", body, re.I):
                keys.append(body + ("（" + comment + "）" if comment else ""))
                continue
            cm = re.match(r"`?(\w+)`?\s+([A-Z]+(?:\([\d, ]+\))?)\s*(.*)", body)
            if cm:
                cols.append([cm.group(1), cm.group(2), cm.group(3).strip(), comment])
        tables.append({"name": name, "notes": notes, "cols": cols, "keys": keys})
        i += 1
    indexes = re.findall(r"CREATE (?:UNIQUE )?INDEX (\w+) ON (\w+) \(([^)]*)\)", text)
    return tables, indexes


def md(s):
    return s.replace("|", "\\|")


def main():
    text = open(SQL, encoding="utf-8").read()
    tables, indexes = parse(text)
    print("共 %d 张表（来源：`backend/src/main/resources/schema.sql`）。\n" % len(tables))
    for t in tables:
        print("#### `%s`\n" % t["name"])
        if t["notes"]:
            print(md(" ".join(t["notes"])) + "\n")
        print("| 列 | 类型 | 约束 / 默认值 | 说明 |\n|---|---|---|---|")
        for c in t["cols"]:
            print("| `%s` | %s | %s | %s |" % (c[0], c[1], md(c[2]) or "", md(c[3]) or ""))
        extra = t["keys"] + ["索引 %s（%s）" % (n, cols) for n, tb, cols in indexes if tb == t["name"]]
        if extra:
            print("\n主键 / 约束 / 索引：" + "；".join(md(x) for x in extra))
        print()


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
