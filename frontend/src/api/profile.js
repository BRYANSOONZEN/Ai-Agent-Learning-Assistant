import request from './request'

/*
 * 学生画像 (backend: ProfileController, /api/profiles). No mock branch: the profile is worked out
 * from the real learning records and mastery every time the page opens.
 */

// GET /api/profiles/me -> { studentName, stats, tags, dimensions, days, quizTrend }
export function getMyProfile() {
  return request.get('/profiles/me')
}

// 每章画像 + 课程四维 (docs/模块说明/学生画像-设计.md)
// GET /api/profiles/me/courses -> [{ courseId, name, chaptersDone, chapters }]
export function getProfileCourses() {
  return request.get('/profiles/me/courses')
}
// GET /api/profiles/me/courses/{id} -> { open, chaptersDone, chapters, refDate, dimensions: [{ key, label, value,
//   numerator, denominator, formula, source, reason }], chapterProfiles: [...], days }
export function getCourseProfile(courseId) {
  return request.get(`/profiles/me/courses/${courseId}`)
}
