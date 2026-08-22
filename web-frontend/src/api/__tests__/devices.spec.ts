import { describe, expect, it, vi, beforeEach } from 'vitest'

vi.mock('@/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import client from '@/api/client'
import { devicesApi } from '@/api/devices'

const mockedGet = vi.mocked(client.get)
const mockedPost = vi.mocked(client.post)
const mockedDelete = vi.mocked(client.delete)

beforeEach(() => {
  mockedGet.mockReset()
  mockedPost.mockReset()
  mockedDelete.mockReset()
})

describe('devicesApi 配对与设备管理（T-F2 TH5）', () => {
  it('generatePairCode POSTs /auth/pair and returns the one-time code', async () => {
    const pair = { code: '123456', expiresAt: 1756000000000 }
    mockedPost.mockResolvedValue({ data: pair })
    await expect(devicesApi.generatePairCode()).resolves.toEqual(pair)
    expect(mockedPost).toHaveBeenCalledWith('/auth/pair')
  })

  it('list resolves the device array from GET /sync/devices', async () => {
    const device = {
      deviceId: 'dev-1',
      deviceName: 'Pixel',
      platform: 'android',
      pairedAt: 1,
      lastSeen: 2,
    }
    mockedGet.mockResolvedValue({ data: [device] })
    await expect(devicesApi.list()).resolves.toEqual([device])
    expect(mockedGet).toHaveBeenCalledWith('/sync/devices')
  })

  it('revoke DELETEs the device by id and resolves void', async () => {
    mockedDelete.mockResolvedValue({ data: undefined })
    await expect(devicesApi.revoke('dev-7')).resolves.toBeUndefined()
    expect(mockedDelete).toHaveBeenCalledWith('/sync/devices/dev-7')
  })
})
