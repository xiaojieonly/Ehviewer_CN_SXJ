import { describe, expect, it, vi, beforeEach } from 'vitest'
import type { SyncPolicy } from '@/api/sync'

vi.mock('@/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import client from '@/api/client'
import { syncApi } from '@/api/sync'

const mockedGet = vi.mocked(client.get)
const mockedPut = vi.mocked(client.put)

const policy: SyncPolicy = {
  conflictStrategy: 'lww',
  clientTier: 1,
  autoSyncIntervalSec: 300,
}

beforeEach(() => {
  mockedGet.mockReset()
  mockedPut.mockReset()
})

describe('syncApi（T-F2 TH5）', () => {
  it('getPolicy GETs /sync/policy', async () => {
    mockedGet.mockResolvedValue({ data: policy })
    await expect(syncApi.getPolicy()).resolves.toBe(policy)
    expect(mockedGet).toHaveBeenCalledWith('/sync/policy')
  })

  it('updatePolicy PUTs the full policy object as the body', async () => {
    mockedPut.mockResolvedValue({ data: policy })
    await expect(syncApi.updatePolicy(policy)).resolves.toBe(policy)
    expect(mockedPut).toHaveBeenCalledWith('/sync/policy', policy)
  })
})
