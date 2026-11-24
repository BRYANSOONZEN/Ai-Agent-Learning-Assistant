"""第 6 章实测：建一个练习项（排在 AI 对话之后），按知识点逐个调用 AI 出题，记录每个知识点的耗时、调用次数、token、费用和检查结果。
用法：python -X utf8 gen_ch6.py <章节id> <输出json>
设置：考查类型 4 种全选；题型：单选、两段式、判断、填空、简答；每个知识点 3 道。"""
import json, re, sys, time, urllib.request
B = 'http://localhost:8080/api'
def call(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    h = {'Content-Type': 'application/json'}
    if token: h['Authorization'] = 'Bearer ' + token
    r = urllib.request.urlopen(urllib.request.Request(B + path, data=data, method=method, headers=h), timeout=900)
    return json.loads(r.read())['data']
ch, out = int(sys.argv[1]), sys.argv[2]
pw = re.search(r'演示账号的密码都是 (\S+?)（', open('C:/ZI/se-learning-platform/backend/src/main/resources/data.sql', encoding='utf-8').read()).group(1)
tok = call('POST', '/auth/login', {'username': 'teacher01', 'password': pw})['token']
items = call('GET', f'/chapters/{ch}/items', token=tok)
ex = next((i for i in items if i['type'] == 'exercise'), None)
if ex is None:
    ex = call('POST', '/chapter-items', {'chapterId': ch, 'type': 'exercise', 'title': '练习：智能测试生成与优化',
                                          'description': '每个知识点 3 道，做完再看结果。'}, tok)
    items.append(ex)
# 学习顺序：课件 → 视频 → AI 对话 → 练习
rank = {'document': 0, 'video': 1, 'dialogue': 2, 'exercise': 3}
order = sorted(items, key=lambda i: (rank[i['type']], i['sortOrder'], i['id']))
call('PUT', f'/chapters/{ch}/items/order', {'ids': [i['id'] for i in order]}, tok)
print('顺序：', ' → '.join(i['title'] for i in order), flush=True)
kps = call('GET', f'/chapters/{ch}/kp-status', token=tok)['kps']
rec = {'chapterId': ch, 'itemId': ex['id'], 'settings': {'assessTypes': ['concept', 'scenario', 'data', 'process'],
       'types': ['single_choice', 'two_tier', 'judge', 'fill', 'short_answer'], 'perKp': 3}, 'kps': []}
for k in kps:
    t = time.time()
    r = call('POST', '/exercises/generate', {'itemId': ex['id'], 'kpIds': [k['id']], **rec['settings']}, tok)
    row = r['kps'][0] | {'ms': r['ms'], 'calls': r['calls'], 'inputTokens': r['inputTokens'],
                         'outputTokens': r['outputTokens'], 'costCny': r['costCny']}
    rec['kps'].append(row)
    print(f"{k['name']}: 出 {row['created']} 丢 {row['dropped']} 要点 {row['points']} 误解 {row['misconceptions']} "
          f"歧义 {row['flagged']} | {row['ms']/1000:.1f}s {row['calls']} 次 {row['costCny']:.4f} 元 | {'；'.join(row['notes'])}", flush=True)
qs = call('GET', f"/chapters/{ch}/exercises?itemId={ex['id']}", token=tok)
rec['questions'] = qs
rec['total'] = {'created': sum(x['created'] for x in rec['kps']), 'ms': sum(x['ms'] for x in rec['kps']),
                'calls': sum(x['calls'] for x in rec['kps']), 'costCny': round(sum(x['costCny'] for x in rec['kps']), 4)}
json.dump(rec, open(out, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print('合计', rec['total'], flush=True)
