import request from './request'

/*
 * Student learning (backend: LearnController, /api/learn).
 * Only published courses and chapters come back; questions have no answers until submitted.
 * No mock branch, like dialogue.js: these pages show what teachers really built.
 * The AI dialogue inside a chapter uses dialogue.js with the item id.
 */

// GET /api/learn/courses?page=&size=&keyword= -> { records, total }
// GET /api/learn/enrollments -> ids of the courses I joined
export function getEnrollments() {
  return request.get('/learn/enrollments')
}

// POST /api/learn/courses/{id}/join -> join a published course (needed before learning it)
export function joinCourse(id) {
  return request.post(`/learn/courses/${id}/join`)
}

export function getLearnCourses({ page = 1, size = 12, keyword = '' } = {}) {
  return request.get('/learn/courses', { params: { page, size, keyword } })
}

// GET /api/learn/courses/{id} -> { course, chapters: [{ id, title, intro, items: [...] }], doneItemIds }
export function getLearnCourse(id, silent = false) {
  return request.get(`/learn/courses/${id}`, { silent })
}

// GET /api/learn/items/{id}/questions -> [{ id, stem, options }]
export function getQuestions(itemId) {
  return request.get(`/learn/items/${itemId}/questions`)
}

// POST /api/learn/items/{id}/submit { answers: { [questionId]: 'A' } }
//   -> { total, correct, results: [{ id, stem, options, chosen, answer, correct, analysis, note }] }
export function submitQuiz(itemId, answers) {
  // short answers are judged twice by the model, so allow more time
  return request.post(`/learn/items/${itemId}/submit`, { answers }, { timeout: 120000 })
}

// ---------- 学习记录（backend: RecordController） ----------

// POST /api/learn/events { type: 'view_item' | 'finish_item', itemId, durationSec }
export function logEvent(type, itemId, durationSec) {
  return request.post('/learn/events', { type, itemId, durationSec })
}

// GET /api/learn/records -> { summary, courses, quizzes, dialogues, timeline }
export function getRecords() {
  return request.get('/learn/records')
}

// GET /api/learn/deadlines -> exercises / AI dialogues with a deadline that this student has not done yet,
// [{ itemId, courseId, courseName, chapterId, chapterTitle, type, title, dueAt, allowLate, state, minutesLeft }]
export function getMyDeadlines() {
  return request.get('/learn/deadlines')
}

// ---------- 视频、文档的学习进度（backend: ProgressController）：学完才解锁下一项 ----------

// POST /api/learn/items/{id}/video-progress { duration, ranges: [[s, e], ...], position }
//   -> { progress, watchedSec, durationSec, ranges, lastPosition, completed, videoPass }
// Completion P = Σ(e − s) ÷ duration over merged ranges; P ≥ 90% = finished.
export function reportVideo(itemId, body) {
  return request.post(`/learn/items/${itemId}/video-progress`, body)
}

// POST /api/learn/items/{id}/reading { seconds } — seconds the document page was in front since the last report
export function reportReading(itemId, seconds) {
  return request.post(`/learn/items/${itemId}/reading`, { seconds })
}

// POST /api/learn/items/{id}/finish-reading — the 我已读完 button; refused before 30 seconds of reading
export function finishReading(itemId) {
  return request.post(`/learn/items/${itemId}/finish-reading`)
}

// GET /api/learn/chapters/{id}/kp-map -> the knowledge point map the teacher confirmed (empty rows until then)
export function getChapterKpMap(chapterId) {
  return request.get(`/learn/chapters/${chapterId}/kp-map`)
}

// ---------- 费曼式 AI 对话：讲给小林听 ----------

// GET /api/learn/items/{id}/feynman -> { enabled, kpCount, names } (not enabled: the old dialogue is used)
export function getFeynman(itemId) {
  return request.get(`/learn/items/${itemId}/feynman`)
}

// start, or continue the open one -> { id, status, kpIdx, waitingCheck, kps: [...], messages: [{ role, content, kpIdx }] }
// a finished dialogue comes back as it was (with its summary); again = true starts a new attempt
export function startFeynman(itemId, again = false) {
  return request.post(`/learn/items/${itemId}/feynman/start`, null, { params: again ? { again: true } : {} })
}

export function feynmanReply(sessionId, text) {
  return request.post(`/learn/feynman/${sessionId}/reply`, { text }, { timeout: 120000 })
}
