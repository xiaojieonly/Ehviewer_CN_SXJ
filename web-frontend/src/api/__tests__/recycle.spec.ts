import { describe, expect, it, vi, beforeEach } from 'vitest'

vi.mock('@/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import client from '@/api/client'
import { recycleApi } from '@/api/recycle'

const mockedGet = vi.mocked(client.get)
const mockedPost = vi.mocked(client.post)

/** Axios `config.params` passed to client.get on call #n (0-based). */
function paramsOf(call = 0): Record<string, string> {
  const cfg = mockedGet.mock.calls[call][1] as { params?: Record<string, string> } | undefined
  return cfg?.params ?? {}
}

beforeEach(() => {
  mockedGet.mockReset().mockResolvedValue({
    data: { items: [], total: 0, page: 1, pageSize: 50 },
  })
  mockedPost.mockReset().mockResolvedValue({ data: { restored: 0, failed: 0 } })
})

describe('recycleApi.list — 路径与 params 序列化', () => {
  it('hits /recycle/list with the page=1&pageSize=50 defaults as strings', async () => {
    await recycleApi.list()
    expect(mockedGet.mock.calls[0][0]).toBe('/recycle/list')
    const p = paramsOf()
    expect(p.page).toBe('1')
    expect(p.pageSize).toBe('50')
  })

  it('serializes explicit page/pageSize as strings (page 1 起, pageSize 钳制在服务端)', async () => {
    await recycleApi.list(3, 200)
    expect(mockedGet.mock.calls[0][0]).toBe('/recycle/list')
    const p = paramsOf()
    expect(p.page).toBe('3')
    expect(p.pageSize).toBe('200')
  })

  it('returns the response envelope untouched', async () => {
    const envelope = {
      items: [
        { entity: 'favorites', key: 42, title: 'T', deletedAt: 1728600000000, remainingDays: 5 },
      ],
      total: 1,
      page: 1,
      pageSize: 50,
    }
    mockedGet.mockResolvedValue({ data: envelope })
    await expect(recycleApi.list()).resolves.toEqual(envelope)
  })
})

describe('recycleApi.restore — POST body 形状', () => {
  it('posts /recycle/restore with the {items:[{entity,key}]} body verbatim', async () => {
    mockedPost.mockResolvedValue({ data: { restored: 2, failed: 0 } })
    const items = [
      { entity: 'favorites' as const, key: 42 },
      { entity: 'quickSearches' as const, key: 'qs-1' },
    ]
    await recycleApi.restore(items)
    expect(mockedPost).toHaveBeenCalledWith('/recycle/restore', { items })
    await expect(recycleApi.restore(items)).resolves.toEqual({ restored: 2, failed: 0 })
  })
})

describe('recycleApi.purge — body 有无 items 两种形状', () => {
  it('posts an empty {} body when items are omitted (= 清全部到期)', async () => {
    mockedPost.mockResolvedValue({ data: { purged: 7 } })
    await recycleApi.purge()
    expect(mockedPost).toHaveBeenCalledWith('/recycle/purge', {})
    await expect(recycleApi.purge()).resolves.toEqual({ purged: 7 })
  })

  it('posts {items} when targets are given', async () => {
    mockedPost.mockResolvedValue({ data: { purged: 1 } })
    const items = [{ entity: 'history' as const, key: 9 }]
    await recycleApi.purge(items)
    expect(mockedPost).toHaveBeenCalledWith('/recycle/purge', { items })
  })
})
