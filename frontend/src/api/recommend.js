import request from './request'

/*
 * 学习推荐 (backend: RecommendController, /api/recommendations). No mock branch.
 * The list is picked from the weakest knowledge points every time it is opened; answering a question
 * here is recorded like a quiz and updates mastery, so reloading gives a new list.
 */

// GET /api/recommendations -> { weakCount, doneCount, points: [{ name, mastery, misconception, content, review, exercises }] }
export function getRecommendations() {
  return request.get('/recommendations')
}

// POST /api/recommendations/practice { answers: { exerciseId: 'A' } } -> same shape as a quiz result
export function practiceRecommended(answers) {
  return request.post('/recommendations/practice', { answers })
}

// POST /api/recommendations/{id}/feedback { status: 'done' | 'dismissed' }
export function recommendationFeedback(id, status) {
  return request.post(`/recommendations/${id}/feedback`, { status })
}
