import { describe, expect, it, vi, beforeEach } from 'vitest'
import type { Job } from '@/api/jobs'

vi.mock('@/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import client from '@/api/client'
import { backupApi } from '@/api/backup'

const mockedGet = vi.mocked(client.get)
const mockedPost = vi.mocked(client.post)

const job: Job = {
  jobId: 'j-1',
  type: 'EXPORT',
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

describe('backupApi 导出 — 流式端点禁用超时（T-F2 TH5）', () => {
  it('exportBackup requests a blob with includeDownloads and timeout 0', async () => {
    const blob = new Blob(['zip'])
    mockedGet.mockResolvedValue({ data: blob })
    await expect(backupApi.exportBackup(true)).resolves.toBe(blob)
    expect(mockedGet).toHaveBeenCalledWith('/backup/export', {
      params: { includeDownloads: true },
      responseType: 'blob',
      timeout: 0,
    })
  })

  it('exportBackupAsync posts the 202 submission with params and timeout 0', async () => {
    mockedPost.mockResolvedValue({ data: job })
    await expect(backupApi.exportBackupAsync(false)).resolves.toBe(job)
    expect(mockedPost).toHaveBeenCalledWith('/backup/export/async', null, {
      params: { includeDownloads: false },
      timeout: 0,
    })
  })

  it('downloadExport fetches the artifact blob by jobId', async () => {
    const blob = new Blob(['zip'])
    mockedGet.mockResolvedValue({ data: blob })
    await expect(backupApi.downloadExport('j-9')).resolves.toBe(blob)
    expect(mockedGet).toHaveBeenCalledWith('/backup/export/j-9', {
      responseType: 'blob',
      timeout: 0,
    })
  })
})

describe('backupApi 还原与导入 — multipart 形状（T-F2）', () => {
  it('restoreBackup appends the file and disables the JSON content-type', async () => {
    mockedPost.mockResolvedValue({ data: { ...job, type: 'RESTORE' as const } })
    const file = new File(['db'], 'backup.7z')
    await backupApi.restoreBackup(file)

    expect(mockedPost).toHaveBeenCalledTimes(1)
    const [url, form, config] = mockedPost.mock.calls[0]
    expect(url).toBe('/backup/restore')
    expect(form).toBeInstanceOf(FormData)
    expect((form as FormData).get('file')).toBe(file)
    expect(config).toEqual({
      headers: { 'Content-Type': undefined },
      timeout: 0,
    })
  })

  it('importEhViewer appends optional cookies and forwards the force flag', async () => {
    mockedPost.mockResolvedValue({ data: { ...job, type: 'IMPORT' as const } })
    const db = new File(['db'], 'ehviewer.db')
    const cookies = new File(['c'], 'cookies.db')

    await backupApi.importEhViewer(db, cookies, true)
    let [url, form, config] = mockedPost.mock.calls[0]
    expect(url).toBe('/backup/import-ehviewer')
    expect((form as FormData).get('file')).toBe(db)
    expect((form as FormData).get('cookies')).toBe(cookies)
    expect((config as { params: { force: boolean } }).params.force).toBe(true)
    expect((config as { headers: Record<string, unknown> }).headers['Content-Type']).toBeUndefined()

    // No cookies → field omitted; default force=false still sent explicitly.
    await backupApi.importEhViewer(db)
    ;[url, form, config] = mockedPost.mock.calls[1]
    expect((form as FormData).get('cookies')).toBeNull()
    expect((config as { params: { force: boolean } }).params.force).toBe(false)
  })
})

describe('backupApi.getBackupState — R4-2 运行态（T-F2）', () => {
  it('reads GET /backup/state', async () => {
    mockedGet.mockResolvedValue({ data: { restorePending: true } })
    await expect(backupApi.getBackupState()).resolves.toEqual({ restorePending: true })
    expect(mockedGet).toHaveBeenCalledWith('/backup/state')
  })
})
