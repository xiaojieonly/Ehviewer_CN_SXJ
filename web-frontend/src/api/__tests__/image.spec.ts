import { describe, expect, it, vi, beforeEach } from 'vitest'
import type { Job } from '@/api/jobs'

vi.mock('@/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import client from '@/api/client'
import { imageApi } from '@/api/image'

const mockedGet = vi.mocked(client.get)
const mockedPost = vi.mocked(client.post)

const job: Job = {
  jobId: 'cc-1',
  type: 'CACHE_CLEAR',
  state: 'PENDING',
  stage: null,
  percent: 0,
  processed: 0,
  total: 0,
  startedAt: null,
  completedAt: null,
  error: null,
  result: null,
}

beforeEach(() => {
  mockedGet.mockReset()
  mockedPost.mockReset()
})

describe('imageApi — 缓存状态与异步清空（T-F2 TH5）', () => {
  it('getCacheStatus reads GET /image/cache/status', async () => {
    mockedGet.mockResolvedValue({ data: { cacheSize: 12 } })
    await expect(imageApi.getCacheStatus()).resolves.toEqual({ cacheSize: 12 })
    expect(mockedGet).toHaveBeenCalledWith('/image/cache/status')
  })

  it('clearCacheAsync posts the 202 submission endpoint with no body', async () => {
    mockedPost.mockResolvedValue({ data: job })
    await expect(imageApi.clearCacheAsync()).resolves.toBe(job)
    expect(mockedPost).toHaveBeenCalledTimes(1)
    expect(mockedPost).toHaveBeenCalledWith('/image/cache/clear')
  })
})
