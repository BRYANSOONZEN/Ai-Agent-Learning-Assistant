import request from './request'

// GET /api/ai/logs?limit=100 -> { totals, records }
// Always talks to the real backend (no mock): these are real model calls.
export function getAiLogs(limit = 100) {
  return request.get('/ai/logs', { params: { limit } })
}
