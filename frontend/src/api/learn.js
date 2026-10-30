import request from './request'

/*
 * Student learning (backend: LearnController, /api/learn).
 * Only published courses and chapters come back; questions have no answers until submitted.
 * No mock branch, like dialogue.js: these pages show what teachers really built.
 * The AI dialogue inside a chapter uses dialogue.js with the item id.
 */

// GET /api/learn/courses?page=&size=&keyword= -> { records, total }
export function getLearnCourses({ page = 1, size = 12, keyword = '' } = {}) {
  return request.get('/learn/courses', { params: { page, size, keyword } })
}

// GET /api/learn/courses/{id} -> { course, chapters: [{ id, title, intro, items: [...] }], doneItemIds }
export function getLearnCourse(id) {
  return request.get(`/learn/courses/${id}`)
}

// GET /api/learn/items/{id}/questions -> [{ id, stem, options }]
export function getQuestions(itemId) {
  return request.get(`/learn/items/${itemId}/questions`)
}

// POST /api/learn/items/{id}/submit { answers: { [questionId]: 'A' } }
//   -> { total, correct, results: [{ id, stem, options, chosen, answer, correct, analysis, note }] }
export function submitQuiz(itemId, answers) {
  return request.post(`/learn/items/${itemId}/submit`, { answers })
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
