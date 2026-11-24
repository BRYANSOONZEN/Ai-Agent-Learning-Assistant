/*
 * Deadlines of exercises and AI dialogues (chapter_item.dueAt, "yyyy-MM-dd HH:mm:ss" from the backend).
 * REMIND_DAYS matches platform.deadline.remind-days on the backend: inside it a deadline counts as "快到期".
 */
export const REMIND_DAYS = 3

export const toDate = (s) => (s ? new Date(s.replace(' ', 'T')) : null)

// "11月30日 23:59" (year added when it is not this year)
export function fmtDue(s) {
  const d = toDate(s)
  if (!d) return ''
  const pad = (n) => String(n).padStart(2, '0')
  const year = d.getFullYear() === new Date().getFullYear() ? '' : `${d.getFullYear()}年`
  return `${year}${d.getMonth() + 1}月${d.getDate()}日 ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

// "还剩 2 天 3 小时" / "还剩 5 小时" / "还剩 20 分钟"
export function leftText(ms) {
  const min = Math.max(1, Math.floor(ms / 60000))
  if (min < 60) return `还剩 ${min} 分钟`
  const h = Math.floor(min / 60)
  if (h < 24) return `还剩 ${h} 小时`
  return `还剩 ${Math.floor(h / 24)} 天${h % 24 ? ` ${h % 24} 小时` : ''}`
}

/*
 * One deadline as the student sees it: { state, text, tone, blocked }
 *   state: 'none' | 'done' | 'overdue' | 'soon' | 'later'
 *   blocked: past the deadline and late work is not allowed
 */
export function dueInfo(dueAt, allowLate = true, done = false, now = Date.now()) {
  const d = toDate(dueAt)
  if (!d) return { state: 'none', text: '', tone: 'gray', blocked: false }
  const ms = d.getTime() - now
  if (done) return { state: 'done', text: `截止 ${fmtDue(dueAt)}`, tone: 'gray', blocked: false }
  if (ms < 0) {
    return allowLate
      ? { state: 'overdue', text: '已过期，可以补做', tone: 'orange', blocked: false }
      : { state: 'overdue', text: '已截止，不能再交', tone: 'red', blocked: true }
  }
  if (ms < REMIND_DAYS * 86400000) return { state: 'soon', text: leftText(ms), tone: 'orange', blocked: false }
  return { state: 'later', text: `截止 ${fmtDue(dueAt)}`, tone: 'gray', blocked: false }
}
