import request from './request'

/*
 * Teacher-side course building: course -> chapter -> items (video / document / exercise / dialogue),
 * plus the chapter's knowledge points and the exercises inside exercise items.
 * (Dialogue topics are in teacherTopic.js.)
 * No mock branch on purpose, like teacherTopic.js: these pages always save to the real backend.
 */

// ---------- courses ----------

// GET /api/courses/mine?page=&size=&keyword=  — only the logged-in teacher's own courses
export function getCourses({ page = 1, size = 10, keyword = '' } = {}) {
  return request.get('/courses/mine', { params: { page, size, keyword } })
}

// GET /api/courses/mine/{id}  — 403 if the course belongs to another teacher
export function getCourse(id) {
  return request.get(`/courses/mine/${id}`)
}

// POST /api/courses  { name, description, status }
export function createCourse(data) {
  return request.post('/courses', data)
}

export function updateCourse(id, data) {
  return request.put(`/courses/${id}`, data)
}

export function deleteCourse(id) {
  return request.delete(`/courses/${id}`)
}

// ---------- chapters ----------

// GET /api/courses/{courseId}/chapters
export function getChapters(courseId) {
  return request.get(`/courses/${courseId}/chapters`)
}

// PUT /api/courses/{courseId}/chapters/order  { ids: [...] }
export function reorderChapters(courseId, ids) {
  return request.put(`/courses/${courseId}/chapters/order`, { ids })
}

export function getChapter(id) {
  return request.get(`/chapters/${id}`)
}

// POST /api/chapters  { courseId, title, intro, videoMaterialId, status }
export function createChapter(data) {
  return request.post('/chapters', data)
}

export function updateChapter(id, data) {
  return request.put(`/chapters/${id}`, data)
}

// Change some fields of a chapter and keep the rest, e.g. patchChapter(ch, { status: 'published' })
export function patchChapter(ch, patch) {
  const { title, intro, status } = ch
  return updateChapter(ch.id, { title, intro, status, ...patch })
}

export function deleteChapter(id) {
  return request.delete(`/chapters/${id}`)
}

// ---------- chapter items (the chapter's content, in learning order) ----------

// GET /api/chapters/{chapterId}/items
export function getItems(chapterId) {
  return request.get(`/chapters/${chapterId}/items`)
}

// POST /api/chapter-items  { chapterId, type: video|document|exercise|dialogue, title, description }
export function createItem(data) {
  return request.post('/chapter-items', data)
}

// PUT /api/chapter-items/{id}  { title, description }
export function updateItem(id, data) {
  return request.put(`/chapter-items/${id}`, data)
}

// also deletes the item's file, exercises or topics
export function deleteItem(id) {
  return request.delete(`/chapter-items/${id}`)
}

// PUT /api/chapters/{chapterId}/items/order  { ids: [...] }
export function reorderItems(chapterId, ids) {
  return request.put(`/chapters/${chapterId}/items/order`, { ids })
}

// POST /api/chapter-items/{id}/file  multipart: file — upload or replace a video / document item's file.
// Videos can be large, so allow up to 10 minutes instead of the default 15 s.
export function uploadItemFile(id, file, onProgress) {
  const form = new FormData()
  form.append('file', file)
  return request.post(`/chapter-items/${id}/file`, form, {
    timeout: 600000,
    onUploadProgress: (e) => onProgress?.(e.total ? Math.round((e.loaded / e.total) * 100) : 0),
  })
}

export function removeItemFile(id) {
  return request.delete(`/chapter-items/${id}/file`)
}

// ---------- knowledge points ----------

export function getKnowledgePoints(chapterId) {
  return request.get(`/chapters/${chapterId}/knowledge-points`)
}

// POST /api/knowledge-points  { chapterId, name, content, difficulty, isKey, isDifficult }
export function createKnowledgePoint(data) {
  return request.post('/knowledge-points', data)
}

export function updateKnowledgePoint(id, data) {
  return request.put(`/knowledge-points/${id}`, data)
}

export function deleteKnowledgePoint(id) {
  return request.delete(`/knowledge-points/${id}`)
}

// ---------- exercises ----------

// GET /api/chapters/{chapterId}/exercises?itemId=  — itemId: only that exercise item's questions
export function getExercises(chapterId, itemId) {
  return request.get(`/chapters/${chapterId}/exercises`, { params: { itemId } })
}

// POST /api/exercises  { chapterId, itemId, stem, options, answer, analysis, optionNotes, difficulty, knowledgePointIds }
export function createExercise(data) {
  return request.post('/exercises', data)
}

export function updateExercise(id, data) {
  return request.put(`/exercises/${id}`, data)
}

export function deleteExercise(id) {
  return request.delete(`/exercises/${id}`)
}
