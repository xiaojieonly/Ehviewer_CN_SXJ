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

export interface SecuritySettings {
  requireAuth: boolean
  sessionTimeout: number
}

export interface ProcessingSettings {
  enabled: boolean
  defaultType: string
  outputFormat: string
  outputQuality: number
}

export interface ProxySettings {
  enabled: boolean
  type: string
  host: string
  port: number
  username: string
  /** Backend echoes "" here — never the stored value; omit on PUT to keep the stored password. */
  password: string
  /** True when a proxy password is stored server-side (GET only). */
  proxyPasswordSet?: boolean
}

export interface Settings {
  download: DownloadSettings
  cache: CacheSettings
  smb: SmbSettings
  security: SecuritySettings
  processing: ProcessingSettings
  proxy: ProxySettings
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
