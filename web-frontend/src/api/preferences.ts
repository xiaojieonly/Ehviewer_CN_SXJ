import client from './client'

export interface GeneralPreferences {
  theme: string
  themeAutoSwitch: boolean
  launchPage: string
  /** grid|list — 画廊列表默认布局（默认 grid） */
  listMode: string
  showReadProgress: boolean
  detailSize: string
  thumbSize: string
  historyInfoSize: number
  showJpnTitle: boolean
  showGalleryPages: boolean
  showTagTranslations: boolean
  showGalleryComment: boolean
  showGalleryRating: boolean
  showEhEvents: boolean
  showEhLimits: boolean
  /** Wave-1 B-2: 画廊卡片显示上传者 */
  showUploader: boolean
  /** Wave-1 B-2: 画廊卡片显示发布时间 */
  showPostedTime: boolean
  /** Wave-1 B-3: 默认收藏槽，clamp -2..9 */
  defaultFavoriteSlot: number
  /** Wave-1 B-4: `|` 分隔的 10 个收藏槽名，空项回退默认 */
  favoriteSlotNames: string
  /** Wave-1 B-5: 最近搜索保留条数，0 = 关闭 */
  recentSearchMax: number
}

export interface ReaderPreferences {
  readingDirection: string
  pageMode: string
  firstPageCover: boolean
  pageScaling: string
  startPosition: string
  autoPlayIntervalSec: number
  showProgress: boolean
  showPageInterval: boolean
  fullscreen: boolean
  brightness: number
  /** Wave-1 A: black|gray|white */
  backgroundColor: string
  /** Wave-1 A: threeZone|edgeOnly|disabled */
  tapZoneScheme: string
  /** Wave-1 A: 键盘翻页 */
  keyboardPaging: boolean
  /** Wave-1 A: 缩放步进（>1） */
  zoomStep: number
  /** Wave-1 A: 最大缩放（≥1） */
  maxZoom: number
  /** Wave-1 A: 双页间距 px（≥0） */
  dualPageGap: number
  /** Wave-1 A: 拆分宽页 */
  splitWidePages: boolean
  /** Wave-1 A: 预加载页数（≥0） */
  preloadCount: number
  /** Wave-1 A: slide|fade|none */
  pageTransition: string
}

export interface PrivacyPreferences {
  enableAnalytics: boolean
}

export interface Preferences {
  general: GeneralPreferences
  reader: ReaderPreferences
  privacy: PrivacyPreferences
}

/**
 * 与后端 PreferenceDto.kt（PreferenceResponse）保持一致的缺省值 ——
 * Wave-1 新键加入后同步更新；测试夹具与回退逻辑共用。
 */
export const DEFAULT_GENERAL_PREFERENCES: GeneralPreferences = {
  theme: 'light',
  themeAutoSwitch: false,
  launchPage: 'homepage',
  listMode: 'grid',
  showReadProgress: true,
  detailSize: 'long',
  thumbSize: 'middle',
  historyInfoSize: 100,
  showJpnTitle: false,
  showGalleryPages: false,
  showTagTranslations: true,
  showGalleryComment: true,
  showGalleryRating: true,
  showEhEvents: true,
  showEhLimits: true,
  showUploader: false,
  showPostedTime: false,
  defaultFavoriteSlot: 0,
  favoriteSlotNames: '',
  recentSearchMax: 10,
}

export const DEFAULT_READER_PREFERENCES: ReaderPreferences = {
  readingDirection: 'rtl',
  pageMode: 'dual',
  firstPageCover: true,
  pageScaling: 'fit',
  startPosition: 'top_right',
  autoPlayIntervalSec: 2,
  showProgress: true,
  showPageInterval: true,
  fullscreen: true,
  brightness: 0,
  backgroundColor: 'black',
  tapZoneScheme: 'threeZone',
  keyboardPaging: true,
  zoomStep: 1.5,
  maxZoom: 5,
  dualPageGap: 8,
  splitWidePages: false,
  preloadCount: 2,
  pageTransition: 'slide',
}

export const DEFAULT_PRIVACY_PREFERENCES: PrivacyPreferences = {
  enableAnalytics: true,
}

export const DEFAULT_PREFERENCES: Preferences = {
  general: DEFAULT_GENERAL_PREFERENCES,
  reader: DEFAULT_READER_PREFERENCES,
  privacy: DEFAULT_PRIVACY_PREFERENCES,
}

export const preferencesApi = {
  async get(): Promise<Preferences> {
    const { data } = await client.get('/preferences')
    return data
  },
  async update(prefs: Partial<Preferences>): Promise<Preferences> {
    const { data } = await client.put('/preferences', prefs)
    return data
  },
}
