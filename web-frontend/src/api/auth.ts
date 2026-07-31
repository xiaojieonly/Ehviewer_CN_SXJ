import client from './client'
import type { AuthResponse, AuthStatusResponse } from '@/types'

export interface AuthStatusResult extends AuthStatusResponse {
  authRequired: boolean
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

  async status(): Promise<AuthStatusResult> {
    const { data } = await client.get('/auth/status')
    return data
  },

  async logout(): Promise<void> {
    await client.post('/auth/logout')
  },
}
