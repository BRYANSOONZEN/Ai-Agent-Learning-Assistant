import request from './request'

/*
 * Student AI dialogue (backend: DialogueController).
 * Like teacherTopic.js there is no mock branch: the point is to see the real AI.
 * Every call except getOpening returns the whole DialogueView
 *   { sessionId, status, currentIdx, topics: [{title, result}], messages: [{role, content, source}], summary }
 */

const AI_TIMEOUT = 110000 // model may think for a while, and the backend retries up to twice

// GET /api/dialogue/chapters/{chapterId}?itemId= -> { chapterId, topicTitles }
// itemId: only that AI 对话 item's topics; leave it out for the whole chapter
export function getOpening(chapterId = 1, itemId) {
  return request.get(`/dialogue/chapters/${chapterId}`, { params: { itemId } })
}

// POST /api/dialogue/sessions?chapterId=1&itemId=
export function startDialogue(chapterId = 1, itemId) {
  return request.post('/dialogue/sessions', null, { params: { chapterId, itemId } })
}

// GET /api/dialogue/sessions/{id}
export function getDialogue(id) {
  return request.get(`/dialogue/sessions/${id}`)
}

// POST /api/dialogue/sessions/{id}/messages { text }
export function sendMessage(id, text) {
  return request.post(`/dialogue/sessions/${id}/messages`, { text }, { timeout: AI_TIMEOUT })
}

// POST /api/dialogue/sessions/{id}/stuck
export function sendStuck(id) {
  return request.post(`/dialogue/sessions/${id}/stuck`)
}

// POST /api/dialogue/sessions/{id}/resume
export function resumeDialogue(id) {
  return request.post(`/dialogue/sessions/${id}/resume`)
}
