# -*- coding: utf-8 -*-
"""最终答辩 PPT 初稿：沿用中期汇报模板（主题、字体、版式风格），全部用图和表讲清楚设计和规则。"""
import copy
import sys
from pptx import Presentation
from pptx.util import Inches, Pt, Emu
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE, MSO_CONNECTOR
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.oxml.ns import qn
from lxml import etree

TEMPLATE = r"C:/ZI/se-learning-platform/docs/汇报-中期.pptx"
OUT = sys.argv[1] if len(sys.argv) > 1 else r"C:/ZI/se-learning-platform/docs/汇报-最终答辩-初稿.pptx"

# 红色模板（北理工）：主色深红 940B01，灰色 595959；绿、橙、红只用在“画像颜色”等有含义的地方
NAVY, INK, TEAL, MINT = "940B01", "262626", "2F7D6D", "E6F2EF"
ORANGE, RED, GREY, LIGHT, LINE, WHITE = "C77A1F", "B4433A", "595959", "F5F5F5", "D9D9D9", "FFFFFF"
LORANGE, LRED, LBLUE = "FBF1E4", "F6E6E4", "EFEFEF"
FONT = "Microsoft YaHei"
FOOTER = "第 2 组 · 软件工程智能学习平台 · 最终答辩（初稿）"

prs = Presentation(TEMPLATE)
SRC = Presentation(TEMPLATE)  # 原模板：复制封面、目录、章节页上的文字框
LAYOUT = {l.name: l for m in prs.slide_masters for l in m.slide_layouts}
# 清掉模板里原来的幻灯片，只留主题和版式
sldIdLst = prs.slides._sldIdLst
for sldId in list(sldIdLst):
    prs.part.drop_rel(sldId.rId)
    sldIdLst.remove(sldId)
BLANK = LAYOUT["内页1"]
PAGE = [0]
SECTION = [0]

# 我的坐标按 13.33 × 7.5 英寸写，模板是 10 × 5.625：统一缩放，内容区落在标题线和底部红条之间
K = 0.74
def X(v): return v * K
def Y(v): return v * K - 0.12
def L(v): return v * K
def F(sz): return max(8, round(sz * K * 2) / 2)


def rgb(h):
    return RGBColor.from_string(h)


def set_font(run, size, color=INK, bold=False):
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.color.rgb = rgb(color)
    run.font.name = FONT
    rPr = run._r.get_or_add_rPr()
    for tag in ("a:ea", "a:cs"):
        el = rPr.find(qn(tag))
        if el is None:
            el = etree.SubElement(rPr, qn(tag))
        el.set("typeface", FONT)


def text(slide, x, y, w, h, lines, size=14, color=INK, bold=False, align="l", anchor="t", fill=None,
         line=None, shape=None, margin=0.08, spacing=1.15):
    """lines: str 或 [str | (str, {size,color,bold})]；每一项一段。"""
    if shape is None and fill is None and line is None:
        sp = slide.shapes.add_textbox(Inches(X(x)), Inches(Y(y)), Inches(L(w)), Inches(L(h)))
    else:
        sp = slide.shapes.add_shape(shape or MSO_SHAPE.RECTANGLE, Inches(X(x)), Inches(Y(y)), Inches(L(w)), Inches(L(h)))
        if shape == MSO_SHAPE.ROUNDED_RECTANGLE:
            sp.adjustments[0] = 0.05
        if fill:
            sp.fill.solid()
            sp.fill.fore_color.rgb = rgb(fill)
        else:
            sp.fill.background()
        if line:
            sp.line.color.rgb = rgb(line)
            sp.line.width = Pt(1)
        else:
            sp.line.fill.background()
        sp.shadow.inherit = False
    tf = sp.text_frame
    tf.word_wrap = True
    for side in ("margin_left", "margin_right", "margin_top", "margin_bottom"):
        setattr(tf, side, Inches(L(margin)))
    tf.vertical_anchor = {"t": MSO_ANCHOR.TOP, "m": MSO_ANCHOR.MIDDLE, "b": MSO_ANCHOR.BOTTOM}[anchor]
    if isinstance(lines, str):
        lines = [lines]
    for i, ln in enumerate(lines):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.alignment = {"l": PP_ALIGN.LEFT, "c": PP_ALIGN.CENTER, "r": PP_ALIGN.RIGHT}[align]
        p.line_spacing = spacing
        opts = {}
        if isinstance(ln, tuple):
            ln, opts = ln
        r = p.add_run()
        r.text = ln
        set_font(r, F(opts.get("size", size)), opts.get("color", color), opts.get("bold", bold))
    return sp


def card(slide, x, y, w, h, title, body, fill=LIGHT, tcolor=NAVY, size=12, tsize=14):
    lines = [(title, {"size": tsize, "bold": True, "color": tcolor})]
    lines += body if isinstance(body, list) else [body]
    return text(slide, x, y, w, h, lines, size=size, fill=fill, shape=MSO_SHAPE.ROUNDED_RECTANGLE, margin=0.14)


def arrow(slide, x1, y1, x2, y2, color=GREY):
    c = slide.shapes.add_connector(MSO_CONNECTOR.STRAIGHT, Inches(X(x1)), Inches(Y(y1)), Inches(X(x2)), Inches(Y(y2)))
    c.line.color.rgb = rgb(color)
    c.line.width = Pt(1.5)
    ln = c.line._get_or_add_ln()
    tail = etree.SubElement(ln, qn("a:tailEnd"))
    tail.set("type", "triangle")
    return c


def flow(slide, x, y, items, w=1.9, h=0.95, gap=0.35, fill=LBLUE, size=12, colors=None):
    """横向流程：items = [(标题, 说明)]"""
    for i, it in enumerate(items):
        t, d = it if isinstance(it, tuple) else (it, "")
        f = colors[i] if colors else fill
        lines = [(t, {"bold": True, "size": size + 1, "color": NAVY})] + ([d] if d else [])
        text(slide, x + i * (w + gap), y, w, h, lines, size=size - 1, fill=f, shape=MSO_SHAPE.ROUNDED_RECTANGLE,
             align="c", anchor="m", margin=0.08)
        if i < len(items) - 1:
            arrow(slide, x + i * (w + gap) + w + 0.03, y + h / 2, x + (i + 1) * (w + gap) - 0.03, y + h / 2)


def table(slide, x, y, w, rows, colw=None, size=12, rowh=0.38, head=NAVY, first_bold=True):
    nr, nc = len(rows), len(rows[0])
    shp = slide.shapes.add_table(nr, nc, Inches(X(x)), Inches(Y(y)), Inches(L(w)), Inches(L(rowh * nr)))
    tbl = shp.table
    tblPr = tbl._tbl.tblPr
    style = tblPr.find(qn("a:tableStyleId"))
    if style is not None:
        style.text = "{5940675A-B579-460E-94D1-54222C63F5DA}"  # 无样式、只有网格
    if colw:
        tot = sum(colw)
        for i, cw in enumerate(colw):
            tbl.columns[i].width = Inches(L(w) * cw / tot)
    for r in range(nr):
        tbl.rows[r].height = Inches(L(rowh))
        for c in range(nc):
            cell = tbl.cell(r, c)
            cell.margin_left = cell.margin_right = Inches(0.06)
            cell.margin_top = cell.margin_bottom = Inches(0.02)
            cell.vertical_anchor = MSO_ANCHOR.MIDDLE
            cell.fill.solid()
            cell.fill.fore_color.rgb = rgb(head if r == 0 else (WHITE if r % 2 else LIGHT))
            val = rows[r][c]
            opts = {}
            if isinstance(val, tuple):
                val, opts = val
            tf = cell.text_frame
            tf.word_wrap = True
            p = tf.paragraphs[0]
            run = p.add_run()
            run.text = str(val)
            set_font(run, F(opts.get("size", size)), opts.get("color", WHITE if r == 0 else INK),
                     opts.get("bold", r == 0 or (first_bold and c == 0)))
    return shp


def cost(slide, x, y, w, items, title="用时 / 费用（实测）"):
    """items = [(项目, 用时, 费用)]"""
    lines = [(title, {"bold": True, "size": 12, "color": TEAL})]
    for a, b, c in items:
        lines.append((f"{a}：{b} · {c}" if b or c else a, {"size": 11, "color": INK}))
    h = 0.42 + 0.27 * len(items)
    return text(slide, x, y, w, h, lines, fill=MINT, shape=MSO_SHAPE.ROUNDED_RECTANGLE, margin=0.12, spacing=1.1)


def new_slide(title, sub=None, notes=None):
    s = prs.slides.add_slide(BLANK)
    PAGE[0] += 1
    for ph in s.placeholders:
        if ph.placeholder_format.type == 1:  # TITLE
            ph.text_frame.text = title
        else:
            ph.text_frame.text = str(SECTION[0])
    if sub:
        sp = s.shapes.add_textbox(Inches(0.3), Inches(0.76), Inches(9.4), Inches(0.24))
        tf = sp.text_frame
        tf.margin_left = tf.margin_right = tf.margin_top = tf.margin_bottom = 0
        tf.word_wrap = True
        run = tf.paragraphs[0].add_run()
        run.text = sub
        set_font(run, 9, GREY)
    if notes:
        s.notes_slide.notes_text_frame.text = notes
    return s


def copy_shape(src_shape, dst_slide):
    el = copy.deepcopy(src_shape._element)
    dst_slide.shapes._spTree.insert_element_before(el, "p:extLst")
    return dst_slide.shapes[-1]


def set_text(shape, value):
    """保留第一个 run 的格式，换成新文字"""
    tf = shape.text_frame
    p0 = tf.paragraphs[0]
    runs = p0.runs
    runs[0].text = value
    for r_ in runs[1:]:
        r_._r.getparent().remove(r_._r)
    for p_ in tf.paragraphs[1:]:
        p_._p.getparent().remove(p_._p)


def dark_slide(title, sub_lines, notes=None):
    """章节页：用模板的“目录1&2-分”版式，复制模板第 3 页的编号和标题"""
    SECTION[0] += 1
    s = prs.slides.add_slide(LAYOUT["目录1&2-分"])
    PAGE[0] += 1
    g = copy_shape(SRC.slides[2].shapes[0], s)
    title = title.split("　", 1)[-1]
    for sh in g.shapes:
        t = sh.text_frame.text
        if t.strip() == "01":
            set_text(sh, "%02d" % SECTION[0])
        elif t == "整体框架":
            set_text(sh, title)
        else:
            set_text(sh, "　".join(sub_lines) if isinstance(sub_lines, list) else sub_lines)
    if notes:
        s.notes_slide.notes_text_frame.text = notes
    return s


def cover(title, sub, eng):
    s = prs.slides.add_slide(LAYOUT["封面3"])
    PAGE[0] += 1
    src = SRC.slides[0].shapes
    t = copy_shape(src[0], s)
    paras = t.text_frame.paragraphs
    paras[0].runs[0].text = title
    paras[1].runs[0].text = sub
    e = copy_shape(src[1], s)
    set_text(e, eng)
    return s


def toc(sections):
    s = prs.slides.add_slide(LAYOUT["目录2-总"])
    PAGE[0] += 1
    for sh in SRC.slides[1].shapes:
        if sh.has_text_frame and sh.text_frame.text.strip() in ("目 录", "CONTENTS"):
            copy_shape(sh, s)
    n = len(sections)
    w = 9.0 / n
    for i, (t, d) in enumerate(sections):
        x = 0.5 + i * w
        for (txt, y, h, sz, col, bold) in (("%02d" % (i + 1), 2.7, 0.5, 24, NAVY, True), (t, 3.3, 0.35, 13, INK, True), (d, 3.7, 0.9, 9, GREY, False)):
            sp = s.shapes.add_textbox(Inches(x), Inches(y), Inches(w - 0.1), Inches(h))
            tf = sp.text_frame
            tf.word_wrap = True
            p_ = tf.paragraphs[0]
            p_.alignment = PP_ALIGN.CENTER
            run = p_.add_run()
            run.text = txt
            set_font(run, sz, col, bold)
    return s


