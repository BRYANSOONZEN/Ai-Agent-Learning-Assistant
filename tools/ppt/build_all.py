# -*- coding: utf-8 -*-
"""最终答辩完整版：把开头（build_m0）、8 个模块（build_m1～m8）、结尾（build_m9）合成一份 PPT。
每个模块脚本原样执行，只去掉各自的封面、保存和打印；页码连续，左上角编号 = 模块编号（0 整体设计，9 总结）。
用法：python build_all.py 输出.pptx"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
g = {"__name__": "__main__", "__file__": os.path.join(HERE, "build_all.py")}
exec(open(os.path.join(HERE, "helpers_v1.py"), encoding="utf-8").read(), g)
sys.path.insert(0, HERE)

for i in range(10):
    src = open(os.path.join(HERE, "build_m%d.py" % i), encoding="utf-8").read().split("\n")
    keep = []
    for line in src:
        if 'helpers_v1.py' in line and line.startswith("exec("):
            continue
        if line.startswith("prs.save(") or line.startswith('print("saved"'):
            continue
        if i > 0 and line.startswith("cover("):
            continue
        keep.append(line)
    exec(compile("\n".join(keep), "build_m%d.py" % i, "exec"), g)

g["prs"].save(g["OUT"])
print("saved", g["OUT"], "slides", g["PAGE"][0])
