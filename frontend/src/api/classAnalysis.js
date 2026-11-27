import request from './request'

/*
 * 老师干预：全班分析 (backend: ClassAnalysisController, docs/模块说明/老师干预-全班分析.md).
 * Each exercise item is analysed at its deadline (or once everyone submitted), only if >= 80% submitted.
 * Rule 1: a question wrong for > 80% -> teacher checks it first. Rule 2: key point x assessment type wrong > 50%
 * -> card with a plan by assessment type -> supplementary explanation + new questions -> pushed to those who got it wrong.
 */

// GET /api/class-analysis?courseId= -> { courseId, courseName, minSubmitRatio, items: [{ itemId, title, chapterTitle,
//   dueAt, students, submitted, status: waiting|void|done|nodue, missing, groups: [{ name, issues }], pushes }] }
export function getClassAnalysis(courseId) {
  return request.get('/class-analysis', { params: { courseId } })
}

// rule 1: the teacher checked the question: ok (counts again) / fixed / void (removed, mastery recomputed)
export function questionOk(issueId) {
  return request.post(`/class-issues/${issueId}/ok`)
}
export function questionFixed(issueId) {
  return request.post(`/class-issues/${issueId}/fixed`)
}
export function questionVoid(issueId) {
  return request.post(`/class-issues/${issueId}/void`)
}
export function ignoreIssue(issueId) {
  return request.post(`/class-issues/${issueId}/ignore`)
}

// rule 2: handle cards -> draft (AI explanation optional + 3 new questions, ~20 s)
export function createPush(issueIds, aiExplanation) {
  return request.post('/class-pushes', { issueIds, aiExplanation }, { timeout: 180000 })
}
export function updatePush(id, data) {
  return request.put(`/class-pushes/${id}`, data)
}
export function regeneratePush(id) {
  return request.post(`/class-pushes/${id}/regenerate`, null, { timeout: 180000 })
}
export function sendPush(id) {
  return request.post(`/class-pushes/${id}/push`)
}

// analysis void (too few submitted): remind those who did not submit / extend the deadline
export function remindMissing(itemId, message) {
  return request.post(`/class-analysis/items/${itemId}/remind`, { message })
}
export function extendDeadline(itemId, dueAt) {
  return request.post(`/class-analysis/items/${itemId}/extend`, { dueAt })
}

// GET /api/class-analysis/open-count -> number for the sidebar badge (void analyses + cards waiting for the teacher)
export function getOpenIssueCount() {
  return request.get('/class-analysis/open-count')
}
