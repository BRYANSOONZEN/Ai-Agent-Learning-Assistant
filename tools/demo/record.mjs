// 自动录演示视频（只录画面，配音另配）：本机 Chrome 无头模式 + Chrome 调试协议的 screencast，
// 按 segments.mjs 里写好的步骤操作页面，每一帧连同时间存下来，再由 encode.py 合成 MP4。
// 用法：node tools/demo/record.mjs 段名 [段名 ...]   （不写段名 = 全部按顺序录）
// 前提：后端 8080、前端 5173、资料处理服务 8091 都在运行；数据已经用 prep_demo_data.py 准备好。
// 输出：<OUT>/<段名>/ 下的 jpg 帧 + timeline.json（每帧时间、快进区间、字幕）
import { spawn } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.join(HERE, '..', '..')
const OUT = process.env.DEMO_OUT || path.join(os.tmpdir(), 'se-demo-frames')
const BASE = 'http://localhost:5173'
const CHROME = process.env.CHROME || 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const PORT = 9334
const W = 1440, H = 810, SCALE = 4 / 3 // 画面 1920×1080

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

// 页面里画一个看得见的鼠标，点击时出一圈波纹（无头浏览器本身不画鼠标）
const CURSOR_JS = `(() => {
  if (window.__cursorInstalled) return; window.__cursorInstalled = true;
  const install = () => {
    const c = document.createElement('div'); c.id = '__demo_cursor';
    c.style.cssText = 'position:fixed;left:0;top:0;width:26px;height:26px;z-index:2147483647;pointer-events:none;' +
      'transform:translate(' + (window.__cx ?? -100) + 'px,' + (window.__cy ?? -100) + 'px);transition:none;';
    c.innerHTML = '<svg width="26" height="26" viewBox="0 0 24 24"><path d="M4 2 L4 19 L8.5 14.8 L11.6 21.5 L14.3 20.3 L11.3 13.7 L17.5 13.4 Z" fill="#111" stroke="#fff" stroke-width="1.6" stroke-linejoin="round"/></svg>';
    document.documentElement.appendChild(c);
    document.addEventListener('mousemove', (e) => { window.__cx = e.clientX; window.__cy = e.clientY;
      c.style.transform = 'translate(' + (e.clientX - 3) + 'px,' + (e.clientY - 2) + 'px)'; }, true);
    document.addEventListener('mousedown', (e) => {
      const r = document.createElement('div');
      r.style.cssText = 'position:fixed;z-index:2147483646;pointer-events:none;border-radius:50%;' +
        'border:3px solid rgba(255,140,0,.95);background:rgba(255,170,0,.25);width:16px;height:16px;' +
        'left:' + (e.clientX - 8) + 'px;top:' + (e.clientY - 8) + 'px;transition:all .45s ease-out;';
      document.documentElement.appendChild(r);
      requestAnimationFrame(() => requestAnimationFrame(() => { r.style.width = r.style.height = '46px';
        r.style.left = (e.clientX - 23) + 'px'; r.style.top = (e.clientY - 23) + 'px'; r.style.opacity = '0'; }));
      setTimeout(() => r.remove(), 600);
    }, true);
  };
  if (document.documentElement) install(); else document.addEventListener('DOMContentLoaded', install);
})()`

// 按文字找元素：在可见元素里找文字完全相同（或包含）的、最小的那个；within 是限定范围的选择器
const FIND_JS = (text, opts) => `(() => {
  const text = ${JSON.stringify(text)}, o = ${JSON.stringify(opts || {})};
  const roots = o.within ? [...document.querySelectorAll(o.within)] : [document];
  const q = (sel) => roots.flatMap((r) => [...r.querySelectorAll(sel)]);
  const vis = (e) => { const r = e.getBoundingClientRect(); const s = getComputedStyle(e);
    return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none' && s.opacity !== '0'; };
  if (o.js) { const el = (0, eval)(o.js); if (!el) return null; window.__demoTarget = el; return true; }
  let cands;
  if (o.css) cands = q(o.css).filter(vis);
  else {
    const norm = (s) => (s || '').replace(/\\s+/g, ' ').trim();
    const all = q('button,a,li,span,div,p,label,td,th,h1,h2,h3,h4,input,textarea,.el-tabs__item,[role=tab],[role=button]').filter(vis);
    cands = all.filter((e) => norm(e.innerText) === text || (e.placeholder && e.placeholder.includes(text)));
    if (!cands.length && !o.exact) cands = all.filter((e) => norm(e.innerText).includes(text));
    cands.sort((a, b) => (a.innerText || '').length - (b.innerText || '').length ||
      a.getBoundingClientRect().width * a.getBoundingClientRect().height - b.getBoundingClientRect().width * b.getBoundingClientRect().height);
  }
  const el = cands[o.nth || 0];
  if (!el) return null;
  window.__demoTarget = el;
  return true;
})()`

class Driver {
  constructor(send, events, seg) {
    this.send = send; this.events = events; this.seg = seg
    this.cx = W / 2; this.cy = H / 2
    this.marks = [] // {t, kind: 'fast'|'normal'|'caption'|'label', ...}
    this.speed = 1
  }
  now() { return Date.now() / 1000 }
  async eval(expr) {
    const r = await this.send('Runtime.evaluate', { expression: expr, awaitPromise: true, returnByValue: true })
    if (r.exceptionDetails) throw new Error('页面脚本出错：' + (r.exceptionDetails.exception?.description || r.exceptionDetails.text))
    return r.result?.value
  }
  /** 旁白分段标记：配音稿按这些点分段 */
  label(text) { this.marks.push({ t: this.now(), kind: 'label', text }); console.log('  ·', text) }
  /** 画面右上角的小标题（例如“老师端 · 模块 1 上传资料”） */
  caption(text) { this.marks.push({ t: this.now(), kind: 'caption', text }) }
  /** 之后的画面按 factor 倍速播放（等大模型、等转文字时用） */
  fast(factor = 8) { this.marks.push({ t: this.now(), kind: 'speed', factor }); this.speed = factor }
  normal() { this.marks.push({ t: this.now(), kind: 'speed', factor: 1 }); this.speed = 1 }
  async wait(ms) { await sleep(ms) }
  async go(url, settle = 1800) {
    this.events.length = 0
    await this.send('Page.navigate', { url: BASE + url })
    for (let i = 0; i < 150 && !this.events.includes('Page.loadEventFired'); i++) await sleep(100)
    await sleep(300)
    await this.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: this.cx, y: this.cy })
    await sleep(settle)
  }
  async waitFor(expr, timeout = 60000, what = expr) {
    const t0 = Date.now()
    while (Date.now() - t0 < timeout) {
      if (await this.eval(`(() => { try { return !!(${expr}) } catch { return false } })()`)) return
      await sleep(250)
    }
    throw new Error('等太久了：' + what)
  }
  async waitText(text, timeout = 60000) {
    await this.waitFor(`document.body.innerText.includes(${JSON.stringify(text)})`, timeout, '出现文字 ' + text)
  }
  async waitNoText(text, timeout = 60000) {
    await this.waitFor(`!document.body.innerText.includes(${JSON.stringify(text)})`, timeout, '文字消失 ' + text)
  }
  /** 找到元素、滚到看得见、返回中心点 */
  async locate(text, opts = {}) {
    const t0 = Date.now()
    while (!(await this.eval(FIND_JS(text, opts)))) {
      if (Date.now() - t0 > (opts.timeout ?? 15000)) throw new Error('找不到：' + text + ' ' + JSON.stringify(opts))
      await sleep(250)
    }
    const r = await this.eval(`(() => { const e = window.__demoTarget; const r0 = e.getBoundingClientRect();
      const hit = document.elementFromPoint(r0.left + r0.width / 2, r0.top + r0.height / 2); const covered = !hit || (hit !== e && !e.contains(hit)); if (r0.top < 70 || r0.bottom > innerHeight - 20 || covered) { e.scrollIntoView({ block: 'center', behavior: covered ? 'auto' : 'smooth' }); return true } return false })()`)
    await sleep(r ? 700 : 0)
    return await this.eval(`(() => { const r = window.__demoTarget.getBoundingClientRect();
      return { x: r.left + r.width / 2, y: r.top + r.height / 2, w: r.width, h: r.height } })()`)
  }
  /** 鼠标移过去：走一条略弯的曲线，距离越远越久，快到时减速（像人手在移） */
  async moveTo(x, y, ms) {
    const x0 = this.cx, y0 = this.cy
    const dist = Math.hypot(x - x0, y - y0)
    if (dist < 2) return
    const dur = ms ?? Math.min(1100, 280 + dist * 0.55) * (0.85 + Math.random() * 0.3)
    const steps = Math.max(10, Math.round(dur / 16))
    // 控制点：在连线中点旁边偏一点，路径就不是直线
    const bend = (Math.random() - 0.5) * Math.min(160, dist * 0.35)
    const mx = (x0 + x) / 2 - ((y - y0) / (dist || 1)) * bend
    const my = (y0 + y) / 2 + ((x - x0) / (dist || 1)) * bend
    for (let i = 1; i <= steps; i++) {
      const k = i / steps
      const e = 1 - Math.pow(1 - k, 3) // 先快后慢
      const u = 1 - e
      this.cx = u * u * x0 + 2 * u * e * mx + e * e * x
      this.cy = u * u * y0 + 2 * u * e * my + e * e * y
      await this.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: this.cx, y: this.cy })
      await sleep(dur / steps)
    }
  }
  /** 像在看某处：鼠标移过去停一会儿（读这一段） */
  async look(text, opts = {}) {
    const p = await this.locate(text, opts)
    const jx = (Math.random() - 0.5) * Math.min(80, p.w * 0.4)
    await this.moveTo(p.x + jx, p.y + (Math.random() - 0.5) * 6)
    await sleep(opts.after ?? 1800)
  }
  async click(text, opts = {}) {
    const p = await this.locate(text, opts)
    const x = p.x + (opts.dx || 0), y = p.y + (opts.dy || 0)
    await this.moveTo(x, y, opts.moveMs)
    await sleep(opts.before ?? 200 + Math.random() * 250)
    await this.send('Input.dispatchMouseEvent', { type: 'mousePressed', x, y, button: 'left', clickCount: 1 })
    await sleep(70)
    await this.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x, y, button: 'left', clickCount: 1 })
    await sleep(opts.after ?? 900)
  }
  async hover(text, opts = {}) {
    const p = await this.locate(text, opts)
    await this.moveTo(p.x, p.y, opts.moveMs)
    await sleep(opts.after ?? 600)
  }
  /** 一个字一个字地打（中文用 insertText）：速度有快有慢，标点后停一下 */
  async type(text, cps = 9) {
    for (const ch of text) {
      if (ch === '\n') await this.key('Enter')
      else await this.send('Input.insertText', { text: ch })
      let ms = (1000 / cps) * (0.5 + Math.random())
      if ('，。；：、！？,.'.includes(ch)) ms += 250 + Math.random() * 350
      await sleep(ms)
    }
  }
  /** 全选输入框里的字（Ctrl+A），接着打字就替换掉 */
  async selectAll() {
    await this.send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'a', code: 'KeyA', windowsVirtualKeyCode: 65, modifiers: 2 })
    await this.send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'a', code: 'KeyA', windowsVirtualKeyCode: 65, modifiers: 2 })
  }
  async key(key) {
    const codes = { Enter: 13, Tab: 9, Escape: 27, Backspace: 8, End: 35 }
    await this.send('Input.dispatchKeyEvent', { type: 'keyDown', key, code: key, windowsVirtualKeyCode: codes[key] || 0,
      text: key === 'Enter' ? '\r' : undefined })
    await this.send('Input.dispatchKeyEvent', { type: 'keyUp', key, code: key, windowsVirtualKeyCode: codes[key] || 0 })
  }
  /** 鼠标滚轮：在 (x, y) 处往下滚 dy 像素，分几次滚得平滑一些 */
  async scroll(dy, at = null, ms = 900) {
    const x = at?.x ?? this.cx, y = at?.y ?? this.cy
    if (at) await this.moveTo(x, y, 400)
    const steps = Math.max(6, Math.round(ms / 40))
    for (let i = 0; i < steps; i++) {
      await this.send('Input.dispatchMouseEvent', { type: 'mouseWheel', x, y, deltaX: 0, deltaY: dy / steps })
      await sleep(ms / steps)
    }
    await sleep(400)
  }
  /** 用脚本把某个可滚动区域平滑滚到底/顶/某元素 */
  async scrollTo(text, opts = {}) { await this.locate(text, { ...opts, force: true }); await sleep(500) }
  async upload(css, file) {
    const doc = await this.send('DOM.getDocument', { depth: 0 })
    const q = await this.send('DOM.querySelector', { nodeId: doc.root.nodeId, selector: css })
    if (!q.nodeId) throw new Error('找不到上传控件 ' + css)
    await this.send('DOM.setFileInputFiles', { nodeId: q.nodeId, files: [file] })
  }
  /** 调试用：存一张截图 */
  async shot(name) {
    const s = await this.send('Page.captureScreenshot', { format: 'png' })
    const dir = path.join(OUT, '_probe'); fs.mkdirSync(dir, { recursive: true })
    fs.writeFileSync(path.join(dir, name + '.png'), Buffer.from(s.data, 'base64'))
  }
  /** 直接调后端接口（准备数据、查状态用，不出现在画面里） */
  async api(method, url, body) {
    return await this.eval(`(async () => { const u = JSON.parse(localStorage.getItem('se-platform-user') || '{}');
      const r = await fetch(${JSON.stringify(url)}, { method: ${JSON.stringify(method)}, headers: { 'Content-Type': 'application/json',
        ...(u.token ? { Authorization: 'Bearer ' + u.token } : {}) }, body: ${body ? JSON.stringify(JSON.stringify(body)) : 'undefined'} });
      return await r.json() })()`)
  }
  /** 登录页点“演示账号”按钮登录 */
  async login(name) {
    await this.go('/login', 1200)
    await this.eval('localStorage.clear(); sessionStorage.clear()')
    await this.go('/login', 1500)
    await this.click(name, { within: '.demos', after: 2500 })
    await this.waitFor(`location.pathname !== '/login'`, 15000, '登录成功')
    await sleep(1200)
  }
}

async function recordSegment(name, fn) {
  const dir = path.join(OUT, name)
  fs.rmSync(dir, { recursive: true, force: true })
  fs.mkdirSync(dir, { recursive: true })
  const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'demo-chrome-'))
  const chrome = spawn(CHROME, ['--headless=new', `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`,
    `--window-size=${W},${H}`, '--hide-scrollbars', '--no-first-run', '--lang=zh-CN', '--autoplay-policy=no-user-gesture-required',
    '--force-device-scale-factor=' + SCALE, 'about:blank'], { stdio: 'ignore' })
  let ws
  try {
    for (let i = 0; i < 60 && !ws; i++) {
      try {
        const t = await (await fetch(`http://127.0.0.1:${PORT}/json/new?about:blank`, { method: 'PUT' })).json()
        ws = new WebSocket(t.webSocketDebuggerUrl)
      } catch { await sleep(250) }
    }
    await new Promise((r) => ws.addEventListener('open', r, { once: true }))
    let id = 0
    const waiting = new Map()
    const events = []
    const frames = []
    let n = 0
    let send
    ws.addEventListener('message', (m) => {
      const msg = JSON.parse(m.data)
      if (msg.id && waiting.has(msg.id)) { waiting.get(msg.id)(msg); waiting.delete(msg.id); return }
      if (msg.method === 'Page.screencastFrame') {
        const f = String(++n).padStart(6, '0') + '.jpg'
        fs.writeFileSync(path.join(dir, f), Buffer.from(msg.params.data, 'base64'))
        frames.push({ f, t: Date.now() / 1000 })
        send('Page.screencastFrameAck', { sessionId: msg.params.sessionId }).catch(() => {})
      } else if (msg.method) events.push(msg.method)
    })
    send = (method, params = {}) => new Promise((resolve, reject) => {
      const k = ++id
      waiting.set(k, (msg) => (msg.error ? reject(new Error(method + ': ' + msg.error.message)) : resolve(msg.result)))
      ws.send(JSON.stringify({ id: k, method, params }))
    })
    await send('Page.enable')
    await send('DOM.enable')
    await send('Emulation.setDeviceMetricsOverride', { width: W, height: H, deviceScaleFactor: SCALE, mobile: false })
    await send('Page.addScriptToEvaluateOnNewDocument', { source: CURSOR_JS })
    const d = new Driver(send, events, name)
    // 先登录、到起始页，再开始录（准备动作不进画面）
    if (fn.setup) await fn.setup(d)
    await send('Page.startScreencast', { format: 'jpeg', quality: 88, maxWidth: W * SCALE, maxHeight: H * SCALE, everyNthFrame: 1 })
    const t0 = Date.now() / 1000
    await sleep(300)
    await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: d.cx, y: d.cy })
    try {
      await fn(d)
    } catch (e) {
      await d.shot(name + '-出错')
      throw e
    } finally {
      await sleep(800)
      await send('Page.stopScreencast').catch(() => {})
      await sleep(300)
      fs.writeFileSync(path.join(dir, 'timeline.json'), JSON.stringify({ t0, t1: Date.now() / 1000, frames, marks: d.marks }, null, 1))
      if (fn.teardown) await fn.teardown(d).catch((e) => console.error('收尾出错：', e.message))
    }
    console.log(`录完 ${name}：${frames.length} 帧，${(Date.now() / 1000 - t0).toFixed(1)} 秒`)
  } finally {
    try { ws?.close() } catch {}
    chrome.kill()
    await sleep(500)
    fs.rmSync(profile, { recursive: true, force: true, maxRetries: 3 })
  }
}

// 用哪个段落文件：默认 segments.mjs；老师端单独录用 DEMO_SEGMENTS=segments_teacher.mjs
const { SEGMENTS } = await import('./' + (process.env.DEMO_SEGMENTS || 'segments.mjs'))
const want = process.argv.slice(2)
const list = Object.entries(SEGMENTS).filter(([k]) => !want.length || want.includes(k))
if (!list.length) { console.error('没有这个段：', want.join(' '), '\n可选：', Object.keys(SEGMENTS).join(' ')); process.exit(1) }
for (const [k, fn] of list) {
  console.log('录', k)
  await recordSegment(k, fn)
}
