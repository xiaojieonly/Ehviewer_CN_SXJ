import client from './client'
import type { AuthResponse, AuthStatusResponse } from '@/types'

export interface AuthStatusResult extends AuthStatusResponse {
  authRequired: boolean
  /** E-Hentai session state from the unified cookie store (backend defaults false). */
  ehSessionValid?: boolean
  ehSessionExpired?: boolean
}

/** POST /auth/eh-logout — tear down the stored E-Hentai session. */
export interface EhLogoutResponse {
  success: boolean
  message: string
}

/** One E-Hentai identity cookie stored server-side (ipb_* / igneous / sk). */
export interface EhSessionCookie {
  name: string
  value: string
  domain: string
  expiresAt: number
}

/** PUT /auth/eh-site — switch gallery site. 400 carries the same shape with success=false. */
export interface EhSiteResponse {
  success: boolean
  message: string
}

/** GET /auth/eh-session — current E-Hentai session state. */
export interface EhSessionResponse {
  signedIn: boolean
  expired: boolean
  /** 0 = e-hentai, 1 = exhentai. */
  gallerySite: number
  cookies: EhSessionCookie[]
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

  async ehLogout(): Promise<EhLogoutResponse> {
    const { data } = await client.post('/auth/eh-logout')
    return data
  },

  async ehSession(): Promise<EhSessionResponse> {
    const { data } = await client.get('/auth/eh-session')
    return data
  },

  /** Switch the gallery site between e-hentai (0) and exhentai (1). */
  async setEhSite(gallerySite: number): Promise<EhSiteResponse> {
    try {
      const { data } = await client.put('/auth/eh-site', { gallerySite })
      return data
    } catch (error) {
      // Rejected site switches answer 400 with the same shape — surface the
      // body so the view can show the exact message instead of throwing.
      const body = (error as { response?: { data?: EhSiteResponse } }).response?.data
      if (body) return body
      throw error
    }
  },
}
