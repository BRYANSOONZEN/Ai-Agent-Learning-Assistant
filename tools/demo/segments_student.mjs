// 学生端演示（单独录，真实速度，不快进）：学生09 学新课「智能软件工程」第 6 章——
//   加入课程 → 章节介绍和知识点地图 → 读课件 → 看 4 个视频（2 倍速，服务器允许的最快速度）→ AI 对话 → 练习 →
//   小助手（学习情况、出补练题 → 学习推荐里做、拓展 → 保存、老师给你的 → 接收 → 学习推荐里做、问问题）→ 学情诊断 → 学生画像
// 用法：DEMO_SEGMENTS=segments_student.mjs node tools/demo/record.mjs [段名 ...]
import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'

const MYSQL_DIR = process.env.MYSQL_DIR || 'C:/ZI/mysql'
const STUDENT = '学生09'
const SID = 13
const COURSE = '智能软件工程'
const VIDEOS = ['视频 6-1 自动化测试', '视频 6-2 智能测试', '视频 6-3 测试用例生成效果对比', '视频 6-4 缺陷自动检测与修复效果对比']
const LONG = 60 * 60 * 1000

const seg = (setup, fn, teardown) => Object.assign(fn, { setup, teardown })

function sql(q) {
  const line = fs.readFileSync(path.join(MYSQL_DIR, '本机密码.txt'), 'utf8').split(/\r?\n/).find((l) => l.includes('se_app'))
  const pw = line.split(/[:：=\s]+/).pop()
  return execFileSync(path.join(MYSQL_DIR, 'mysql-8.4.11-winx64/bin/mysql.exe'),
    ['-h127.0.0.1', '-use_app', '-p' + pw, 'se_platform', '--default-character-set=utf8mb4', '-N', '-e', q],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim()
}
const rows = (q) => { const s = sql(q); return s ? s.split(/\r?\n/).map((l) => l.split('\t')) : [] }
const courseId = () => Number(sql(`SELECT id FROM course WHERE name='${COURSE}' ORDER BY id DESC LIMIT 1`))
const chapterId = () => Number(sql(`SELECT id FROM chapter WHERE course_id=${courseId()} ORDER BY id LIMIT 1`))
const itemId = (title) => Number(sql(`SELECT id FROM chapter_item WHERE course_id=${courseId()} AND title='${title}' LIMIT 1`))
const learnUrl = (view) => `/learn/courses/${courseId()}?chapter=${chapterId()}&view=${view}`

/** 学生09 在新课里的学习记录全部清掉（重录时用；不动「软件测试」里的记录和老师推送） */
function resetStudent() {
  const c = courseId()
  sql(`DELETE FROM feynman_message WHERE session_id IN (SELECT id FROM (SELECT id FROM feynman_session WHERE student_id=${SID}
         AND item_id IN (SELECT id FROM chapter_item WHERE course_id=${c})) s);
    DELETE FROM feynman_session WHERE student_id=${SID} AND item_id IN (SELECT id FROM chapter_item WHERE course_id=${c});
    DELETE FROM answer_record WHERE student_id=${SID} AND (course_id=${c} OR exercise_id IN (SELECT id FROM exercise WHERE course_id=${c}));
    DELETE FROM remedy_round WHERE student_id=${SID} AND knowledge_point_id IN (SELECT id FROM knowledge_point WHERE course_id=${c});
    DELETE FROM mastery WHERE student_id=${SID} AND knowledge_point_id IN (SELECT id FROM knowledge_point WHERE course_id=${c});
    DELETE FROM item_progress WHERE student_id=${SID} AND course_id=${c};
    DELETE FROM learning_event WHERE student_id=${SID} AND course_id=${c};
    DELETE FROM assistant_message WHERE student_id=${SID} AND chapter_id=${chapterId()};
    DELETE FROM profile_snapshot WHERE student_id=${SID} AND course_id=${c};
    DELETE FROM enrollment WHERE student_id=${SID} AND course_id=${c};`)
}

/** 重录小助手这段时：撤掉补练、拓展（题、作答、轮次）和“补回来”的状态，回到刚交完卷的样子 */
function resetAssistant() {
  const kps = `SELECT id FROM knowledge_point WHERE course_id=${courseId()}`
  const ids = rows(`SELECT exercise_ids FROM remedy_round WHERE student_id=${SID} AND knowledge_point_id IN (${kps})`)
    .flatMap((r) => r[0].split(',')).filter((x) => /^\d+$/.test(x))
  if (ids.length) {
    sql(`DELETE FROM answer_record WHERE student_id=${SID} AND exercise_id IN (${ids.join(',')});
      UPDATE exercise SET deleted=1 WHERE id IN (${ids.join(',')});`)
  }
  sql(`DELETE FROM remedy_round WHERE student_id=${SID} AND knowledge_point_id IN (${kps});
    UPDATE mastery SET attempt_count = attempt_count - remedy_count,
      correct_count = GREATEST(0, correct_count - ROUND(IFNULL(remedy,0) * remedy_count)),
      mastery = IFNULL(first_mastery, mastery), remedy = NULL, remedy_count = 0, remedy_status = NULL
      WHERE student_id=${SID} AND knowledge_point_id IN (${kps});
    DELETE FROM assistant_message WHERE student_id=${SID} AND chapter_id=${chapterId()};
    UPDATE class_push SET accepted_ids = TRIM(BOTH ',' FROM REPLACE(CONCAT(',', accepted_ids, ','), ',${SID},', ',')) WHERE FIND_IN_SET(${SID}, accepted_ids);`)
}

/** 学习推荐页刚打开时可能还没取到刚出的题：等一下，没有就刷新一次 */
async function waitSet(d, sel) {
  try {
    await d.waitFor(`document.querySelector('${sel}')`, 8000, '学习推荐里的题')
  } catch {
    await d.eval('location.reload()')
    await d.wait(3000)
    await d.waitFor(`document.querySelector('${sel}')`, 20000, '学习推荐里的题')
  }
}

/** 重录 AI 对话时只清对话（不动视频、课件的学习记录） */
function resetDialogue() {
  const it = `SELECT id FROM chapter_item WHERE course_id=${courseId()} AND type='dialogue'`
  sql(`DELETE FROM feynman_message WHERE session_id IN (SELECT id FROM (SELECT id FROM feynman_session WHERE student_id=${SID} AND item_id IN (${it})) s);
    DELETE FROM feynman_session WHERE student_id=${SID} AND item_id IN (${it});
    DELETE FROM learning_event WHERE student_id=${SID} AND target_id IN (${it});`)
}

/** 平台配置的大模型（读本机私有配置里的 key，只在本机用） */
function llmKey() {
  const y = fs.readFileSync(path.join(path.dirname(new URL(import.meta.url).pathname.replace(/^\/(\w:)/, '$1')), '..', '..',
    'backend/src/main/resources/application-local.yml'), 'utf8')
  return ((y.match(/api-key:\s*(\S+)/) || [])[1] || '').replace(/^["']|["']$/g, '')
}

/**
 * 学生这一句说什么：针对 AI 同学刚问的那句，按这个知识点的要点，用口语回答（1～2 句）。
 * 第一句故意只说一半，让 AI 同学用提示追问。
 */
async function studentSays(kpName, aiLast, history, first) {
  const kp = rows(`SELECT key_points FROM chapter_kp WHERE chapter_id=${chapterId()} AND name='${kpName}' LIMIT 1`)[0]
  const points = kp ? JSON.parse(kp[0]).map((p, i) => `${i + 1}. ${p.text}`).join('\n') : ''
  const sys = `你是一名认真学过“${kpName}”这一节的大三学生，正在用自己的话讲给一位没学过的同学听。下面是这一节的要点（你心里知道，但不要照抄原文、不要列编号）：\n${points}\n` +
    `要求：只回答对方最后说的那句话；用口语，像聊天，1～2 句，不超过 70 个字；对方举了例子就结合例子回答；不要反问对方。` +
    (first ? '这是你的第一句：只说出大概意思，故意讲得不完整（比如只说一个方面）。' : '')
  const msgs = [{ role: 'system', content: sys }, ...history.slice(-6), { role: 'user', content: aiLast }]
  const r = await fetch('https://api.deepseek.com/chat/completions', {
    method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + llmKey() },
    body: JSON.stringify({ model: 'deepseek-flash', messages: msgs, temperature: 0.7, max_tokens: 160, thinking: { type: 'disabled' } }),
  })
  const j = await r.json()
  return (j.choices?.[0]?.message?.content || '').replace(/\s+/g, ' ').trim()
}

/** 读题：按题干找到这道题的答案（两段式是“答案|理由”） */
function answerKey(stemText, ids) {
  const list = rows(`SELECT id, type, answer, IFNULL(reason_answer,''), REPLACE(REPLACE(LEFT(stem,16),'\\n',''),'\\t','') FROM exercise WHERE id IN (${ids})`)
  const flat = stemText.replace(/\s/g, '')
  return list.find((r) => flat.includes(r[4].replace(/\s/g, '')))
}

/** 在一组题（.q / .question）里点选项：right=false 时故意选错 */
async function choose(d, qSel, i, key, right = true) {
  const [, type, ans, reason] = key
  const L = 'ABCDEF'
  const n = await d.eval(`${qSel}[${i}].querySelectorAll('${'.opt, button.option'}').length`)
  const opts = `[...${qSel}[${i}].querySelectorAll('label.opt, button.option')]`
  if (type === 'multiple') {
    // 多选：对的就把正确选项都勾上；故意做错就只勾第一个
    for (const ch of (right ? ans.split('') : [ans[0]])) {
      await d.click('', { js: `${opts}[${L.indexOf(ch)}]`, before: 150 + Math.random() * 250, after: 250 + Math.random() * 300 })
    }
    return
  }
  let a = L.indexOf(ans)
  if (!right) a = (a + 1) % (type === 'judge' ? 2 : 4)
  await d.click('', { js: `${opts}[${a}]`, before: 150 + Math.random() * 300, after: 300 + Math.random() * 400 })
  if (type === 'two_tier' && reason) {
    const first = await d.eval(`${qSel}[${i}].querySelectorAll('label.opt, button.option').length`)
    const optCount = first - (await d.eval(`${opts}.filter((o) => (o.querySelector('input')?.name || '').startsWith('r') || o.closest('.part') ).length`))
    void optCount
    // 理由选项排在答案选项后面（练习里是 button.option，补练里是 name 以 r 开头的 radio）
    const reasonJs = `(() => { const all = ${opts}; const byName = all.filter((o) => (o.querySelector('input')?.name || '').startsWith('r'));
      return byName.length ? byName[${L.indexOf(reason)}] : all[4 + ${L.indexOf(reason)}] })()`
    await d.click('', { js: reasonJs, before: 200, after: 400 })
  }
  void n
}

export const SEGMENTS = {
  // ---------------------------------------------------------------- 登录、首页、老师推送先弹出一次
  u0_login: seg(async (d) => {
    resetStudent()
    await d.go('/login', 1500)
    await d.eval('localStorage.clear(); sessionStorage.clear()')
    await d.go('/login', 2000)
  }, async (d) => {
    d.caption('学生端演示')
    d.label('学生登录（演示账号：学生09）')
    await d.wait(2500)
    await d.click(STUDENT, { within: '.demos', after: 3500 })
    d.label('首页右下角的小助手自动弹出：老师推送了补充讲解（上一段老师端推的）。先把新课学完，等会儿再来接收')
    await d.wait(3000)
    if (await d.eval("!!document.querySelector('.assistant .panel')")) {
      await d.look('老师给你的', { within: '.assistant .panel', after: 3000 })
      await d.click('', { css: '.assistant .panel .close', after: 1500 })
    }
    d.label('学生首页：接着上次学习、快到期的任务、学习主线')
    await d.scroll(450, { x: 1000, y: 550 }, 1500)
    await d.wait(3000)
    await d.scroll(-450, null, 1200)
    await d.wait(1500)
  }),

  // ---------------------------------------------------------------- 选课、章节介绍
  u1_join: seg(async (d) => {
    await d.login(STUDENT)
    await d.eval("localStorage.setItem('se-assistant-seen', JSON.stringify(['push:2']))")
    await d.go('/dashboard', 2500)
  }, async (d) => {
    d.caption('学生端 · 选课和章节介绍')
    d.label('课程学习：老师刚发布的新课「智能软件工程」，先加入课程')
    await d.click('课程学习', { within: 'aside, nav', after: 2500 })
    await d.look(COURSE, { after: 1500 })
    await d.click('加入课程', { after: 2500 })
    if (await d.eval("!!document.querySelector('.el-message-box')")) await d.click('确定', { within: '.el-message-box', exact: true, after: 2000 })
    if (await d.eval("location.pathname.endsWith('/courses')")) await d.click(COURSE, { after: 3000 })
    d.label('第 6 章：章节介绍，下面是知识点地图（圆圈是知识点，颜色是自己的掌握情况，现在还没练习）')
    await d.wait(3000)
    await d.look('学习目标', { after: 3000 })
    d.label('点一个知识点：讲到它的视频第几秒、课件第几页，点了直接跳过去')
    await d.click('', { css: 'button.circle', nth: 2, after: 3500 })
    d.label('学习顺序：资料随便看；资料都学完才解锁 AI 对话，对话完成才解锁练习（锁着的有小锁）')
    await d.look('AI 对话：讲给同学听', { within: 'main', after: 3500 })
  }),

  // ---------------------------------------------------------------- 读课件
  u2_doc: seg(async (d) => {
    await d.login(STUDENT)
    await d.eval("localStorage.setItem('se-assistant-seen', JSON.stringify(['push:2']))")
    await d.go(learnUrl('intro'), 3000)
  }, async (d) => {
    d.caption('学生端 · 读课件')
    d.label('打开课件：在页面里直接读')
    await d.click('课件：智能测试生成与优化', { within: 'main', after: 3000 })
    await d.scroll(420, { x: 800, y: 600 }, 1400)
    d.label('至少读 30 秒才能点“我已读完”；切到别的页面会暂停计时')
    await d.wait(4000)
    await d.scroll(300, { x: 800, y: 500 }, 1200)
    await d.wait(5000)
    await d.waitText('我已读完', 90000)
    await d.wait(1500)
    await d.click('我已读完', { exact: true, after: 3000 })
    d.label('读完了，这一项打勾')
    await d.wait(2500)
  }),

  // ---------------------------------------------------------------- 看视频（2 倍速）
  u3_videos: seg(async (d) => {
    await d.login(STUDENT)
    await d.eval("localStorage.setItem('se-assistant-seen', JSON.stringify(['push:2']))")
    await d.go(learnUrl('intro'), 3000)
  }, async (d) => {
    d.caption('学生端 · 看视频')
    for (const [k, v] of VIDEOS.entries()) {
      d.label(k === 0 ? '看视频：看过的时间段都记下来，看够 90% 才算看完；拖进度条跳过的不算（这里用 2 倍速，系统允许的最快速度）'
        : '下一个视频：' + v)
      await d.click(v, { within: 'main', exact: false, after: 2500 })
      await d.look('', { css: 'video', after: 800 })
      await d.eval(`(() => { const v = document.querySelector('video'); v.muted = true; v.playbackRate = 2; v.play() })()`)
      await d.waitFor(`document.body.innerText.includes('已看完')`, LONG, '看完 ' + v)
      await d.wait(2500)
      await d.eval(`document.querySelector('video')?.pause()`)
    }
    d.label('4 个视频都看完了：AI 对话解锁')
    await d.look('AI 对话：讲给同学听', { within: 'main', after: 3000 })
  }),

  // ---------------------------------------------------------------- AI 对话
  u4_dialogue: seg(async (d) => {
    resetDialogue()
    await d.login(STUDENT)
    await d.eval("localStorage.setItem('se-assistant-seen', JSON.stringify(['push:2']))")
    await d.go(learnUrl('item:' + itemId('AI 对话：讲给同学听')), 3500)
  }, async (d) => {
    d.caption('学生端 · AI 对话')
    d.label('AI 对话：AI 同学带着误解来请教，学生用自己的话讲给它听；AI 只追问、不给答案，也不打分')
    await d.waitText('第 ', 60000)
    await d.wait(3000)
    await d.look('', { css: '.fc .row.ai .bubble', after: 4000 })
    const history = []
    let lastKp = ''
    for (let turn = 0; turn < 24; turn++) {
      if (await d.eval(`document.body.innerText.includes('对话完成')`)) break
      const bar = await d.eval(`document.querySelector('.fc .bar')?.innerText || ''`)
      const kpName = (bar.match(/：(.+?)(\s|$)/) || [])[1] || ''
      const first = kpName !== lastKp
      if (first) { history.length = 0; lastKp = kpName }
      const aiLast = await d.eval(`[...document.querySelectorAll('.fc .row.ai .bubble')].pop()?.innerText || ''`)
      const text = await studentSays(kpName, aiLast, history, first)
      if (!text) throw new Error('没生成出学生的话')
      history.push({ role: 'user', content: aiLast }, { role: 'assistant', content: text })
      if (turn === 1) d.label('第一句讲得不完整：AI 同学不给答案，用提示追问（三级提示，每级问两次才升级）')
      if (turn === 2) d.label('学生接着对方的追问讲；讲清一条要点，程序记下来，再问下一条；每个知识点最多说 10 句')
      await d.click('', { css: '.fc textarea', after: 300 })
      await d.type(text, 11)
      await d.wait(600)
      await d.key('Enter')
      await d.waitFor(`!document.querySelector('.fc .thinking')`, 120000, 'AI 同学回复')
      await d.wait(1200)
      await d.look('', { js: `[...document.querySelectorAll('.fc .row.ai .bubble, .fc .sys')].pop()`, after: 3000 + Math.random() * 1500 })
    }
    await d.waitText('对话完成', 60000)
    d.label('对话完成：总结哪些要点自己讲清、哪些是提示后讲清；练习解锁了')
    await d.scroll(500, { x: 900, y: 500 }, 1500)
    await d.wait(5000)
  }),

  // ---------------------------------------------------------------- 练习
  u5_exercise: seg(async (d) => {
    await d.login(STUDENT)
    await d.eval("localStorage.setItem('se-assistant-seen', JSON.stringify(['push:2']))")
    await d.go(learnUrl('item:' + itemId('练习：智能测试生成与优化')), 3500)
  }, async (d) => {
    d.caption('学生端 · 做练习')
    d.label('练习：29 道题，单选和两段式单选（先选答案，再选理由）；全部做完才交卷，交卷前不显示答案')
    await d.wait(3500)
    const ids = sql(`SELECT GROUP_CONCAT(id) FROM exercise WHERE item_id=${itemId('练习：智能测试生成与优化')} AND deleted=0 AND draft=0`)
    // 故意做错两个知识点的题，交卷后这两个变红，小助手来补
    const WRONG_KP = ['传统自动化测试的五大局限', '测试用例生成的四种技术']
    const wrongIds = new Set(rows(`SELECT x.exercise_id FROM exercise_knowledge_point x JOIN knowledge_point k ON k.id=x.knowledge_point_id
      WHERE x.exercise_id IN (${ids}) AND k.name IN ('${WRONG_KP.join("','")}')`).map((r) => r[0]))
    let wrongLeft = 3
    const n = await d.eval(`document.querySelectorAll('.question').length`)
    for (let i = 0; i < n; i++) {
      const stem = await d.eval(`document.querySelectorAll('.question')[${i}].querySelector('.stem').innerText`)
      const key = answerKey(stem, ids)
      if (!key) throw new Error('对不上题目：' + stem.slice(0, 30))
      if (i % 4 === 0) await d.look('', { js: `document.querySelectorAll('.question')[${i}].querySelector('.stem')`, after: 1500 + Math.random() * 1500 })
      const right = !(wrongIds.has(key[0]) && wrongLeft-- > 0) && !(wrongIds.has(key[0]) && Math.random() < 0.4)
      await choose(d, `document.querySelectorAll('.question')`, i, key, right)
    }
    d.label('全部做完，交卷：程序马上判分')
    await d.click('交卷', { exact: true, after: 1000 })
    if (await d.eval("!!document.querySelector('.el-message-box')")) await d.click('确定', { within: '.el-message-box', exact: true, after: 1000 })
    await d.waitFor(`document.querySelector('.change') || document.body.innerText.includes('再做一次')`, 120000, '判分')
    await d.wait(2500)
    d.label('结果：每道题对错、正确答案；选错时告诉你“你可能是这样想的”；上面是每个知识点掌握度的变化（只算第一次作答）')
    await d.look('', { css: '.change', after: 4000 })
    await d.scroll(600, { x: 700, y: 600 }, 1500)
    await d.wait(5000)
    await d.scroll(700, null, 1500)
    await d.wait(4000)
  }),

  // ---------------------------------------------------------------- 小助手（最后这部分最完整）
  u6_assistant: seg(async (d) => {
    resetAssistant()
    await d.login(STUDENT)
    await d.eval("localStorage.setItem('se-assistant-seen', JSON.stringify(['push:2']))")
    await d.go(learnUrl('intro'), 3500)
  }, async (d) => {
    d.caption('学生端 · 小助手')
    d.label('交完卷，打开右下角的小助手：只看自己的学习情况，每个知识点一份报告，颜色只看练习')
    await d.click('', { css: '.fab', after: 3000 })
    await d.wait(2000)
    d.label('这一章的结论：几个知识点第一次就掌握，几个还没掌握')
    await d.look('', { css: '.assistant .panel .headline', after: 3500 })
    // 红色的知识点
    d.label('没掌握的知识点：哪种考查类型错了、漏了哪条要点、反复出现的误解，以及复习方案')
    await d.look('还没掌握', { within: '.assistant .panel', after: 3000 })
    await d.look('看《', { within: '.assistant .panel', after: 3000 })
    d.label('复习方案第一步：点“去看”，直接跳到视频那一秒或课件那一页')
    await d.hover('去看', { within: '.assistant .panel', after: 1800 })
    d.label('第二步：点“出补练题”，大模型按自己的错题当场出 3 道')
    await d.click('出补练题', { within: '.assistant .panel', after: 1000 })
    await d.waitFor(`document.querySelector('.assistant .panel .gen') && !document.querySelector('.assistant .panel .gen').innerText.includes('正在')`, LONG, '补练题出好')
    await d.wait(1500)
    d.label('题目出好了，点“去查看”：到学习推荐页（小助手的延伸）去做')
    await d.click('去查看', { within: '.assistant .panel', after: 3500 })
    // 学习推荐页：做补练
    d.label('学习推荐 · 系统补充：补练题一律保存，知识点补回来（红变橙）要靠这些记录')
    await waitSet(d, '.remedy .q')
    await d.look('系统补充', { after: 2500 })
    const rid = sql(`SELECT exercise_ids FROM remedy_round WHERE student_id=${SID} AND kind='remedy' ORDER BY id DESC LIMIT 1`)
    const qn = await d.eval(`document.querySelectorAll('.set.focus .remedy .q, .remedy .q').length`)
    for (let i = 0; i < qn; i++) {
      const stem = await d.eval(`document.querySelectorAll('.remedy .q')[${i}].querySelector('.stem').innerText`)
      const key = answerKey(stem, rid)
      if (i === 0) await d.look('', { js: `document.querySelectorAll('.remedy .q')[0].querySelector('.stem')`, after: 3000 })
      await choose(d, `document.querySelectorAll('.remedy .q')`, i, key, true)
    }
    await d.click('交补练题', { exact: true, after: 3500 })
    d.label('马上判分：都做对，这个知识点“补回来了”，从红变橙（曾经错过，复习时多留意）')
    await d.wait(4000)
    await d.scroll(450, { x: 900, y: 600 }, 1500)
    await d.wait(3500)
    // 回到小助手：拓展
    d.label('回到小助手：掌握得好的知识点可以“来点更难的”，选一种考查类型')
    await d.click('', { css: '.fab', after: 3000 })
    await d.look('来点更难的', { within: '.assistant .panel', after: 2000 })
    await d.click('情境应用', { within: '.assistant .panel .extend', after: 1000 })
    await d.waitFor(`document.querySelector('.assistant .panel .gen') && !document.querySelector('.assistant .panel .gen').innerText.includes('正在')`, LONG, '拓展题出好')
    await d.wait(1500)
    await d.click('去查看', { within: '.assistant .panel', after: 3500 })
    d.label('学习推荐 · 拓展：比练习更难的情境题，做对做错都不影响掌握度')
    await waitSet(d, '.remedy .q')
    const eid = sql(`SELECT exercise_ids FROM remedy_round WHERE student_id=${SID} AND kind='extend' ORDER BY id DESC LIMIT 1`)
    const en = await d.eval(`document.querySelectorAll('.remedy .q').length`)
    for (let i = 0; i < en; i++) {
      const stem = await d.eval(`document.querySelectorAll('.remedy .q')[${i}].querySelector('.stem').innerText`)
      if (i === 0) await d.look('', { js: `document.querySelectorAll('.remedy .q')[0].querySelector('.stem')`, after: 3500 })
      await choose(d, `document.querySelectorAll('.remedy .q')`, i, answerKey(stem, eid), i !== 1)
    }
    await d.click('交补练题', { exact: true, after: 3500 })
    d.label('做完了，自己选保存不保存；保存了就留在“拓展”里，以后可以回看')
    await d.look('保存到学习推荐吗', { after: 2500 })
    await d.click('保存', { within: '.decide', exact: true, after: 3000 })
    // 老师给你的
    d.label('再打开小助手：最上面“老师给你的”——老师看了全班练习后推送的补充讲解和新题')
    await d.click('', { css: '.fab', after: 3000 })
    await d.scrollTo('老师给你的', { within: '.assistant .panel' })
    await d.click('老师补充讲解', { within: '.assistant .panel', after: 2500 })
    await d.look('', { css: '.assistant .panel .push-body .explain', after: 5000 })
    d.label('点“接收”：保存到学习推荐的“老师推送”，在那里看讲解、做老师的新题')
    await d.click('接收', { within: '.assistant .panel', exact: true, after: 3500 })
    d.label('学习推荐 · 老师推送：先看老师的补充讲解，再做 3 道新题；做对了算进补练')
    await waitSet(d, '.push .q')
    await d.look('', { css: '.set .explain', after: 5000 })
    const pid = sql(`SELECT exercise_ids FROM class_push WHERE status='pushed' AND FIND_IN_SET(${SID}, student_ids) ORDER BY id DESC LIMIT 1`)
    const pn = await d.eval(`document.querySelectorAll('.push .q').length`)
    for (let i = 0; i < pn; i++) {
      const stem = await d.eval(`document.querySelectorAll('.push .q')[${i}].querySelector('.stem').innerText`)
      await choose(d, `document.querySelectorAll('.push .q')`, i, answerKey(stem, pid), true)
    }
    await d.click('交题', { exact: true, after: 3500 })
    await d.wait(3000)
    d.label('三类都存在学习推荐里：系统补充、拓展、老师推送，做过的可以回看自己的答案、正确答案和解析')
    await d.click('系统补充', { css: '.tabs button', nth: 0, after: 3000 })
    await d.scroll(500, { x: 900, y: 600 }, 1500)
    await d.wait(3500)
    // 问问题
    d.label('小助手的“问问题”：随便问概念；不给练习的答案，程序会检查')
    await d.click('', { css: '.fab', after: 2500 })
    await d.click('问问题', { within: '.assistant .panel', exact: true, after: 1500 })
    await d.click('', { css: '.assistant .panel textarea, .assistant .panel input', after: 400 })
    await d.type('自愈测试和普通的失败重试有什么区别？', 8)
    await d.key('Enter')
    await d.wait(1500)
    await d.waitFor(`!document.querySelector('.assistant .panel .typing')`, 120000, '小助手回复')
    await d.wait(1500)
    await d.look('', { js: `[...document.querySelectorAll('.assistant .panel .line.assistant')].pop()`, after: 8000 })
  }),

  // ---------------------------------------------------------------- 学情诊断、学生画像
  u7_profile: seg(async (d) => {
    await d.login(STUDENT)
    await d.eval("localStorage.setItem('se-assistant-seen', JSON.stringify(['push:2']))")
    await d.go('/diagnosis', 3500)
  }, async (d) => {
    d.caption('学生端 · 学情诊断和学生画像')
    d.label('学情诊断：按课程 → 章节 → 知识点列出掌握度和依据（第一次作答几题对几题、补练情况）')
    await d.wait(3000)
    await d.scroll(450, { x: 1000, y: 600 }, 1500)
    await d.wait(4000)
    d.label('还没掌握的知识点旁边有“让小助手帮我补”，点了直接打开小助手的这一章')
    await d.hover('让小助手帮我补', { after: 2500 })
    await d.click('让小助手帮我补', { after: 3500 })
    await d.click('', { css: '.assistant .panel .close', after: 1500 })
    d.label('学生画像：每章画像；这门课的章节都学完了，开放课程四维画像，每一维都写了公式、数据来源和原因')
    await d.click('学生画像', { within: 'aside, nav', after: 3500 })
    if (await d.eval("!!document.querySelector('.el-select')")) {
      await d.click('', { css: '.el-select', after: 900 })
      await d.click(COURSE, { within: '.el-select-dropdown', after: 3000 })
    }
    await d.wait(5000)
    await d.scroll(450, { x: 1100, y: 600 }, 1500)
    await d.wait(6000)
    await d.scroll(600, null, 1500)
    await d.wait(5000)
  }),
}
