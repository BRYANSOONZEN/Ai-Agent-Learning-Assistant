import request from './request'

/*
 * 干预 (backend: InterventionController). No mock branch.
 * Teacher: the board scans for new alerts each time it is opened (two layers: AI dialogue = concept,
 * exercises = application) and returns open alerts with the system's suggestion, plus running / done
 * interventions. Student: what the teacher assigned; exercises are answered with practiceRecommended.
 */

// GET /api/interventions/board?courseId= -> { counts, alerts: [{ type, evidence, suggestion }], interventions }
export function getInterventionBoard(courseId) {
  return request.get('/interventions/board', { params: { courseId } })
}

// GET /api/interventions/open-count -> number of open alerts in my courses (sidebar badge)
export function getOpenAlertCount() {
  return request.get('/interventions/open-count')
}

// POST /api/interventions/{id}/review { approve, exerciseIds?, note? }
export function reviewIntervention(id, data) {
  return request.post(`/interventions/${id}/review`, data)
}

// PUT /api/alerts/{id} { status: 'ignored' }
export function ignoreAlert(id) {
  return request.put(`/alerts/${id}`, { status: 'ignored' })
}

// GET /api/interventions/mine -> { running: [...], done: [...] }
export function getMyAssignments() {
  return request.get('/interventions/mine')
}
