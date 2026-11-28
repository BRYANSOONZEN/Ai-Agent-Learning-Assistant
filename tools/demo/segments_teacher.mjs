// 老师端演示（单独录，真实速度，不快进）：
//   新建空课「智能软件工程」，用第六章的真实资料完整跑一遍：建章 → 写介绍 → 上传课件 + 4 个视频 → 转文字 →
//   上传完毕、识别知识点 → 确认 → AI 对话草稿 → 出题 → 发布；最后用做好的「软件测试」展示老师干预。
// 用法：DEMO_SEGMENTS=segments_teacher.mjs node tools/demo/record.mjs [段名 ...]
import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'

const FILES = process.env.DEMO_FILES || path.join(os.tmpdir(), 'se-demo-files')
const MYSQL_DIR = process.env.MYSQL_DIR || 'C:/ZI/mysql'
const TEACHER = '李老师'
const COURSE = '智能软件工程'
const CHAPTER = '第 6 章 智能测试生成与优化'
const VIDEOS = ['6-1 自动化测试', '6-2 智能测试', '6-3 测试用例生成效果对比', '6-4 缺陷自动检测与修复效果对比']
const LONG = 60 * 60 * 1000 // 真实速度：转文字、识别、出题最多等 1 小时

const seg = (setup, fn, teardown) => Object.assign(fn, { setup, teardown })

function sql(q) {
  const line = fs.readFileSync(path.join(MYSQL_DIR, '本机密码.txt'), 'utf8').split(/\r?\n/).find((l) => l.includes('se_app'))
  const pw = line.split(/[:：=\s]+/).pop()
  return execFileSync(path.join(MYSQL_DIR, 'mysql-8.4.11-winx64/bin/mysql.exe'),
    ['-h127.0.0.1', '-use_app', '-p' + pw, 'se_platform', '--default-character-set=utf8mb4', '-N', '-e', q],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim()
}

/** 删掉一门课和它下面的全部内容（重录时用） */
function deleteCourse(id) {
  const C = `course_id = ${id}`
  sql(`START TRANSACTION;
    CREATE TEMPORARY TABLE x_ch AS SELECT id FROM chapter WHERE ${C};
    CREATE TEMPORARY TABLE x_it AS SELECT id FROM chapter_item WHERE ${C};
    CREATE TEMPORARY TABLE x_kp AS SELECT id FROM knowledge_point WHERE ${C};
    CREATE TEMPORARY TABLE x_ex AS SELECT id FROM exercise WHERE ${C};
    CREATE TEMPORARY TABLE x_fs AS SELECT id FROM feynman_session WHERE item_id IN (SELECT id FROM x_it);
    DELETE FROM feynman_message WHERE session_id IN (SELECT id FROM x_fs);
    DELETE FROM feynman_session WHERE id IN (SELECT id FROM x_fs);
    DELETE FROM feynman_setup WHERE item_id IN (SELECT id FROM x_it);
    DELETE FROM exercise_knowledge_point WHERE exercise_id IN (SELECT id FROM x_ex);
    DELETE FROM exercise WHERE ${C};
    DELETE FROM answer_record WHERE ${C};
    DELETE FROM mastery WHERE knowledge_point_id IN (SELECT id FROM x_kp);
    DELETE FROM remedy_round WHERE knowledge_point_id IN (SELECT id FROM x_kp);
    DELETE FROM material_text WHERE item_id IN (SELECT id FROM x_it);
    DELETE FROM material_job WHERE item_id IN (SELECT id FROM x_it);
    DELETE FROM material WHERE ${C};
    DELETE FROM chapter_kp WHERE chapter_id IN (SELECT id FROM x_ch);
    DELETE FROM chapter_kp_state WHERE chapter_id IN (SELECT id FROM x_ch);
    DELETE FROM knowledge_point WHERE ${C};
    DELETE FROM item_progress WHERE ${C};
    DELETE FROM learning_event WHERE ${C};
    DELETE FROM chapter_item WHERE ${C};
    DELETE FROM chapter WHERE ${C};
    DELETE FROM enrollment WHERE ${C};
    DELETE FROM course WHERE id = ${id};
    COMMIT;`)
}
const courseId = () => Number(sql(`SELECT id FROM course WHERE name='${COURSE}' ORDER BY id DESC LIMIT 1`)) || null
const chapterId = (cid) => Number(sql(`SELECT id FROM chapter WHERE course_id=${cid} ORDER BY id LIMIT 1`)) || null

async function openBuilder(d, view) {
  const cid = courseId()
  const ch = chapterId(cid)
  await d.go(`/teacher/courses/${cid}?chapter=${ch}&view=${view}`, 2500)
}

/** 在大纲里加一项内容：点“添加内容”→ 类型 → 输入标题 → 确定 */
async function addItem(d, type, title) {
  await d.click('添加内容', { after: 800 })
  await d.click(type, { within: '.el-popper:not([style*="display: none"]), .el-dropdown-menu', exact: true, after: 1000 })
  await d.selectAll()
  await d.type(title, 12)
  await d.wait(400)
  await d.click('确定', { within: '.el-message-box, .el-dialog', exact: true, after: 2000 })
}

/** 等上传完：进度条没了，而且页面上出现这个文件名（视频项还要出现预览） */
async function waitUploaded(d, name) {
  await d.waitFor(`!document.querySelector('.app-main .el-progress') && document.body.innerText.includes(${JSON.stringify(name)})`
    + (name.endsWith('.mp4') ? ` && document.querySelector('.app-main video')` : ''), 600000, '上传完 ' + name)
}

export const SEGMENTS = {
  // ---------------------------------------------------------------- 登录、老师首页
  t0_login: seg(async (d) => {
    await d.go('/login', 1500)
    await d.eval('localStorage.clear(); sessionStorage.clear()')
    await d.go('/login', 2000)
  }, async (d) => {
    d.caption('老师端演示')
    d.label('登录页：演示账号一键登录（老师：李老师）')
    await d.wait(3000)
    await d.click(TEACHER, { within: '.demos', after: 3000 })
    d.label('老师首页：待确认的事、截止情况、我的课程')
    await d.wait(4000)
    await d.scroll(450, { x: 1000, y: 550 }, 1500)
    await d.wait(3000)
    await d.scroll(-450, null, 1000)
    await d.wait(1500)
  }),

  // ---------------------------------------------------------------- 新建空课
  t1_course: seg(async (d) => {
    const old = courseId()
    if (old) deleteCourse(old)
    await d.login(TEACHER)
    await d.go('/teacher/courses', 2500)
  }, async (d) => {
    d.caption('新建课程：智能软件工程')
    d.label('我的课程：新建一门空课')
    await d.wait(2500)
    await d.click('新建课程', { after: 1500 })
    await d.click('例：软件测试', { css: '.el-dialog input', after: 400 })
    await d.type(COURSE, 10)
    await d.click('', { css: '.el-dialog textarea', after: 400 })
    await d.type('人工智能在软件工程各环节的应用：需求、设计、编码、测试、部署与运维。', 14)
    d.label('选这门课用哪些模块：视频、文档、知识点、AI 对话、题目')
    await d.wait(2500)
    await d.click('保存', { within: '.el-dialog', exact: true, after: 3000 })
    d.label('进入建课页：现在是一门空课')
    await d.wait(3000)
  }),

  // ---------------------------------------------------------------- 建章、写介绍、上传资料
  t2_upload: seg(async (d) => {
    await d.login(TEACHER)
    await d.go(`/teacher/courses/${courseId()}`, 2500)
  }, async (d) => {
    d.caption('模块 1 上传资料')
    d.label('新建章节：第 6 章 智能测试生成与优化')
    await d.click('添加第一个章节', { after: 1000 })
    await d.selectAll()
    await d.type(CHAPTER, 10)
    await d.click('确定', { within: '.el-message-box, .el-dialog', exact: true, after: 2500 })
    d.label('先写章节介绍：学生进入本章最先看到')
    await d.click('', { css: '.el-textarea textarea', after: 400 })
    await d.type('学习目标：学完本章，你能说出智能测试和传统自动化测试的区别，讲清智能测试的四大优化环节，并能按场景选择测试用例生成技术。\n概要：本章有 1 份课件和 4 个讲课视频，学完资料后和 AI 同学对话，再做练习。', 18)
    await d.click('保存', { exact: true, after: 2000 })
    d.label('加一份文档：上传第六章课件 PDF')
    await addItem(d, '文档', '课件：智能测试生成与优化')
    await d.hover('附件', { after: 600 })
    await d.upload('input[type=file]', path.join(FILES, '6 智能测试生成与优化.pdf'))
    await waitUploaded(d, '6 智能测试生成与优化.pdf')
    await d.wait(2500)
    for (const v of VIDEOS) {
      d.label('加一个视频：' + v)
      await addItem(d, '视频', '视频 ' + v)
      await d.wait(800)
      await d.upload('input[type=file]', path.join(FILES, v + '.mp4'))
      await waitUploaded(d, v + '.mp4')
      await d.wait(2500)
    }
    await d.shot('t2-end')
  }),

  // ---------------------------------------------------------------- 模块 2 识别知识点
  t3_kps: seg(async (d) => {
    await d.login(TEACHER)
    await openBuilder(d, 'kps')
  }, async (d) => {
    d.caption('模块 2 识别知识点')
    d.label('知识点页：每份资料转文字的进度（课件取每页文字、图片让大模型看；视频语音转文字）')
    await d.wait(4000)
    d.label('资料传齐了，点“上传完毕”：资料处理完后自动识别知识点')
    await d.click('上传完毕', { exact: true, after: 3000 })
    d.label('（真实等待：转文字、识别知识点）')
    await d.waitText('待确认', LONG)
    await d.wait(3000)
    d.label('识别完成：每个知识点有名称、重点 / 一般、学习目标、要点和出处（课件第几页、视频第几段）、常见误解')
    await d.wait(4000)
    await d.scroll(450, { x: 1300, y: 600 }, 1500)
    await d.wait(5000)
    await d.scroll(500, null, 1500)
    await d.wait(5000)
    d.label('老师可以编辑、删除、添加；看过没问题，点确认')
    await d.click('编辑', { exact: true, after: 2500 })
    await d.wait(2000)
    await d.click('取消', { within: '.el-dialog', exact: true, after: 1200 })
    await d.scroll(-2000, null, 1200)
    await d.click('确认这', { after: 3000 })
    d.label('已确认：学生端能看到知识点地图；后面 AI 对话、出题都按这些知识点')
    await d.locate('知识点地图')
    await d.wait(5000)
  }),

  // ---------------------------------------------------------------- 模块 3 AI 对话
  t4_dialogue: seg(async (d) => {
    await d.login(TEACHER)
    await openBuilder(d, 'intro')
  }, async (d) => {
    d.caption('模块 3 AI 对话')
    d.label('加一个 AI 对话：学生把知识点讲给 AI 同学听（自动排在练习前面）')
    await addItem(d, 'AI 对话', 'AI 对话：讲给同学听')
    await d.wait(2000)
    d.label('选这次要讲的知识点（最多 2 个，选学生最难理解的）')
    await d.locate('选这次对话要讲的知识点')
    await d.click('', { css: 'button.kp', nth: 1, after: 800 })
    await d.click('', { css: 'button.kp', nth: 2, after: 800 })
    d.label('生成对话草稿：大模型起草，程序检查 4 项')
    await d.click('生成对话草稿', { exact: true, after: 2000 })
    await d.waitFor(`[...document.querySelectorAll('button')].some((b) => b.innerText.trim() === '确认草稿')`, LONG, '草稿生成')
    await d.wait(3000)
    d.label('草稿：AI 同学的人设、场景、开场白、带着的误解；每条要点的判断标准、L1–L3 三级提示、最后指向资料')
    await d.scroll(450, { x: 1300, y: 600 }, 1500)
    await d.wait(5000)
    await d.scroll(500, null, 1500)
    await d.wait(5000)
    await d.scroll(600, null, 1500)
    await d.wait(5000)
    d.label('老师可以修改；看过没问题，确认草稿，学生就能用了')
    await d.click('确认草稿', { exact: true, after: 3000 })
    await d.wait(3000)
  }),

  // ---------------------------------------------------------------- 模块 4 出题
  t5_exercise: seg(async (d) => {
    await d.login(TEACHER)
    await openBuilder(d, 'intro')
  }, async (d) => {
    d.caption('模块 4 出题')
    d.label('加一次练习，设截止时间（过了截止还能补做，记为迟交）')
    await addItem(d, '练习', '练习：智能测试生成与优化')
    await d.wait(1500)
    await d.click('不设 = 不限时', { css: 'input[placeholder="不设 = 不限时"]', after: 800 })
    await d.type('2026-12-05 23:59', 10)
    await d.key('Enter')
    await d.wait(800)
    await d.click('保存', { exact: true, nth: 0, after: 2000 })
    d.label('AI 出题：老师选 5 项——考哪些知识点、考查类型、题型、每个知识点几道、其他要求')
    await d.click('AI 出题', { exact: true, after: 2000 })
    await d.click('数据分析', { within: '.el-dialog', after: 600 })
    await d.click('简答', { within: '.el-dialog', after: 600 })
    await d.click('', { within: '.el-dialog', css: 'textarea', after: 400 })
    await d.type('情境题用电商项目的例子', 10)
    await d.wait(1500)
    d.label('开始出题：每道题出完，AI 还会不看答案自己做一遍，做不对的标出来')
    await d.click('开始出题', { exact: true, after: 2000 })
    d.label('（真实等待：按知识点出题）')
    await d.waitText('去检查草稿', LONG)
    await d.wait(2000)
    d.label('出题结果：每个知识点出了几道、丢掉几道、要点覆盖、误解用上、可能有歧义的，用时和费用')
    await d.wait(7000)
    await d.click('去检查草稿', { exact: true, after: 2500 })
    d.label('新题先是草稿，学生看不到；老师检查、修改')
    await d.scroll(600, { x: 1300, y: 600 }, 1500)
    await d.wait(5000)
    await d.scroll(600, null, 1500)
    await d.wait(5000)
    d.label('确认全部草稿；覆盖检查：每个知识点 × 考查类型各几道')
    await d.click('确认全部草稿', { exact: true, after: 3000 })
    await d.locate('覆盖检查')
    await d.wait(6000)
  }),

  // ---------------------------------------------------------------- 发布
  t6_publish: seg(async (d) => {
    await d.login(TEACHER)
    await openBuilder(d, 'publish')
  }, async (d) => {
    d.caption('发布本章')
    d.label('预览发布：检查每一项是否完整（介绍、资料、知识点、AI 对话、练习截止时间）')
    await d.wait(5000)
    await d.scroll(400, { x: 1300, y: 600 }, 1200)
    await d.wait(3000)
    await d.click('发布本章', { exact: true, after: 3000 })
    d.label('发布后选了课的学生就能按“资料 → AI 对话 → 练习”的顺序学')
    await d.wait(4000)
  }),

  // ---------------------------------------------------------------- 课程本身也要发布，学生才看得到
  t6b_course: seg(async (d) => {
    await d.login(TEACHER)
    await openBuilder(d, 'publish')
  }, async (d) => {
    d.caption('发布课程')
    d.label('这门课新建时是草稿：到“课程设置”把课程状态改成已发布，学生才能在课程列表里看到、加入')
    await d.wait(1500)
    await d.click('课程设置', { after: 2000 })
    await d.look('状态', { within: '.el-dialog', after: 1500 })
    await d.click('已发布', { within: '.el-dialog', exact: true, after: 1200 })
    await d.click('保存', { within: '.el-dialog', exact: true, after: 3000 })
    d.label('课程和第 6 章都已发布')
    await d.look('已发布', { after: 3000 })
  }),

  // ---------------------------------------------------------------- 模块 5 老师干预（用做好的「软件测试」展示，按 PPT 模块五的顺序）
  t7_intervention: seg(async (d) => {
    const ex = sql("SELECT GROUP_CONCAT(exercise_ids) FROM class_push WHERE item_id=65")
    const ids = (ex === 'NULL' ? '' : ex).split(',').filter((x) => /^\d+$/.test(x))
    if (ids.length) {
      sql(`DELETE FROM answer_record WHERE exercise_id IN (${ids.join(',')})`)
      sql(`UPDATE exercise SET deleted=1 WHERE id IN (${ids.join(',')})`)
    }
    sql('DELETE FROM class_push WHERE item_id=65; DELETE FROM class_issue WHERE item_id IN (2,65); DELETE FROM item_analysis WHERE item_id IN (2,65); DELETE FROM assistant_notice WHERE item_id=2;')
    await d.login(TEACHER)
    await d.go('/intervention', 3500)
  }, async (d) => {
    d.caption('模块 5 老师干预（做好的课程「软件测试」）')
    d.label('干预管理 · 全班分析：每次练习到截止（或全班交完）才分析，只看截止前的第一次作答')
    await d.wait(2000)
    await d.look('每次练习到截止', { after: 3500 })
    d.label('第 1 章：截止时只交了 7/10，不到 80%，分析作废；列出没交的学生')
    await d.look('这次分析作废', { after: 3000 })
    d.label('老师可以提醒没交的学生（出现在他们的小助手里），或延长截止时间，到新截止再分析')
    await d.click('提醒他们', { exact: true, after: 2500 })
    await d.click('延长截止时间', { exact: true, after: 2000 })
    await d.wait(1500)
    await d.click('取消', { within: '.el-dialog', exact: true, after: 1500 })
    d.label('第 6 章：交了 8/10，已分析。规则①：一道题错 > 80%，先请老师检查题目本身')
    await d.look('题目检查', { after: 3500 })
    await d.look('先检查这道题', { after: 2500 })
    d.label('看过题目没问题，点“没问题”：这道题算回对应的要点，重新按规则②分析；有问题可以“已修改”或“作废”')
    await d.hover('作废', { after: 1200 })
    await d.click('没问题', { exact: true, after: 3500 })
    d.label('规则②：某条要点 × 某种考查类型错 > 50%，出一张卡片：哪条要点、错几成、推给做错的几人')
    await d.look('推送给做错的', { after: 3500 })
    d.label('推荐方案按考查类型不同：概念辨析用对比讲解，情境应用用例题讲解……新题和出错的题同一种考查类型')
    await d.look('推荐方案', { after: 4000 })
    d.label('同一个知识点的几张卡片可以勾上合并处理；也可以“我自己写讲解”，或“忽略”')
    await d.hover('我自己写讲解', { after: 1500 })
    d.label('点“AI 起草讲解 + 出题”')
    await d.click('AI 起草讲解 + 出题', { exact: true, after: 1000 })
    await d.waitText('确认推送', LONG)
    await d.wait(2500)
    d.label('补充讲解（大模型起草，老师可以改）')
    await d.look('补充讲解', { within: '.el-dialog', after: 3000 })
    await d.click('', { within: '.el-dialog', css: 'textarea', after: 600 })
    await d.eval("(() => { const t = document.querySelector('.el-dialog textarea'); t.focus(); t.setSelectionRange(t.value.length, t.value.length); t.scrollTop = t.scrollHeight })()")
    await d.type('\n（课上我们会再用一个电商项目的例子讲一遍。）', 9)
    await d.wait(1500)
    d.label('3 道同类型新题：可以删掉、重新出题')
    await d.scrollTo('新题', { within: '.el-dialog' })
    await d.wait(4000)
    await d.scroll(350, { x: 960, y: 560 }, 1300)
    await d.wait(3500)
    d.label('推送给谁：这一格至少做错过一道的学生；再设个完成期限')
    await d.scrollTo('推送给', { within: '.el-dialog' })
    await d.look('推送给', { within: '.el-dialog', after: 3500 })
    await d.click('', { within: '.el-dialog', css: '.el-date-editor input', after: 600 })
    await d.type('2026-12-03 23:59', 9)
    await d.key('Enter')
    await d.wait(1200)
    d.label('确认推送：学生的小助手会自动弹出一次“老师给你的”')
    await d.click('确认推送', { exact: true, after: 3500 })
    d.label('另一张卡片这次不处理，点“忽略”')
    if (await d.eval("[...document.querySelectorAll('button')].some((b) => b.innerText.trim() === '忽略')")) {
      await d.click('忽略', { exact: true, after: 3000 })
    }
    d.label('（几位学生在小助手里接收、做了老师的新题）')
    studentsDoPush()
    await d.wait(1500)
    await d.go('/intervention', 3500)
    d.label('推送记录和效果：推给几人、几人做完、这一格推送前后的错误率、补回来几人')
    await d.look('已推送 / 草稿', { after: 2000 })
    await d.look('错误率', { after: 6000 })
  }),
}

/** 被推送的学生（除了最后一个）用演示账号登录，在小助手里做老师推送的新题（大部分做对） */
function studentsDoPush() {
  const pw = (fs.readFileSync(path.join(path.dirname(new URL(import.meta.url).pathname.replace(/^\/(\w:)/, '$1')), '..', '..',
    'backend/src/main/resources/data.sql'), 'utf8').match(/密码都是\s*(\S+?)（/) || [])[1]
  const row = sql("SELECT id, student_ids, exercise_ids FROM class_push WHERE item_id=65 AND status='pushed' ORDER BY id DESC LIMIT 1")
  if (!row) return
  const [pid, sids, eids] = row.split('\t')
  const keys = Object.fromEntries(sql(`SELECT id, type, answer, IFNULL(reason_answer,'') FROM exercise WHERE id IN (${eids})`)
    .split(/\r?\n/).map((l) => l.split('\t')).map(([id, t, a, r]) => [id, t === 'two_tier' ? a + '|' + r : a]))
  const users = sql(`SELECT username FROM user WHERE id IN (${sids}) ORDER BY id`).split(/\r?\n/)
  const script = `
    const users = ${JSON.stringify(users.slice(0, -1))}, keys = ${JSON.stringify(keys)}, pw = ${JSON.stringify(pw)};
    for (const [n, u] of users.entries()) {
      const t = (await (await fetch('http://localhost:8080/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: u, password: pw }) })).json()).data.token;
      const h = { 'Content-Type': 'application/json', Authorization: 'Bearer ' + t };
      await fetch('http://localhost:8080/api/assistant/pushes/${pid}/accept', { method: 'POST', headers: h });
      const answers = Object.fromEntries(Object.entries(keys).map(([id, a], i) => [id, n === 0 && i === 0 ? (a[0] === 'A' ? 'B' : 'A') + a.slice(1) : a]));
      await fetch('http://localhost:8080/api/assistant/pushes/${pid}/submit', { method: 'POST', headers: h, body: JSON.stringify({ answers }) });
    }`
  execFileSync(process.execPath, ['--input-type=module', '-e', script], { stdio: 'inherit' })
}
