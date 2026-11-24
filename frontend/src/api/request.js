import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'

export const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

/*
 * All backend responses use one format:
 *   { code: 200, message: 'ok', data: ... }
 * This interceptor returns `data` directly, and shows an error message otherwise.
 */
const request = axios.create({ baseURL: '/api', timeout: 15000 })

request.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const { code, message, data } = response.data
    if (code === 200) return data
    // `silent: true` in the request config: the page shows the error itself (e.g. under the login form)
    if (!response.config.silent) ElMessage.error(message || '请求失败')
    const err = new Error(message || '请求失败')
    err.code = code
    return Promise.reject(err)
  },
  (error) => {
    // the backend's auth check answers 401 / 403 with the same { code, message } body
    const message = error.response?.data?.message
    if (error.config?.silent) {
      error.message = message || (error.response ? error.message : '连不上服务器，请检查后端是否已启动')
      return Promise.reject(error)
    }
    if (error.response?.status === 401) {
      useUserStore().logout()
      const current = router.currentRoute.value
      if (current.name !== 'login') {
        router.push({ name: 'login', query: { redirect: current.fullPath } })
      }
      ElMessage.error(message || '登录已过期，请重新登录')
    } else if (message) {
      ElMessage.error(message)
    } else if (!error.response) {
      ElMessage.error('连不上服务器，请检查后端是否已启动')
    } else {
      ElMessage.error(error.message || '网络错误')
    }
    return Promise.reject(error)
  },
)

// <video src> / <a href> can't send the Authorization header, so material files carry the token in the URL
export function withToken(url) {
  const { token } = useUserStore()
  if (!url || !token) return url
  return url + (url.includes('?') ? '&' : '?') + 'token=' + encodeURIComponent(token)
}

// Simulates network delay so mock data behaves like a real API
export function mockResponse(data, delay = 300) {
  return new Promise((resolve) => setTimeout(() => resolve(structuredClone(data)), delay))
}

export default request
