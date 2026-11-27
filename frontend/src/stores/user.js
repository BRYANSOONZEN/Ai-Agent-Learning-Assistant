import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

const STORAGE_KEY = 'se-platform-user'

export const useUserStore = defineStore('user', () => {
  const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null')
  const token = ref(saved?.token || '')
  const user = ref(saved?.user || null)

  const isLoggedIn = computed(() => !!token.value)
  const role = computed(() => user.value?.role || '')

  function setLogin(data) {
    token.value = data.token
    user.value = data.user
    localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
  }

  // a renewed token from the backend (response header X-Refresh-Token)
  function setToken(t) {
    token.value = t
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...(saved || {}), token: t, user: user.value }))
  }

  // after changing the password: the user is no longer asked to change it
  function setUser(u) {
    user.value = u
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ token: token.value, user: u }))
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem(STORAGE_KEY)
  }

  return { token, user, isLoggedIn, role, setLogin, setToken, setUser, logout }
})
