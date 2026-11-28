// 把 mermaid 图渲染成 PNG（给 Word 版文档用）：本机 Chrome 无头模式 + Chrome 调试协议，mermaid 从 jsDelivr 加载。
// 用法：node tools/docs/render_mermaid.mjs 输入.json 输出目录
//   输入.json：[{ "name": "fig1", "code": "flowchart LR ..." }, ...]，每张图存成 输出目录/name.png（2 倍清晰度）
import { spawn } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'

const CHROME = process.env.CHROME || 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const PORT = 9335
const MERMAID = 'https://cdn.jsdelivr.net/npm/mermaid@10.9.1/dist/mermaid.min.js'
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

const [, , input, outDir] = process.argv
const figs = JSON.parse(fs.readFileSync(input, 'utf8'))
fs.mkdirSync(outDir, { recursive: true })

const html = `<!doctype html><html><head><meta charset="utf-8">
<style>body{margin:0;background:#fff;font-family:"Microsoft YaHei",sans-serif} #box{display:inline-block;padding:12px}</style>
<script src="${MERMAID}"></script></head><body><div id="box"></div>
<script>mermaid.initialize({ startOnLoad: false, theme: 'neutral', fontFamily: 'Microsoft YaHei, sans-serif',
  flowchart: { htmlLabels: true, curve: 'basis' }, securityLevel: 'loose' });
window.draw = async (code) => { const { svg } = await mermaid.render('g' + Date.now(), code);
  document.getElementById('box').innerHTML = svg; const s = document.querySelector('#box svg'); const mw = parseFloat(s.style.maxWidth); if (mw) { s.style.width = mw + 'px'; s.style.maxWidth = 'none' } const r = document.getElementById('box').getBoundingClientRect();
  return { w: Math.ceil(r.width), h: Math.ceil(r.height) } }</script></body></html>`
const page = path.join(os.tmpdir(), 'mermaid-render.html')
fs.writeFileSync(page, html)

const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'mmd-'))
const chrome = spawn(CHROME, ['--headless=new', `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`,
  '--hide-scrollbars', '--no-first-run', 'about:blank'], { stdio: 'ignore' })
try {
  let ws
  for (let i = 0; i < 60 && !ws; i++) {
    try {
      const t = await (await fetch(`http://127.0.0.1:${PORT}/json/new?about:blank`, { method: 'PUT' })).json()
      ws = new WebSocket(t.webSocketDebuggerUrl)
    } catch { await sleep(250) }
  }
  await new Promise((r) => ws.addEventListener('open', r, { once: true }))
  let id = 0
  const waiting = new Map()
  ws.addEventListener('message', (m) => {
    const msg = JSON.parse(m.data)
    if (msg.id && waiting.has(msg.id)) { waiting.get(msg.id)(msg); waiting.delete(msg.id) }
  })
  const send = (method, params = {}) => new Promise((resolve, reject) => {
    const k = ++id
    waiting.set(k, (msg) => (msg.error ? reject(new Error(method + ': ' + msg.error.message)) : resolve(msg.result)))
    ws.send(JSON.stringify({ id: k, method, params }))
  })
  const evaluate = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, awaitPromise: true, returnByValue: true })
    if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description || r.exceptionDetails.text)
    return r.result.value
  }
  await send('Page.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 1600, height: 1200, deviceScaleFactor: 2, mobile: false })
  await send('Page.navigate', { url: 'file:///' + page.replace(/\\/g, '/') })
  for (let i = 0; i < 100; i++) {
    if (await evaluate('typeof window.mermaid !== "undefined" && typeof window.draw === "function"').catch(() => false)) break
    await sleep(200)
  }
  for (const f of figs) {
    const size = await evaluate(`draw(${JSON.stringify(f.code)})`)
    await sleep(300)
    const shot = await send('Page.captureScreenshot', { format: 'png', clip: { x: 0, y: 0, width: size.w, height: size.h, scale: 1 }, captureBeyondViewport: true })
    fs.writeFileSync(path.join(outDir, f.name + '.png'), Buffer.from(shot.data, 'base64'))
    console.log('图', f.name, size.w + '×' + size.h)
  }
  ws.close()
} finally {
  chrome.kill()
}
