import { describe, expect, it, vi, beforeEach } from 'vitest'
import type { SmbConfig, SmbSyncProgress } from '@/api/smb'

vi.mock('@/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import client from '@/api/client'
import { smbApi } from '@/api/smb'

const mockedGet = vi.mocked(client.get)
const mockedPost = vi.mocked(client.post)
const mockedPut = vi.mocked(client.put)

const config: SmbConfig = {
  id: 1,
  host: 'nas.local',
  port: 445,
  share: 'media',
  path: 'comics',
  loginMode: 'user',
  username: 'bob',
  enabled: true,
}

beforeEach(() => {
  mockedGet.mockReset()
  mockedPost.mockReset()
  mockedPut.mockReset()
})

describe('smbApi（T-F2 TH5）', () => {
  it('getConfig GETs /smb/config and passes a null config through', async () => {
    mockedGet.mockResolvedValue({ data: null })
    await expect(smbApi.getConfig()).resolves.toBeNull()
    expect(mockedGet).toHaveBeenCalledWith('/smb/config')

    mockedGet.mockResolvedValue({ data: config })
    await expect(smbApi.getConfig()).resolves.toEqual(config)
  })

  it('updateConfig PUTs the partial patch including an optional password', async () => {
    mockedPut.mockResolvedValue({ data: true })
    const patch = { host: 'new.local', password: 'pw' }
    await smbApi.updateConfig(patch)
    expect(mockedPut).toHaveBeenCalledWith('/smb/config', patch)
  })

  it('testConnection posts the candidate config as the body', async () => {
    mockedPost.mockResolvedValue({ data: { success: true, message: 'ok' } })
    await smbApi.testConnection({ ...config, password: 'x' })
    expect(mockedPost).toHaveBeenCalledWith('/smb/test-connection', { ...config, password: 'x' })
  })

  it('sync sends the aggressive flag in the body — default false, explicit true', async () => {
    mockedPost.mockResolvedValue({ data: true })
    await smbApi.sync()
    expect(mockedPost).toHaveBeenCalledWith('/smb/sync', { aggressive: false })

    await smbApi.sync(true)
    expect(mockedPost).toHaveBeenLastCalledWith('/smb/sync', { aggressive: true })
  })

  it('cancel posts /smb/cancel without a body', async () => {
    mockedPost.mockResolvedValue({ data: true })
    await expect(smbApi.cancel()).resolves.toBe(true)
    expect(mockedPost).toHaveBeenCalledWith('/smb/cancel')
  })

  it('getProgress reads the sync progress snapshot', async () => {
    const progress: SmbSyncProgress = {
      state: 'RUNNING',
      totalFiles: 100,
      syncedFiles: 42,
      currentFile: 'a.cbz',
      speed: 1.5,
    }
    mockedGet.mockResolvedValue({ data: progress })
    await expect(smbApi.getProgress()).resolves.toBe(progress)
    expect(mockedGet).toHaveBeenCalledWith('/smb/progress')
  })
})
