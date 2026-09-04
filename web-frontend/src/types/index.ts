export interface GalleryInfo {
  gid: number
  token: string
  title: string
  titleJpn: string
  thumb: string
  category: number
  posted: string
  uploader: string
  rating: number
  rated: boolean
  simpleLanguage: string
  simpleTags: string[]
  thumbWidth: number
  thumbHeight: number
  pages: number
  favoriteSlot: number
  favoriteName: string
  /** Server-generated gallery detail URL (share/copy link). */
  galleryUrl?: string
  /**
   * 阅读进度（0 起页索引，plan-2026-09-02 D1/D2）。服务器从历史行带出
   * （GalleryDetail extends GalleryInfo 自动获得）；旧服务器 / 上游直取路径
   * 不下发该字段 → undefined，运行时必须容错（§6 兼容矩阵）。
   */
  readProgress?: number
}

export interface GalleryDetail extends GalleryInfo {
  tags: TagInfo[]
  imageUrl: string
  /** 站点真实评论（详情接口随画廊返回；本地历史路径为空）。 */
  comments?: Array<{
    id: number
    uploader: string
    comment: string
    time: string
    score: number
  }>
  /** Upstream failure marker (plan-2026-08-30 §4.1); `"EH_UNAVAILABLE"` means
   *  the EH platform is unreachable (see {@link ListCause}). */
  cause?: ListCause | null
}

export interface TagInfo {
  namespace: string
  tag: string
}

export interface DownloadInfo {
  id: number
  gid: number
  token: string
  title: string
  titleJpn: string
  thumb: string
  category: number
  state: number
  total: number
  done: number
  label: number
  downloadDir: string
}

export interface QuickSearch {
  id: number
  name: string
  mode: number
  category: number
  keyword: string
  advanceSearch: number
  minRating: number
  pageFrom: number
  pageTo: number
  /**
   * Sort order persisted with the preset (W3 R4-11, contracts
   * QuickSearchDto.sort): 0 default / 1 posted desc / 2 rating desc /
   * 3 title asc. Optional — absent on legacy server rows and on device-local
   * presets saved before W3 (reads as 0).
   */
  sort?: number
}

export interface AuthResponse {
  success: boolean
  message: string
  token?: string
  username?: string
}

export interface AuthStatusResponse {
  authenticated: boolean
  username?: string
}

/**
 * Upstream failure marker (plan-2026-08-30 §4.1): list-class endpoints keep
 * the `success:false` envelope and report the circuit-breaker via this
 * optional field instead of an HTTP error code. `"EH_UNAVAILABLE"` means the
 * EH platform is currently unreachable (client shows the local-only tip).
 */
export type ListCause = 'EH_UNAVAILABLE' | string

export interface GalleryListResponse {
  success: boolean
  data: GalleryInfo[]
  total: number
  cause?: ListCause | null
}

/** One ranked entry of the toplist feed (GET /gallery/feed?mode=toplist). */
export interface TopListItem {
  /** 站点解析通常为 null；打码模式下由服务端回填（href 提取的 gid）。 */
  gid: number | string | null
  token: string | null
  tag: string | null
  value: string | null
  href: string | null
}

/** Response envelope of the toplist feed — data rows differ from GalleryInfo. */
export interface TopListResponse {
  success: boolean
  data: TopListItem[]
  total: number
  cause?: ListCause | null
}

