import client from './client'

export interface SmbConfig {
  id: number
  host: string
  port: number
  share: string
  path: string | null
  loginMode: string
  username: string | null
  enabled: boolean
}

export interface SmbTestResult {
  success: boolean
  message: string
}

export interface SmbSyncProgress {
  state: string
  totalFiles: number
  syncedFiles: number
  currentFile: string
  speed: number
}

export const smbApi = {
  async getConfig(): Promise<SmbConfig | null> {
    const { data } = await client.get('/smb/config')
    return data
  },

  async updateConfig(config: Partial<SmbConfig> & { password?: string }): Promise<boolean> {
    const { data } = await client.put('/smb/config', config)
    return data
  },

  async testConnection(config: Partial<SmbConfig> & { password?: string }): Promise<SmbTestResult> {
    const { data } = await client.post('/smb/test-connection', config)
    return data
  },

  async sync(aggressive: boolean = false): Promise<boolean> {
    const { data } = await client.post('/smb/sync', { aggressive })
    return data
  },

  async cancel(): Promise<boolean> {
    const { data } = await client.post('/smb/cancel')
    return data
  },

  async getProgress(): Promise<SmbSyncProgress> {
    const { data } = await client.get('/smb/progress')
    return data
  },
}
