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

export const CATEGORY_MAP: Record<number, string> = {
  0: 'All',
  1: 'Doujinshi',
  2: 'Manga',
  4: 'Artist CG',
  8: 'Western',
  16: 'Non-H',
  32: 'Image Set',
  64: 'Cosplay',
  128: 'Asian Porn',
  256: 'Misc',
  512: 'Unknown',
}

export const CATEGORY_COLORS: Record<number, string> = {
  1: '#e74c3c',
  2: '#3498db',
  4: '#2ecc71',
  8: '#f39c12',
  16: '#9b59b6',
  32: '#1abc9c',
  64: '#e67e22',
  128: '#95a5a6',
  256: '#7f8c8d',
  512: '#bdc3c7',
}
