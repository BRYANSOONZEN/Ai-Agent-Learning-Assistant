import request from './request'

/*
 * Teacher-side dialogue topics (backend: TeacherTopicController).
 * No mock branch on purpose: this page always talks to the real backend,
 * even when VITE_USE_MOCK=true, because the point is to see the real AI result.
 */

// GET /api/teacher/topics/ai-status -> { available, model }
export function getAiStatus() {
  return request.get('/teacher/topics/ai-status')
}

// POST /api/teacher/topics/draft  { question, answer, sourceRef, sourceText }
// The model may "think" for a while, and the backend retries up to twice (3 x 30 s + waits),
// so allow up to 110 s instead of the default 15 s.
export function draftTopic(data) {
  return request.post('/teacher/topics/draft', data, { timeout: 110000 })
}

// GET /api/teacher/topics?chapterId=1&itemId=  — itemId: only that AI dialogue item's topics
export function getTopics(chapterId = 1, itemId) {
  return request.get('/teacher/topics', { params: { chapterId, itemId } })
}

// POST /api/teacher/topics?chapterId=1&itemId=  — itemId optional (the old standalone page has none)
export function createTopic(data, chapterId = 1, itemId) {
  return request.post('/teacher/topics', data, { params: { chapterId, itemId } })
}

// PUT /api/teacher/topics/{id}
export function updateTopic(id, data) {
  return request.put(`/teacher/topics/${id}`, data)
}

// DELETE /api/teacher/topics/{id}
export function deleteTopic(id) {
  return request.delete(`/teacher/topics/${id}`)
}
