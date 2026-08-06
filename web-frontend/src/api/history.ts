import client from './client'

export interface HistoryItem {
  gid: number
  token: string
  title: string
  titleJpn: string
  thumb: string
  category: number
  rating: number
  mode: number
  time: number
}

export interface HistoryListResponse {
  history: HistoryItem[]
  total: number
}

export const historyApi = {
  async listHistory(q?: string | null, regex?: boolean): Promise<HistoryListResponse> {
    const params: Record<string, string> = {}
    if (q !== undefined && q !== null && q !== '') params.q = q
    if (regex) params.regex = 'true'
    const { data } = await client.get('/history/list', { params })
    return data
  },

  async clearHistory(): Promise<{ success: boolean }> {
    const { data } = await client.delete('/history/clear')
    return data
  },
}
