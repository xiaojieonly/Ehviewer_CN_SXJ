import { describe, expect, it, vi, beforeEach } from 'vitest'
import type { DownloadListResponse } from '../download'

vi.mock('@/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import client from '@/api/client'
import { downloadApi } from '@/api/download'

const mockedGet = vi.mocked(client.get)
const mockedPost = vi.mocked(client.post)
const mockedDelete = vi.mocked(client.delete)

/** Axios `config.params` passed to client.get on call #n (0-based). */
function paramsOf(call = 0): Record<string, string> {
  const cfg = mockedGet.mock.calls[call][1] as { params?: Record<string, string> } | undefined
  return cfg?.params ?? {}
}

function listPayload(): DownloadListResponse {
  return { downloads: [], labels: [], total: 0 }
}

beforeEach(() => {
  mockedGet.mockReset()
  mockedGet.mockResolvedValue({ data: listPayload() })
  mockedPost.mockReset()
  mockedPost.mockResolvedValue({ data: true })
  mockedDelete.mockReset()
  mockedDelete.mockResolvedValue({ data: true })
})

describe('downloadApi.list — 参数序列化（T-F2 TH5）', () => {
  it('omits every optional param when called with no arguments', async () => {
    await downloadApi.list()
    const p = paramsOf()
    expect(mockedGet.mock.calls[0][0]).toBe('/download/list')
    for (const key of ['label', 'offset', 'limit', 'sort', 'q', 'regex']) {
      expect(key in p).toBe(false)
    }
  })

  it('sends pagination and sort as strings', async () => {
    await downloadApi.list(2, 40, 50, 'time_desc')
    const p = paramsOf()
    expect(p.label).toBe('2')
    expect(p.offset).toBe('40')
    expect(p.limit).toBe('50')
    expect(p.sort).toBe('time_desc')
    expect('regex' in p).toBe(false)
  })

  it("sends regex='true' only when the flag is truthy", async () => {
    await downloadApi.list(undefined, undefined, undefined, undefined, '^a', true)
    expect(paramsOf(0).q).toBe('^a')
    expect(paramsOf(0).regex).toBe('true')

    await downloadApi.list(undefined, undefined, undefined, undefined, '^a', false)
    expect(paramsOf(1).q).toBe('^a')
    expect(paramsOf(1).regex).toBeUndefined()
  })

  it('drops empty-string and null q but keeps a real keyword', async () => {
    await downloadApi.list(undefined, undefined, undefined, undefined, '')
    expect('q' in paramsOf()).toBe(false)

    await downloadApi.list(undefined, undefined, undefined, undefined, null)
    expect('q' in paramsOf(1)).toBe(false)

    await downloadApi.list(undefined, undefined, undefined, undefined, 'naruto')
    expect(paramsOf(2).q).toBe('naruto')
  })
})

describe('downloadApi 单条与批量操作 — POST body 形状（T-F2）', () => {
  it('add posts the full task payload with a defaulted label', async () => {
    await downloadApi.add(42, 'tok', 'Title', '/thumb.png')
    expect(mockedPost).toHaveBeenCalledWith('/download/add', {
      gid: 42,
      token: 'tok',
      title: 'Title',
      thumb: '/thumb.png',
      label: 0,
    })
  })

  it('start/pause/cancel target the id-scoped endpoints', async () => {
    await downloadApi.start(7)
    expect(mockedPost).toHaveBeenLastCalledWith('/download/start/7')

    await downloadApi.pause(8)
    expect(mockedPost).toHaveBeenLastCalledWith('/download/pause/8')

    await downloadApi.cancel(9)
    expect(mockedPost).toHaveBeenLastCalledWith('/download/cancel/9')

    await downloadApi.startAll()
    expect(mockedPost).toHaveBeenLastCalledWith('/download/start-all')
  })

  it('delete uses DELETE on /download/delete/{id}', async () => {
    await downloadApi.delete(3)
    expect(mockedDelete).toHaveBeenCalledWith('/download/delete/3')
  })

  it('batch range/move pass the target object straight through as the body', async () => {
    const idsTarget = { ids: [1, 2, 3] }
    await downloadApi.startRange(idsTarget)
    expect(mockedPost).toHaveBeenCalledWith('/download/start-range', idsTarget)

    const allRegexTarget = { all: true, label: 1, q: '^x', regex: true }
    await downloadApi.stopRange(allRegexTarget)
    expect(mockedPost).toHaveBeenCalledWith('/download/stop-range', allRegexTarget)

    await downloadApi.deleteRange(allRegexTarget)
    expect(mockedPost).toHaveBeenCalledWith('/download/delete-range', allRegexTarget)

    await downloadApi.move({ all: true, labelId: 4 })
    expect(mockedPost).toHaveBeenCalledWith('/download/move', { all: true, labelId: 4 })
  })

  it('labels: create posts {label}, delete hits /download/label/{id}', async () => {
    await downloadApi.createLabel('sudou')
    expect(mockedPost).toHaveBeenCalledWith('/download/label', { label: 'sudou' })

    await downloadApi.deleteLabel(11)
    expect(mockedDelete).toHaveBeenCalledWith('/download/label/11')
  })
})

describe('downloadApi 维护两段式（T-F2）', () => {
  it('preview is a plain GET without a body', async () => {
    mockedGet.mockResolvedValueOnce({
      data: { redundantFiles: [], invalidDownloads: [] },
    })
    await downloadApi.previewMaintenance()
    expect(mockedGet).toHaveBeenCalledWith('/download/maintenance/preview')
  })

  it('clean posts the kind enum as the body', async () => {
    await downloadApi.cleanMaintenance('REDUNDANT_FILES')
    expect(mockedPost).toHaveBeenCalledWith('/download/maintenance/clean', {
      kind: 'REDUNDANT_FILES',
    })
  })
})

describe('downloadApi.getInfo（T-F2）', () => {
  it('resolves the info payload from GET /download/info/{id}', async () => {
    const item = { gid: 1 } as unknown
    mockedGet.mockResolvedValueOnce({ data: item })
    await expect(downloadApi.getInfo(5)).resolves.toBe(item)
    expect(mockedGet).toHaveBeenCalledWith('/download/info/5')
  })
})
