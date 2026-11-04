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
  // temporary until the backend has login: act as another demo student (see backend DemoIdentity)
  let demoStudent = null
  try {
    demoStudent = localStorage.getItem('se-demo-student-id')
  } catch {
    // storage unavailable: stay the default demo student
  }
  if (demoStudent && userStore.role === 'student') {
    config.headers['X-Demo-Student-Id'] = demoStudent
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const { code, message, data } = response.data
    if (code === 200) return data
    ElMessage.error(message || '请求失败')
    return Promise.reject(new Error(message))
  },
  (error) => {
    if (error.response?.status === 401) {
      useUserStore().logout()
      router.push('/login')
      ElMessage.error('登录已过期，请重新登录')
    } else {
      ElMessage.error(error.message || '网络错误')
    }
    return Promise.reject(error)
  },
)

// Simulates network delay so mock data behaves like a real API
export function mockResponse(data, delay = 300) {
  return new Promise((resolve) => setTimeout(() => resolve(structuredClone(data)), delay))
}

export default request
