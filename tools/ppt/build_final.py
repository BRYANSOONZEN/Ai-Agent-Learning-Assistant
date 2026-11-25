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


# =====================================================================================================
# 封面、问题、闭环、原则
# =====================================================================================================
cover("软件工程智能学习平台", "最终答辩", "SE LEARNING PLATFORM · FINAL DEFENSE · GROUP 2")
toc([("整体设计", "问题、闭环、三条原则"), ("老师端", "资料 → 知识点 → 对话草稿 → 练习题"),
     ("学生学习与诊断", "学完判定、AI 对话、只算第一次、画像颜色"), ("小助手", "两个阶段、补练、拓展、聊天"),
     ("老师干预", "分析时机、两条规则、方案、推送和效果"), ("总结", "对照、质量、成本、下一步")])
SECTION[0] = 0
dark_slide("整体设计", "问题 · 闭环 · 三条原则")

s = new_slide("要解决的问题：学生跳过思考，老师看不到卡点",
              "平台的目标：先让学生自己想、自己讲，再用数据告诉学生和老师哪里没掌握")
card(s, 0.5, 1.6, 5.9, 2.4, "学生这一边", [
    "遇到题直接让通用 AI 给答案，思考被跳过",
    "做完题只知道对错，不知道错在哪一条要点、该回去看哪里",
    "重做同一道题会“越做越对”，但不一定真的会了"], fill=LRED, tcolor=RED, size=13)
card(s, 6.9, 1.6, 5.9, 2.4, "老师这一边", [
    "不知道全班卡在哪个知识点、哪一条要点",
    "分不清是“学生不会”还是“题目出得有问题”",
    "人工出题、批简答、逐个跟进学生，工作量太大"], fill=LORANGE, tcolor=ORANGE, size=13)
card(s, 0.5, 4.3, 12.3, 2.4, "我们的做法（后面每一部分展开）", [
    "① 资料上传后自动识别知识点和要点，老师确认，后面所有模块都用它",
    "② AI 对话：学生把知识点讲给 AI 同学听，只诊断、不打分",
    "③ 练习：只算第一次作答；老师选考查类型和题型，AI 出题、程序检查、老师确认",
    "④ 小助手：自动服务学生个人（补练、拓展、截止提醒、答疑但不给答案）",
    "⑤ 老师干预：只处理全班问题，按“要点 × 考查类型”给推荐方案，全部在平台上完成"],
     fill=MINT, tcolor=TEAL, size=13)

s = new_slide("整体闭环：学 → 记 → 诊 → 画 → 荐 → 干", "每一环对应必做 M1–M10，在平台里由哪个模块完成")
steps = [("学", "M1–M5、M7", "资料 → 知识点\nAI 对话 · 练习"), ("记", "M6", "学习事件\n作答记录（只算第一次）"),
         ("诊", "M8", "掌握度、证据\n错在哪条要点"), ("画", "M9", "画像颜色\n绿 / 橙 / 红"),
         ("荐", "M9", "小助手个人补练\n拓展（更难的题）"), ("干", "M10", "老师全班干预\n补充讲解 + 新题")]
for i, (a, b, c) in enumerate(steps):
    x = 0.6 + i * 2.1
    text(s, x, 1.9, 1.7, 1.7, [(a, {"size": 40, "bold": True, "color": WHITE}), (b, {"size": 12, "color": "D5DDE4"})],
         fill=NAVY if i % 2 == 0 else TEAL, shape=MSO_SHAPE.OVAL, align="c", anchor="m")
    text(s, x - 0.15, 3.8, 2.0, 1.0, c.split("\n"), size=12, align="c", color=INK)
    if i < 5:
        arrow(s, x + 1.72, 2.75, x + 2.08, 2.75)
text(s, 0.6, 5.2, 12.1, 1.3, [("三方分工", {"bold": True, "color": NAVY, "size": 14}),
     "大模型：起草（知识点、对话草稿、题目、讲解）和理解学生的话；程序：判对错、算分数、做所有规则判断；老师：确认后内容才给学生，处理全班问题"],
     fill=LIGHT, shape=MSO_SHAPE.ROUNDED_RECTANGLE, size=13, margin=0.15)

s = new_slide("三条设计原则", "后面每个模块都按这三条来设计，阈值都可以在配置里调")
for i, (t, d, f, c) in enumerate([
        ("大模型只起草，老师确认才给学生", ["知识点、对话草稿、练习题都先是草稿", "老师可以改，确认后学生才看得到", "例外：小助手给个人的补练题、拓展题当场生成（时间原因，组内决定），只给这个学生"], LBLUE, NAVY),
        ("对错由程序判，不靠大模型", ["客观题程序判分", "大模型的判断要经过程序核对（引用原话必须存在、状态只升不降）", "简答判两次，一致才采用，不一致交给老师"], MINT, TEAL),
        ("只算第一次作答", ["第一次最能反映真实水平", "重做只是练习，不改原始掌握度", "补练、推送的新题单独记，用来判断“补回来没有”"], LORANGE, ORANGE)]):
    card(s, 0.5 + i * 4.15, 1.6, 3.95, 4.6, t, d, fill=f, tcolor=c, size=13, tsize=16)

# =====================================================================================================
# 第一部分：老师端
# =====================================================================================================
dark_slide("老师端", "资料 → 知识点 → AI 对话草稿 → 练习题")

s = new_slide("资料上传即转文字", "老师上传后，后台自动把每种资料转成一段一段带编号的文字，后面识别知识点、出题、复习位置都用它",
              "PDF 用本机程序读文字；图片交给大模型看图；视频在本机做语音识别，不上传声音。")
rows = [["资料", "怎么处理", "切成多大一段", "编号（出处）"],
        ["PDF 课件", "本机读出每页文字", "一页一段", "D1-P4 = 第 1 份资料第 4 页"],
        ["PDF 里的图片", "大模型看图，写出图里的内容", "一张图一段", "D1-P13-图1"],
        ["视频", "本机语音识别，每句带开始时间；去掉“嗯、呃”", "攒够 30 秒且一句说完成一段，最长 60 秒", "V1-02 = 第 1 个视频第 2 段（01:14～01:52）"],
        ["老师写的文字", "直接用", "一段", "T0"]]
table(s, 0.5, 1.55, 8.2, rows, colw=[1.4, 2.6, 2.3, 2.6], size=12, rowh=0.62)
cost(s, 9.0, 1.55, 3.8, [("PDF 文字", "0.7 秒", "免费"), ("PDF 图片 29 张", "172 秒", "约 0.35 元"),
                         ("视频 4 个", "约 7 分钟", "免费（本机）"), ("一章合计", "约 11 分钟", "约 0.5 元")])
text(s, 9.0, 3.5, 3.8, 2.2, [("为什么要编号", {"bold": True, "color": NAVY, "size": 13}),
     "知识点、要点都带出处编号，学生做错时，小助手能直接跳到“视频那一秒、课件那一页”"],
     fill=LIGHT, shape=MSO_SHAPE.ROUNDED_RECTANGLE, size=12, margin=0.14)

s = new_slide("识别知识点：什么时候触发、交给大模型什么", "老师点“上传完毕”后自动识别，识别完提醒老师确认")
flow(s, 0.5, 1.6, [("老师上传资料", "可以分几次传"), ("转文字", "后台逐份处理"), ("点“上传完毕”", "触发识别"),
                   ("大模型识别", "整章资料一次"), ("提醒老师", "确认 / 修改")], w=2.15, h=1.0, gap=0.37)
card(s, 0.5, 3.0, 5.5, 3.7, "交给大模型的（提示词 kp_extract_v1）", [
    "课程名、章节名",
    "整章资料的文字，每段带编号",
    "规则：每个知识点写名称、说明、重点 / 一般、2～3 条要点（每条写出处编号）、常见误解",
    "要点是“标准答案”，后面判断 AI 对话、出题都对照它",
    "只输出固定格式的 JSON"], fill=LBLUE, size=13)
text(s, 6.3, 3.0, 6.5, 3.7, [("识别出的一个知识点（示例）", {"bold": True, "color": TEAL, "size": 14}),
     ("智能测试四大优化环节　[重点]", {"bold": True, "size": 13}),
     "说明：智能测试在四个环节对测试做优化……",
     "要点 1：生成环节根据需求和代码自动生成用例　[D1-P11]",
     "要点 2：执行环节实时感知变化、自愈并调整优先级　[D1-P17, V2-03]",
     "要点 3：报告环节汇总数据并预测风险　[D1-P19]",
     "常见误解：智能测试只是自动生成用例"], fill=MINT, shape=MSO_SHAPE.ROUNDED_RECTANGLE, size=12, margin=0.16)

s = new_slide("识别后的检查和老师确认", "程序先检查，老师再确认；确认后才进入 AI 对话、出题、诊断")
rows = [["规则", "怎么做", "为什么"],
        ["每个知识点最多 3 条要点", "超过 3 条程序截断；老师编辑时超过 3 条不让保存", "一条要点对应 L1～L3 提示和至少 1 道题，太多学生讲不完、10 句对话不够"],
        ["出处要真的存在", "要点写的编号在资料里找不到，就删掉这个出处", "学生“去复习”时要能跳到真实位置"],
        ["重点 / 一般", "大模型标，老师可改", "出题时重点知识点多出 1 道"],
        ["老师确认", "可以改名称、要点、误解、出处；确认后同步给练习和诊断", "知识点是后面所有模块的基础"],
        ["改过要重新确认", "确认后又改资料或知识点，状态回到“待确认”", "用到它的 AI 对话草稿会标“过期”，不能确认"]]
table(s, 0.5, 1.55, 8.6, rows, colw=[1.8, 3.4, 3.4], size=12, rowh=0.72)
cost(s, 9.4, 1.55, 3.4, [("识别一章", "35～60 秒", "0.08～0.15 元")])
text(s, 9.4, 2.6, 3.4, 2.2, [("价格说明", {"bold": True, "color": NAVY, "size": 12}),
     "deepseek-flash；按输入每百万 token 2.2 元、输出 8.7 元（高峰价，故意算高，低谷约一半）"],
     fill=LIGHT, shape=MSO_SHAPE.ROUNDED_RECTANGLE, size=11, margin=0.14)

s = new_slide("AI 对话：老师设置和草稿用到的材料", "每个 AI 对话最多 2 个知识点；知识点必须先确认。草稿每个知识点生成一次，之后每个学生都用这份")
card(s, 0.5, 1.6, 4.2, 4.9, "交给大模型的 5 类材料", [
    "① 知识点名称、说明",
    "② 要点（老师确认的）和出处位置",
    "③ 常见误解",
    "④ 资料原文：只给这几条要点出处的片段，不给整份资料",
    "⑤ 已经用过的场景和 AI 同学名字（避免重复）"], fill=LBLUE, size=13)
arrow(s, 4.8, 4.0, 5.4, 4.0)
card(s, 5.5, 1.6, 7.3, 4.9, "草稿内容（每个知识点一份）", [
    "AI 同学的人设、名字；一个和资料不同的场景；开场白（≤ 80 字）",
    "每条要点：“讲清楚”的标准、“不完整”的标准",
    "每条要点 L1、L2、L3 三级提示（都不能说出答案，每条 ≤ 80 字）",
    "每条要点的复习位置（课件第几页、视频几分几秒）",
    "起草规则 6 条：同学口吻、场景不照抄资料、开场 ≤ 80 字、提示不给答案、写判断标准、只输出 JSON",
    "程序再做 4 项检查：格式、长度、提示里有没有要点原文、复习位置是否存在"], fill=MINT, tcolor=TEAL, size=13)
cost(s, 0.5, 6.55, 6.0, [("每个知识点起草", "7～16 秒", "0.015～0.03 元")], title="用时 / 费用")

s = new_slide("老师修改草稿、过期和确认", "草稿是老师的教学设计：老师可以改，改完程序重新检查")
flow(s, 0.5, 1.7, [("生成草稿", "4 项检查"), ("待确认", "老师看草稿"), ("老师修改", "除要点原文外都能改"),
                   ("重新检查", "回到待确认"), ("确认", "学生才能用")], w=2.15, h=1.0, gap=0.37,
     colors=[LBLUE, LORANGE, LORANGE, LBLUE, MINT])
rows = [["情况", "结果"],
        ["知识点还没确认，或改过之后没再确认", "不能生成草稿"],
        ["草稿生成后，知识点的要点或误解被改了", "草稿标“过期”，不能确认，要重新生成"],
        ["老师改了已确认的草稿", "重新检查，状态回到“待确认”，标“老师改过”"],
        ["重新生成草稿", "正在进行的对话作废，学生下次进来用新草稿"]]
table(s, 0.5, 3.2, 12.3, rows, colw=[5, 7], size=13, rowh=0.55)

s = new_slide("出题：老师有哪些选项", "AI 出题：老师选 5 项；也可以上传已有题目（AI 只整理格式、标知识点和考查类型，不改内容）")
rows = [["选项", "可选内容", "默认"],
        ["① 知识点", "本章已确认的知识点，可多选（标出重点）", "全选"],
        ["② 考查类型", "概念辨析 · 情境应用 · 数据分析 · 流程步骤", "概念辨析 + 情境应用"],
        ["③ 题型", "单选 · 两段式（答案 + 理由）· 多选 · 判断 · 填空 · 简答", "单选 + 两段式"],
        ["④ 每个知识点几道", "1～6", "3（每条要点至少 1 道）"],
        ["⑤ 其他要求（可选）", "老师用自己的话写，≤ 500 字，原样放进提示词", "—"]]
table(s, 0.5, 1.55, 7.6, rows, colw=[1.8, 4.2, 2.0], size=12, rowh=0.62)
card(s, 8.4, 1.55, 4.4, 4.6, "“其他要求”怎么用", [
    "例：“第 2 个知识点用简答考”“加一道找出测试步骤错误的题”",
    "提示词里告诉大模型“这是第几个知识点”，老师写第几个它能对上",
    "老师的要求优先：可以改题型、题量（每个知识点最多 8 道）",
    "4 种考查类型不够用时写在这里，这类题记为“老师定制”",
    "格式、资料范围、程序检查不变",
    "实测：第一次模型没照做；加“输出前自查 + 写一句说明”后照做了"], fill=LBLUE, size=12)

s = new_slide("出题计划：整章均匀分配", "程序先排好每个知识点出几道、什么题型、什么考查类型，再交给大模型照着出")
rows = [["知识点", "重点？", "题数", "题型", "考查类型"],
        ["1", "重点", "4", "单选 2 · 判断 1 · 简答 1", "概念 2 · 情境 2"],
        ["2", "一般", "3", "判断 1 · 简答 1 · 单选 1", "概念 2 · 情境 1"],
        ["3", "一般", "3", "简答 1 · 单选 1 · 判断 1", "情境 2 · 概念 1"],
        [("整章", {"bold": True}), "", ("10", {"bold": True}), ("单选 4 · 判断 3 · 简答 3", {"bold": True}), ("概念 5 · 情境 5", {"bold": True})]]
table(s, 0.5, 1.55, 8.0, rows, colw=[1, 1, 0.8, 3, 2.2], size=12, rowh=0.55)
card(s, 8.8, 1.55, 4.0, 4.4, "排计划的规则", [
    "一般知识点 = 老师设的题数；重点知识点 + 1",
    "题型、考查类型按整章轮流分配：上一个排到哪种，下一个从下一种接着排",
    "整章每种的数量相差不超过 1",
    "题数 ≥ 要点数时，每条要点至少 1 道",
    "出完程序按计划数一遍，对不上的提醒老师（不丢题）"], fill=MINT, tcolor=TEAL, size=12)
text(s, 0.5, 4.6, 8.0, 1.4, [("例子设置", {"bold": True, "color": NAVY, "size": 12}),
     "题型选单选、判断、简答；考查类型选概念辨析、情境应用；每个知识点 3 道；第 1 个知识点是重点"],
     fill=LIGHT, shape=MSO_SHAPE.ROUNDED_RECTANGLE, size=12, margin=0.14)

s = new_slide("出完之后的检查", "每个知识点调用 2 次大模型：出题 1 次、不看答案做一遍 1 次；全部存成草稿，老师确认后学生才看得到")
flow(s, 0.5, 1.6, [("① 出题", "按计划，每题标第几条要点"), ("② 格式检查", "不合格的丢掉"), ("③ 照抄检查", "连续 15 字和要点一样"),
                   ("④ 覆盖统计", "要点、误解覆盖几条"), ("⑤ 不看答案做", "做错 → 可能有歧义"), ("⑥ 老师确认", "改、删、补")],
     w=1.8, h=1.1, gap=0.3)
rows = [["实测（第 6 章，7 个知识点）", "结果"],
        ["出了几道", "20 道（丢掉 1 道格式不对的）"],
        ["要点覆盖", "每个知识点 3/4（旧数据 4 条要点、只出 3 道）"],
        ["误解用上", "13/14"],
        ["可能有歧义", "0 道"],
        ["均匀分配测试", "题型 4/3/3、考查类型 5/5，和计划完全一致"]]
table(s, 0.5, 3.1, 7.5, rows, colw=[3, 4.5], size=12, rowh=0.5)
cost(s, 8.3, 3.1, 4.5, [("一个知识点出题", "约 5.4 秒", "约 0.014 元"), ("不看答案做一遍", "约 0.9 秒", "很少"),
                        ("7 个知识点 20 道", "44 秒", "0.097 元")])

s = new_slide("计分：对 1 分、错 0 分，简答由大模型判两次", "所有对错都由程序判；简答两次判断一致才采用，不一致交给老师")
rows = [["题型", "怎么答", "谁判", "得分"],
        ["单选", "选一个", "程序", "对 1 / 错 0"],
        ["两段式", "先选答案，再选理由", "程序", "两段都对才 1"],
        ["多选", "选全部正确的", "程序", "全对 1；选的都对但没选全 0.5；选了错的 0"],
        ["判断", "正确 / 错误", "程序", "对 1 / 错 0"],
        ["填空", "一个词或短语", "程序：和 1～5 个说法比（去空格标点、全角转半角）", "对 1 / 错 0"],
        ["简答", "一段话", "大模型判两次（见右）", "答到的要点数 ÷ 要点总数"]]
table(s, 0.5, 1.55, 7.6, rows, colw=[1, 1.8, 2.8, 2.2], size=12, rowh=0.55)
flow(s, 8.4, 1.6, [("同时判两次", "每条要点答到没有，引原话")], w=4.4, h=0.8)
flow(s, 8.4, 2.6, [("程序核对", "引用的原话必须在答案里")], w=4.4, h=0.8)
flow(s, 8.4, 3.6, [("两次一致 → 采用", "不一致 → 等老师确认")], w=4.4, h=0.8, fill=MINT)
for yy in (2.42, 3.42):
    arrow(s, 10.6, yy, 10.6, yy + 0.16)
cost(s, 8.4, 4.7, 4.4, [("每份简答（判两次）", "约 1 秒", "约 0.004 元")])
text(s, 8.4, 5.6, 4.4, 1.1, "少于 5 个字直接 0 分；和参考答案完全一样直接满分（都不调大模型）",
     fill=LIGHT, shape=MSO_SHAPE.ROUNDED_RECTANGLE, size=12, margin=0.14)

# =====================================================================================================
# 第二部分：学生学习与诊断
# =====================================================================================================
dark_slide("学生学习与诊断", "学完判定 · AI 对话 · 只算第一次 · 画像颜色")

s = new_slide("怎么算“学完”，怎么解锁下一项", "学完才解锁下一项；视频、文档由后端判定，前端不能直接报“学完”")
card(s, 0.5, 1.6, 3.0, 3.2, "视频", ["真正播放过的时间段合并后 ÷ 总时长 ≥ 90%", "拖进度条跳过的不算；同一段看几遍只算一次",
                                    "防改请求：两次上报之间新增时长 ≤ 经过时间 × 2 + 5 秒"], fill=LBLUE, size=12)
card(s, 3.65, 1.6, 3.0, 3.2, "文档", ["页面在前台累计 ≥ 30 秒", "并且点了“我已读完”", "每次上报最多加“距上次的秒数 + 3”"], fill=LBLUE, size=12)
card(s, 6.8, 1.6, 3.0, 3.2, "AI 对话", ["对话结束（全部讲清 / 10 句 / 没有可追问的要点）"], fill=LBLUE, size=12)
card(s, 9.95, 1.6, 2.85, 3.2, "练习", ["交卷", "练习必须设截止时间才能发布"], fill=LBLUE, size=12)
flow(s, 0.5, 5.2, [("第 1 项", "默认解锁"), ("第 2 项", "第 1 项学完才解锁"), ("第 3 项", "第 2 项学完才解锁"), ("……", "已学完的永远解锁")],
     w=2.8, h=0.95, gap=0.35, fill=MINT)

s = new_slide("AI 对话：每一句怎么判断", "大模型理解学生的话，程序决定下一步；每句约 1～2 秒")
flow(s, 0.5, 1.6, [("学生说一句", ""), ("大模型判断", "类型、每条要点状态 + 引用原话"), ("程序核对", "原话存在？只升不降？"),
                   ("选追问目标", "先讲错的，再按顺序"), ("回复", "反应 + 追问 / 提示")], w=2.25, h=1.05, gap=0.3)
rows = [["程序核对的规则", "为什么"],
        ["引用的学生原话必须真的出现过", "防止大模型“替学生讲”"],
        ["要点状态只升不降（讲错例外）", "前面讲清的不会被后面一句推翻"],
        ["只认当前正在追问的那条要点的进步", "避免东讲一点西讲一点就算过"],
        ["判断失败不算轮数", "“我没听清，你能再说一遍吗？”"],
        ["泄露检查：回复里有没讲到的要点原文（连续 4 字）就换掉", "AI 同学不能把答案说出来"]]
table(s, 0.5, 3.0, 8.3, rows, colw=[4.8, 3.5], size=12, rowh=0.55)
card(s, 9.1, 3.0, 3.7, 3.3, "提示怎么升级", ["每一级问 2 次再升级", "L1 → L2 → L3 → 复习位置（这条要点结束，换下一条）",
                                              "有进步：停在这一级，换一个针对缺的部分的追问"], fill=LORANGE, tcolor=ORANGE, size=12)

s = new_slide("AI 对话：怎么结束，学生看到什么", "对话不打分，只诊断；详细情况留在后台，给小助手和以后的分析用")
flow(s, 0.5, 1.6, [("全部要点讲清", ""), ("10 句用完", ""), ("没有可追问的要点", "")], w=3.0, h=0.8, gap=0.4, fill=LBLUE)
text(s, 11.0, 1.6, 1.8, 0.8, "任一条件 → 结束", size=13, bold=True, color=NAVY, anchor="m")
card(s, 0.5, 2.8, 6.0, 3.3, "学生看到（内容总结）", ["每个知识点、每条要点的原文（老师确认的）",
     "没讲清的要点后面加一行：“这一条这次还没讲清，可以复习：课件第几页、视频几分几秒”",
     "可以“再讲一次”；重新打开时看到上一次的总结"], fill=MINT, tcolor=TEAL, size=13)
card(s, 6.8, 2.8, 6.0, 3.3, "后台记录（不给学生看）", ["每条要点：讲清 / 不完整 / 讲错 / 没讲到，引用原话",
     "是否认真尝试、误解有没有纠正、复习位置", "给小助手和以后的分析用"], fill=LIGHT, size=13)
cost(s, 0.5, 6.25, 7.0, [("一个学生讲一个知识点", "每句 1～2 秒，收尾约 3 秒", "约 0.04 元")], title="用时 / 费用")

s = new_slide("只算第一次作答：三个数", "重做只是练习；补练、推送的新题单独记")
flow(s, 0.5, 1.6, [("练习里的第一次作答", "→ 原始掌握度（不再改）"), ("推荐 / 推送的新题第一次", "→ 补练结果"),
                   ("当前掌握度", "补练够 2 道用补练，否则用原始")], w=3.8, h=1.1, gap=0.45, colors=[LBLUE, LORANGE, MINT])
rows = [["情况", "原始", "补练", "当前", "画像颜色"],
        ["单选对、多选少选、简答答到 1/3", "0.61", "—", "0.61", "红（第一次没到 0.8）"],
        ["第 1 题第一次错，之后重做对了", "0", "—", "0", "红（重做不算）"],
        ["原始 0，补练 2 道都对", "0", "1", "1", "橙（已补回来）"],
        ["原始 0，补练 2 道对 1 道", "0", "0.5", "0.5", "红（补练中）"],
        ["第一次 0.9", "0.9", "—", "0.9", "绿"]]
table(s, 0.5, 3.1, 12.3, rows, colw=[4.5, 1.2, 1.2, 1.2, 3], size=12, rowh=0.5)

s = new_slide("画像颜色：看第一次有没有掌握", "补回来是橙色，不会变成绿色：提醒学生这里曾经错过，复习时多留意")
for i, (t, d, f, c) in enumerate([("绿", "第一次练习就掌握\n（原始 ≥ 0.8）", MINT, TEAL), ("橙", "第一次没掌握，\n补练后补回来了", LORANGE, ORANGE),
                                    ("红", "第一次没掌握，\n还没补回来", LRED, RED), ("灰", "还没练过", LIGHT, GREY)]):
    text(s, 0.7 + i * 3.1, 1.8, 2.7, 2.4, [(t, {"size": 40, "bold": True, "color": c})] + d.split("\n"), fill=f,
         shape=MSO_SHAPE.ROUNDED_RECTANGLE, align="c", anchor="m", size=14)
arrow(s, 6.4, 4.5, 3.9, 4.5, color=ORANGE)
text(s, 4.0, 4.6, 2.4, 0.4, "补回来：红 → 橙", size=12, color=ORANGE, align="c")
text(s, 0.7, 5.3, 12.0, 1.2, [("“补回来”的标准", {"bold": True, "color": NAVY, "size": 13}),
     "补练（小助手补练题、老师推送的新题）至少 2 道，平均 ≥ 0.8。只有红色的知识点才推补练；绿色、橙色可以做拓展。"],
     fill=LIGHT, shape=MSO_SHAPE.ROUNDED_RECTANGLE, size=13, margin=0.15)

s = new_slide("学生画像：每章画像 + 课程四维", "结构参考组员方案；计分口径用我们的“只算第一次”；AI 对话不进画像分数")
flow(s, 0.5, 1.6, [("学的过程中", "每章画像一直更新"), ("一章学完", "变成这一章的学习小结"), ("全部章节学完", "开放课程四维画像")],
     w=3.8, h=1.0, gap=0.45, colors=[LBLUE, LORANGE, MINT])
rows = [["决定", "为什么"],
        ["分两层：每章 + 课程", "每章边学边看哪里没掌握，及时补；课程画像是学完后的整体描述"],
        ["知识点直接用绿 / 橙 / 红", "和练习、小助手、老师干预是同一套判断，学生在哪里看到的颜色都一样"],
        ["AI 对话不进分数", "AI 对话只诊断、不打分，只算流程有没有完成"],
        ["四维，去掉练习参与率", "练习必须交卷才解锁下一项，学完时参与率基本都是 100%，没有区分度，还和完成度重复"],
        ["没数据显示“暂无数据”", "不用 0 或 100 补；四维不全时不画多边形"],
        ["各章合并：分子分母相加再除", "各章题数不同，平均百分比会算错（例：8/10 和 3/6 → 11/16 = 68.75%，不是 65%）"],
        ["不算总分、不排名", "是学习过程的描述，不是成绩，也不推断性格、态度"]]
table(s, 0.5, 2.9, 12.3, rows, colw=[3.5, 8.8], size=12, rowh=0.5)

s = new_slide("每章画像：怎么算、数据从哪来", "学的过程中一直显示；学完这一章就是“学习小结”，文字由程序按数字拼出来")
rows = [["部分", "怎么算", "数据从哪来"],
        ["学习流程进度", "已完成的项 ÷ 本章全部项（含 AI 对话）", "完成判定：视频 90%、文档 30 秒 + 已读完、练习交卷、对话结束"],
        ["内容进度", "已完成的视频、文档、练习 ÷ 本章全部（不含 AI 对话）", "同上"],
        ["练习交了几组", "交过卷的练习 ÷ 本章练习", "作答记录（按练习项）"],
        ["第一次作答全对", "每道题第一次全对的题数 ÷ 已判题数；等老师确认的另外显示", "作答记录"],
        ["知识点颜色", "绿：第一次 ≥ 0.8；橙：补回来了；红：还没补回来；灰：没练过", "掌握度表（原始掌握度、补练结果）"]]
table(s, 0.5, 1.55, 12.3, rows, colw=[2.2, 5.6, 4.5], size=12, rowh=0.6)
text(s, 0.5, 5.4, 12.3, 1.2, [("示例（学生 08，第 6 章，真实数据）", {"bold": True, "color": NAVY, "size": 12}),
     "学习流程完成 7/7 项（AI 对话只算完成状态）；内容完成 6/6 项；练习交了 1/1 组。第一次作答 14/20 题全对。知识点：4 个第一次就掌握，1 个补回来了，2 个还没掌握。"],
     fill=LIGHT, shape=MSO_SHAPE.ROUNDED_RECTANGLE, size=12, margin=0.15)

s = new_slide("课程四维：怎么算、数据从哪来", "全部章节学完后开放；持续性的参考日 = 最后一项完成的那天（之后打开不会无缘无故变低）")
rows = [["维度", "算法", "数据从哪来", "没数据时"],
        ["学习持续性", "min(1, 有效学习日 ÷ 7)，参考日在内最近 14 天。有效学习日：看视频、文档累计 ≥ 30 秒（各章先相加），或交过 ≥ 1 道已判的题", "页面停留记录（视频、文档）、作答时间；AI 对话不算", "—"],
        ["学习完成度", "完成的视频、文档、练习 ÷ 全部视频、文档、练习", "完成判定", "没有内容"],
        ["按时完成率", "截止前第一次交卷的练习 ÷ 已过截止的练习", "练习截止时间、第一次交卷时间", "没有到期的练习"],
        ["有效作答正确率", "第一次作答全对的题数 ÷ 已判题数（等老师确认的不算）", "练习作答记录（每道题只看第一次）", "没有已判作答"]]
table(s, 0.5, 1.55, 8.6, rows, colw=[1.6, 3.6, 2.4, 1.3], size=11, rowh=0.82)
rows = [["两章例子", "第 1 章", "第 2 章", "全课"],
        ["有效学习日", "3 天", "4 天", "去重 6 天 → 85.7%"],
        ["完成内容", "4/4", "4/4", "8/8 = 100%"],
        ["按时交", "2/2", "1/2", "3/4 = 75%"],
        ["第一次全对", "8/10", "3/6", "11/16 = 68.75%"]]
table(s, 9.3, 1.55, 3.5, rows, colw=[1.3, 0.8, 0.8, 1.6], size=10, rowh=0.62)
text(s, 9.3, 4.9, 3.5, 1.6, [("说明", {"bold": True, "color": NAVY, "size": 11}),
     "同一天两章各看 20 秒，合起来 40 秒，算 1 个有效学习日；两章都在 D−8 学，只算 1 天"],
     fill=LIGHT, shape=MSO_SHAPE.ROUNDED_RECTANGLE, size=10, margin=0.12)

# =====================================================================================================
# 第三部分：小助手
# =====================================================================================================
dark_slide("小助手", "两个阶段 · 补练 · 拓展 · 聊天 · 截止提醒")

s = new_slide("小助手的定位和两个阶段", "以这一章练习的第一次交卷为分界：练习要等前面内容（含 AI 对话）学完才解锁，交过卷就说明都学过了")
card(s, 0.5, 1.6, 5.9, 2.0, "小助手（自动）", ["服务学生个人；截止提醒、补练、拓展、答疑", "老师不插手，只看补练情况"], fill=MINT, tcolor=TEAL, size=13)
card(s, 6.9, 1.6, 5.9, 2.0, "老师干预（手动）", ["只处理全班问题（第四部分）", "补充讲解 + 新题，推给做错的学生"], fill=LBLUE, size=13)
rows = [["", "阶段一：学习中", "阶段二：练习交卷后"],
        ["判断", "这一章练习还没交过卷", "第一次交卷之后"],
        ["能做什么", "回答问题、解释概念、告诉资料在哪", "阶段一的全部 + 自动干预、拓展"],
        ["题目", "不出现任何题目，不用选择题形式举例", "补练题、拓展题由系统给，聊天里大模型不出题"],
        ["学生要题", "“先完成本章的 AI 对话和练习”", "进入补练 / 拓展"]]
table(s, 0.5, 3.9, 12.3, rows, colw=[1.6, 5, 5.7], size=12, rowh=0.55)

s = new_slide("自动干预流程：交卷后补红色的知识点", "触发：这一章练习第一次交卷后，有红色知识点 → 小助手自动弹出")
flow(s, 0.5, 1.6, [("① 提醒", "哪条要点错、可能怎么想错"), ("② 看资料", "跳到那一秒、那一页"), ("③ 先看 / 直接做", "学生选"),
                   ("④ 3 道补练题", "按他自己的错题当场出"), ("⑤ 程序判分", "马上给对错和解析"), ("⑥ 颜色更新", "补回来：红 → 橙")],
     w=1.85, h=1.15, gap=0.25)
rows = [["这一轮的结果", "接下来"],
        ["补练够 2 道、平均 ≥ 0.8", "已补回来，红 → 橙，结束"],
        ["没达到，第 1 轮", "补练中，可以再来一轮（换题型、换场景），建议先去 AI 对话把知识点讲一遍"],
        ["没达到，第 2 轮", "不再出题：建议看资料、去 AI 对话；记录下来，老师能看到（不转给老师处理）"]]
table(s, 0.5, 3.2, 12.3, rows, colw=[3.5, 8.8], size=13, rowh=0.6)
text(s, 0.5, 5.8, 12.3, 0.8, "橙色、绿色不推补练；橙色提示“曾经错过，复习时多留意”，可以做拓展", fill=LIGHT,
     shape=MSO_SHAPE.ROUNDED_RECTANGLE, size=13, margin=0.15)

s = new_slide("补练题怎么出：个人、当场生成、不经过老师审核", "补练题只给这个学生：不出现在别人的推荐、干预里，也不算进老师的覆盖统计")
card(s, 0.5, 1.6, 4.3, 4.6, "根据他自己的数据", ["做错的题考的是第几条要点 → 补练题都考这几条",
     "他选错的选项对应的误解（出现最多的）→ 错误选项要用到它", "他做过的题 → 不能重复，换新场景",
     "第几轮 → 每轮换着题型排"], fill=LBLUE, size=13)
arrow(s, 4.9, 3.9, 5.4, 3.9)
card(s, 5.5, 1.6, 3.8, 4.6, "出题和检查", ["复用出题提示词；程序在“其他要求”一栏写好上面这些",
     "只出单选、判断、多选（有标准答案、程序能马上判）", "每轮 3 道", "大模型不看答案做一遍，做错的（有歧义）直接丢掉",
     "可用的少于 2 道就再生成一次"], fill=MINT, tcolor=TEAL, size=13)
cost(s, 9.6, 1.6, 3.2, [("一轮 3 道", "约 9 秒", "约 0.015 元"), ("（出题 1 次 + 不看答案做 3 次）", "", "")])
text(s, 9.6, 3.0, 3.2, 3.2, [("实测（学生 08）", {"bold": True, "color": NAVY, "size": 12}),
     "红色知识点「四大优化环节」第一次 8%", "生成 3 道新场景的题（当时还有两段式；之后改成只出单选、判断、多选）", "3 道全对 → 已补回来，0.08 → 1",
     "发现：有一题正确选项明显更长，之后加“选项长度相近”"], fill=LIGHT, shape=MSO_SHAPE.ROUNDED_RECTANGLE, size=11, margin=0.14)

s = new_slide("拓展（E1 Elaborate）：掌握了的去挑战更难的题", "按练习结果分流：红色去补救，绿色、橙色去拓展；拓展是自由的，不算分")
text(s, 0.5, 1.7, 2.8, 1.0, "练习结果", fill=NAVY, color=WHITE, bold=True, shape=MSO_SHAPE.ROUNDED_RECTANGLE, align="c", anchor="m", size=16)
arrow(s, 3.35, 2.0, 4.3, 1.75)
arrow(s, 3.35, 2.4, 4.3, 3.15)
text(s, 4.4, 1.35, 3.0, 0.8, "红 → 补救（补练题）", fill=LRED, color=RED, bold=True, shape=MSO_SHAPE.ROUNDED_RECTANGLE, align="c", anchor="m", size=14)
text(s, 4.4, 2.75, 3.0, 0.8, "绿 / 橙 → 拓展", fill=MINT, color=TEAL, bold=True, shape=MSO_SHAPE.ROUNDED_RECTANGLE, align="c", anchor="m", size=14)
card(s, 7.8, 1.35, 5.0, 2.2, "拓展怎么做", ["学生点“来点更难的”，选考查类型：情境应用 / 数据分析 / 概念辨析 / 流程步骤",
     "大模型当场出 3 道更难的题：场景更复杂、要多想一步、要把几条要点结合用"], fill=LBLUE, size=12)
rows = [["5E 阶段", "在哪里"], ["Engage 引入", "AI 对话：场景 + AI 同学开场"], ["Explore 探究", "AI 对话：学生讲，AI 同学追问、提示"],
        ["Explain 解释", "AI 对话：学生讲清；没讲清的给复习位置"], ["Evaluate 评价", "练习：第一次作答算掌握度"],
        [("Elaborate 拓展", {"bold": True, "color": TEAL}), ("小助手：掌握了的做更难的题（不算分，次数不限）", {"color": TEAL})]]
table(s, 0.5, 3.9, 8.5, rows, colw=[2.2, 6.3], size=12, rowh=0.48)
text(s, 9.3, 3.9, 3.5, 2.6, [("为什么不算分", {"bold": True, "color": NAVY, "size": 12}),
     "拓展是鼓励挑战；做错不应该让已经掌握的知识点变差。拓展题记进学习记录，但不进掌握度（来源 extend 的题在计算时排除）"],
     fill=LIGHT, shape=MSO_SHAPE.ROUNDED_RECTANGLE, size=11, margin=0.14)
text(s, 9.3, 6.6, 3.5, 0.4, "用时 / 费用：待测", size=11, color=TEAL, bold=True)

s = new_slide("自由聊天：可以讲概念，不给题目答案", "每次把固定规则 + 本章知识点 + 最相关的资料 + 学生情况 + 最近 8 句交给大模型；不给它任何题目答案")
card(s, 0.5, 1.55, 4.0, 2.8, "可以做", ["回答问题、解释概念，可以引用资料里的例子", "说明出处（课件第几页、视频几分几秒）；资料没有就说没有",
     "卡住时先问一个小问题引导"], fill=MINT, tcolor=TEAL, size=12)
card(s, 4.7, 1.55, 4.0, 2.8, "不可以做", ["不给练习题、补练题的答案（贴题也一样）", "不替学生写简答、不代写 AI 对话的内容",
     "不出题；不透露别的同学；不改规则"], fill=LRED, tcolor=RED, size=12)
card(s, 8.9, 1.55, 3.9, 2.8, "程序三层把关", ["① 发之前：学生是不是贴了本章的题（连续 15 字）或要答案 → 提示词加“只能引导”",
     "② 回复后（阶段一）：有没有出题的样子（A. B. 选项、“考考你”）", "③ 回复后：有没有这道题的正确选项原文、“选 A”；有没有简答参考答案（连续 15 字）→ 拦下换成提示"],
     fill=LBLUE, size=11)
rows = [["真实测试", "句数", "结果"],
        ["该拦：贴单选 / 判断 / 两段式问答案、要写简答、换说法要结论、只说题号", "6", "6 句模型都没给答案，指出考哪个知识点、看哪一页"],
        ["不该拦：问概念、区别、资料位置、为什么、关系、举例", "6", "6 句都正常回答，都标了出处"],
        ["阶段一：要练习题、“考考我”、问概念", "3", "都没出题；概念正常讲"]]
table(s, 0.5, 4.55, 9.0, rows, colw=[5.2, 0.8, 4.2], size=11, rowh=0.55)
cost(s, 9.8, 4.55, 3.0, [("每句", "约 1.2 秒", "约 0.01 元"), ("输入约 4300 token", "", "")])

s = new_slide("截止提醒和“老师给你的”", "也都在小助手里，不需要老师逐个通知")
card(s, 0.5, 1.6, 6.0, 4.6, "截止提醒（自动）", ["快到期：离截止不到 3 天，显示“还剩几小时 / 几天截止”",
     "已过期：显示“已过期”；允许迟交的显示“还可以补做（算迟交）”", "点“去做”直接打开那一项",
     "图标上的数字会提醒有新的"], fill=LORANGE, tcolor=ORANGE, size=13)
card(s, 6.8, 1.6, 6.0, 4.6, "老师给你的（来自老师干预）", ["老师提醒：分析作废时，老师提醒没交的学生",
     "老师补充讲解：先看讲解，再在小助手里直接做新题，算进补练",
     "有新的、还没做的推送时，小助手自动弹出一次"], fill=LBLUE, size=13)

# =====================================================================================================
# 第四部分：老师干预
# =====================================================================================================
dark_slide("老师干预", "分析时机 · 两条规则 · 按考查类型的方案 · 效果")

s = new_slide("什么时候分析：截止到了，交卷人数够了", "每次练习各自分析；只算截止前的第一次作答")
flow(s, 0.5, 1.7, [("截止时间到", "或全班提前交完"), ("交卷率 ≥ 80%？", "截止前交卷人数 ÷ 全班")], w=3.0, h=1.0, gap=0.5)
arrow(s, 7.05, 2.0, 7.9, 1.6)
arrow(s, 7.05, 2.4, 7.9, 2.9)
text(s, 8.0, 1.2, 4.8, 0.85, [("是 → 分析", {"bold": True, "color": TEAL, "size": 14}), "规则 ①、② 出卡片"], fill=MINT,
     shape=MSO_SHAPE.ROUNDED_RECTANGLE, size=12, margin=0.12)
text(s, 8.0, 2.5, 4.8, 1.2, [("否 → 分析作废", {"bold": True, "color": RED, "size": 14}), "列出没交的学生：[提醒他们]（显示在小助手）· [延长截止时间]"],
     fill=LRED, shape=MSO_SHAPE.ROUNDED_RECTANGLE, size=12, margin=0.12)
rows = [["细节", "规则"],
        ["练习必须设截止时间", "不设截止时间这一章不能发布"],
        ["延期", "到新的截止时间再判断；延期前交的都算"],
        ["截止后再做", "可以做、可以重做，但都只算练习"],
        ["截止前一次都没交的学生", "截止后的第一次：算进他自己的画像（标迟交，小助手照样补练），不算进全班分析"],
        ["80% 这个数", "可以在配置里调（platform.class-analysis.min-submit-ratio）"]]
table(s, 0.5, 4.0, 12.3, rows, colw=[3.5, 8.8], size=12, rowh=0.5)

s = new_slide("两条规则：先看题目，再看要点 × 考查类型", "出题时每道题都标了“知识点 → 第几条要点 → 考查类型”，判断单位就是要点 × 考查类型；知识点只用来分组显示")
card(s, 0.5, 1.6, 6.0, 2.4, "规则 ①　题目检查", ["某道题：做错的人 ÷ 做的人 > 80%", "→ 先退回给老师检查，不出推荐方案",
     "在等老师检查时，这道题暂时不算进规则 ②"], fill=LRED, tcolor=RED, size=13)
card(s, 6.8, 1.6, 6.0, 2.4, "规则 ②　要点 × 考查类型", ["某条要点、某种考查类型的所有题：做错的人次 ÷ 作答人次 > 50%",
     "→ 卡片 + 推荐方案（按考查类型）", "推送对象：这组题里至少错过一道的学生"], fill=LORANGE, tcolor=ORANGE, size=13)
flow(s, 0.5, 4.4, [("老师检查题目", ""), ("有问题", "修改 / 作废（重算掌握度）"), ("没问题", "算回规则 ②")], w=3.6, h=0.9, gap=0.75,
     colors=[LBLUE, LRED, MINT])
text(s, 0.5, 5.6, 12.3, 1.0, [("例子", {"bold": True, "color": NAVY, "size": 12}),
     "要点 2 · 情境应用有 2 道题，10 人做，20 人次，错 15 人次 → 75% > 50% → 出卡片，推给错过的 8 人；没标要点的旧题按“知识点 × 考查类型”算"],
     fill=LIGHT, shape=MSO_SHAPE.ROUNDED_RECTANGLE, size=12, margin=0.14)

s = new_slide("推荐方案按考查类型设计", "卡片上写明考查类型和推荐的讲解形式；大模型起草讲解时也按这种形式写")
rows = [["考查类型", "说明学生", "补充讲解（200～400 字，附原资料位置）", "新题（2～3 道）"],
        ["概念辨析", "概念分不清", "对比讲解：容易混的概念放在一起，列出区别，各举一个例子", "辨析题"],
        ["情境应用", "会背不会用", "例题讲解：新场景，分步讲“看到什么 → 想到哪个概念 → 选什么方法”", "新场景的情境题"],
        ["数据分析", "不会读数据", "示范讲解：一组数据，先看什么、比较什么、得出什么结论", "数据分析题"],
        ["流程步骤", "步骤顺序不清", "步骤讲解：每一步做什么、为什么在这一步", "步骤题"],
        ["老师定制", "—", "按老师出题时写的要求", "同类的题"]]
table(s, 0.5, 1.55, 12.3, rows, colw=[1.6, 1.8, 6.6, 2.3], size=12, rowh=0.68)
text(s, 0.5, 5.8, 12.3, 0.9, "讲解要换一个和原资料不同的角度，不能照抄资料原句；例子不能和学生做错的题一样，不能说出那几道题的答案",
     fill=LIGHT, shape=MSO_SHAPE.ROUNDED_RECTANGLE, size=12, margin=0.14)

s = new_slide("推送流程和效果", "老师确认讲解和每一道题后才推送；同一个知识点的几张卡片可以合并成一份讲解")
flow(s, 0.5, 1.6, [("老师处理卡片", "勾选要合并的"), ("补充讲解", "大模型起草 / 老师自己写"), ("新题 3 道", "同一种考查类型，老师逐题确认"),
                   ("推送", "可设期限"), ("学生", "小助手里看讲解、做题")], w=2.2, h=1.1, gap=0.3)
card(s, 0.5, 3.0, 6.0, 3.0, "交给大模型起草讲解的材料", ["知识点、这条要点、考查类型和讲解形式",
     "学生做错的题：题干、正确答案、选得最多的错误选项和它的误解", "常见误解；这条要点出处的资料原文"], fill=LBLUE, size=12)
card(s, 6.8, 3.0, 6.0, 3.0, "效果（自动统计）", ["推送前错误率（卡片上的）→ 推送后新题第一次作答的错误率",
     "做完的人数；做完的人里补回来（不再是红色）的人数", "例：要点 2 · 情境应用：推给 8 人，7 人做完，75% → 25%",
     "新题算进补练：做对了红 → 橙"], fill=MINT, tcolor=TEAL, size=12)
text(s, 0.5, 6.2, 12.3, 0.5, "用时 / 费用：起草讲解 + 出新题　待测（之后用真实模型补上）", size=12, color=TEAL, bold=True)

# =====================================================================================================
# 总结
# =====================================================================================================
dark_slide("总结", "对照 · 质量 · 成本 · 下一步")

s = new_slide("必做和选做对照", "主线 M1–M10 都已完成；选做 E1、E4 的功能都有着落")
rows = [["编号", "内容", "平台里在哪", "状态"],
        ["M1–M4、M7", "资料、知识点、学习、答疑", "上传即转文字、识别知识点；学完判定和解锁；小助手答疑", "完成"],
        ["M5", "测验", "6 种题型、AI 出题、简答判两次、老师改判", "完成"],
        ["M6", "学习记录", "学习事件、作答记录", "完成"],
        ["M8", "诊断 + 证据", "原始 / 补练 / 当前掌握度，错在哪条要点", "完成"],
        ["M9", "画像与推荐", "推荐：小助手补练、拓展；画像：每章画像 + 课程四维", "完成"],
        ["M10", "老师查看 / 干预", "全班分析、题目检查、补充讲解推送、简答改判", "完成"],
        ["E1", "5E 学习智能体", "引入、探究、解释在 AI 对话；评价在练习；拓展在小助手", "完成"],
        ["E4", "重点难点", "重点：识别时标出、出题多 1 道；难点：老师选进 AI 对话 + 全班分析里错误率高的要点", "完成"]]
table(s, 0.5, 1.55, 12.3, rows, colw=[1.3, 2.0, 7.2, 1.8], size=12, rowh=0.55)

s = new_slide("质量保证", "单元测试保证规则和流程对；评测用真实模型统计准不准、会不会泄露")
for i, (num, lab) in enumerate([("152", "个单元测试，全部通过"), ("CI", "每次推送自动跑测试和前端检查"), ("待做", "评测 T1–T5、P1–P5")]):
    text(s, 0.5 + i * 4.15, 1.7, 3.95, 1.8, [(num, {"size": 44, "bold": True, "color": TEAL if i < 2 else ORANGE}), (lab, {"size": 13, "color": GREY})],
         align="c", anchor="m", fill=LIGHT, shape=MSO_SHAPE.ROUNDED_RECTANGLE)
rows = [["", "单元测试", "评测"],
        ["大模型", "假的（FakeAiClient），结果固定、不花钱", "真实模型"],
        ["测什么", "规则、流程、权限、计分", "泄露率、判分一致率（和人工比）、稳定性、用时费用"],
        ["什么时候跑", "每次推送（CI）", "单独跑，不进 CI"]]
table(s, 0.5, 3.9, 12.3, rows, colw=[1.6, 5, 5.7], size=12, rowh=0.55)

s = new_slide("成本汇总", "价格按 deepseek-flash 高峰价计算（故意算高）；一学期估算待补")
rows = [["步骤", "谁触发", "用时", "费用"],
        ["一章资料转文字 + 识别知识点", "老师", "约 11 分钟", "约 0.5 元"],
        ["AI 对话草稿（每个知识点）", "老师", "7～16 秒", "0.015～0.03 元"],
        ["AI 出题（7 个知识点 20 道）", "老师", "44 秒", "0.097 元"],
        ["一个学生讲一个知识点（AI 对话）", "学生", "每句 1～2 秒", "约 0.04 元"],
        ["简答判分（每份）", "学生交卷", "约 1 秒", "约 0.004 元"],
        ["小助手补练（每轮 3 道）", "学生", "约 9 秒", "约 0.015 元"],
        ["小助手聊天（每句）", "学生", "约 1.2 秒", "约 0.01 元"],
        ["拓展题（每组）、老师起草讲解 + 出题", "学生 / 老师", ("待测", {"color": ORANGE}), ("待测", {"color": ORANGE})]]
table(s, 0.5, 1.55, 12.3, rows, colw=[5, 2, 2.3, 3], size=12, rowh=0.55)

s = new_slide("不足和下一步", "按重要程度排序")
for i, (t, d) in enumerate([("画像补充", "老师看学生的四维；AI 对话的文字反馈放进每章画像；学完时保存快照"),
                            ("评测", "用真实模型跑 T1–T5、P1–P5，给出泄露率、判分一致率"),
                            ("补测试", "老师干预、拓展的真实模型测试和浏览器测试；补上用时和费用"),
                            ("出题质量", "正确选项不能明显更长；“讲概念”和“给答案”的边界在评测里统计")]):
    x = 0.5 + (i % 2) * 6.2
    y = 1.7 + (i // 2) * 2.2
    text(s, x, y, 0.9, 0.9, str(i + 1), fill=NAVY, color=WHITE, bold=True, size=24, shape=MSO_SHAPE.OVAL, align="c", anchor="m")
    text(s, x + 1.1, y, 4.9, 1.6, [(t, {"bold": True, "size": 18, "color": INK}), (d, {"size": 13, "color": GREY})], margin=0)

prs.save(OUT)
print("saved", OUT, "slides", PAGE[0])
