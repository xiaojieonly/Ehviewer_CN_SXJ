import client from './client'
import type { Job } from './jobs'

/**
 * 图片缓存 API（ImageCacheService）：
 *   GET  /api/v1/image/cache/status → { cacheSize }（缓存条目数）
 *   POST /api/v1/image/cache/clear  → 202 { jobId, state }（CACHE_CLEAR Job）
 *
 * 清除缓存已异步化为 Job（plan-2026-08-06 A2）：202 响应只携带 Job 的
 * jobId/state 子集，其余字段缺省；进度经 STOMP /topic/jobs/all 推送。
 */

export interface CacheStatus {
  cacheSize: number
}

export const imageApi = {
  /** 缓存条目数（管理页「缓存统计」）。 */
  async getCacheStatus(): Promise<CacheStatus> {
    const { data } = await client.get<CacheStatus>('/image/cache/status')
    return data
  },

  /** 异步清空缓存（202 提交；并发护栏：活跃 CACHE_CLEAR 时 409 抛错）。 */
  async clearCacheAsync(): Promise<Job> {
    const { data } = await client.post<Job>('/image/cache/clear')
    return data
  },
}
