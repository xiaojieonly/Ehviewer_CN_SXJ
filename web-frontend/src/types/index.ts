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
}

export interface GalleryDetail extends GalleryInfo {
  tags: TagInfo[]
  imageUrl: string
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

export interface GalleryListResponse {
  success: boolean
  data: GalleryInfo[]
  total: number
}

