import request, { USE_MOCK, mockResponse } from './request'
import { mockUsers } from '@/mock/data'

// POST /api/auth/login { username, password } -> { token, expiresIn, user }
// The role is NOT sent: it comes from the account in the database.
export function login({ username, password, remember }) {
  if (USE_MOCK) {
    const role = username.startsWith('teacher') ? 'teacher' : username === 'admin' ? 'admin' : 'student'
    return mockResponse({ token: 'mock-token', expiresIn: 86400, user: { ...mockUsers[role], username } })
  }
  return request.post('/auth/login', { username, password, remember: !!remember }, { silent: true })
}

// POST /api/auth/register { username, password, name } -> same as login; always a student
export function register({ username, password, name }) {
  return request.post('/auth/register', { username, password, name }, { silent: true })
}

// GET /api/auth/demo-accounts -> demo accounts for one-click switching ([] when switched off in application.yml)
export function getDemoAccounts() {
  return request.get('/auth/demo-accounts', { silent: true })
}

// POST /api/auth/demo-login { username } -> same as login (only the listed demo accounts, no password)
export function demoLogin(username) {
  return request.post('/auth/demo-login', { username }, { silent: true })
}

// GET /api/auth/me -> { id, username, name, role }
export function getMe() {
  return request.get('/auth/me')
}

// POST /api/auth/logout -> the token stops working at once (on every device)
export function logoutApi() {
  return request.post('/auth/logout', null, { silent: true })
}

// PUT /api/auth/password { oldPassword, newPassword } -> a new login { token, user }; other devices are signed out
export function changePassword(data) {
  return request.put('/auth/password', data)
}

// ---- admin: user management ----

export function getUsers({ page = 1, size = 10, role, keyword } = {}) {
  return request.get('/admin/users', { params: { page, size, role: role || undefined, keyword: keyword || undefined } })
}

export function getUserCounts() {
  return request.get('/admin/users/counts')
}

export function createUser(data) {
  return request.post('/admin/users', data)
}

export function setUserStatus(id, status) {
  return request.put(`/admin/users/${id}/status`, { status })
}

export function resetUserPassword(id, password) {
  return request.put(`/admin/users/${id}/password`, { password })
}
