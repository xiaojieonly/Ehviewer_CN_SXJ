import { describe, expect, it, vi, beforeEach } from 'vitest'

vi.mock('@/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import client from '@/api/client'
import { historyApi } from '@/api/history'

const mockedGet = vi.mocked(client.get)
const mockedDelete = vi.mocked(client.delete)

/** Axios `config.params` passed to client.get on call #n (0-based). */
function paramsOf(call = 0): Record<string, string> {
  const cfg = mockedGet.mock.calls[call][1] as { params?: Record<string, string> } | undefined
  return cfg?.params ?? {}
}

beforeEach(() => {
  mockedGet.mockReset().mockResolvedValue({ data: { history: [], total: 0 } })
  mockedDelete.mockReset().mockResolvedValue({ data: { success: true } })
})

describe('historyApi.listHistory — 分页与 regex 参数（T-F2 TH5）', () => {
  it('sends no params when called bare (server returns the full list)', async () => {
    await historyApi.listHistory()
    expect(mockedGet.mock.calls[0][0]).toBe('/history/list')
    const p = paramsOf()
    for (const key of ['q', 'regex', 'page', 'pageSize']) {
      expect(key in p).toBe(false)
    }
  })

  it('serializes the server-paging pair as strings', async () => {
    await historyApi.listHistory(undefined, undefined, 3, 100)
    const p = paramsOf()
    expect(p.page).toBe('3')
    expect(p.pageSize).toBe('100')
    expect('q' in p).toBe(false)
    expect('regex' in p).toBe(false)
  })

  it("sends regex='true' only when requested", async () => {
    await historyApi.listHistory('^a', true, 0, 20)
    let p = paramsOf(0)
    expect(p.q).toBe('^a')
    expect(p.regex).toBe('true')

    await historyApi.listHistory('^a', false, 0, 20)
    p = paramsOf(1)
    expect(p.q).toBe('^a')
    expect(p.regex).toBeUndefined()
  })

  it('drops empty-string / null keywords but keeps real ones', async () => {
    await historyApi.listHistory('')
    expect('q' in paramsOf()).toBe(false)

    await historyApi.listHistory(null)
    expect('q' in paramsOf(1)).toBe(false)

    await historyApi.listHistory('miko')
    expect(paramsOf(2).q).toBe('miko')
  })
})

describe('historyApi.clearHistory（T-F2）', () => {
  it('issues DELETE /history/clear and resolves the payload', async () => {
    await expect(historyApi.clearHistory()).resolves.toEqual({ success: true })
    expect(mockedDelete).toHaveBeenCalledWith('/history/clear')
  })
})
