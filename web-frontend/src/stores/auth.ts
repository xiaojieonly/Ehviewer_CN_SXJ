import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const username = ref<string | null>(localStorage.getItem('username'))

  const isAuthenticated = computed(() => !!token.value)

  async function login(user: string, pass: string) {
    const res = await authApi.login(user, pass)
    if (res.success && res.token) {
      token.value = res.token
      username.value = res.username ?? user
      localStorage.setItem('token', res.token)
      localStorage.setItem('username', username.value!)
    }
    return res
  }

  async function register(user: string, pass: string) {
    const res = await authApi.register(user, pass)
    if (res.success && res.token) {
      token.value = res.token
      username.value = res.username ?? user
      localStorage.setItem('token', res.token)
      localStorage.setItem('username', username.value!)
    }
    return res
  }

  async function logout() {
    await authApi.logout().catch(() => {})
    token.value = null
    username.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('username')
  }

  return { token, username, isAuthenticated, login, register, logout }
})
