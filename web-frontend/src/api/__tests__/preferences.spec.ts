import { describe, expect, it, vi, beforeEach } from 'vitest'
import type { Preferences } from '@/api/preferences'

vi.mock('@/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import client from '@/api/client'
import { preferencesApi, DEFAULT_PREFERENCES } from '@/api/preferences'

const mockedGet = vi.mocked(client.get)
const mockedPut = vi.mocked(client.put)

beforeEach(() => {
  mockedGet.mockReset()
  mockedPut.mockReset()
})

describe('preferencesApi（T-F2 TH5）', () => {
  it('get resolves the full preference tree', async () => {
    mockedGet.mockResolvedValue({ data: DEFAULT_PREFERENCES })
    const prefs = await preferencesApi.get()
    expect(prefs).toBe(DEFAULT_PREFERENCES)
    expect(prefs.reader.keyboardPaging).toBe(true)
    expect(mockedGet).toHaveBeenCalledWith('/preferences')
  })

  it('update PUTs a partial patch (single section) as the JSON body', async () => {
    mockedPut.mockResolvedValue({ data: DEFAULT_PREFERENCES })
    const patch: Partial<Preferences> = {
      reader: DEFAULT_PREFERENCES.reader,
    }
    await preferencesApi.update(patch)
    expect(mockedPut).toHaveBeenCalledWith('/preferences', patch)
  })

  it('update forwards an empty patch untouched', async () => {
    mockedPut.mockResolvedValue({ data: DEFAULT_PREFERENCES })
    await preferencesApi.update({})
    expect(mockedPut).toHaveBeenCalledWith('/preferences', {})
  })
})
