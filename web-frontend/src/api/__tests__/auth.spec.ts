import { describe, expect, it, vi, beforeEach } from 'vitest'
import type { AuthResponse } from '@/types'

vi.mock('@/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import client from '@/api/client'
import { authApi } from '@/api/auth'

const mockedGet = vi.mocked(client.get)
const mockedPost = vi.mocked(client.post)
const mockedPut = vi.mocked(client.put)

function authOk(): AuthResponse {
  return { success: true } as AuthResponse
}

/** Axios-style rejection carrying `response.data`. */
function rejectWithBody(data: unknown) {
  return Promise.reject({ response: { status: 400, data } })
}

beforeEach(() => {
  mockedGet.mockReset()
  mockedPost.mockReset()
  mockedPut.mockReset()
})

describe('authApi 登录/注册/登出 — POST body 形状（T-F2 TH5）', () => {
  it('login and register send credentials as JSON bodies', async () => {
    mockedPost.mockResolvedValue({ data: authOk() })
    await authApi.login('bob', 'secret')
    expect(mockedPost).toHaveBeenCalledWith('/auth/login', {
      username: 'bob',
      password: 'secret',
    })

    await authApi.register('bob', 'secret')
    expect(mockedPost).toHaveBeenLastCalledWith('/auth/register', {
      username: 'bob',
      password: 'secret',
    })
  })

  it('changePassword posts both passwords', async () => {
    mockedPost.mockResolvedValue({ data: authOk() })
    await authApi.changePassword('old', 'new')
    expect(mockedPost).toHaveBeenCalledWith('/auth/change-password', {
      oldPassword: 'old',
      newPassword: 'new',
    })
  })

  it('logout posts without a body and resolves void', async () => {
    mockedPost.mockResolvedValue({ data: undefined })
    await expect(authApi.logout()).resolves.toBeUndefined()
    expect(mockedPost).toHaveBeenCalledWith('/auth/logout')
  })
})

describe('authApi.changePassword — 400 错误体透出（T-F2）', () => {
  it('surfaces the backend body instead of throwing on 400', async () => {
    const body = { success: false, message: 'Wrong old password' }
    mockedPost.mockReturnValue(rejectWithBody(body))
    await expect(authApi.changePassword('x', 'y')).resolves.toEqual(body)
  })

  it('rethrows errors that carry no response body (network outage)', async () => {
    mockedPost.mockRejectedValue(new Error('offline'))
    await expect(authApi.changePassword('x', 'y')).rejects.toThrow('offline')
  })
})

describe('authApi 状态与会话（T-F2）', () => {
  it('status GETs /auth/status', async () => {
    mockedGet.mockResolvedValue({
      data: { authRequired: true, authenticated: true, ehSessionValid: false },
    })
    const res = await authApi.status()
    expect(res.authRequired).toBe(true)
    expect(mockedGet).toHaveBeenCalledWith('/auth/status')
  })

  it('ehSession GETs /auth/eh-session', async () => {
    mockedGet.mockResolvedValue({
      data: { signedIn: true, expired: false, gallerySite: 1, cookies: [] },
    })
    await authApi.ehSession()
    expect(mockedGet).toHaveBeenCalledWith('/auth/eh-session')
  })

  it('ehLogout posts /auth/eh-logout and resolves the message envelope', async () => {
    mockedPost.mockResolvedValue({ data: { success: true, message: 'bye' } })
    await expect(authApi.ehLogout()).resolves.toEqual({ success: true, message: 'bye' })
    expect(mockedPost).toHaveBeenCalledWith('/auth/eh-logout')
  })
})

describe('authApi.setEhSite — 站点切换与拒绝透出（T-F2）', () => {
  it('PUTs the target site as the JSON body', async () => {
    mockedPut.mockResolvedValue({ data: { success: true, message: '' } })
    await authApi.setEhSite(1)
    expect(mockedPut).toHaveBeenCalledWith('/auth/eh-site', { gallerySite: 1 })
  })

  it('surfaces the same-shape body when the switch is rejected with 400', async () => {
    const body = { success: false, message: 'No exhentai access' }
    mockedPut.mockReturnValue(rejectWithBody(body))
    await expect(authApi.setEhSite(1)).resolves.toEqual(body)
  })

  it('rethrows non-HTTP failures untouched', async () => {
    mockedPut.mockRejectedValue(new Error('boom'))
    await expect(authApi.setEhSite(0)).rejects.toThrow('boom')
  })
})
