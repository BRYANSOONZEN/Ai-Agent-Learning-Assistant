// 演示视频的每一段：按答辩 PPT 的顺序，先老师端（模块 1–5），再学生端（模块 6–8）。
// 每段是一个函数 d => {...}；fn.setup 是开录前的准备（登录、打开起始页），不进画面。
// d.label(...) 是配音稿的分段点，d.caption(...) 是画面顶部的小标题，d.fast()/d.normal() 是快进区间。
import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'

const FILES = process.env.DEMO_FILES || path.join(os.tmpdir(), 'se-demo-files')
const MYSQL_DIR = process.env.MYSQL_DIR || 'C:/ZI/mysql'
const TEACHER = '李老师'
const STUDENT = '学生03'

const seg = (setup, fn, teardown) => Object.assign(fn, { setup, teardown })

/** 直接执行 SQL（重录前恢复数据用）；密码从本机 MySQL 目录的密码文件里读，不写在仓库里 */
function sql(q) {
  const line = fs.readFileSync(path.join(MYSQL_DIR, '本机密码.txt'), 'utf8').split(/\r?\n/).find((l) => l.includes('se_app'))
  const pw = line.split(/[:：=\s]+/).pop()
  return execFileSync(path.join(MYSQL_DIR, 'mysql-8.4.11-winx64/bin/mysql.exe'),
    ['-h127.0.0.1', '-use_app', '-p' + pw, 'se_platform', '--default-character-set=utf8mb4', '-N', '-e', q],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim()
}

export const SEGMENTS = {
  // ---------------------------------------------------------------- 开场：登录
  s0_login: seg(async (d) => {
    await d.go('/login', 1500)
    await d.eval('localStorage.clear(); sessionStorage.clear()')
    await d.go('/login', 2000)
  }, async (d) => {
    d.caption('软件工程智能学习平台 · 系统演示')
    d.label('开场：登录页')
    await d.wait(2500)
    await d.hover('李老师', { within: '.demos', after: 800 })
    await d.click('李老师', { within: '.demos', after: 2500 })
    d.label('老师首页')
    await d.wait(2500)
    await d.scroll(400, { x: 900, y: 500 })
    await d.wait(1500)
    await d.scroll(-400)
    await d.wait(1000)
  }),

  // ---------------------------------------------------------------- 模块 1：上传资料
  s1_upload: seg(async (d) => {
    await d.login(TEACHER)
    // 重录时先清掉上次建的章节（课程 2 原来是空的）
    const chs = (await d.api('GET', '/api/courses/2/chapters')).data || []
    for (const c of chs) {
      for (const it of (await d.api('GET', `/api/chapters/${c.id}/items`)).data || []) await d.api('DELETE', '/api/chapter-items/' + it.id)
      for (const k of (await d.api('GET', `/api/chapters/${c.id}/knowledge-points`)).data || []) await d.api('DELETE', '/api/knowledge-points/' + k.id)
      const r = await d.api('DELETE', '/api/chapters/' + c.id)
      if (r.code !== 200) throw new Error('清不掉上次的章节：' + r.message)
    }
    await d.go('/teacher/courses/2', 2000)
  }, async (d) => {
    d.caption('老师端 · 模块 1 上传资料')
    d.label('建一章：第 1 章 智能软件工程导论')
    await d.wait(1200)
    await d.click('添加第一个章节', { after: 1000 })
    await d.type('智能软件工程导论')
    await d.wait(400)
    await d.click('确定', { within: '.el-message-box, .el-dialog', exact: true, after: 2000 })
    d.label('在这一章里加一份文档，上传课件 PDF')
    await d.click('添加内容', { after: 800 })
    await d.click('文档', { within: '.el-popper:not([style*="display: none"]), .el-dropdown-menu', exact: true, after: 1200 })
    await d.type('课件：智能软件工程导论')
    await d.click('确定', { within: '.el-message-box, .el-dialog', exact: true, after: 2000 })
    await d.hover('附件', { after: 800 })
    await d.upload('input[type=file]', path.join(FILES, '智能软件工程导论.pdf'))
    await d.waitText('已上传', 30000)
    await d.wait(2500)
    d.label('上传后马上在后台转成文字；到“知识点”页看处理进度')
    await d.click('下一步：知识点', { after: 2500 })
    d.fast(6)
    await d.waitText('已转成文字', 240000)
    d.normal()
    await d.wait(2000)
    d.label('资料传齐，点“上传完毕”：系统自动识别知识点')
    await d.click('上传完毕', { exact: true, after: 2500 })
    d.fast(12)
    await d.waitText('待确认', 600000)
    await d.wait(1500)
    d.normal()
    d.label('识别完成：每个知识点的名称、要点和出处，等老师确认')
    await d.wait(3000)
    await d.scroll(500, { x: 1300, y: 600 }, 1500)
    await d.wait(2500)
    await d.scroll(600, null, 1500)
    await d.wait(2500)
    await d.scroll(-1100, null, 1200)
    await d.wait(1500)
  }),

  // ---------------------------------------------------------------- 模块 2：识别知识点（看第 6 章已确认的结果）
  s2_kps: seg(async (d) => {
    await d.login(TEACHER)
    await d.go('/teacher/courses/1?chapter=3&view=kps', 2500)
  }, async (d) => {
    d.caption('老师端 · 模块 2 识别知识点')
    d.label('第 6 章：1 份课件 + 4 个视频都已转成文字，识别出 7 个知识点，老师已确认')
    await d.wait(3500)
    await d.scroll(420, { x: 1300, y: 600 }, 1500)
    d.label('每个知识点：名称、重点/一般、学习目标、几条要点，每条要点后面是出处（课件第几页、视频第几段）')
    await d.wait(5000)
    d.label('还有常见误解，后面 AI 对话和出题都会用到')
    await d.scroll(300, null, 1000)
    await d.wait(3500)
    d.label('老师可以编辑、删除、添加知识点；改过的会标“老师改过”')
    await d.click('编辑', { exact: true, after: 2500 })
    await d.wait(2500)
    await d.click('取消', { within: '.el-dialog', exact: true, after: 1200 })
    d.label('知识点地图：每个知识点在哪份资料的哪里讲到')
    await d.locate('知识点地图')
    await d.wait(1000)
    await d.scroll(250, null, 900)
    await d.wait(5000)
  }),

  // ---------------------------------------------------------------- 模块 3：AI 对话（老师端草稿）
  s3_dialogue: seg(async (d) => {
    await d.login(TEACHER)
    await d.go('/teacher/courses/1?chapter=3&view=item:33', 2500)
  }, async (d) => {
    d.caption('老师端 · 模块 3 AI 对话')
    d.label('AI 对话：学生把知识点讲给“AI 同学”听。老师先选这次要讲的知识点（最多 2 个）')
    await d.wait(2500)
    await d.scroll(380, { x: 1300, y: 600 }, 1200)
    await d.wait(3500)
    d.label('大模型起草，程序检查 4 项：每条要点都有草稿、三级提示和判断标准都写了、每句不超过 80 字、提示没照抄原文')
    await d.scroll(330, null, 1000)
    await d.wait(5000)
    d.label('AI 同学的人设、场景、开场白，以及它带着的误解')
    await d.scroll(330, null, 1000)
    await d.wait(5000)
    d.label('每条要点一套：讲清 / 不完整的判断标准，L1–L3 三级提示，最后指向课件页码和视频时间')
    await d.scroll(420, null, 1200)
    await d.wait(6000)
    await d.scroll(450, null, 1200)
    await d.wait(3000)
    d.label('规则：每个知识点学生最多说 10 句；结果只给学生自己看')
    await d.scroll(2500, null, 1800)
    await d.wait(3500)
  }),

  // ---------------------------------------------------------------- 模块 4：出题
  s4_exercise: seg(async (d) => {
    await d.login(TEACHER)
    await d.go('/teacher/courses/1?chapter=3&view=item:65', 2500)
  }, async (d) => {
    d.caption('老师端 · 模块 4 出题')
    d.label('练习：截止时间、能不能补交；下面是覆盖检查：每个知识点 × 考查类型出了几道')
    await d.wait(2500)
    await d.scroll(520, { x: 1300, y: 600 }, 1300)
    await d.wait(4000)
    d.label('“报告生成与风险预测”只有 2 道，提醒少于 3 道。用 AI 给它补题')
    await d.hover('少于 3 道', { after: 2000 })
    await d.click('AI 出题', { exact: true, after: 1800 })
    d.label('老师选 5 项：考哪些知识点、考查类型、题型、每个知识点几道、其他要求')
    for (const k of ['自动化测试的概念与局限', '智能测试的概念与特性', '智能测试四大优化环节', '测试用例生成四种技术',
      '执行优化与缺陷修复技术', '生成技术实验对比与选型']) {
      await d.click(k, { within: '.el-dialog', moveMs: 300, after: 250 })
    }
    await d.wait(800)
    await d.click('数据分析', { within: '.el-dialog', after: 600 })
    await d.click('简答', { within: '.el-dialog', after: 600 })
    await d.click('例如', { within: '.el-dialog', css: 'textarea', after: 500 })
    await d.type('情境题用电商项目的例子')
    await d.wait(1200)
    d.label('点开始出题：每道题出完，AI 还会不看答案自己做一遍，做不对的标出来')
    await d.click('开始出题', { exact: true, after: 1500 })
    d.fast(10)
    await d.waitText('去检查草稿', 360000)
    d.normal()
    d.label('出题结果：出了几道、要点覆盖、误解用上、可能有歧义的，用时和费用')
    await d.wait(6000)
    await d.click('去检查草稿', { exact: true, after: 2500 })
    d.label('新题先是草稿，学生看不到；老师检查、修改，确认后才发布')
    await d.locate('确认全部草稿')
    await d.wait(4000)
    await d.scroll(500, null, 1300)
    await d.wait(4000)
  }, async (d) => {
    // 录完删掉这次出的草稿题，不影响后面学生做的练习
    const n = sql("SELECT COUNT(*) FROM exercise WHERE item_id=65 AND draft=1 AND deleted=0")
    sql("UPDATE exercise SET deleted=1 WHERE item_id=65 AND draft=1 AND deleted=0")
    console.log('  已删掉草稿题', n, '道')
  }),

  // ---------------------------------------------------------------- 模块 5：老师干预（全班分析）
  s5_intervention: seg(async (d) => {
    // 重录时恢复：删掉上次的推送、推送出的新题和分析结果，让页面重新分析
    const ex = sql("SELECT GROUP_CONCAT(exercise_ids) FROM class_push WHERE item_id=65")
    const ids = (ex === 'NULL' ? '' : ex).split(',').filter((x) => /^\d+$/.test(x))
    if (ids.length) sql(`UPDATE exercise SET deleted=1 WHERE id IN (${ids.join(',')})`)
    sql('DELETE FROM class_push WHERE item_id=65; DELETE FROM class_issue WHERE item_id=65; DELETE FROM item_analysis WHERE item_id=65;')
    await d.login(TEACHER)
    await d.go('/intervention', 3500)
  }, async (d) => {
    d.caption('老师端 · 模块 5 老师干预')
    await d.wait(1500)
    await d.click('', { css: '.el-select', after: 900 })
    await d.click('软件测试', { within: '.el-select-dropdown', exact: true, after: 3000 })
    d.label('练习截止后全班分析：交卷不到 80% 作废（第 1 章只交了 7/10），可以提醒没交的人或延长截止')
    await d.wait(5000)
    d.label('第 6 章交了 8/10，已分析。一道题错 > 80%：先请老师检查题目')
    await d.scroll(330, { x: 1250, y: 600 }, 1200)
    await d.wait(4500)
    d.label('老师看过，题目没问题，点“没问题”：这道题算回对应要点重新分析')
    await d.click('没问题', { exact: true, after: 3000 })
    d.label('某条要点 × 某种考查类型错 > 50%：给出推荐方案，按考查类型不同')
    await d.locate('AI 起草讲解 + 出题')
    await d.wait(4500)
    d.label('点“AI 起草讲解 + 出题”：AI 写一段补充讲解，再出几道同类型新题')
    await d.click('AI 起草讲解 + 出题', { exact: true, after: 1000 })
    d.fast(8)
    await d.waitText('确认推送', 300000)
    await d.wait(1500)
    d.normal()
    d.label('老师检查讲解和新题，可以修改、删掉、重新出题；确认后推送给做错的学生')
    await d.wait(4000)
    await d.scroll(400, { x: 960, y: 600 }, 1300)
    await d.wait(4000)
    await d.scroll(400, null, 1300)
    await d.wait(3000)
    await d.click('确认推送', { exact: true, after: 3000 })
    d.label('推送记录：推给了谁、做了没有、推送前后的错误率')
    await d.wait(5000)
  }),

  // ---------------------------------------------------------------- 模块 6：学生学习、AI 对话、做练习、掌握度
  s6_student: seg(async (d) => {
    resetStudent()
    await d.login(STUDENT)
    await d.go('/learn/courses/1?chapter=3&view=intro', 3000)
  }, async (d) => {
    d.caption('学生端 · 模块 6 学习、AI 对话、做练习')
    d.label('学生03 进入第 6 章：章节介绍和知识点地图（圆圈是知识点，点开看在哪个视频第几秒、课件第几页讲）')
    await d.wait(3500)
    await d.click('智能测试四大优化环节', { within: 'main', after: 3500 })
    d.label('学习顺序：资料随便学；资料都学完才解锁 AI 对话，对话完成才解锁练习（锁着的有小锁）')
    await d.hover('AI 对话：讲给同学听', { within: 'main', after: 3000 })
    d.label('视频：看过的部分记下来，看够 90% 算看完')
    await d.click('视频 6-2 智能测试', { within: 'main', after: 2500 })
    await d.eval(`(() => { const v = document.querySelector('video'); if (v) { v.muted = true; v.currentTime = 30; v.play() } })()`)
    await d.wait(6000)
    await d.eval(`document.querySelector('video')?.pause()`)
    d.label('课件：至少读 30 秒才能点“我已读完”；切到别的页面暂停计时')
    await d.click('课件：智能测试生成与优化', { within: 'main', after: 2500 })
    await d.scroll(450, { x: 900, y: 600 }, 1200)
    await d.wait(2000)
    d.fast(6)
    await d.waitText('我已读完', 60000)
    await d.wait(500)
    d.normal()
    await d.click('我已读完', { exact: true, after: 2500 })
    d.label('读完了，AI 对话解锁')
    await d.click('AI 对话：讲给同学听', { within: 'main', after: 3000 })
    await d.waitText('第 ', 60000)
    d.label('AI 同学“小舟”带着误解来请教；学生用自己的话讲给它听')
    await d.wait(4000)
    const says = [
      '它不只是帮忙生成用例，后面执行、找缺陷这些也会帮忙。',
      '具体说，智能测试贯穿整个测试流程，有四个关键环节：测试用例生成、测试用例执行与优化、缺陷自动检测与修复、测试报告生成与分析。',
      '用例生成时，工具会自动分析需求文档、代码和历史测试数据，生成覆盖更全面的用例，既提高效率，也提高覆盖质量。',
      '执行的时候，它会实时感知页面或逻辑的变化，比如按钮位置变了，就自动调整执行路径和元素定位继续跑，这叫自愈；跑完还会根据反馈调整用例的优先级和结构。',
      '缺陷这块，它用图像识别、日志分析等方法自动发现并验证缺陷，还会给出修复建议；报告环节用数据挖掘和 AI 自动生成报告，并根据历史数据做风险预测，标出高风险模块。',
      '总结一下：四个环节是用例生成、执行与优化、缺陷检测与修复、报告生成与分析，它们连起来是一个完整的测试闭环，所以不能只投入用例生成。',
      '所以智能测试是整条流程都参与，不只是生成用例这一步。',
    ]
    for (let i = 0; i < says.length; i++) {
      if (await d.eval(`document.body.innerText.includes('对话完成')`)) break
      if (i === 1) d.label('第一句讲得不完整：AI 同学不给答案，用提示追问（三级提示，每级问 2 次才升级）')
      if (i === 2) d.label('讲清一条要点，程序记下来，接着问下一条')
      await d.click('用你自己的话讲给', { css: '.fc textarea', after: 300 })
      await d.type(says[i], 22)
      await d.wait(500)
      await d.key('Enter')
      d.fast(4)
      await d.waitFor(`!document.querySelector('.fc .thinking')`, 120000, 'AI 同学回复')
      d.normal()
      await d.wait(3500)
    }
    await d.waitText('对话完成', 30000)
    d.label('对话完成：总结讲清了哪些要点；练习解锁')
    await d.wait(5000)

    d.label('做练习：20 道题，单选、两段式（先选答案再选理由）、判断、简答')
    await d.click('练习：智能测试生成与优化', { within: 'main', after: 3000 })
    const rows = sql("SELECT id, type, answer, IFNULL(reason_answer,''), JSON_LENGTH(options), REPLACE(REPLACE(LEFT(stem,18),'\\n',''),'\\t','') FROM exercise WHERE item_id=65 AND deleted=0 AND draft=0")
      .split(/\r?\n/).map((l) => l.split('\t'))
    const WRONG = new Set(['33', '39', '47', '51'])
    const SHORT = {
      35: '自动化测试的核心价值是把重复执行交给机器，所以执行次数多很多；但测试逻辑还是人按预设场景写好的，覆盖有限，复杂交互测不到，所以深层缺陷发现得少。而且脚本对界面变化很敏感，每个版本都要花人力修脚本，所以不能认为上了自动化就万事大吉。',
      41: '报告生成与分析环节用数据挖掘和 AI 自动生成、分析报告，还能根据历史数据预测风险，比如提示支付模块要优先回归，为测试决策提供依据。缺陷检测与修复环节是用图像识别、日志分析等自动发现、验证缺陷并给修复建议。一个侧重分析和预测，一个侧重找缺陷和修复。',
      44: '先根据需求构建状态机这样的模型，再由工具根据模型自动生成用例去执行。不一致的话就去修改。',
    }
    const n = await d.eval(`document.querySelectorAll('.question').length`)
    d.fast(3)
    for (let i = 0; i < n; i++) {
      const stem = await d.eval(`document.querySelectorAll('.question')[${i}].querySelector('.stem').innerText`)
      const r = rows.find((x) => stem.replace(/\s/g, '').includes(x[5].replace(/\s/g, '')))
      if (!r) throw new Error('对不上题目：' + stem.slice(0, 30))
      const [id, type, ans, reason, nopt] = r
      const q = `document.querySelectorAll('.question')[${i}]`
      if (type === 'short_answer') {
        await d.click('', { js: `${q}.querySelector('textarea')`, moveMs: 400, after: 300 })
        await d.type(SHORT[id], 40)
        continue
      }
      const L = 'ABCD'
      let a = L.indexOf(ans)
      if (WRONG.has(id)) a = (a + 1) % Number(nopt)
      await d.click('', { js: `${q}.querySelectorAll('button.option')[${a}]`, moveMs: 350, before: 120, after: 250 })
      if (type === 'two_tier') {
        await d.click('', { js: `${q}.querySelectorAll('button.option')[${Number(nopt) + L.indexOf(reason)}]`, moveMs: 300, before: 120, after: 250 })
      }
    }
    d.normal()
    d.label('全部做完交卷：客观题程序判分；简答由 AI 对照判分要点判两次，不一致交给老师')
    await d.click('交卷', { exact: true, after: 1000 })
    const box = await d.eval(`!!document.querySelector('.el-message-box')`)
    if (box) await d.click('确定', { within: '.el-message-box', exact: true, after: 1000 })
    d.fast(6)
    await d.waitFor(`!document.body.innerText.includes('AI 正在判简答')`, 180000, '判分完成')
    await d.wait(1500)
    d.normal()
    d.label('结果：每道题对错、选错时“你可能是这样想的”、简答每条要点有没有写到；掌握度按知识点变化')
    await d.wait(5000)
    await d.scroll(700, { x: 900, y: 600 }, 1500)
    await d.wait(4000)
    await d.scroll(900, null, 1500)
    await d.wait(4000)
  }),

  // ---------------------------------------------------------------- 模块 7：小助手（补练、拓展、问问题）
  s7_assistant: seg(async (d) => {
    await d.login(STUDENT)
    await d.go('/learn/courses/1?chapter=3&view=intro', 3000)
  }, async (d) => {
    d.caption('学生端 · 模块 7 小助手')
    d.label('右下角的小助手：只看自己的学习情况。交卷后每个知识点一份报告，颜色只看练习')
    await d.click('', { css: '.fab', after: 3000 })
    await d.wait(3000)
    await d.scroll(400, { x: 1650, y: 600 }, 1200)
    await d.wait(4000)
    d.label('没掌握的知识点：哪种考查类型错了、漏了哪条要点、反复出现的误解，以及复习方案（从视频第几秒看起 → 补练 → 讲给 AI 同学）')
    await d.wait(3000)
    await d.click('做补练题', { within: '.panel', after: 1000 })
    d.fast(6)
    await d.waitFor(`document.querySelector('.panel .remedy .q')`, 120000, '补练题出来')
    d.normal()
    d.label('补练题按这个学生自己的错题现出，做对了掌握度才回来')
    await d.wait(3000)
    const qs = await d.eval(`document.querySelectorAll('.panel .remedy .q').length`)
    for (let i = 0; i < qs; i++) {
      await d.click('', { js: `document.querySelectorAll('.panel .remedy .q')[${i}].querySelectorAll('label.opt')[0]`, moveMs: 400, after: 400 })
      const hasReason = await d.eval(`document.querySelectorAll('.panel .remedy .q')[${i}].innerText.includes('理由：')`)
      if (hasReason) await d.click('', { js: `[...document.querySelectorAll('.panel .remedy .q')[${i}].querySelectorAll('label.opt')].find((l) => l.querySelector('input[name^=r]'))`, moveMs: 400, after: 400 })
    }
    await d.click('交补练题', { exact: true, after: 3500 })
    await d.wait(4000)
    await d.scroll(500, null, 1200)
    await d.wait(3000)
    d.label('掌握得好的知识点：可以“来点更难的”，选一种考查类型')
    const ext = await d.eval(`!!document.querySelector('.panel .extend')`)
    if (ext) {
      await d.click('情境应用', { within: '.panel .extend', after: 1000 })
      d.fast(6)
      await d.waitFor(`document.querySelector('.panel .extend ~ .remedy .q, .panel .remedy .q')`, 120000, '拓展题出来')
      d.normal()
      await d.wait(4000)
    }
    d.label('“问问题”：随便问；不给练习答案，交卷前不让问题目')
    await d.click('问问题', { within: '.panel', exact: true, after: 1500 })
    await d.click('', { css: '.panel textarea, .panel input', after: 400 })
    await d.type('自愈测试和普通的失败重试有什么区别？', 14)
    await d.key('Enter')
    d.fast(4)
    await d.wait(3000)
    await d.waitFor(`!document.querySelector('.panel .thinking, .panel .is-loading')`, 90000, '小助手回复')
    d.normal()
    await d.wait(6000)
  }),

  // ---------------------------------------------------------------- 模块 8：学生画像
  s8_profile: seg(async (d) => {
    await d.login(STUDENT)
    await d.go('/profile', 3500)
  }, async (d) => {
    d.caption('学生端 · 模块 8 学生画像')
    d.label('学生画像：根据练习、对话、学习记录现算，每个结论都写依据')
    await d.wait(5000)
    await d.scroll(500, { x: 1100, y: 600 }, 1500)
    await d.wait(5000)
    await d.scroll(600, null, 1500)
    await d.wait(5000)
    await d.scroll(700, null, 1500)
    await d.wait(5000)
    d.label('结束')
    await d.scroll(-3000, null, 1500)
    await d.wait(2500)
  }),
}

/** 学生03（id 7）重录前恢复：删掉 10:45 以后产生的第 6 章学习记录，只留“4 个视频已看完” */
function resetStudent() {
  const T = "'2026-11-28 10:45:00'"
  sql(`DELETE FROM answer_record WHERE student_id=7 AND submitted_at>=${T};
    DELETE FROM feynman_message WHERE session_id IN (SELECT id FROM (SELECT id FROM feynman_session WHERE student_id=7 AND created_at>=${T}) s);
    DELETE FROM feynman_session WHERE student_id=7 AND created_at>=${T};
    DELETE FROM learning_event WHERE student_id=7 AND created_at>=${T};
    DELETE FROM item_progress WHERE student_id=7 AND created_at>=${T};
    DELETE FROM remedy_round WHERE student_id=7 AND created_at>=${T};
    DELETE FROM assistant_message WHERE student_id=7 AND created_at>=${T};
    DELETE FROM mastery WHERE student_id=7 AND updated_at>=${T}
      AND knowledge_point_id IN (SELECT knowledge_point_id FROM chapter_kp WHERE chapter_id=3);`)
}
