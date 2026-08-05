import { describe, expect, it, vi, beforeEach } from 'vitest'
import type { GalleryListResponse, QuickSearch } from '@/types'

vi.mock('@/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn(), delete: vi.fn() },
}))

import client from '@/api/client'
import { galleryApi } from '@/api/gallery'
import type { SearchFilters } from '@/api/gallery'

const mockedGet = vi.mocked(client.get)
const mockedPost = vi.mocked(client.post)
const mockedDelete = vi.mocked(client.delete)

function okResponse(data: GalleryListResponse): { data: GalleryListResponse } {
  return { data }
}

/** Query string of the URL passed to `client.get` on call #n (0-based). */
function searchQuery(call = 0): URLSearchParams {
  const url = mockedGet.mock.calls[call][0] as string
  return new URLSearchParams(url.split('?')[1] ?? '')
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

describe('galleryApi.search — Wave-1 1a filter params (task A5, additive)', () => {
  beforeEach(() => {
    mockedGet.mockReset()
    mockedGet.mockResolvedValue(okResponse({ success: true, data: [], total: 0 }))
  })

  it('keeps the legacy call shape untouched (keyword/category/page/pageSize only)', async () => {
    await galleryApi.search('naruto', 6, 2, 50)
    const q = searchQuery()
    expect(q.get('keyword')).toBe('naruto')
    expect(q.get('category')).toBe('6')
    expect(q.get('page')).toBe('2')
    expect(q.get('pageSize')).toBe('50')
    for (const key of ['sort', 'pageMin', 'pageMax', 'minRating', 'searchName', 'searchTags', 'searchDesc', 'searchTorrents']) {
      expect(q.get(key)).toBeNull()
    }
  })

  it('appends every extended param when the filter diverges from defaults', async () => {
    const filters: SearchFilters = {
      category: 0x2 | 0x4,
      sort: 2,
      pageMin: 5,
      pageMax: 50,
      minRating: 4,
      searchName: true,
      searchTags: true,
      searchDesc: true,
      searchTorrents: true,
    }
    await galleryApi.search('x', undefined, 0, 20, filters)
    const q = searchQuery()
    expect(q.get('category')).toBe('6')
    expect(q.get('sort')).toBe('2')
    expect(q.get('pageMin')).toBe('5')
    expect(q.get('pageMax')).toBe('50')
    expect(q.get('minRating')).toBe('4')
    expect(q.get('searchName')).toBe('true')
    expect(q.get('searchTags')).toBe('true')
    expect(q.get('searchDesc')).toBe('true')
    expect(q.get('searchTorrents')).toBe('true')
  })

  it('omits default-valued filter fields (sort=0, minRating=0, booleans false)', async () => {
    await galleryApi.search('x', undefined, 0, 20, {
      sort: 0,
      pageMin: 0,
      pageMax: 0,
      minRating: 0,
      searchName: false,
      searchTags: false,
      searchDesc: false,
      searchTorrents: false,
    })
    const q = searchQuery()
    expect(q.get('keyword')).toBe('x')
    expect(q.get('sort')).toBeNull()
    expect(q.get('pageMin')).toBeNull()
    expect(q.get('pageMax')).toBeNull()
    expect(q.get('minRating')).toBeNull()
    expect(q.get('searchName')).toBeNull()
    expect(q.get('searchTags')).toBeNull()
    expect(q.get('searchDesc')).toBeNull()
    expect(q.get('searchTorrents')).toBeNull()
  })

  it('lets filters.category override the positional category argument', async () => {
    await galleryApi.search('x', 0x1, 0, 20, { category: 0x200 })
    expect(searchQuery().get('category')).toBe('512')
  })

  it('appends the W3 R4-10 higher advance bits when set (and only then)', async () => {
    await galleryApi.search('x', undefined, 0, 20, {
      searchTorrentsOnly: true,
      searchLowPowerTags: true,
      searchDownvotedTags: true,
      searchExpunged: true,
      disableLanguageFilter: true,
      disableUploaderFilter: true,
      disableTagFilter: true,
    })
    const q = searchQuery()
    for (const key of [
      'searchTorrentsOnly',
      'searchLowPowerTags',
      'searchDownvotedTags',
      'searchExpunged',
      'disableLanguageFilter',
      'disableUploaderFilter',
      'disableTagFilter',
    ]) {
      expect(q.get(key)).toBe('true')
    }

    await galleryApi.search('x', undefined, 0, 20, {})
    const empty = searchQuery(1)
    for (const key of [
      'searchTorrentsOnly',
      'searchLowPowerTags',
      'searchDownvotedTags',
      'searchExpunged',
      'disableLanguageFilter',
      'disableUploaderFilter',
      'disableTagFilter',
    ]) {
      expect(empty.get(key)).toBeNull()
    }
  })

  it('sends each sort order 1..3 verbatim', async () => {
    for (const sort of [1, 2, 3] as const) {
      await galleryApi.search(undefined, undefined, 0, 20, { sort })
    }
    expect(searchQuery(0).get('sort')).toBe('1')
    expect(searchQuery(1).get('sort')).toBe('2')
    expect(searchQuery(2).get('sort')).toBe('3')
  })
})

describe('galleryApi.deleteQuickSearch (DELETE /gallery/quick-search/{id}, W3 R4-12)', () => {
  beforeEach(() => {
    mockedDelete.mockReset()
  })

  it('deletes the given preset id and returns the server envelope', async () => {
    mockedDelete.mockResolvedValue({ data: { success: true } })
    await expect(galleryApi.deleteQuickSearch(42)).resolves.toEqual({ success: true })
    expect(mockedDelete).toHaveBeenCalledWith('/gallery/quick-search/42')
  })
})

describe('galleryApi.createQuickSearch (POST /gallery/quick-search)', () => {
  beforeEach(() => {
    mockedPost.mockReset()
  })

  it('posts the QuickSearchDto payload (sans id) and returns the created DTO', async () => {
    const created: QuickSearch = {
      id: 42,
      name: 'Rated classics',
      mode: 0,
      category: 6,
      keyword: 'naruto',
      advanceSearch: 0x3,
      minRating: 4,
      pageFrom: 5,
      pageTo: 50,
    }
    mockedPost.mockResolvedValue({ data: created })

    const { id: _id, ...payload } = created
    await expect(galleryApi.createQuickSearch(payload)).resolves.toEqual(created)
    expect(mockedPost).toHaveBeenCalledWith('/gallery/quick-search', payload)
  })
})

describe('galleryApi.feed (GET /gallery/feed, frozen feed contract)', () => {
  beforeEach(() => {
    mockedGet.mockReset()
  })

  it('calls /gallery/feed with mode/page/pageSize for subscription/popular', async () => {
    mockedGet.mockResolvedValue(okResponse({ success: true, data: [], total: 0 }))
    await galleryApi.feed('subscription', 2, 50)
    const q = searchQuery()
    expect(mockedGet.mock.calls[0][0]).toContain('/gallery/feed')
    expect(q.get('mode')).toBe('subscription')
    expect(q.get('page')).toBe('2')
    expect(q.get('pageSize')).toBe('50')

    await galleryApi.feed('popular', 0, 20)
    expect(searchQuery(1).get('mode')).toBe('popular')
  })

  it('passes mode=toplist through and returns the ranked TopListResponse', async () => {
    const payload = {
      success: true,
      data: [{ gid: 1, token: 'a', tag: 'parody:one piece', value: 999, href: 'https://e-hentai.org/g/1/a/' }],
      total: 1,
    }
    mockedGet.mockResolvedValue({ data: payload })
    await expect(galleryApi.feed('toplist', 0, 20)).resolves.toEqual(payload)
    expect(searchQuery().get('mode')).toBe('toplist')
  })

  it('rejects a feed answered with success:false', async () => {
    mockedGet.mockResolvedValue(okResponse({ success: false, data: [], total: 0 }))
    await expect(galleryApi.feed('popular', 0, 20)).rejects.toThrow()
  })
})
