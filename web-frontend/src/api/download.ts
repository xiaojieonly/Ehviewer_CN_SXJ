import client from './client'
import type { DownloadSort } from '@/utils/downloadListSettings'

export interface DownloadItem {
  id: number
  gid: number
  token: string
  title: string | null
  titleJpn: string | null
  thumb: string | null
  category: number
  state: number
  total: number
  done: number
  label: number
  downloadDir: string | null
  /** Failure/cancel reason; present only when the task is failed (state 4). */
  error?: string | null
}

export interface DownloadLabel {
  id: number
  label: string
  time: number
}

/** Paginated `/download/list` response (plan-2026-08-06 A5). */
export interface DownloadListResponse {
  downloads: DownloadItem[]
  labels: DownloadLabel[]
  /** Total entries under the current label (counted before paging). */
  total: number
}

/** 批量操作目标（Android multi-select）：`all=true` 时忽略 ids，按当前
    (label, q) 过滤条件在服务端解析全集（跨页全选）。`regex=true` 时把 `q`
    解释为正则表达式（槽位筛选的批量目标带该字段）。 */
export interface DownloadBatchTarget {
  ids?: number[]
  all?: boolean
  label?: number | null
  q?: string | null
  regex?: boolean
}

export const downloadApi = {
  /**
   * Paginated download list. `offset`/`limit`/`sort`/`q`/`regex` are only
   * sent when defined; the server clamps `limit` into [1, 500], falls back to
   * `time_desc` on unknown sort values. `q` is a server-side case-insensitive
   * title/titleJpn LIKE search; `regex=true` reinterprets `q` as a regular
   * expression (server-side match + sort, invalid pattern → 400 REGEX_INVALID).
   */
  async list(
    label?: number,
    offset?: number,
    limit?: number,
    sort?: DownloadSort,
    q?: string | null,
    regex?: boolean,
  ): Promise<DownloadListResponse> {
    const params: Record<string, string> = {}
    if (label !== undefined) params.label = label.toString()
    if (offset !== undefined) params.offset = offset.toString()
    if (limit !== undefined) params.limit = limit.toString()
    if (sort !== undefined) params.sort = sort
    if (q !== undefined && q !== null && q !== '') params.q = q
    if (regex) params.regex = 'true'
    const { data } = await client.get('/download/list', { params })
    return data
  },

  async getInfo(id: number): Promise<DownloadItem> {
    const { data } = await client.get(`/download/info/${id}`)
    return data
  },

  async add(gid: number, token: string, title: string | null, thumb: string | null, label: number = 0): Promise<boolean> {
    const { data } = await client.post('/download/add', { gid, token, title, thumb, label })
    return data
  },

  async start(id: number): Promise<boolean> {
    const { data } = await client.post(`/download/start/${id}`)
    return data
  },

  async startAll(): Promise<boolean> {
    const { data } = await client.post('/download/start-all')
    return data
  },

  async pause(id: number): Promise<boolean> {
    const { data } = await client.post(`/download/pause/${id}`)
    return data
  },

  async cancel(id: number): Promise<boolean> {
    const { data } = await client.post(`/download/cancel/${id}`)
    return data
  },

  async delete(id: number): Promise<boolean> {
    const { data } = await client.delete(`/download/delete/${id}`)
    return data
  },

  // ── batch (Android multi-select Start/Stop/Delete/Move) ──────

  async startRange(target: DownloadBatchTarget): Promise<number> {
    const { data } = await client.post('/download/start-range', target)
    return data
  },

  async stopRange(target: DownloadBatchTarget): Promise<number> {
    const { data } = await client.post('/download/stop-range', target)
    return data
  },

  async deleteRange(target: DownloadBatchTarget): Promise<number> {
    const { data } = await client.post('/download/delete-range', target)
    return data
  },

  async move(target: DownloadBatchTarget & { labelId: number }): Promise<number> {
    const { data } = await client.post('/download/move', target)
    return data
  },

  async createLabel(label: string): Promise<boolean> {
    const { data } = await client.post('/download/label', { label })
    return data
  },

  async deleteLabel(id: number): Promise<boolean> {
    const { data } = await client.delete(`/download/label/${id}`)
    return data
  },
}
