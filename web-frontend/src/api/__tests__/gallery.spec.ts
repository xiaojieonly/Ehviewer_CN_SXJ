import { describe, expect, it, vi, beforeEach } from 'vitest'
import type { GalleryListResponse } from '@/types'

vi.mock('@/api/client', () => ({
  default: { get: vi.fn() },
}))

import client from '@/api/client'
import { galleryApi } from '@/api/gallery'

const mockedGet = vi.mocked(client.get)

function okResponse(data: GalleryListResponse): { data: GalleryListResponse } {
  return { data }
}

describe('galleryApi.search (E2E-6: HTTP 200 {success:false} must not read as an empty result)', () => {
  beforeEach(() => {
    mockedGet.mockReset()
  })

  it('returns the payload for a successful search', async () => {
    mockedGet.mockResolvedValue(okResponse({ success: true, data: [], total: 0 }))
    await expect(galleryApi.search('x')).resolves.toEqual({ success: true, data: [], total: 0 })
  })

  it('rejects a search answered with success:false', async () => {
    mockedGet.mockResolvedValue(okResponse({ success: false, data: [], total: 0 }))
    await expect(galleryApi.search('x')).rejects.toThrow()
  })

  it('rejects getHistory / getFavorites when the backend reports failure', async () => {
    mockedGet.mockResolvedValue(okResponse({ success: false, data: [], total: 0 }))
    await expect(galleryApi.getHistory()).rejects.toThrow()
    await expect(galleryApi.getFavorites()).rejects.toThrow()
  })

  it('keeps real empty results (success:true, empty array) as valid', async () => {
    mockedGet.mockResolvedValue(okResponse({ success: true, data: [], total: 0 }))
    await expect(galleryApi.getHistory()).resolves.toEqual({ success: true, data: [], total: 0 })
  })
})
