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
}

export const historyApi = {
  async listHistory(): Promise<HistoryListResponse> {
    const { data } = await client.get('/history/list')
    return data
  },

  async clearHistory(): Promise<{ success: boolean }> {
    const { data } = await client.delete('/history/clear')
    return data
  },
}
