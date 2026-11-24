"""演示用：学生按正常的进度接口把第 6 章练习之前的项学完（文档读满 30 秒并点读完；视频上报看完全程），看练习是否解锁。
用法：python -X utf8 student_unlock.py <章节id> <学号>"""
import json, re, sys, urllib.request
B = 'http://localhost:8080/api'
def call(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    h = {'Content-Type': 'application/json'}
    if token: h['Authorization'] = 'Bearer ' + token
    try:
        r = urllib.request.urlopen(urllib.request.Request(B + path, data=data, method=method, headers=h), timeout=120)
        return json.loads(r.read()).get('data')
    except urllib.error.HTTPError as e:
        return {'error': e.code, 'body': e.read().decode('utf-8')[:200]}
ch, user = int(sys.argv[1]), sys.argv[2]
pw = re.search(r'演示账号的密码都是 (\S+?)（', open('C:/ZI/se-learning-platform/backend/src/main/resources/data.sql', encoding='utf-8').read()).group(1)
tok = call('POST', '/auth/login', {'username': user, 'password': pw})['token']
course = None
for c in call('GET', '/learn/courses?page=1&size=50', token=tok)['records']:
    lc = call('GET', f"/learn/courses/{c['id']}", token=tok)
    if any(x['id'] == ch for x in lc['chapters']):
        course = lc
        break
chap = next(x for x in course['chapters'] if x['id'] == ch)
for it in chap['items']:
    if it['id'] in course['doneItemIds']:
        print('已学完', it['title']); continue
    if it['type'] == 'document':
        for _ in range(4):
            call('POST', f"/learn/items/{it['id']}/reading", {'seconds': 10}, tok)
        print('文档', it['title'], call('POST', f"/learn/items/{it['id']}/finish-reading", token=tok))
    elif it['type'] == 'video':
        d = float(it.get('duration') or it.get('durationSec') or 600)
        print('视频', it['title'], call('POST', f"/learn/items/{it['id']}/video-progress", {'duration': d, 'ranges': [[0, d]], 'position': d}, tok))
    else:
        print('其他', it['type'], it['title'], '（要自己完成）')
lc = call('GET', f"/learn/courses/{course['course']['id']}", token=tok)
print('课程', course['course']['id'], '锁着的：', [i['title'] for i in chap['items'] if i['id'] in lc['lockedItemIds']])
