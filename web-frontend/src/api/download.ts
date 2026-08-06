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

export const downloadApi = {
  /**
   * Paginated download list. `offset`/`limit`/`sort` are only sent when
   * defined (callers without paging keep the legacy behaviour); the server
   * clamps `limit` into [1, 500] and falls back to `time_desc` on unknown
   * sort values.
   */
  async list(
    label?: number,
    offset?: number,
    limit?: number,
    sort?: DownloadSort,
  ): Promise<DownloadListResponse> {
    const params: Record<string, string> = {}
    if (label !== undefined) params.label = label.toString()
    if (offset !== undefined) params.offset = offset.toString()
    if (limit !== undefined) params.limit = limit.toString()
    if (sort !== undefined) params.sort = sort
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

  async createLabel(label: string): Promise<boolean> {
    const { data } = await client.post('/download/label', { label })
    return data
  },

  async deleteLabel(id: number): Promise<boolean> {
    const { data } = await client.delete(`/download/label/${id}`)
    return data
  },
}
