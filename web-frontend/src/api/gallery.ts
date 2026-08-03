import client from './client'
import type { GalleryListResponse, GalleryDetail, QuickSearch } from '@/types'

/**
 * The backend reports gallery-upstream failures with HTTP 200 +
 * `{success:false, data:[], total:0}` (E2E-6). Views must be able to
 * distinguish that from a genuine empty result, so these helpers reject
 * instead of returning the same shape as an empty list — callers' existing
 * catch paths then show the error state.
 */
function ensureSuccess(response: GalleryListResponse): GalleryListResponse {
  if (response.success === false) {
    throw new Error('Gallery request failed')
  }
  return response
}

export const galleryApi = {
  async search(keyword?: string, category?: number, page = 0, pageSize = 20): Promise<GalleryListResponse> {
    const params = new URLSearchParams()
    if (keyword) params.set('keyword', keyword)
    if (category !== undefined) params.set('category', category.toString())
    params.set('page', page.toString())
    params.set('pageSize', pageSize.toString())
    const { data } = await client.get(`/gallery/search?${params.toString()}`)
    return ensureSuccess(data)
  },

  async getDetail(gid: number): Promise<GalleryDetail> {
    const { data } = await client.get(`/gallery/${gid}`)
    return data
  },

  async getHistory(page = 0, pageSize = 20): Promise<GalleryListResponse> {
    const { data } = await client.get(`/gallery/history?page=${page}&pageSize=${pageSize}`)
    return ensureSuccess(data)
  },

  async getFavorites(): Promise<GalleryListResponse> {
    const { data } = await client.get('/gallery/favorites')
    return ensureSuccess(data)
  },

  async getQuickSearches(): Promise<{ success: boolean; data: QuickSearch[] }> {
    const { data } = await client.get('/gallery/quick-search')
    return data
  },
}
