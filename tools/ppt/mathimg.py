# -*- coding: utf-8 -*-
"""把数学公式渲染成透明背景的 PNG（matplotlib mathtext，Computer Modern 字体），插到幻灯片上。"""
import hashlib
import os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
from pptx.util import Inches  # noqa: E402

matplotlib.rcParams["mathtext.fontset"] = "cm"
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "math")
os.makedirs(OUT, exist_ok=True)


def render(expr, size=20):
    """返回 (png 路径, 宽英寸, 高英寸)；按 size 点数渲染"""
    key = hashlib.md5((expr + str(size)).encode("utf-8")).hexdigest()[:12]
    path = os.path.join(OUT, key + ".png")
    fig = plt.figure(figsize=(0.01, 0.01))
    t = fig.text(0, 0, expr, fontsize=size)
    fig.savefig(path, dpi=300, transparent=True, bbox_inches="tight", pad_inches=0.02)
    plt.close(fig)
    from PIL import Image
    w, h = Image.open(path).size
    return path, w / 300, h / 300


def place(slide, expr, x, y, height=None, size=20):
    """在 (x, y) 英寸处放公式；默认按渲染大小放（size 点 ≈ 屏幕上同样大小）"""
    path, w, h = render(expr, size)
    scale = 1.0 if height is None else height / h
    pic = slide.shapes.add_picture(path, Inches(x), Inches(y), Inches(w * scale), Inches(h * scale))
    return pic, w * scale, h * scale
