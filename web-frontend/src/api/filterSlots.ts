import client from './client'

/** 筛选槽位（命名正则预设）：id 由前端生成（服务端仅透传），整体经 PUT 全量替换。 */
export interface FilterSlot {
  id: string
  name: string
  pattern: string
}

export const filterSlotsApi = {
  /** GET /api/v1/download/slots → {slots} → 返回 slots（未配置时为空数组）。 */
  async get(): Promise<FilterSlot[]> {
    const { data } = await client.get<{ slots: FilterSlot[] }>('/download/slots')
    return data.slots ?? []
  },

  /** PUT /api/v1/download/slots {slots} → 回显存储后的 slots。 */
  async put(slots: FilterSlot[]): Promise<FilterSlot[]> {
    const { data } = await client.put<{ slots: FilterSlot[] }>('/download/slots', { slots })
    return data.slots ?? []
  },
}
