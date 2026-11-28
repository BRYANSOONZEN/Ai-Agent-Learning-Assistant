# -*- coding: utf-8 -*-
"""
录演示视频前准备数据（只动第 6 章的练习，item 65）：
- 再给 7 个学生（学生01、02、04、05、06、07、09）各交一次第 6 章练习，加上原来的学生08，共 8/10 人（= 80%，够全班分析）；
- 第 47 题大多数人错（规则 ①，给老师检查）；要点 1 × 概念理解（33、39、42、48）错的超过一半（规则 ②，出卡片）；
- 把练习截止时间设成已经过了（2026-11-27 23:59），老师端马上能看到分析结果；
- 学生03（id 7）和学生10（id 14）不交；学生03 的 4 个视频记为已看完，视频里现场读课件、AI 对话、做练习。
输出 SQL 到标准输出：python tools/demo/prep_demo_data.py > demo.sql，再用 mysql 执行。
恢复：用 C:\\ZI\\mysql\\backup-20261128-录视频前.sql。
"""
import random
import sys

ITEM, COURSE = 65, 1
DUE = '2026-11-27 23:59:00'
STUDENTS = [5, 6, 8, 9, 10, 11, 13]
# 题号 → (题型, 正确答案)；两段式写成 “答案|理由”
KEYS = {
    33: ('single_choice', 'A'), 34: ('two_tier', 'A|A'), 35: ('short_answer', None), 36: ('single_choice', 'B'),
    37: ('two_tier', 'B|B'), 38: ('judge', 'A'), 39: ('two_tier', 'B|B'), 40: ('single_choice', 'B'),
    41: ('short_answer', None), 42: ('single_choice', 'B'), 43: ('two_tier', 'B|A'), 44: ('short_answer', None),
    45: ('two_tier', 'A|A'), 46: ('single_choice', 'D'), 47: ('judge', 'B'), 48: ('single_choice', 'B'),
    49: ('two_tier', 'B|B'), 50: ('two_tier', 'A|A'), 51: ('single_choice', 'B'), 52: ('judge', 'A'),
}
# 每道题答错的概率：47 题几乎全错；要点 1 概念理解那 4 题错得多；其余少量错
WRONG = {47: 0.9, 33: 0.7, 39: 0.85, 42: 0.75, 48: 0.75}
BASE_WRONG = 0.2


def q(s):
    return "'" + s.replace("\\", "\\\\").replace("'", "''") + "'"


def wrong_letter(right, choices='ABCD'):
    return random.choice([c for c in choices if c != right])


def main():
    random.seed(20261128)
    out = ['SET NAMES utf8mb4;', 'START TRANSACTION;',
           'DELETE FROM answer_record WHERE item_id=%d AND student_id IN (%s);' % (ITEM, ','.join(map(str, STUDENTS))),
           'DELETE FROM item_analysis WHERE item_id=%d;' % ITEM,
           'DELETE FROM class_issue WHERE item_id=%d;' % ITEM,
           "UPDATE chapter_item SET due_at='%s' WHERE id=%d;" % (DUE, ITEM)]
    for n, sid in enumerate(STUDENTS):
        at = '2026-11-26 %02d:%02d:00' % (19 + n // 3, (n * 17) % 60)
        for eid, (typ, key) in KEYS.items():
            bad = random.random() < WRONG.get(eid, BASE_WRONG)
            detail = 'NULL'
            if typ == 'short_answer':
                # 简答题照抄学生08 的那一份（答案和 AI 批改明细），分数按对错调整
                ans = "(SELECT answer FROM answer_record WHERE student_id=12 AND exercise_id=%d AND item_id=%d LIMIT 1)" % (eid, ITEM)
                detail = "(SELECT detail FROM answer_record WHERE student_id=12 AND exercise_id=%d AND item_id=%d LIMIT 1)" % (eid, ITEM)
                score, guess = (0.5 if bad else 1.0), 0
                out.append('INSERT INTO answer_record (student_id,exercise_id,course_id,item_id,answer,is_correct,score,guess,'
                           'reliability,status,detail,submitted_at) SELECT %d,%d,%d,%d,a.answer,%d,%s,0,1,\'graded\',a.detail,\'%s\' '
                           'FROM answer_record a WHERE a.student_id=12 AND a.exercise_id=%d AND a.item_id=%d LIMIT 1;'
                           % (sid, eid, COURSE, ITEM, 0 if bad else 1, score, at, eid, ITEM))
                continue
            if typ == 'two_tier':
                a, r = key.split('|')
                ans = (wrong_letter(a) + '|' + r) if bad else key
                guess = 1 / 12
            elif typ == 'judge':
                ans = ('B' if key == 'A' else 'A') if bad else key
                guess = 0.5
            else:
                ans = wrong_letter(key) if bad else key
                guess = 0.25
            out.append('INSERT INTO answer_record (student_id,exercise_id,course_id,item_id,answer,is_correct,score,guess,'
                       "reliability,status,detail,submitted_at) VALUES (%d,%d,%d,%d,%s,%d,%s,%s,1,'graded',NULL,'%s');"
                       % (sid, eid, COURSE, ITEM, q(ans), 0 if bad else 1, 0 if bad else 1, guess, at))
    # 学生03：4 个视频算看完，只剩课件没读，视频里现场读课件 → AI 对话解锁 → 对话 → 练习
    out.append("DELETE FROM learning_event WHERE student_id=7 AND target_id IN (4,5,6,7,8,33,65);")
    for n, item in enumerate((5, 6, 7, 8)):
        out.append("INSERT INTO learning_event (student_id,event_type,target_id,course_id,created_at) "
                   "VALUES (7,'finish_item',%d,%d,'2026-11-28 10:%02d:00');" % (item, COURSE, 5 + n * 13))
    out.append('COMMIT;')
    print('\n'.join(out))


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main()
