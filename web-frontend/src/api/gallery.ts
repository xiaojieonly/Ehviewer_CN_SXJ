import client from './client'
import type { GalleryListResponse, GalleryDetail, QuickSearch } from '@/types'

export const galleryApi = {
  async search(keyword?: string, category?: number, page = 0, pageSize = 20): Promise<GalleryListResponse> {
    const params = new URLSearchParams()
    if (keyword) params.set('keyword', keyword)
    if (category !== undefined) params.set('category', category.toString())
    params.set('page', page.toString())
    params.set('pageSize', pageSize.toString())
    const { data } = await client.get(`/gallery/search?${params.toString()}`)
    return data
  },

  async getDetail(gid: number): Promise<GalleryDetail> {
    const { data } = await client.get(`/gallery/${gid}`)
    return data
  },

  async getHistory(page = 0, pageSize = 20): Promise<GalleryListResponse> {
    const { data } = await client.get(`/gallery/history?page=${page}&pageSize=${pageSize}`)
    return data
  },

  async getFavorites(): Promise<GalleryListResponse> {
    const { data } = await client.get('/gallery/favorites')
    return data
  },

  async getQuickSearches(): Promise<{ success: boolean; data: QuickSearch[] }> {
    const { data } = await client.get('/gallery/quick-search')
    return data
  },
}
