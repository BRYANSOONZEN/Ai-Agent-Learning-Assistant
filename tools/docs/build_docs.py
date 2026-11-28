# -*- coding: utf-8 -*-
"""重新生成《接口设计》《数据设计》里的自动清单部分：保留标记行之前手写的内容，标记行之后换成脚本输出。
用法：python tools/docs/build_docs.py"""
import os
import subprocess
import sys
sys.stdout.reconfigure(encoding="utf-8")

HERE = os.path.dirname(os.path.abspath(__file__))
DOCS = os.path.join(HERE, "..", "..", "docs", "提交材料")
JOBS = [("03-接口设计.md", "gen_api.py"), ("04-数据设计.md", "gen_schema.py"), ("06-测试说明.md", "gen_tests.py"), ("07-数据库说明（MySQL）.md", ["gen_db_report.py", "gen_db_calls.py"])]

for name, script in JOBS:
    path = os.path.join(DOCS, name)
    text = open(path, encoding="utf-8").read()
    marker = text.index("<!-- 以下由 tools/docs/")
    head = text[:text.index("\n", marker) + 1]
    out = "".join(subprocess.run([sys.executable, os.path.join(HERE, sc)], capture_output=True, check=True).stdout.decode("utf-8")
                  for sc in (script if isinstance(script, list) else [script]))
    open(path, "w", encoding="utf-8", newline="\n").write(head + "\n" + out)
    print("updated", name)

# 清单变了，目录跟着刷新
subprocess.run([sys.executable, os.path.join(HERE, "normalize_docs.py")], check=True)
