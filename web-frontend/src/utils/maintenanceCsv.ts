/**
 * 冗余清单导出 CSV 纯函数（2026-08-31 新功能）。
 *
 * 本地生成（无需后端新端点）：预览数据 → 带 UTF-8 BOM 的 CSV 字符串，
 * 双引号转义（Excel/WPS 中文字段安全）。分类备注对齐
 * DownloadMaintenanceService 判定语义：杂项（非 gid 前缀）/ 副本（尾缀
 * -1/-2…）/ 磁盘-only（无行引用）。
 */
import type { MaintenanceFileIssue } from '@/api/download'

/** 单行单元格输出模板（引号包裹 + 内部引号转义）。 */
function cell(v: string | number): string {
  return `"${String(v).replace(/"/g, '""')}"`
}

/** 可读字节（区别于展示层 formatBytes 的「，释放 …」前缀文案）。 */
export function readableBytes(bytes: number): string {
  if (bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let value = bytes
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit += 1
  }
  const text = value >= 100 || unit === 0 ? Math.round(value).toString() : value.toFixed(2)
  return `${text} ${units[unit]}`
}

/** 冗余类别备注（对齐 DownloadMaintenanceService.scanRedundant 语义）。 */
export function classifyRedundant(path: string): string {
  const gid = path.split('-')[0]
  if (!/^\d+$/.test(gid)) return '杂项/非下载布局'
  if (/^.*-(\d+)$/.test(path)) return '副本（同 gid 其他副本保留）'
  return '磁盘-only（无下载行引用）'
}

/**
 * 构造冗余 CSV（含表头与合计行，UTF-8 BOM 前缀）。
 */
export function buildMaintenanceCsv(files: MaintenanceFileIssue[]): string {
  const totalBytes = files.reduce((sum, f) => sum + f.sizeBytes, 0)
  const rows: (string | number)[][] = [
    ['路径', '大小（字节）', '大小（可读）', 'gid', '判断备注'],
    ...files.map((f) => [
      f.path,
      f.sizeBytes,
      readableBytes(f.sizeBytes),
      (f.path.split('-')[0] ?? '').replace(/^\D+/, '') || '',
      classifyRedundant(f.path),
    ]),
    [],
    ['合计', totalBytes, readableBytes(totalBytes), files.length, '项'],
  ]
  const csv = rows.map((r) => r.map(cell).join(',')).join('\r\n')
  return '\uFEFF' + csv
}

/** 下载文件名：anotherviewer-redundant-<日期>.csv */
export function csvFileName(stamp: string): string {
  return `anotherviewer-redundant-${stamp}.csv`
}
