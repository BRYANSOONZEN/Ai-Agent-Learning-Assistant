# -*- coding: utf-8 -*-
"""
生成《数据库说明》第六节：32 张表逐张写清楚。
每张表：模块、用途、什么时候写入、和哪些表关联（手写，在 TABLES 里）；
        每一列的类型、约束、含义（从 schema.sql 读）；主键、唯一约束、索引；
        哪些代码写它、哪些代码读它（扫描后端源码里每个 Mapper 的调用）；
        MySQL 里现在有几行、一行真实数据（连本机 MySQL 查；密码从 C:/ZI/mysql/本机密码.txt 读，不打印；
        密码哈希等敏感列打码）。
用法：python tools/docs/gen_db_report.py   （由 build_docs.py 调用）
"""
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', '..')
SRC = os.path.join(ROOT, 'backend', 'src', 'main', 'java', 'com', 'se', 'platform')
sys.path.insert(0, HERE)
from gen_schema import parse, SQL  # noqa: E402
from db_columns import meaning  # noqa: E402

MYSQL = r'C:\ZI\mysql\mysql-8.4.11-winx64\bin\mysql.exe'
PWFILE = r'C:\ZI\mysql\本机密码.txt'
MASK = {'password_hash'}

# 表 → (模块, 用途, 什么时候写入, 关联)
TABLES = {
    'user': ('账号', '所有登录账号：学生、老师、管理员。', '学生注册时、管理员开通老师 / 学生账号时新增；改密码、禁用、退出登录（令牌版本 +1）时修改。', '被几乎所有表的 student_id / teacher_id / uploaded_by 引用。'),
    'course': ('课程内容', '一门课：课程名、简介、授课老师、状态（草稿 / 已发布 / 已归档）、开了哪些模块。', '老师新建、编辑、发布课程时。', 'teacher_id → user；下面挂 chapter、chapter_item、knowledge_point、exercise、enrollment。'),
    'chapter': ('课程内容', '课程里的一章：标题、章节介绍、状态、顺序。', '老师在建课页新建、编辑、发布章节时；发布前后端检查章节完整。', 'course_id → course；下面挂 chapter_item、knowledge_point、chapter_kp。'),
    'chapter_item': ('课程内容', '章节里的一项内容：视频、文档、练习、AI 对话；顺序、截止时间、过期后能不能补交。', '老师在建课页加内容项、改标题和截止时间、上传文件（写 material_id）时。', 'chapter_id → chapter；material_id → material；练习项下挂 exercise，AI 对话项下挂 feynman_setup；学生的学习记录都按内容项记。'),
    'material': ('资料', '上传的文件信息：原文件名、磁盘上的相对路径、大小、类型。文件本身存在磁盘 backend/uploads/，不进数据库。', '老师给视频 / 文档项上传或替换文件时新增；替换或删除时逻辑删除旧的（deleted = 1）。', '被 chapter_item.material_id、material_job.material_id 引用。'),
    'material_job': ('资料', '每个视频 / 文档项的“转文字”任务：排队 → 处理中 → 完成 / 失败，失败原因、处理统计、同音字纠正做过没有。', '上传文件后马上排队；后台线程处理时改状态；识别知识点前做同音字纠正后标记 terms_checked。', 'item_id → chapter_item；material_id → material。'),
    'material_text': ('资料', '资料转出来的文字，一段一行：课件一页、一张图、视频 30～60 秒一段，带出处编号（seg_key）、页码或起止秒；同音字纠正前的原文。', '资料处理完成时一段一行写入；视频同音字纠正改正文、原文存进 original；重新上传时整份删掉重写。', 'item_id → chapter_item；识别知识点时整章的文字交给大模型；知识点要点的出处编号指向这里的 seg_key。'),
    'chapter_kp_state': ('知识点', '每章一行：识别知识点走到哪一步（等上传完 / 处理资料中 / 识别中 / 待确认 / 已确认 / 失败），老师点没点“上传完毕”。', '老师点“上传完毕”、开始识别、识别完成、老师确认、资料有增删时。', 'chapter_id → chapter；ai_call_id → ai_call_log。'),
    'chapter_kp': ('知识点', '大模型识别出的知识点（老师可以改）：名称、一句话说明、重点 / 一般、要点（每条带出处，JSON）、常见误解、出处。', '识别完成时整章写入；老师改、加、删知识点时；老师确认时写上对应的 knowledge_point_id。', 'chapter_id → chapter；knowledge_point_id → knowledge_point（确认后）；feynman_setup.kp_ids 引用它。'),
    'knowledge_point': ('知识点', '老师确认后的知识点：练习出题、掌握度、诊断、画像、小助手都按这张表的知识点算。', '老师确认识别结果时从 chapter_kp 同步过来；老师也可以手动加。', 'chapter_id → chapter；被 exercise_knowledge_point、mastery、remedy_round、class_issue、class_push 引用。'),
    'exercise': ('练习', '一道题：题型（6 种）、考查类型、考第几条要点、题干、选项、答案、解析、每个选项的误解说明、简答的参考答案和要点、来源（老师手写 / 上传识别 / AI 生成 / 小助手 / 全班推送）、是不是草稿、程序检查结果。', '老师手动出题、AI 出题（先存草稿，老师确认）、小助手补练和拓展当场出题、老师全班推送出新题时。', 'item_id → chapter_item（练习项）；和知识点的关系在 exercise_knowledge_point；被 answer_record 引用。'),
    'exercise_knowledge_point': ('练习', '题目和知识点的关联（一道题可以关联多个知识点）。', '出题时一起写入。', 'exercise_id → exercise；knowledge_point_id → knowledge_point。'),
    'enrollment': ('选课', '谁加入了哪门课。加入后才能学；老师的全班分析、截止情况、课程人数只算这里的学生。', '学生在“课程学习”里点“加入课程”时；演示数据里所有学生已加入所有课程。', 'course_id → course；student_id → user。'),
    'learning_event': ('学习记录', '学习行为，一个行为一行：看了一项内容（停留秒数）、学完一项。', '学生打开内容项、离开时（停留秒数）、视频看到 90% / 文档读满 30 秒点“我已读完”、AI 对话结束时。', 'student_id → user；target_id → chapter_item。画像的“有效学习日”、解锁规则的“学完”都读它。'),
    'item_progress': ('学习记录', '每人每项一行：视频看过的时间段（合并后的 JSON）、总时长、上次位置；文档累计阅读秒数；什么时候判定学完。', '学生看视频时每约 10 秒上报一次、读文档时计时上报。', 'student_id → user；item_id → chapter_item。'),
    'answer_record': ('学习记录', '每一次作答一行：答案、对错、得分、蒙对概率、可信度、状态（已判 / 等老师确认）、简答每条要点的判定、谁确认的。', '学生交卷、做补练题、做拓展题、做老师推送的新题时；老师确认或改判简答时修改。', 'student_id → user；exercise_id → exercise；item_id → chapter_item（练习项；补练、推送题为空）。掌握度、诊断、画像、全班分析都从这里算。'),
    'mastery': ('诊断 / 画像', '每个学生每个知识点一行：原始掌握度 F（练习里每道题第一次作答的平均分）、补练 R、当前掌握度、补练状态。颜色由程序从这一行现算。', '每次交卷、做补练题或推送的新题后重算并更新。', 'student_id → user；knowledge_point_id → knowledge_point。'),
    'profile_snapshot': ('诊断 / 画像', '学完一门课那一刻的课程画像（四维和参考日，JSON），每人每门课一份，之后不再改。', '学生全部章节学完后第一次打开课程画像时。', 'student_id → user；course_id → course。平时的画像不存表，每次从学习记录和 mastery 现算。'),
    'feynman_setup': ('AI 对话', '一个 AI 对话项的设置：老师选的知识点（最多 2 个）和大模型起草、老师确认的草稿（场景、每条要点的判断标准和三级提示，JSON）、状态（待确认 / 已确认）。', '老师生成草稿、修改草稿、确认草稿时。', 'item_id → chapter_item（AI 对话项）；kp_ids → chapter_kp。'),
    'feynman_session': ('AI 对话', '一个学生的一次对话，进度整个存在 state（JSON）里：每个知识点每条要点的状态、提示级别、问了几次、怎么讲清的、学习情况小结。', '学生开始对话时新增；每说一句更新 state；结束时状态改成 done；老师重新生成草稿时没聊完的改成 expired。', 'student_id → user；item_id → chapter_item。小助手和画像里的“AI 对话情况”从这里读。'),
    'feynman_message': ('AI 对话', '对话里的每一句：谁说的、内容；学生的话附上大模型的判断（JSON，存档、复核用）。', '学生每说一句、AI 同学每回一句时。', 'session_id → feynman_session；ai_call_id → ai_call_log。'),
    'dialogue_topic': ('AI 对话（话题式）', '话题式对话的话题：情境问题、参考答案、要点、三级提示、变式题。', '老师在话题管理页编辑时。用于没有设置“讲给 AI 同学听”的对话项。', 'item_id → chapter_item；knowledge_point_id → knowledge_point。'),
    'dialogue_session': ('AI 对话（话题式）', '话题式对话的一次会话。', '学生开始、结束话题式对话时。', 'student_id → user；item_id → chapter_item。'),
    'dialogue_topic_state': ('AI 对话（话题式）', '话题式对话里每个话题的进度：答错次数、提示级别、答到的要点、结果。', '话题式对话每一轮。', 'session_id → dialogue_session；topic_id → dialogue_topic。'),
    'dialogue_message': ('AI 对话（话题式）', '话题式对话的每一句。', '话题式对话每一轮。', 'session_id → dialogue_session；ai_call_id → ai_call_log。'),
    'item_analysis': ('老师干预', '每次练习的全班分析：按哪个截止时间分析、全班人数、截止前交卷人数、结果（已分析 / 交卷不到 80% 作废）。', '练习到截止时间（或全班都交完）后，老师打开干预管理页时分析一次；延期后重新分析。', 'item_id → chapter_item（练习项）；下面挂 class_issue。'),
    'class_issue': ('老师干预', '全班分析发现的问题卡片：规则①一道题全班 ≥ 80% 做错（题目检查）；规则②某条要点在某种考查类型上错误率 > 50%。做错的学生、老师的处理状态。', '分析时写入；老师点“没问题 / 已修正 / 作废 / 忽略”、推送后改状态。', 'analysis_id → item_analysis；exercise_id → exercise；knowledge_point_id → knowledge_point。'),
    'class_push': ('老师干预', '老师对全班问题的处理：补充讲解（大模型起草或老师写）、新题、推送给哪些学生、推送前的错误率、状态（草稿 / 已推送）。', '老师处理卡片时新增草稿；修改、重新生成、推送时更新。', 'issue_ids → class_issue；exercise_ids → exercise（来源 class）；学生在小助手里看到。'),
    'remedy_round': ('小助手', '小助手的补练和拓展，每一轮一行：知识点、补练还是拓展、考查类型、第几轮、这一轮的题、平均分、2 轮后还没补回来。', '学生在小助手里点“做补练题”“来点更难的”时新增；交了以后更新。', 'student_id → user；knowledge_point_id → knowledge_point；exercise_ids → exercise（来源 assistant）。'),
    'assistant_message': ('小助手', '小助手聊天的每一句；被程序拦下的回复记下原因和原文（给开发看，学生看到的是固定的话）。', '学生在小助手里问一句、小助手回一句时。', 'student_id → user；chapter_id → chapter；ai_call_id → ai_call_log。每天最多 20 句也按这张表数。'),
    'assistant_notice': ('小助手', '老师给学生的提醒（例如分析作废时提醒没交卷的学生），显示在学生的小助手和消息铃铛里。', '老师在干预管理页点“提醒没交的学生”时。', 'student_id → user；item_id → chapter_item。'),
    'ai_call_log': ('运维', '每一次调用大模型：任务名、提示词版本、输入 / 输出 token、耗时、费用、成功或失败原因、程序检查结果。', '每次调用大模型（看图、识别知识点、AI 对话、出题、判简答、聊天、补充讲解、同音字纠正）都写一行。', '被 chapter_kp_state、feynman_message、dialogue_message、assistant_message 的 ai_call_id 引用；管理员在“AI 调用记录”页查看。'),
}

OPS = [('insert', '新增'), ('updateById', '修改'), ('update', '修改'), ('deleteById', '删除'), ('delete', '删除')]


def mapper_usage():
    """表 → {'写': {类: set(操作)}, '读': set(类)}"""
    entity_table, mapper_entity = {}, {}
    files = []
    for dp, _, fs in os.walk(SRC):
        for f in fs:
            if f.endswith('.java'):
                files.append(os.path.join(dp, f))
    for p in files:
        s = open(p, encoding='utf-8').read()
        m = re.search(r'@TableName\("`?(\w+)`?"\)\s*public class (\w+)', s)
        if m:
            entity_table[m.group(2)] = m.group(1)
        m = re.search(r'interface (\w+Mapper) extends BaseMapper<(\w+)>', s)
        if m:
            mapper_entity[m.group(1)] = m.group(2)
    # Mapper 里手写的 SQL（@Select 等）：方法名 → 它读写的表
    custom = {}
    for p in files:
        s = open(p, encoding='utf-8').read()
        m = re.search(r'interface (\w+Mapper)', s)
        if not m:
            continue
        for sql, method in re.findall(r'@(?:Select|Update|Insert|Delete)\("([^"]*)"\)\s*\w[\w<>, ]*\s+(\w+)\(', s):
            tables = re.findall(r'(?:FROM|JOIN|UPDATE|INTO)\s+`?(\w+)`?', sql, re.I)
            custom[(m.group(1), method)] = tables
    usage = {}
    for p in files:
        s = open(p, encoding='utf-8').read()
        cls = os.path.basename(p)[:-5]
        for mtype, field in re.findall(r'private (?:final )?(\w+Mapper) (\w+);', s):
            for call in re.findall(r'\b' + field + r'\.(\w+)\(', s):
                for t in custom.get((mtype, call), []):
                    usage.setdefault(t, {'写': {}, '读': set()})['读'].add(cls + '（' + mtype + '.' + call + ' 手写 SQL）')
    for p in files:
        s = open(p, encoding='utf-8').read()
        cls = os.path.basename(p)[:-5]
        for mtype, field in re.findall(r'private (?:final )?(\w+Mapper) (\w+);', s):
            table = entity_table.get(mapper_entity.get(mtype, ''))
            if not table:
                continue
            u = usage.setdefault(table, {'写': {}, '读': set()})
            for call in re.findall(r'\b' + field + r'\.(\w+)\(', s):
                op = next((name for k, name in OPS if call == k), None)
                if op:
                    u['写'].setdefault(cls, set()).add(op)
                else:
                    u['读'].add(cls)
    return usage


def mysql(sql):
    if not (os.path.exists(MYSQL) and os.path.exists(PWFILE)):
        return None
    pw = next((l.split(':', 1)[1].strip() for l in open(PWFILE, encoding='utf-8') if l.startswith('root:')), None)
    r = subprocess.run([MYSQL, '-uroot', '-p' + pw, '-h127.0.0.1', '--default-character-set=utf8mb4', '-B',
                        'se_platform', '-e', sql], capture_output=True)
    if r.returncode != 0:
        return None
    return r.stdout.decode('utf-8')


def cell(v, col):
    if col in MASK and v not in ('NULL', ''):
        return '（BCrypt 哈希，打码）'
    v = v.replace('|', '\\|').replace('\\n', ' ')
    return v if len(v) <= 80 else v[:80] + '…'


def main():
    tables, indexes = parse(open(SQL, encoding='utf-8').read())
    usage = mapper_usage()
    live = mysql('SELECT 1') is not None
    print('共 %d 张表。每张表的“谁写 / 谁读”是扫描后端源码里每个 Mapper 的调用得出的（类名）；'
          '“现在的数据”来自本机 MySQL `se_platform`%s。\n' % (len(tables), '' if live else '（这次生成时 MySQL 没开，略去）'))
    order = list(TABLES)
    for t in sorted(tables, key=lambda x: order.index(x['name']) if x['name'] in order else 99):
        name = t['name']
        mod, purpose, when, rel = TABLES.get(name, ('—', '—', '—', '—'))
        print('### `%s`（%s）\n' % (name, mod))
        print('- **用途**：' + purpose)
        print('- **什么时候写入**：' + when)
        print('- **和哪些表关联**：' + rel)
        keys = t['keys'] + ['索引 %s（%s）' % (n, c) for n, tb, c in indexes if tb == name]
        print('- **主键 / 唯一约束 / 索引**：' + '；'.join(k.replace('|', '\\|') for k in keys))
        u = usage.get(name, {'写': {}, '读': set()})
        w = '；'.join('`%s`（%s）' % (c, '、'.join(sorted(ops))) for c, ops in sorted(u['写'].items())) or '—'
        r = '、'.join('`%s`' % c for c in sorted(u['读'])) or '—'
        print('- **谁写**：' + w)
        print('- **谁读**：' + r)
        if live:
            n = mysql('SELECT COUNT(*) FROM `%s`' % name).split('\n')[1]
            print('- **现在的数据**：%s 行' % n)
        print('\n| 列 | 类型 | 约束 / 默认值 | 含义 |\n|---|---|---|---|')
        for c in t['cols']:
            m = meaning(name, c[0], c[3])
            print('| `%s` | %s | %s | %s |' % (c[0], c[1], c[2].replace('|', '\\|') or '', m.replace('|', '\\|')))
        if live:
            out = mysql('SELECT * FROM `%s` ORDER BY 1 LIMIT 1' % name)
            lines = [l for l in out.split('\n') if l]
            if len(lines) >= 2:
                head, row = lines[0].split('\t'), lines[1].split('\t')
                print('\n一行真实数据（长的内容截断到 80 字）：\n\n| 列 | 值 |\n|---|---|')
                for h, v in zip(head, row):
                    print('| `%s` | %s |' % (h, cell(v, h)))
            else:
                print('\n（现在没有数据。）')
        print()


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main()
