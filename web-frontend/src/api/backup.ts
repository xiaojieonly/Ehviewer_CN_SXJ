import client from './client'
import type { Job } from './jobs'

/** RESTORE Job 的 result 载荷（A1：{success, message}）。 */
export interface BackupRestoreResult {
  success: boolean
  message?: string
}

/** R4-2: restore 运行态感知（GET /backup/state）。 */
export interface BackupState {
  restorePending: boolean
}

/** B5: EhViewer 备份导入各表成功行数（B3 契约 `imported` 对象键）。 */
export interface EhImportCounts {
  downloads: number
  history: number
  filters: number
  quickSearches: number
  labels: number
  bookmarks: number
  favorites: number
  dirnames: number
  blackList: number
  galleryTags: number
}

/** cookies 段结果：siteDomain=命中站点域的 cookie 数，imported=实际写入 cookieStore 数。 */
export interface EhCookieImportResult {
  imported: number
  siteDomain: number
}

/** IMPORT Job 的 result 载荷（B3 契约）。 */
export interface EhImportResult {
  success: boolean
  imported: EhImportCounts
  cookies: EhCookieImportResult
  skipped: number
}

/**
 * 备份/还原 API（T11 BackupController，plan-2026-08-06 A2）：
 *   GET  /api/v1/backup/export?includeDownloads=<bool> → application/zip 流（同步，兼容保留）
 *   POST /api/v1/backup/export/async?includeDownloads= → 202 { jobId, state }（EXPORT Job）
 *   GET  /api/v1/backup/export/{jobId} → application/zip 流（EXPORT Job COMPLETED 后）
 *   POST /api/v1/backup/restore → multipart 字段 file → 202 { jobId, state }（RESTORE Job）
 *   GET  /api/v1/backup/state → { restorePending }（R4-2 重启横幅）
 *   POST /api/v1/backup/import-ehviewer → multipart file + cookies（可选）→ 202 { jobId, state }（IMPORT Job）
 *
 * 大数据量操作已异步化为 Job：提交端点返回 202 的 Job 子集，进度经 STOMP
 * /topic/jobs/all 推送，终态计数在 Job.result 中。导出需要压缩分片、上传也
 * 可能较大，涉及流式传输的端点禁用 axios 默认 30s 超时。
 */
export const backupApi = {
  /** 同步导出备份 zip（兼容端点），返回 blob 供浏览器触发下载。 */
  async exportBackup(includeDownloads: boolean): Promise<Blob> {
    const { data } = await client.get('/backup/export', {
      params: { includeDownloads },
      responseType: 'blob',
      timeout: 0,
    })
    return data
  },

  /** 异步导出备份（202 提交 EXPORT Job；产物在 Job COMPLETED 后经 downloadExport 下载）。 */
  async exportBackupAsync(includeDownloads: boolean): Promise<Job> {
    const { data } = await client.post<Job>('/backup/export/async', null, {
      params: { includeDownloads },
      timeout: 0,
    })
    return data
  },

  /** 下载异步导出产物（GET /backup/export/{jobId}；job 未 COMPLETED 时 409 抛错）。 */
  async downloadExport(jobId: string): Promise<Blob> {
    const { data } = await client.get(`/backup/export/${jobId}`, {
      responseType: 'blob',
      timeout: 0,
    })
    return data
  },

  /** 上传备份 zip 并异步还原（202 提交 RESTORE Job；覆盖数据库，重启后生效）。 */
  async restoreBackup(file: File): Promise<Job> {
    const form = new FormData()
    form.append('file', file)
    const { data } = await client.post<Job>('/backup/restore', form, {
      // 覆盖 client 默认的 application/json，让浏览器自动带上 multipart boundary。
      headers: { 'Content-Type': undefined },
      timeout: 0,
    })
    return data
  },

  /** R4-2: 读取 restore 运行态（是否"已还原待重启"），供管理页横幅展示。 */
  async getBackupState(): Promise<BackupState> {
    const { data } = await client.get<BackupState>('/backup/state')
    return data
  },

  /**
   * 异步导入原版 EhViewer 备份（202 提交 IMPORT Job；终态计数在 Job.result，
   * 结构见 EhImportResult）。
   * multipart 字段 file（SQLite db，必选）+ cookies（okhttp3-cookie.db 或 JSON 数组，可选）；
   * ?force=false 默认，gid 冲突跳过（upsert 需 force=true）。失败返回统一 M-6 错误信封。
   */
  async importEhViewer(file: File, cookies?: File | null, force = false): Promise<Job> {
    const form = new FormData()
    form.append('file', file)
    if (cookies) form.append('cookies', cookies)
    const { data } = await client.post<Job>('/backup/import-ehviewer', form, {
      params: { force },
      // 覆盖 client 默认的 application/json，让浏览器自动带上 multipart boundary。
      headers: { 'Content-Type': undefined },
      timeout: 0,
    })
    return data
  },
}
