import { describe, it, expect } from 'vitest'
import { buildMaintenanceCsv, classifyRedundant, readableBytes, csvFileName } from '../maintenanceCsv'

describe('maintenanceCsv', () => {
  it('classifies paths per the maintenance semantics', () => {
    expect(classifyRedundant('777')).toBe('磁盘-only（无下载行引用）')
    expect(classifyRedundant('147999-[title] サンプル-1')).toBe('副本（同 gid 其他副本保留）')
    expect(classifyRedundant('misc-notes')).toBe('杂项/非下载布局')
    expect(classifyRedundant('stray.tmp')).toBe('杂项/非下载布局')
  })

  it('builds a BOM-prefixed CSV with header, rows and totals', () => {
    const csv = buildMaintenanceCsv([
      { path: '777', sizeBytes: 1024 * 1024 },
      { path: '888-[タイトル]",sample', sizeBytes: 2048 },
    ])
    expect(csv.startsWith('\uFEFF"路径"')).toBe(true)
    expect(csv).toContain('"777"')
    expect(csv).toContain('"1.00 MB"')
    expect(csv).toContain('"888-[タイトル]"",sample"') // 内部引号已转义
    expect(csv).toContain('"合计"')
    expect(csv).toContain('"1050624"')
    expect(csv).not.toContain('undefined')
  })

  it('formats readable bytes', () => {
    expect(readableBytes(0)).toBe('0 B')
    expect(readableBytes(1023)).toBe('1023 B')
    expect(readableBytes(2048)).toBe('2.00 KB')
    expect(readableBytes(1024 * 1024)).toBe('1.00 MB')
  })

  it('names the file with the given stamp', () => {
    expect(csvFileName('2026-08-31')).toBe('anotherviewer-redundant-2026-08-31.csv')
  })
})
