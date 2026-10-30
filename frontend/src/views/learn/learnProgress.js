/*
 * Which items a student has finished.
 * The real record is on the server (学习记录, learning_event): markDone sends it there, and the
 * learning page merges the server's list back in (syncDone). localStorage is kept as a fast local copy
 * so 首页 and the course list can show progress without extra requests.
 */
import { logEvent } from '@/api/learn'

const KEY = 'se-platform-learn-done' // { [courseId]: [itemId, ...] }

function readAll() {
  try {
    return JSON.parse(localStorage.getItem(KEY) || '{}')
  } catch {
    return {}
  }
}

export function doneItems(courseId) {
  return readAll()[courseId] || []
}

function write(all) {
  try {
    localStorage.setItem(KEY, JSON.stringify(all))
  } catch {
    /* storage unavailable: the server still has the record */
  }
}

export function markDone(courseId, itemId) {
  const all = readAll()
  const list = all[courseId] || []
  if (list.includes(itemId)) return list
  all[courseId] = [...list, itemId]
  write(all)
  logEvent('finish_item', itemId).catch(() => {})
  return all[courseId]
}

// the server's list of finished items wins; keep anything finished locally but not yet sent
export function syncDone(courseId, serverIds = []) {
  const all = readAll()
  all[courseId] = [...new Set([...serverIds, ...(all[courseId] || [])])]
  write(all)
  return all[courseId]
}

export function courseProgress(courseId) {
  return doneItems(courseId).length
}

export function totalDone() {
  return Object.values(readAll()).reduce((n, list) => n + list.length, 0)
}

// Where the student was last, for "接着上次学习" on 首页:
// { courseId, courseName, chapterId, chapterTitle, view, title, done, total }
const LAST_KEY = 'se-platform-learn-last'

export function saveLast(position) {
  try {
    localStorage.setItem(LAST_KEY, JSON.stringify(position))
  } catch {
    /* ignore */
  }
}

export function lastPosition() {
  try {
    return JSON.parse(localStorage.getItem(LAST_KEY) || 'null')
  } catch {
    return null
  }
}
