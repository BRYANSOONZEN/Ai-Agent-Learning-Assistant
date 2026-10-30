import request from './request'

/*
 * 学情诊断 (backend: DiagnosisController, /api/diagnosis). No mock branch: it reads the real records.
 * Mastery is updated when a quiz is submitted; these calls only read it.
 */

// GET /api/diagnosis/me -> { studentName, summary, weakPoints, courses: [{ chapters: [{ points }] }], dialogueTopics }
export function getMyDiagnosis() {
  return request.get('/diagnosis/me')
}

// GET /api/diagnosis/courses/{id} -> { courseName, studentCount, points: [{ avgMastery, weakStudents, misconceptions }] }
export function getCourseDiagnosis(courseId) {
  return request.get(`/diagnosis/courses/${courseId}`)
}
