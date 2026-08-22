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
  /** 过滤后全集条数（分页前计数），用于客户端判断是否还有下一页。 */
  total: number
}

export const historyApi = {
  /**
   * 历史列表。`page`（0 起）/`pageSize` 缺省时服务端返回全量（旧调用方兼容）；
   * 传入后走服务端分页（W2-DL F3，pageSize 服务端钳制 1..200）。
   * `q` 为标题 LIKE 子串过滤；`regex=true` 时按正则解释（非法 → 400 REGEX_INVALID），
   * 两种路径均支持分页参数。
   */
  async listHistory(
    q?: string | null,
    regex?: boolean,
    page?: number,
    pageSize?: number,
  ): Promise<HistoryListResponse> {
    const params: Record<string, string> = {}
    if (q !== undefined && q !== null && q !== '') params.q = q
    if (regex) params.regex = 'true'
    if (page !== undefined) params.page = page.toString()
    if (pageSize !== undefined) params.pageSize = pageSize.toString()
    const { data } = await client.get('/history/list', { params })
    return data
  },

  async clearHistory(): Promise<{ success: boolean }> {
    const { data } = await client.delete('/history/clear')
    return data
  },
}
