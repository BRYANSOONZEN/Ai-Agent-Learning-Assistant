# -*- coding: utf-8 -*-
"""重新生成《接口设计》《数据设计》里的自动清单部分：保留标记行之前手写的内容，标记行之后换成脚本输出。
用法：python tools/docs/build_docs.py"""
import os
import subprocess
import sys
sys.stdout.reconfigure(encoding="utf-8")

HERE = os.path.dirname(os.path.abspath(__file__))
DOCS = os.path.join(HERE, "..", "..", "docs", "提交材料")
JOBS = [("03-接口设计.md", "gen_api.py"), ("04-数据设计.md", "gen_schema.py")]

for name, script in JOBS:
    path = os.path.join(DOCS, name)
    text = open(path, encoding="utf-8").read()
    marker = text.index("<!-- 以下由 tools/docs/")
    head = text[:text.index("\n", marker) + 1]
    out = subprocess.run([sys.executable, os.path.join(HERE, script)], capture_output=True, check=True).stdout.decode("utf-8")
    open(path, "w", encoding="utf-8", newline="\n").write(head + "\n" + out)
    print("updated", name)
