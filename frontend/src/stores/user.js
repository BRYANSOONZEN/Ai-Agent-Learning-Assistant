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

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem(STORAGE_KEY)
  }

  return { token, user, isLoggedIn, role, setLogin, logout }
})
