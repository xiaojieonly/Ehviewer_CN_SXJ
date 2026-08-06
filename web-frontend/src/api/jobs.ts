import client from './client'

/**
 * 统一后台任务（Job）共享类型（plan-2026-08-06 A1/A2）。
 *
 * 契约：`POST .../async` 类端点返回 202 `{jobId, state}`（Job 子集），随后经
 * STOMP `/topic/jobs/all`（+`/topic/jobs/{jobId}`）推送进度，跨刷新可用
 * `getJob` / `getActiveJob` 恢复。
 */

/** Job 业务类型（A1）。 */
export type JobType = 'IMPORT' | 'EXPORT' | 'RESTORE' | 'CACHE_CLEAR'

/** Job 生命周期状态（A1）。 */
export type JobState = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'

/** Job JSON schema（A1）。 */
export interface Job {
  jobId: string
  type: JobType
  state: JobState
  stage: string | null
  /** 0~100（total=0 时恒为 0，worker 端计算）。 */
  percent: number
  processed: number
  total: number
  startedAt: number | null
  completedAt: number | null
  error: string | null
  /** 按 type 结构化的终态结果（A1：IMPORT/EXPORT/RESTORE/CACHE_CLEAR 各不同）。 */
  result: unknown
}

export const jobsApi = {
  /** GET /api/v1/jobs/{jobId} — 未知 jobId 404 抛错，调用方 catch 处理。 */
  async getJob(jobId: string): Promise<Job> {
    const { data } = await client.get<Job>(`/jobs/${jobId}`)
    return data
  },

  /** GET /api/v1/jobs/active?type= — 无活跃任务返回 404 抛错，调用方 catch 视为无活跃。 */
  async getActiveJob(type: JobType): Promise<Job> {
    const { data } = await client.get<Job>('/jobs/active', { params: { type } })
    return data
  },
}
