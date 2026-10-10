import request, { USE_MOCK, mockResponse } from './request'
import { mockCourses } from '@/mock/data'

/*
 * Pattern for every API module:
 *   1. one function per backend endpoint
 *   2. a mock branch so pages work before the backend is ready
 * The endpoint paths here must match the shared API contract.
 */

// GET /api/courses?page=1&size=10&keyword=
export function getCourses({ page = 1, size = 10, keyword = '' } = {}) {
  if (USE_MOCK) {
    const filtered = mockCourses.filter((c) => c.name.includes(keyword))
    return mockResponse({
      records: filtered.slice((page - 1) * size, page * size),
      total: filtered.length,
    })
  }
  return request.get('/courses', { params: { page, size, keyword } })
}

// GET /api/courses/{id}
export function getCourse(id) {
  if (USE_MOCK) return mockResponse(mockCourses.find((c) => c.id === Number(id)))
  return request.get(`/courses/${id}`)
}

// POST /api/courses
export function createCourse(data) {
  if (USE_MOCK) {
    mockCourses.unshift({ ...data, id: Date.now(), chapterCount: 0, studentCount: 0 })
    return mockResponse(null)
  }
  return request.post('/courses', data)
}

// PUT /api/courses/{id}
export function updateCourse(id, data) {
  if (USE_MOCK) {
    Object.assign(mockCourses.find((c) => c.id === id), data)
    return mockResponse(null)
  }
  return request.put(`/courses/${id}`, data)
}

// DELETE /api/courses/{id}
export function deleteCourse(id) {
  if (USE_MOCK) {
    mockCourses.splice(mockCourses.findIndex((c) => c.id === id), 1)
    return mockResponse(null)
  }
  return request.delete(`/courses/${id}`)
}
