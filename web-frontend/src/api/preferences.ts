import client from './client'

export interface GeneralPreferences {
  theme: string
  themeAutoSwitch: boolean
  launchPage: string
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
}

export interface PrivacyPreferences {
  enableAnalytics: boolean
}

export interface Preferences {
  general: GeneralPreferences
  reader: ReaderPreferences
  privacy: PrivacyPreferences
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
