import client from './client'
import type {
  GalleryListResponse,
  GalleryDetail,
  QuickSearch,
  TopListResponse,
} from '@/types'

/**
 * The backend reports gallery-upstream failures with HTTP 200 +
 * `{success:false, data:[], total:0}` (E2E-6). Views must be able to
 * distinguish that from a genuine empty result, so these helpers reject
 * instead of returning the same shape as an empty list — callers' existing
 * catch paths then show the error state.
 */
function ensureSuccess<T extends { success: boolean }>(response: T): T {
  if (response.success === false) {
    throw new Error('Gallery request failed')
  }
  return response
}

/** Feed modes accepted by `GET /gallery/feed?mode=` (frozen contract). */
export type FeedMode = 'subscription' | 'popular' | 'toplist'

/**
 * Home feeds — `GET /gallery/feed?mode=subscription|popular|toplist`
 * (frozen contract). subscription / popular return the same GalleryInfo
 * envelope as search; toplist returns ranked rows (tag/value/href).
 */
async function feed(
  mode: 'subscription' | 'popular',
  page?: number,
  pageSize?: number,
): Promise<GalleryListResponse>
async function feed(mode: 'toplist', page?: number, pageSize?: number): Promise<TopListResponse>
async function feed(
  mode: FeedMode,
  page = 0,
  pageSize = 20,
): Promise<GalleryListResponse | TopListResponse> {
  const params = new URLSearchParams()
  params.set('mode', mode)
  params.set('page', page.toString())
  params.set('pageSize', pageSize.toString())
  const { data } = await client.get(`/gallery/feed?${params.toString()}`)
  return ensureSuccess(data)
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
  /** W3 R4-10 higher option: only show galleries with torrents (`f_sto`). */
  searchTorrentsOnly?: boolean
  /** W3 R4-10 higher option: also match low-power tags (`f_sdt1`). */
  searchLowPowerTags?: boolean
  /** W3 R4-10 higher option: also match downvoted tags (`f_sdt2`). */
  searchDownvotedTags?: boolean
  /** W3 R4-10 higher option: include expunged galleries (`f_sh`). */
  searchExpunged?: boolean
  /** W3 R4-10 higher option: disable the default language filter (`f_sfl`). */
  disableLanguageFilter?: boolean
  /** W3 R4-10 higher option: disable the default uploader filter (`f_sfu`). */
  disableUploaderFilter?: boolean
  /** W3 R4-10 higher option: disable the default tag filter (`f_sft`). */
  disableTagFilter?: boolean
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
  // W3 R4-10 higher AdvanceSearchTable bits (backendized).
  if (filters.searchTorrentsOnly) params.set('searchTorrentsOnly', 'true')
  if (filters.searchLowPowerTags) params.set('searchLowPowerTags', 'true')
  if (filters.searchDownvotedTags) params.set('searchDownvotedTags', 'true')
  if (filters.searchExpunged) params.set('searchExpunged', 'true')
  if (filters.disableLanguageFilter) params.set('disableLanguageFilter', 'true')
  if (filters.disableUploaderFilter) params.set('disableUploaderFilter', 'true')
  if (filters.disableTagFilter) params.set('disableTagFilter', 'true')
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

  feed,

  async getDetail(gid: number, token?: string): Promise<GalleryDetail> {
    const { data } = await client.get(`/gallery/${gid}`, { params: token ? { token } : undefined })
    return data
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

  /**
   * Delete a quick-search preset — `DELETE /api/v1/gallery/quick-search/{id}`
   * (contracts/openapi.yaml `deleteQuickSearch`). W3 R4-12: the endpoint
   * existed since Wave-1 but the frontend never called it.
   */
  async deleteQuickSearch(id: number): Promise<{ success: boolean }> {
    const { data } = await client.delete(`/gallery/quick-search/${id}`)
    return data
  },
}
