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
