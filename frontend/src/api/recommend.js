import request from './request'

/*
 * 学习推荐 = 小助手的延伸（backend: RecommendController）。分三类：系统补充（补练）、拓展、老师推送。
 * 出题在小助手里；做题用小助手原来的提交接口（api/assistant.js）。
 */

// GET /api/recommendations -> { studentName, remedyCount, extendCount, teacherCount, sets: [...] }
export function getRecommendations() {
  return request.get('/recommendations')
}

// PUT /api/recommendations/extend/{roundId}/saved { saved } -> 拓展做完后保存 / 不保存
export function saveExtend(roundId, saved) {
  return request.put(`/recommendations/extend/${roundId}/saved`, { saved })
}
