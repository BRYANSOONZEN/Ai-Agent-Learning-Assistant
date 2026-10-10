import request, { USE_MOCK, mockResponse } from './request'
import { mockUsers } from '@/mock/data'

export function login({ username, role }) {
  if (USE_MOCK) {
    return mockResponse({ token: 'mock-token', user: { ...mockUsers[role], username } })
  }
  return request.post('/auth/login', { username, role })
}
