import request from './request'

/*
 * 个人学习助手 (backend: AssistantController, docs/计划-个人学习助手.md). No mock branch.
 * The backend first scans this student, so interventions that run automatically show up right away.
 */

// GET /api/assistant/summary -> { chapters: [{ courseId, chapterId, title, done, total, completed,
//   nextItemId, nextItemTitle, dialogueItemId, report: { headline, good, weak, points: [...] } }], focusChapterId }
export function getAssistantSummary() {
  return request.get('/assistant/summary')
}

// ---- automatic intervention (docs/模块说明/小助手-自动干预与聊天.md, section 4) ----
// POST /api/assistant/remedy/{kpId}/start -> { knowledgePointId, name, round, maxRounds, escalated, questions, text }
// The questions are generated on the spot from this student's own mistakes (takes ~10-20 s).
export function startRemedy(kpId) {
  return request.post(`/assistant/remedy/${kpId}/start`, null, { timeout: 120000 })
}

// POST /api/assistant/remedy/{kpId}/submit { answers: { id: 'A' } }
//   -> { round, results, score, change: { after, level, remedyStatus }, recovered, escalated, canContinue, text }
export function submitRemedy(kpId, answers) {
  return request.post(`/assistant/remedy/${kpId}/submit`, { answers })
}

// ---- free chat (section 3) ----
// GET /api/assistant/chat?chapterId= -> [{ role: 'student' | 'assistant', content, at }]
export function getChat(chapterId) {
  return request.get('/assistant/chat', { params: { chapterId } })
}

// POST /api/assistant/chat { chapterId, message } -> { reply, stage, blocked, latencyMs, costCny }
export function sendChat(chapterId, message) {
  return request.post('/assistant/chat', { chapterId, message }, { timeout: 60000 })
}
