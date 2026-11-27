// 给《界面设计》截图：用本机 Chrome 无头模式 + Chrome 调试协议（Node 自带 WebSocket），不用装任何包。
// 前提：后端 8080、前端 5173 都在运行，演示数据已导入。
// 用法：node tools/docs/shoot.mjs [只截名字包含这个词的页面]
// 演示账号的密码从 backend/src/main/resources/data.sql 的注释里读，不写在这里。
import { spawn } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.join(HERE, '..', '..')
const OUT = path.join(ROOT, 'docs', '提交材料', '界面截图')
const BASE = 'http://localhost:5173'
const CHROME = process.env.CHROME || 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const PORT = 9333
const ONLY = process.argv[2] || ''

const sql = fs.readFileSync(path.join(ROOT, 'backend/src/main/resources/data.sql'), 'utf8')
const PASSWORD = (sql.match(/密码都是\s*(\S+?)（/) || [])[1]
if (!PASSWORD) throw new Error('data.sql 里没找到演示密码的注释')

// 每个角色要截的页面：[文件名, 路径, 进入后要做的事（可选，页面里执行的 JS）]
const CLICK = (text) => `[...document.querySelectorAll('button, .el-tabs__item, a, li, span')].find((e) => e.textContent.trim() === ${JSON.stringify(text)})?.click()`
const PAGES = {
  none: [
    ['01-登录', '/login'],
    ['02-学生注册', '/register'],
  ],
  '2023008': [
    ['10-学生首页', '/dashboard'],
    ['11-课程列表', '/learn/courses'],
    ['12-章节介绍与知识点地图', '/learn/courses/1?chapter=3&view=intro'],
    ['13-学习页-视频', '/learn/courses/1?chapter=3&view=item:5'],
    ['14-学习页-文档', '/learn/courses/1?chapter=3&view=item:4'],
    ['15-学习页-AI对话', '/learn/courses/1?chapter=3&view=item:33'],
    ['16-学习页-练习结果', '/learn/courses/1?chapter=3&view=item:65'],
    ['17-小助手', '/learn/courses/1?chapter=3&view=intro', `document.querySelector('.fab')?.click()`],
    ['18-学习记录', '/records'],
    ['19-学情诊断-学生', '/diagnosis'],
    ['20-学生画像', '/profile'],
    ['21-学习推荐', '/recommend'],
  ],
  '2023009': [
    ['22-解锁提示', '/learn/courses/1?chapter=3&view=item:33'],
  ],
  teacher01: [
    ['30-老师首页', '/dashboard'],
    ['31-我的课程', '/teacher/courses'],
    ['32-建课-章节介绍', '/teacher/courses/1?chapter=3&view=intro'],
    ['33-建课-视频项与转文字', '/teacher/courses/1?chapter=3&view=item:5'],
    ['34-识别知识点', '/teacher/courses/1?chapter=3&view=kps'],
    ['35-AI对话设置', '/teacher/courses/1?chapter=3&view=item:33'],
    ['36-练习题目', '/teacher/courses/1?chapter=3&view=item:65'],
    ['37-发布检查', '/teacher/courses/1?chapter=3&view=publish'],
    ['38-全班分析', '/intervention'],
    ['39-学情诊断-老师', '/diagnosis'],
  ],
  admin: [
    ['40-用户管理', '/admin/users'],
    ['41-AI调用记录', '/admin/ai-logs'],
  ],
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function main() {
  fs.mkdirSync(OUT, { recursive: true })
  const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'shoot-'))
  const chrome = spawn(CHROME, ['--headless=new', `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`,
    '--window-size=1440,900', '--hide-scrollbars', '--no-first-run', 'about:blank'], { stdio: 'ignore' })
  try {
    let ws
    for (let i = 0; i < 50 && !ws; i++) {
      try {
        const t = await (await fetch(`http://127.0.0.1:${PORT}/json/new?about:blank`, { method: 'PUT' })).json()
        ws = new WebSocket(t.webSocketDebuggerUrl)
      } catch { await sleep(200) }
    }
    await new Promise((r) => ws.addEventListener('open', r, { once: true }))
    let id = 0
    const waiting = new Map()
    const events = []
    ws.addEventListener('message', (m) => {
      const msg = JSON.parse(m.data)
      if (msg.id && waiting.has(msg.id)) { waiting.get(msg.id)(msg); waiting.delete(msg.id) } else if (msg.method) events.push(msg.method)
    })
    const send = (method, params = {}) => new Promise((resolve, reject) => {
      const n = ++id
      waiting.set(n, (msg) => (msg.error ? reject(new Error(method + ': ' + msg.error.message)) : resolve(msg.result)))
      ws.send(JSON.stringify({ id: n, method, params }))
    })
    const evaluate = async (expr) => (await send('Runtime.evaluate', { expression: expr, awaitPromise: true, returnByValue: true })).result?.value
    const go = async (url) => {
      events.length = 0
      await send('Page.navigate', { url: BASE + url })
      for (let i = 0; i < 100 && !events.includes('Page.loadEventFired'); i++) await sleep(100)
      await sleep(2500) // 等接口数据回来、图表画完
    }
    await send('Page.enable')
    await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false })

    for (const [user, pages] of Object.entries(PAGES)) {
      const todo = pages.filter(([name]) => name.includes(ONLY))
      if (!todo.length) continue
      await go('/login')
      await evaluate('localStorage.clear()')
      if (user !== 'none') {
        const ok = await evaluate(`fetch('/api/auth/login', {method: 'POST', headers: {'Content-Type': 'application/json'},
          body: JSON.stringify({username: ${JSON.stringify(user)}, password: ${JSON.stringify(PASSWORD)}})})
          .then((r) => r.json()).then((r) => { localStorage.setItem('se-platform-user', JSON.stringify(r.data)); return r.code })`)
        if (ok !== 200) throw new Error('登录失败：' + user)
      }
      for (const [name, url, action] of todo) {
        await go(url)
        if (action) { await evaluate(action); await sleep(2500) }
        // 整页：把视口拉到页面实际高度（最多 2400），截完再改回来
        const full = Math.min(2400, Math.max(900, await evaluate(`900 + Math.max(0, ...[...document.querySelectorAll('*')]
          .filter((e) => /(auto|scroll)/.test(getComputedStyle(e).overflowY))
          .map((e) => e.scrollHeight - e.clientHeight))`)))
        if (full > 900) {
          await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: full, deviceScaleFactor: 1, mobile: false })
          await sleep(800)
        }
        const shot = await send('Page.captureScreenshot', { format: 'png' })
        if (full > 900) await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false })
        fs.writeFileSync(path.join(OUT, name + '.png'), Buffer.from(shot.data, 'base64'))
        console.log('saved', name)
      }
    }
    ws.close()
  } finally {
    chrome.kill()
  }
}

main().catch((e) => { console.error(e); process.exit(1) })
