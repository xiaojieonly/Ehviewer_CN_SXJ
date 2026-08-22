import { describe, expect, it, vi, beforeEach } from 'vitest'
import type { Settings } from '@/api/settings'

vi.mock('@/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import client from '@/api/client'
import { settingsApi } from '@/api/settings'

const mockedGet = vi.mocked(client.get)
const mockedPut = vi.mocked(client.put)

function settingsFixture(): Settings {
  return {
    download: {
      path: '/data/downloads',
      workerCount: 3,
      downloadDelay: 0,
      downloadTimeout: 30,
      maxConcurrentGalleries: 1,
      maxConcurrentImages: 3,
    },
    cache: { path: '/data/cache', sizeMb: 512 },
    smb: { enabled: false },
    security: { requireAuth: true, sessionTimeout: 604800 },
    processing: {
      enabled: true,
      defaultType: 'UPSCALE',
      outputFormat: 'jpg',
      outputQuality: 90,
    },
    proxy: {
      enabled: false,
      type: 'socks',
      host: '',
      port: 1080,
      username: '',
      password: '',
      proxyPasswordSet: false,
    },
  }
}

beforeEach(() => {
  mockedGet.mockReset()
  mockedPut.mockReset()
})

describe('settingsApi（T-F2 TH5）', () => {
  it('get reads the full settings object', async () => {
    const settings = settingsFixture()
    mockedGet.mockResolvedValue({ data: settings })
    await expect(settingsApi.get()).resolves.toBe(settings)
    expect(mockedGet).toHaveBeenCalledWith('/settings')
  })

  it('update PUTs the partial patch as the JSON body and resolves the ack', async () => {
    mockedPut.mockResolvedValue({ data: true })
    const patch = { security: { requireAuth: true, sessionTimeout: 3600 } }
    await expect(settingsApi.update(patch)).resolves.toBe(true)
    expect(mockedPut).toHaveBeenCalledWith('/settings', patch)
  })

  it('update forwards an empty patch untouched (server round-trip use case)', async () => {
    mockedPut.mockResolvedValue({ data: true })
    await settingsApi.update({})
    expect(mockedPut).toHaveBeenCalledWith('/settings', {})
  })
})
