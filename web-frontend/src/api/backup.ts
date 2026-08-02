import client from './client'

export interface BackupRestoreResult {
  success: boolean
  message?: string
}

/**
 * 备份/还原 API（T11 BackupController）：
 *   GET  /api/v1/backup/export?includeDownloads=<bool> → application/zip 流
 *   POST /api/v1/backup/restore → multipart 字段 file → { success, message }
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
}
