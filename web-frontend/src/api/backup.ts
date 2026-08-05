import client from './client'

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

/** POST /backup/import-ehviewer 响应（B3 契约）。 */
export interface EhImportResult {
  success: boolean
  imported: EhImportCounts
  cookies: EhCookieImportResult
  skipped: number
}

/**
 * 备份/还原 API（T11 BackupController）：
 *   GET  /api/v1/backup/export?includeDownloads=<bool> → application/zip 流
 *   POST /api/v1/backup/restore → multipart 字段 file → { success, message }
 *   GET  /api/v1/backup/state → { restorePending }（R4-2 重启横幅）
 *   POST /api/v1/backup/import-ehviewer → multipart file + cookies（可选）→ B3 计数契约
 *
 * 导出需要压缩分片、上传也可能较大，两处均禁用 axios 默认 30s 超时。
 */
export const backupApi = {
  /** 导出备份 zip，返回 blob 供浏览器触发下载。 */
  async exportBackup(includeDownloads: boolean): Promise<Blob> {
    const { data } = await client.get('/backup/export', {
      params: { includeDownloads },
      responseType: 'blob',
      timeout: 0,
    })
    return data
  },

  /** 上传备份 zip 并还原（覆盖数据库，重启后生效）。 */
  async restoreBackup(file: File): Promise<BackupRestoreResult> {
    const form = new FormData()
    form.append('file', file)
    const { data } = await client.post<BackupRestoreResult>('/backup/restore', form, {
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
   * B5: 导入原版 EhViewer 备份（B3 契约）。
   * multipart 字段 file（SQLite db，必选）+ cookies（okhttp3-cookie.db 或 JSON 数组，可选）；
   * ?force=false 默认，gid 冲突跳过（upsert 需 force=true）。失败返回统一 M-6 错误信封。
   */
  async importEhViewer(file: File, cookies?: File | null, force = false): Promise<EhImportResult> {
    const form = new FormData()
    form.append('file', file)
    if (cookies) form.append('cookies', cookies)
    const { data } = await client.post<EhImportResult>('/backup/import-ehviewer', form, {
      params: { force },
      // 覆盖 client 默认的 application/json，让浏览器自动带上 multipart boundary。
      headers: { 'Content-Type': undefined },
      timeout: 0,
    })
    return data
  },
}
