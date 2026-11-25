# -*- coding: utf-8 -*-
"""原生 PowerPoint 流程图：按网格放节点，自动画直线 / 折线箭头。坐标单位：英寸（10 × 5.625 的模板）。
颜色约定（每张图右下角有图例）：红框 = 程序，绿框 = 老师，灰框 = 大模型，菱形 = 判断，虚线框 = 公式 / 说明。
"""
from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE, MSO_CONNECTOR
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.oxml.ns import qn
from lxml import etree

FONT = "Microsoft YaHei"
# 黑白：所有框白底黑框黑字，只靠形状区分（标准流程图符号）；公式 / 说明用灰色虚线框
INK_ = "262626"
STYLE = {k: ("FFFFFF", INK_) for k in ("p", "t", "ai", "s", "d", "e", "io", "db")}
STYLE["f"] = ("FFFFFF", "8C8C8C")


def _rgb(h):
    return RGBColor.from_string(h)


def _font(run, size, color="262626", bold=False):
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.color.rgb = _rgb(color)
    run.font.name = FONT
    rPr = run._r.get_or_add_rPr()
    for tag in ("a:ea", "a:cs"):
        el = rPr.find(qn(tag))
        if el is None:
            el = etree.SubElement(rPr, qn(tag))
        el.set("typeface", FONT)


def node(slide, cx, cy, w, h, text, kind="p", size=8, bold_first=True):
    fill, line = STYLE[kind]
    shape = {"d": MSO_SHAPE.DIAMOND, "e": MSO_SHAPE.FLOWCHART_TERMINATOR, "db": MSO_SHAPE.CAN,
             "io": MSO_SHAPE.FLOWCHART_DATA}.get(kind, MSO_SHAPE.RECTANGLE)
    sp = slide.shapes.add_shape(shape, Inches(cx - w / 2), Inches(cy - h / 2), Inches(w), Inches(h))
    sp.fill.solid()
    sp.fill.fore_color.rgb = _rgb(fill)
    sp.line.color.rgb = _rgb(line)
    sp.line.width = Pt(1)
    if kind == "f":
        sp.line.dash_style = 4  # dash
    sp.shadow.inherit = False
    tf = sp.text_frame
    tf.word_wrap = True
    for side in ("margin_left", "margin_right"):
        setattr(tf, side, Inches(0.04))
    tf.margin_top = tf.margin_bottom = Inches(0.02)
    tf.vertical_anchor = MSO_ANCHOR.MIDDLE
    lines = text.split("\n")
    for i, ln in enumerate(lines):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.alignment = PP_ALIGN.LEFT if kind == "f" else PP_ALIGN.CENTER
        r = p.add_run()
        r.text = ln
        color = "262626"
        _font(r, size if i else size + (0.5 if bold_first and kind != "f" else 0), color,
              bold=(i == 0 and bold_first and kind != "f") or kind == "e")
    return sp


def seg(slide, x1, y1, x2, y2, head=False, dash=False, color="595959"):
    c = slide.shapes.add_connector(MSO_CONNECTOR.STRAIGHT, Inches(x1), Inches(y1), Inches(x2), Inches(y2))
    c.line.color.rgb = _rgb(color)
    c.line.width = Pt(1.1)
    if dash:
        c.line.dash_style = 4
    if head:
        ln = c.line._get_or_add_ln()
        t = etree.SubElement(ln, qn("a:tailEnd"))
        t.set("type", "triangle")
        t.set("w", "sm")
        t.set("len", "sm")
    return c


def label(slide, x, y, text, size=7, color="262626", anchor="above"):
    """线旁边的“是 / 否”等小字：不填底色、不压在线上。
    anchor='above'：放在 (x, y) 正上方（横线用）；anchor='right'：左边缘在 x、竖直居中于 y（竖线用）"""
    width = 0.1 * len(text) + 0.02
    if anchor == "right":
        left, top = x, y - 0.09
    else:
        left, top = x - width / 2, y - 0.2
    sp = slide.shapes.add_textbox(Inches(left), Inches(top), Inches(width), Inches(0.18))
    tf = sp.text_frame
    tf.margin_left = tf.margin_right = tf.margin_top = tf.margin_bottom = 0
    tf.word_wrap = False
    p = tf.paragraphs[0]
    p.alignment = PP_ALIGN.LEFT if anchor == "right" else PP_ALIGN.CENTER
    r = p.add_run()
    r.text = text
    _font(r, size, color, bold=True)


def diagram(slide, nodes, edges, x0=0.3, y0=1.05, cw=1.56, rh=0.72, bw=1.38, bh=0.5, size=8, legend=True):
    """nodes: [(id, col, row, text, kind, {w,h})]；edges: [(a, b, label, opts)]，opts 可含 dash、via（'h' 先横后竖 / 'v' 先竖后横）"""
    pos = {}
    for n in nodes:
        nid, c, r, text, kind = n[:5]
        o = n[5] if len(n) > 5 else {}
        w = o.get("w", bw * (1.15 if kind == "d" else 1))
        h = o.get("h", bh * (1.35 if kind == "d" else 1))
        # 公式框从格子左边开始放；所有框都不能超出幻灯片（左右各留 0.25 英寸）
        cx = x0 + c * cw + (w / 2 if kind == "f" else cw / 2)
        cx = min(max(cx, 0.25 + w / 2), 9.75 - w / 2)
        cy = y0 + r * rh + rh / 2
        node(slide, cx, cy, w, h, text, kind, o.get("size", size))
        pos[nid] = (cx, cy, w, h)
    for e in edges:
        a, b = e[0], e[1]
        lab = e[2] if len(e) > 2 else ""
        o = e[3] if len(e) > 3 else {}
        ax, ay, aw, ah = pos[a]
        bx, by, bw_, bh_ = pos[b]
        dash = o.get("dash", False)
        if "out" in o:  # 指定出口、入口和中间拐点（网格坐标），每条线走自己的路，不和别的线重叠
            def side(cx, cy, w, h, sd):
                return {"t": (cx, cy - h / 2), "b": (cx, cy + h / 2), "l": (cx - w / 2, cy), "r": (cx + w / 2, cy)}[sd]
            pts = [side(ax, ay, aw, ah, o["out"])]
            for (c, r) in o.get("via_pts", []):
                pts.append((x0 + c * cw + cw / 2, y0 + r * rh + rh / 2))
            pts.append(side(bx, by, bw_, bh_, o["in"]))
            for i in range(len(pts) - 1):
                seg(slide, pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], i == len(pts) - 2, dash)
            if lab:
                (px, py), (qx, qy) = pts[0], pts[1]
                # 字放在第一段线的中点旁边：竖线放右边、横线放上方，不压线、不碰框
                if abs(px - qx) < 0.01:
                    label(slide, px + 0.05, (py + qy) / 2, lab, anchor="right")
                else:
                    label(slide, (px + qx) / 2, py - 0.03, lab)
            continue
        if abs(ay - by) < 0.05:  # 同一行
            sgn = 1 if bx > ax else -1
            x1, x2 = ax + sgn * aw / 2, bx - sgn * bw_ / 2
            seg(slide, x1, ay, x2, by, True, dash)
            if lab:
                label(slide, (x1 + x2) / 2, ay - 0.09, lab)
        elif abs(ax - bx) < 0.05:  # 同一列
            sgn = 1 if by > ay else -1
            y1, y2 = ay + sgn * ah / 2, by - sgn * bh_ / 2
            seg(slide, ax, y1, bx, y2, True, dash)
            if lab:
                label(slide, ax + 0.06, (y1 + y2) / 2, lab, anchor="right")
        elif o.get("via", "v") == "h":  # 先横后竖
            sgn = 1 if bx > ax else -1
            x1 = ax + sgn * aw / 2
            sy = 1 if by > ay else -1
            seg(slide, x1, ay, bx, ay, False, dash)
            seg(slide, bx, ay, bx, by - sy * bh_ / 2, True, dash)
            if lab:
                label(slide, (x1 + bx) / 2, ay - 0.09, lab)
        else:  # 先竖后横
            sy = 1 if by > ay else -1
            y1 = ay + sy * ah / 2
            sgn = 1 if bx > ax else -1
            seg(slide, ax, y1, ax, by, False, dash)
            seg(slide, ax, by, bx - sgn * bw_ / 2, by, True, dash)
            if lab:
                label(slide, ax + 0.06, (y1 + by) / 2, lab, anchor="right")
    if legend == "std":  # 标准流程图符号
        lx, ly = 4.45, 4.86
        lx, ly = 0.45, 4.72  # 放左下角空白处，不碰框
        for i, (k, t) in enumerate([("e", "开始 / 结束"), ("p", "处理"), ("d", "判断"), ("io", "输入 / 输出"), ("db", "数据")]):
            node(slide, lx + i * 0.76, ly, 0.22, 0.14 if k != "d" else 0.17, "", k, 6)
            sp = slide.shapes.add_textbox(Inches(lx + i * 0.76 + 0.14), Inches(ly - 0.09), Inches(0.62), Inches(0.18))
            tf = sp.text_frame
            tf.margin_left = tf.margin_right = tf.margin_top = tf.margin_bottom = 0
            r = tf.paragraphs[0].add_run()
            r.text = t
            _font(r, 6.5, "595959")
        return pos
    if legend:
        lx, ly = 7.05, 4.86
        for i, (k, t) in enumerate([("p", "程序"), ("t", "老师"), ("ai", "大模型"), ("d", "判断")]):
            node(slide, lx + i * 0.68, ly, 0.2, 0.12 if k != "d" else 0.16, "", k, 6)
            sp = slide.shapes.add_textbox(Inches(lx + i * 0.68 + 0.13), Inches(ly - 0.09), Inches(0.5), Inches(0.18))
            tf = sp.text_frame
            tf.margin_left = tf.margin_right = tf.margin_top = tf.margin_bottom = 0
            r = tf.paragraphs[0].add_run()
            r.text = t
            _font(r, 7, "595959")
    return pos


def formula(slide, x, y, w, h, lines, title="公式", size=8):
    """虚线框里的公式 / 规则；lines 每项一行"""
    sp = node(slide, x + w / 2, y + h / 2, w, h, "\n".join([title] + lines), "f", size, bold_first=False)
    r = sp.text_frame.paragraphs[0].runs[0]
    _font(r, size + 0.5, "262626", bold=True)
    return sp
