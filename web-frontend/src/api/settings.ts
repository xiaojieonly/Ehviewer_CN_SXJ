import client from './client'

export interface DownloadSettings {
  path: string
  workerCount: number
  downloadDelay: number
  downloadTimeout: number
  maxConcurrentGalleries: number
  maxConcurrentImages: number
}

export interface CacheSettings {
  path: string
  sizeMb: number
}

export interface SmbSettings {
  enabled: boolean
}

export interface Settings {
  download: DownloadSettings
  cache: CacheSettings
  smb: SmbSettings
}

export const settingsApi = {
  async get(): Promise<Settings> {
    const { data } = await client.get('/settings')
    return data
  },

  async update(settings: Partial<Settings>): Promise<boolean> {
    const { data } = await client.put('/settings', settings)
    return data
  },
}
