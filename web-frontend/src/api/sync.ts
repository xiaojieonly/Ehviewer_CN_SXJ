import client from './client'

/** SyncPolicy (contracts/openapi.yaml SyncPolicy, ADR-0003 / contract v2 §8). */
export interface SyncPolicy {
  conflictStrategy: 'device_priority' | 'lww' | 'web_priority'
  clientTier: 0 | 1 | 2 | 3
  autoSyncIntervalSec: number
}

export const syncApi = {
  async getPolicy(): Promise<SyncPolicy> {
    const { data } = await client.get<SyncPolicy>('/sync/policy')
    return data
  },

  async updatePolicy(policy: SyncPolicy): Promise<SyncPolicy> {
    const { data } = await client.put<SyncPolicy>('/sync/policy', policy)
    return data
  },
}
