/**
 * 下载列表的设备本地偏好（下载页 / AdminDownload 共用）。
 *
 * 存储于 localStorage `anotherviewer-admin-download-ui`（与 AdminDownload 的
 * 其他本地设置同键）；本模块只负责列表相关字段（排序模式 + 每页条数），
 * 并容忍旧版键迁移：
 * - 旧 `sortAscending: boolean`（从未接线）→ 迁移为 sortMode（false=time_desc、
 *   true=time_asc）；
 * - 旧 `paginated` 键忽略并随下次写入清除。
 */
export type DownloadSort = 'time_desc' | 'time_asc' | 'title_asc' | 'title_desc'

export interface DownloadListPrefs {
  sortMode: DownloadSort
  pageSize: number
}

export const DOWNLOAD_UI_KEY = 'anotherviewer-admin-download-ui'

/** 排序模式选项（AdminDownload 选择器 + DownloadView 消费）。 */
export const DOWNLOAD_SORT_OPTIONS: { value: DownloadSort; label: string }[] = [
  { value: 'time_desc', label: '添加时间（最新在前）' },
  { value: 'time_asc', label: '添加时间（最早在前）' },
  { value: 'title_asc', label: '标题（A → Z）' },
  { value: 'title_desc', label: '标题（Z → A）' },
]

/** 每页条数选项——与 Android 端 perPageCountChoices 对齐（默认 50 条/页）。
 *  服务端 listDownloads limit 上限 500，300/500 亦受支持。 */
export const DOWNLOAD_PAGE_SIZES = [50, 100, 200, 300, 500] as const

export const DEFAULT_DOWNLOAD_LIST_PREFS: DownloadListPrefs = {
  sortMode: 'time_desc',
  pageSize: 50,
}

export function isDownloadPageSize(value: unknown): value is number {
  return typeof value === 'number' && (DOWNLOAD_PAGE_SIZES as readonly number[]).includes(value)
}

/** 读取下载列表偏好；损坏/缺失时回落默认值。 */
export function loadDownloadListPrefs(): DownloadListPrefs {
  try {
    const raw = localStorage.getItem(DOWNLOAD_UI_KEY)
    if (raw) {
      const stored = JSON.parse(raw) as Partial<DownloadListPrefs> & { sortAscending?: boolean }
      const sortMode: DownloadSort =
        stored.sortMode === 'time_desc' ||
        stored.sortMode === 'time_asc' ||
        stored.sortMode === 'title_asc' ||
        stored.sortMode === 'title_desc'
          ? stored.sortMode
          : stored.sortAscending === true
            ? 'time_asc'
            : DEFAULT_DOWNLOAD_LIST_PREFS.sortMode
      const pageSize = isDownloadPageSize(stored.pageSize)
        ? stored.pageSize
        : DEFAULT_DOWNLOAD_LIST_PREFS.pageSize
      return { sortMode, pageSize }
    }
  } catch {
    // Corrupt/unavailable storage — fall back to defaults.
  }
  return { ...DEFAULT_DOWNLOAD_LIST_PREFS }
}

/** 保存下载列表偏好（写回与读取相同的 localStorage 键；存储不可用则静默）。 */
export function saveDownloadListPrefs(prefs: DownloadListPrefs): void {
  try {
    localStorage.setItem(
      DOWNLOAD_UI_KEY,
      JSON.stringify({ sortMode: prefs.sortMode, pageSize: prefs.pageSize }),
    )
  } catch {
    // Storage unavailable / full — the in-memory list keeps working.
  }
}
