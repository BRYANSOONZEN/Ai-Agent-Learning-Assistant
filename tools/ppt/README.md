# 最终答辩 PPT 生成脚本

用红色模板 `docs/汇报-中期.pptx` 的版式（封面、目录、章节页、内页），流程图用 PowerPoint 原生形状画（可以直接在 PowerPoint 里改）。

```
python tools/ppt/build_v2.py docs/汇报-最终答辩-初稿.pptx
```

- `helpers_v1.py`：模板、版式、文字框、表格、卡片等基础函数
- `diag.py`：流程图（按网格放节点、自动画箭头；红框 = 程序、绿框 = 老师、灰框 = 大模型、菱形 = 判断、虚线框 = 公式）
- `build_v2.py`：每一页的内容

生成的 .pptx 不提交（二进制）。
