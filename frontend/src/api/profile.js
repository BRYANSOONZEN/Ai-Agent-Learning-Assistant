import request, { USE_MOCK, mockResponse } from './request'
import { mockProfile } from '@/mock/data'

// GET /api/students/{id}/profile
export function getStudentProfile(studentId) {
  if (USE_MOCK) return mockResponse(mockProfile)
  return request.get(`/students/${studentId}/profile`)
}
