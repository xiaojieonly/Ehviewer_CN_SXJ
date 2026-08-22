import { describe, expect, it, vi, beforeEach } from 'vitest'

vi.mock('@/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import client from '@/api/client'
import { favoriteApi } from '@/api/favorite'

const mockedGet = vi.mocked(client.get)
const mockedPost = vi.mocked(client.post)
const mockedDelete = vi.mocked(client.delete)

/** Axios `config.params` passed to client.get on call #n (0-based). */
function paramsOf(call = 0): Record<string, string> {
  const cfg = mockedGet.mock.calls[call][1] as { params?: Record<string, string> } | undefined
  return cfg?.params ?? {}
}

beforeEach(() => {
  mockedGet.mockReset().mockResolvedValue({
    data: { favorites: [], totalPages: 0, currentPage: 1 },
  })
  mockedPost.mockReset().mockResolvedValue({ data: { success: true } })
  mockedDelete.mockReset().mockResolvedValue({ data: { success: true } })
})

describe('favoriteApi.listFavorites — 参数序列化（T-F2 TH5）', () => {
  it('always sends slot/page with their defaults', async () => {
    await favoriteApi.listFavorites()
    expect(mockedGet.mock.calls[0][0]).toBe('/favorite/list')
    const p = paramsOf()
    expect(p.slot).toBe('0')
    expect(p.page).toBe('1')
    expect('q' in p).toBe(false)
    expect('regex' in p).toBe(false)
  })

  it("sends regex='true' only alongside a real keyword and the flag", async () => {
    await favoriteApi.listFavorites(3, 2, '^hentai$', true)
    let p = paramsOf(0)
    expect(p.slot).toBe('3')
    expect(p.page).toBe('2')
    expect(p.q).toBe('^hentai$')
    expect(p.regex).toBe('true')

    await favoriteApi.listFavorites(0, 1, 'kw', false)
    p = paramsOf(1)
    expect(p.q).toBe('kw')
    expect(p.regex).toBeUndefined()
  })

  it('drops empty-string / null keywords entirely', async () => {
    await favoriteApi.listFavorites(0, 1, '')
    expect('q' in paramsOf()).toBe(false)

    await favoriteApi.listFavorites(0, 1, null)
    expect('q' in paramsOf(1)).toBe(false)
  })
})

describe('favoriteApi.addFavorite — POST body 形状（T-F2）', () => {
  it('omits slot when no folder is targeted (backend DTO default −1)', async () => {
    await favoriteApi.addFavorite(42, 'tok')
    expect(mockedPost).toHaveBeenCalledWith('/favorite/add', {
      gid: 42,
      token: 'tok',
      category: 0,
    })
    const body = mockedPost.mock.calls[0][1] as Record<string, unknown>
    expect('slot' in body).toBe(false)
  })

  it('includes slot only when provided', async () => {
    await favoriteApi.addFavorite(42, 'tok', 2, 5)
    expect(mockedPost).toHaveBeenCalledWith('/favorite/add', {
      gid: 42,
      token: 'tok',
      category: 2,
      slot: 5,
    })
  })
})

describe('favoriteApi.removeFavorite — DELETE 携带 body（T-F2）', () => {
  it('passes gid/token/category through the axios delete config', async () => {
    await favoriteApi.removeFavorite(7, 'abc', 1)
    expect(mockedDelete).toHaveBeenCalledWith('/favorite/remove', {
      data: { gid: 7, token: 'abc', category: 1 },
    })

    // Defaults: empty token + category 0.
    await favoriteApi.removeFavorite(7)
    expect(mockedDelete).toHaveBeenLastCalledWith('/favorite/remove', {
      data: { gid: 7, token: '', category: 0 },
    })
  })
})
