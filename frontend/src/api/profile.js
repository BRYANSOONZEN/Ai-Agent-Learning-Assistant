import request from './request'

/*
 * 学生画像 (backend: ProfileController, /api/profiles). No mock branch: the profile is worked out
 * from the real learning records and mastery every time the page opens.
 */

// GET /api/profiles/me -> { studentName, stats, tags, dimensions, days, quizTrend }
export function getMyProfile() {
  return request.get('/profiles/me')
}
