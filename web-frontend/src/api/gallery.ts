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

/**
 * Sort orders accepted by `GET /gallery/search?sort=` (contracts/openapi.yaml
 * `searchGallery`): 0/absent = default order, 1 = posted time desc,
 * 2 = rating desc, 3 = title asc. The server passes the value through to the
 * site URL as `f_order`.
 */
export type SearchSortOrder = 0 | 1 | 2 | 3

/**
 * Advanced search filters for `GET /gallery/search` (Wave-1 1a extended
 * params, contracts/openapi.yaml `searchGallery`). Every field is optional;
 * absent fields keep the legacy server default (no filter applied).
 *
 * The field set mirrors the backend query parameters 1:1 so a filter state
 * can round-trip through `QuickSearchDto` (see
 * `components/search/searchFilters.ts` for the mapping).
 */
export interface SearchFilters {
  /**
   * Category bitmask, Android `SiteConfig` semantics — bit set = category
   * EXCLUDED (same encoding the positional `category` argument uses).
   */
  category?: number
  /** Sort order; 0/absent = default order. */
  sort?: SearchSortOrder
  /** Minimum page count; absent = no lower bound. */
  pageMin?: number
  /** Maximum page count; absent = no upper bound. */
  pageMax?: number
  /** Minimum rating threshold, 2–5; 0/absent = disabled. */
  minRating?: number
  /** Advanced scope: match gallery name (`f_sname`). */
  searchName?: boolean
  /** Advanced scope: match tags (`f_stags`). */
  searchTags?: boolean
  /** Advanced scope: match description (`f_sdesc`). */
  searchDesc?: boolean
  /** Advanced scope: match torrent filenames (`f_storr`). */
  searchTorrents?: boolean
}

/** Append the extended filter params — only values diverging from defaults. */
function appendFilterParams(params: URLSearchParams, filters: SearchFilters): void {
  if (filters.sort) params.set('sort', filters.sort.toString())
  if (filters.pageMin && filters.pageMin > 0) params.set('pageMin', filters.pageMin.toString())
  if (filters.pageMax && filters.pageMax > 0) params.set('pageMax', filters.pageMax.toString())
  if (filters.minRating && filters.minRating > 0) params.set('minRating', filters.minRating.toString())
  if (filters.searchName) params.set('searchName', 'true')
  if (filters.searchTags) params.set('searchTags', 'true')
  if (filters.searchDesc) params.set('searchDesc', 'true')
  if (filters.searchTorrents) params.set('searchTorrents', 'true')
}

export const galleryApi = {
  /**
   * Search galleries. Wave-1 1a additive (task A5): the optional `filters`
   * argument carries the extended backend params (sort / pageMin / pageMax /
   * minRating / searchName / searchTags / searchDesc / searchTorrents) and —
   * when its `category` field is set — overrides the positional `category`
   * bitmask. Existing call sites keep working unchanged.
   */
  async search(
    keyword?: string,
    category?: number,
    page = 0,
    pageSize = 20,
    filters?: SearchFilters,
  ): Promise<GalleryListResponse> {
    const params = new URLSearchParams()
    if (keyword) params.set('keyword', keyword)
    const mergedCategory = filters?.category !== undefined ? filters.category : category
    if (mergedCategory !== undefined) params.set('category', mergedCategory.toString())
    if (filters) appendFilterParams(params, filters)
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

  /**
   * Create a quick-search preset — `POST /api/v1/gallery/quick-search`
   * (contracts/openapi.yaml `createQuickSearch`). Wave-1 1a additive
   * (task A5): the request body is the `QuickSearchDto` schema minus the
   * server-assigned `id`; the server answers 201 with the created DTO.
   */
  async createQuickSearch(preset: Omit<QuickSearch, 'id'>): Promise<QuickSearch> {
    const { data } = await client.post('/gallery/quick-search', preset)
    return data
  },
}
