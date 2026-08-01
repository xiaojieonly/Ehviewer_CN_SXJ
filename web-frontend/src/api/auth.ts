import client from './client'
import type { AuthResponse, AuthStatusResponse } from '@/types'

export interface AuthStatusResult extends AuthStatusResponse {
  authRequired: boolean
  /** E-Hentai session state from the unified cookie store (backend defaults false). */
  ehSessionValid?: boolean
  ehSessionExpired?: boolean
}

export const authApi = {
  async login(username: string, password: string): Promise<AuthResponse> {
    const { data } = await client.post('/auth/login', { username, password })
    return data
  },

  async register(username: string, password: string): Promise<AuthResponse> {
    const { data } = await client.post('/auth/register', { username, password })
    return data
  },

  async changePassword(oldPassword: string, newPassword: string): Promise<AuthResponse> {
    try {
      const { data } = await client.post('/auth/change-password', { oldPassword, newPassword })
      return data
    } catch (error) {
      // 401 is handled by the client interceptor (redirects to /login). The
      // backend answers 400 with `{success: false, message}` for wrong old
      // password / invalid length / auth disabled — surface that body instead
      // of throwing so the view can show the exact message.
      const body = (error as { response?: { data?: AuthResponse } }).response?.data
      if (body) return body
      throw error
    }
  },

  async status(): Promise<AuthStatusResult> {
    const { data } = await client.get('/auth/status')
    return data
  },

  async logout(): Promise<void> {
    await client.post('/auth/logout')
  },
}
