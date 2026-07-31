import client from './client'

export interface PairCode {
  code: string
  expiresAt: number
}

export interface DeviceInfo {
  deviceId: string
  deviceName: string
  platform: string
  pairedAt: number
  lastSeen: number
}

export const devicesApi = {
  async generatePairCode(): Promise<PairCode> {
    const { data } = await client.post('/auth/pair')
    return data
  },

  async list(): Promise<DeviceInfo[]> {
    const { data } = await client.get('/sync/devices')
    return data
  },

  async revoke(deviceId: string): Promise<void> {
    await client.delete(`/sync/devices/${deviceId}`)
  },
}
