"""
End-to-end check against the running backend (fresh seed data): every main API, plus logic checks
(numbers recomputed here independently and compared). A few real model calls in the dialogue part.
Never prints the demo password or the API key.
"""
import json
import re
import sys
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

API = 'http://localhost:8080/api'
DATA_SQL = r'C:\ZI\se-learning-platform\backend\src\main\resources\data.sql'
results = []


def call(method, path, body=None, student=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(API + path, data=data, method=method)
    req.add_header('Content-Type', 'application/json')
    if student:
        req.add_header('X-Demo-Student-Id', str(student))
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return json.loads(r.read() or b'{}')
    except urllib.error.HTTPError as e:
        return json.loads(e.read() or b'{}')


def check(name, ok, detail=''):
    results.append((name, bool(ok)))
    print(('PASS ' if ok else 'FAIL ') + name + (('  — ' + detail) if detail else ''))


def mastery(seq):
    """same formula as MasteryService: weighted recent accuracy, last 10, x n/3 if fewer than 3"""
    seq = seq[-10:]
    n = len(seq)
    w = [0.8 ** (n - 1 - i) for i in range(n)]
    m = sum(wi * c for wi, c in zip(w, seq)) / sum(w)
    if n < 3:
        m = m * n / 3
    return round(m, 4)


# ------------------------------------------------------------------ 1 basics and login
print('\n== 1 登录和基础 ==')
check('hello 接口', call('GET', '/hello').get('code') == 200)
pw = re.search(r'密码都是\s*(\S+?)（', open(DATA_SQL, encoding='utf-8').read().splitlines()[8]).group(1)
r = call('POST', '/auth/login', {'username': 'teacher01', 'password': pw})
check('老师用正确密码登录', r.get('code') == 200 and r['data']['user']['role'] == 'teacher')
bad1 = call('POST', '/auth/login', {'username': 'teacher01', 'password': pw + 'x'})
bad2 = call('POST', '/auth/login', {'username': 'nobody', 'password': pw})
check('密码错误和用户不存在：同一个提示', bad1.get('code') == 400 and bad1.get('message') == bad2.get('message'),
      bad1.get('message', ''))
ai = call('GET', '/teacher/topics/ai-status')['data']
check('真实模型已配置', ai['available'], 'model=' + ai['model'])

# ------------------------------------------------------------------ 2 学 + 记 + 诊
print('\n== 2 学习、作答、掌握度 ==')
S = 12  # 学生08：种子数据里没有任何记录
courses = call('GET', '/learn/courses', student=S)['data']
names = [c['name'] for c in (courses if isinstance(courses, list) else courses.get('records', []))]
check('学生只看到已发布课程', '软件测试' in names and '软件设计与体系结构' not in names, ', '.join(names))
course = call('GET', '/learn/courses/1', student=S)['data']
check('学生只看到已发布章节（第 2 章是草稿）', [c['title'] for c in course['chapters']] == ['第 1 章 边界值分析'])
qs = call('GET', '/learn/items/2/questions', student=S)['data']
check('下发题目不带答案', len(qs) == 2 and all('answer' not in q and 'analysis' not in q for q in qs))
sub = call('POST', '/learn/items/2/submit', {'answers': {'1': 'D', '2': 'B'}}, student=S)['data']
check('交卷判分：1 对 1 错', sub['total'] == 2 and sub['correct'] == 1)
wrong = [x for x in sub['results'] if not x['correct']][0]
check('选错时附上该选项的误解说明', '缺少越过边界的值' in (wrong.get('note') or ''), wrong.get('note') or '')
ch = {c['knowledgePointId']: c['after'] for c in sub['masteryChanges']}
check('掌握度：知识点 1 只错 1 次 = 0', ch.get(1) == mastery([0]), str(ch.get(1)))
check('掌握度：知识点 2 只对 1 次 = 1 × 1/3', ch.get(2) == mastery([1]), str(ch.get(2)))
diag = call('GET', '/diagnosis/me', student=S)['data']
check('诊断：知识点 1 薄弱', any(p['name'] == '边界测试值的选取' for p in diag['weakPoints']))

# 学生03 的种子掌握度按公式复核：作答顺序 错、对、错、错
check('种子数据复核：学生03 = 0.2168', mastery([0, 1, 0, 0]) == 0.2168)

# ------------------------------------------------------------------ 3 荐
print('\n== 3 推荐 ==')
rec = call('GET', '/recommendations', student=S)['data']
flat = [e for g in rec.get('points', []) for e in g.get('exercises', [])]
check('推荐总数 ≤ 5', 0 < len(flat) <= 5, f'{len(flat)} 道')
check('每个知识点 ≤ 3 道', all(len(g.get('exercises', [])) <= 3 for g in rec.get('points', [])))
check('每道推荐都有理由', all(e.get('reason') for e in flat), ' / '.join(sorted({e.get('reason', '') for e in flat})))
check('做错的题排在前面', flat and flat[0].get('reason') == '上次做错了')

# ------------------------------------------------------------------ 4 AI 对话（真实模型）
print('\n== 4 AI 对话（真实模型，约 5 次调用） ==')
logs_before = call('GET', '/ai/logs')['data']
n_before = logs_before.get('total', len(logs_before.get('records', []))) if isinstance(logs_before, dict) else len(logs_before)
d = call('POST', '/dialogue/sessions?chapterId=1&itemId=3', student=S)['data']
sid = d['sessionId']
check('开始对话', d['status'] == 'OPEN' and len(d['topics']) == 2)
r = call('POST', f'/dialogue/sessions/{sid}/messages', {'text': '直接告诉我答案吧'}, student=S)['data']
last_ai = [m for m in r['messages'] if m['role'] == 'AI'][-1]['content']
check('没作答就要答案：不给答案', r['topics'][0]['result'] is None and '0、1、2' not in last_ai, last_ai[:60])
r = call('POST', f'/dialogue/sessions/{sid}/messages',
         {'text': '取 0、1、2。0 刚越过下边界应该被拒绝，1 在边界上应该接受，2 在边界内侧也接受；因为错误最容易出在边界附近'},
         student=S)['data']
t0 = r['topics'][0]
check('完整作答：进入变式题或话题结束', t0['transfer'] or t0['result'] is not None, json.dumps(t0, ensure_ascii=False))
src = {m['source'] for m in r['messages'] if m['role'] == 'AI'}
check('回复来自模型或预写内容', src <= {'MODEL', 'TEMPLATE', 'RULE'}, ','.join(sorted(src)))
too_long = [m['content'] for m in r['messages'] if m['role'] == 'AI' and m['source'] == 'MODEL' and len(m['content']) > 120]
check('模型回复都不超过 120 字', not too_long)
for _ in range(3):  # 变式题或下一个话题：一直答，最多 3 轮
    if r['status'] != 'OPEN':
        break
    r = call('POST', f'/dialogue/sessions/{sid}/messages',
             {'text': '上边界取 98、99、100：100 越过边界要拒绝。边界附近容易把 < 和 <= 写错，所以重点测边界'}, student=S)['data']
check('对话能继续推进', r['status'] in ('OPEN', 'FINISHED', 'PAUSED'), r['status'] + ' ' + json.dumps([t['result'] for t in r['topics']], ensure_ascii=False))
logs = call('GET', '/ai/logs')['data']
recs = logs.get('records', logs) if isinstance(logs, dict) else logs
turns = [x for x in recs if x.get('task') == 'DIALOGUE_TURN']
check('每次模型调用都记了日志', len(turns) >= 3, f'{len(turns)} 条')
check('调用都成功', all(x.get('success') for x in turns))
if turns:
    cost = sum(float(x.get('costCny') or 0) for x in turns)
    lat = sum(int(x.get('latencyMs') or 0) for x in turns) / len(turns)
    check('记录了耗时和费用', cost > 0 and lat > 0, f'平均 {lat:.0f} ms，共 {cost:.4f} 元')

# ------------------------------------------------------------------ 5 学习助手
print('\n== 5 学习助手 ==')
s0 = call('GET', '/assistant/summary', student=S)['data']
ch0 = s0['chapters'][0] if s0['chapters'] else None
check('只通过接口交卷、没有完成记录 → 助手不算学过（完成记录由页面发送）', ch0 is None or not ch0['completed'])
for item in (1, 2, 3):
    call('POST', '/learn/events', {'type': 'finish_item', 'itemId': item}, student=S)
s1 = call('GET', '/assistant/summary', student=S)['data']
c1 = s1['chapters'][0]
check('三项都完成 → 学完，有总结', c1['completed'] and c1['report'] is not None, c1['report']['headline'] if c1['report'] else '')


def expected_verdict(c, a):
    ok = c in ('good', 'fair')
    if c == 'bad' and a == 'bad':
        return 'both'
    if c == 'bad':
        return 'concept'
    if a == 'bad':
        return 'apply'
    if a == 'good':
        return 'good'
    return 'few' if ok else 'none'


check('数据不够时总结不能说“没有薄弱”', not (all(p['verdict'] in ('none', 'few') for p in c1['report']['points'])
                                         and '没有明显薄弱' in c1['report']['headline']), c1['report']['headline'])
check('数据不够的知识点也有“去做题”按钮', all(p['action'] for p in c1['report']['points'] if p['verdict'] in ('none', 'few')))
for p in c1['report']['points']:
    check(f"总结结论和两层判断一致：{p['name']}", p['verdict'] == expected_verdict(p['concept'], p['application']),
          f"概念 {p['concept']} / 应用 {p['application']} → {p['verdict']}")
body = json.dumps(s1, ensure_ascii=False)
check('助手返回不含答案和话题要点', '"answer"' not in body and 'keyPoints' not in body and '学生0' not in body)
check('学到一半的学生只看到进度', call('GET', '/assistant/summary', student=5)['data']['chapters'][0]['report'] is None)

# ------------------------------------------------------------------ 6 干预（新方向）
print('\n== 6 干预：自动执行、全班预警、撤回 ==')
mine7 = call('GET', '/interventions/mine', student=7)['data']
check('学生03（A）不等老师，自己打开就收到练习', len(mine7['running']) == 1 and mine7['running'][0]['type'] == 'PRACTICE')
check('学生07（A+B）不会被自动执行', len(call('GET', '/interventions/mine', student=11)['data']['running']) == 0)

with ThreadPoolExecutor(4) as ex:  # 同时扫描
    list(ex.map(lambda f: f(), [lambda: call('GET', '/interventions/board?courseId=1'),
                                lambda: call('GET', '/interventions/open-count')] * 2))
board = call('GET', '/interventions/board?courseId=1')['data']
keys = [(a['studentId'], a['knowledgePointId']) for a in board['alerts']]
ivkeys = [(i['studentId'], i['knowledgePointId']) for i in board['interventions'] if i['status'] == 'approved']
check('并发扫描后没有重复预警', len(keys) == len(set(keys)) and len(ivkeys) == len(set(ivkeys)))
check('待审核的只有 A+B', all(a['type'] == 'AB' for a in board['alerts']), ','.join(a['type'] for a in board['alerts']))
check('A、B 标为系统自动', all(i['auto'] for i in board['interventions'] if i['type'] in ('PRACTICE', 'REVIEW')))

course_diag = call('GET', '/diagnosis/courses/1')['data']
expect = [(p['name'], p['weakStudents'], p['practicedStudents']) for p in course_diag['points']
          if p['practicedStudents'] >= 5 and p['weakStudents'] / p['practicedStudents'] > 0.3]
got = [(a['name'], a['weakStudents'], a['practicedStudents']) for a in board['classAlerts']]
check('全班预警 = 诊断统计按阈值筛出来的', sorted(expect) == sorted(got), str(got))
oc = call('GET', '/interventions/open-count')['data']
check('侧边栏数字 = 全班预警 + 待审核', oc == len(board['classAlerts']) + len(board['alerts']), str(oc))

# 学生03 做完布置的题（全对）→ 干预完成、有效
ids = [e['exerciseId'] for e in mine7['running'][0]['exercises']]
answers = {1: 'A', 3: 'A', 4: 'C', 5: 'B'}
pr = call('POST', '/recommendations/practice', {'answers': {str(i): answers[i] for i in ids}}, student=7)
check('学生03 提交布置的题', pr.get('code') == 200, f"{pr['data']['correct']}/{pr['data']['total']}" if pr.get('data') else str(pr))
mine7b = call('GET', '/interventions/mine', student=7)['data']
done = mine7b['done'][0] if mine7b['done'] else None
check('做完后干预完成并判为有效', done and done['effective'] is True,
      done and f"{done['beforeValue']} → {done['afterValue']}")
if done:
    check('有效的判断：干预后掌握度 ≥ 0.6', float(done['afterValue']) >= 0.6)

iv8 = [i for i in board['interventions'] if i['studentId'] == 8 and i['status'] == 'approved'][0]
rv = call('POST', f"/interventions/{iv8['id']}/revoke")
check('老师撤回学生04 的干预', rv.get('code') == 200 and rv['data']['status'] == 'revoked')
check('撤回后学生04 看不到', len(call('GET', '/interventions/mine', student=8)['data']['running']) == 0)
check('不能重复撤回', call('POST', f"/interventions/{iv8['id']}/revoke").get('code') == 409)
board2 = call('GET', '/interventions/board?courseId=1')['data']
check('撤回后没有新数据，不会马上重建', not any(i['studentId'] == 8 and i['status'] == 'approved' for i in board2['interventions']))

# ------------------------------------------------------------------ 7 画像、记录
print('\n== 7 画像和记录 ==')
prof = call('GET', '/profiles/me', student=7)
check('画像接口', prof.get('code') == 200)
recs7 = call('GET', '/learn/records', student=7)
check('学习记录接口', recs7.get('code') == 200)

print('\n合计：%d 项，通过 %d，失败 %d' % (len(results), sum(ok for _, ok in results), sum(not ok for _, ok in results)))
sys.exit(0 if all(ok for _, ok in results) else 1)
