import { beforeEach, describe, expect, it } from 'vitest'
import {
  DOWNLOAD_UI_KEY,
  DEFAULT_DOWNLOAD_LIST_PREFS,
  DOWNLOAD_PAGE_SIZES,
  isDownloadPageSize,
  loadDownloadListPrefs,
  type DownloadListPrefs,
} from '../downloadListSettings'

beforeEach(() => {
  localStorage.clear()
})

function seed(stored: unknown): void {
  localStorage.setItem(
    DOWNLOAD_UI_KEY,
    typeof stored === 'string' ? stored : JSON.stringify(stored),
  )
}

describe('loadDownloadListPrefs — 旧 sortAscending 键迁移（表驱动）', () => {
  it.each([
    {
      name: 'sortAscending:true → time_asc',
      stored: { sortAscending: true },
      expected: { sortMode: 'time_asc', pageSize: 50 },
    },
    {
      name: 'sortAscending:true 且合法 pageSize 一并保留',
      stored: { sortAscending: true, pageSize: 200 },
      expected: { sortMode: 'time_asc', pageSize: 200 },
    },
    {
      name: 'sortAscending:false → 默认 time_desc',
      stored: { sortAscending: false },
      expected: { sortMode: 'time_desc', pageSize: 50 },
    },
    {
      name: '真值但非 boolean（"yes"）不迁移 → time_desc',
      stored: { sortAscending: 'yes' },
      expected: { sortMode: 'time_desc', pageSize: 50 },
    },
    {
      name: '真值但非 boolean（1）不迁移 → time_desc',
      stored: { sortAscending: 1 },
      expected: { sortMode: 'time_desc', pageSize: 50 },
    },
])('$name', ({ stored, expected }) => {
    seed(stored)
    expect(loadDownloadListPrefs()).toEqual(expected)
  })
})

describe('loadDownloadListPrefs — sortMode 合法值优先于旧键', () => {
  it.each([
    {
      name: '合法 sortMode 存在时忽略 sortAscending:true',
      stored: { sortMode: 'title_desc', sortAscending: true },
      expected: { sortMode: 'title_desc', pageSize: 50 },
    },
    {
      name: '非法 sortMode 回落到 sortAscending:true → time_asc',
      stored: { sortMode: 'newest_first', sortAscending: true },
      expected: { sortMode: 'time_asc', pageSize: 50 },
    },
    {
      name: '非法 sortMode 且无旧键 → 默认 time_desc',
      stored: { sortMode: 42 },
      expected: { sortMode: 'time_desc', pageSize: 50 },
    },
    {
      name: 'sortMode:null 视为非法 → 默认 time_desc',
      stored: { sortMode: null },
      expected: { sortMode: 'time_desc', pageSize: 50 },
    },
    {
      name: '空对象 → 全默认',
      stored: {},
      expected: { sortMode: 'time_desc', pageSize: 50 },
    },
  ])('$name', ({ stored, expected }) => {
    seed(stored)
    expect(loadDownloadListPrefs()).toEqual(expected)
  })
})

describe('loadDownloadListPrefs — 旧 paginated 键忽略', () => {
  it.each([
    {
      name: 'paginated:true 不影响其余字段读取',
      stored: { paginated: true, sortMode: 'time_asc', pageSize: 100 },
      expected: { sortMode: 'time_asc', pageSize: 100 },
    },
    {
      name: 'paginated 与旧 sortAscending 并存时仍走迁移',
      stored: { paginated: 'false', sortAscending: true },
      expected: { sortMode: 'time_asc', pageSize: 50 },
    },
  ])('$name', ({ stored, expected }) => {
    seed(stored)
    const prefs: DownloadListPrefs = loadDownloadListPrefs()
    expect(prefs).toEqual(expected)
    expect(Object.keys(prefs).sort()).toEqual(['pageSize', 'sortMode'])
  })

  it('loader 只读：不清除存储中的 paginated（清除发生在下次写入）', () => {
    const legacy = JSON.stringify({ paginated: true, sortAscending: true })
    localStorage.setItem(DOWNLOAD_UI_KEY, legacy)
    loadDownloadListPrefs()
    expect(localStorage.getItem(DOWNLOAD_UI_KEY)).toBe(legacy)
  })
})

describe('loadDownloadListPrefs — 非法 pageSize 回落默认（表驱动）', () => {
  it.each([
    { name: '越界数字 37', stored: { pageSize: 37 }, expectedPageSize: 50 },
    { name: '字符串 "50"', stored: { pageSize: '50' }, expectedPageSize: 50 },
    { name: 'null', stored: { pageSize: null }, expectedPageSize: 50 },
    { name: 'boolean true', stored: { pageSize: true }, expectedPageSize: 50 },
    { name: '非法 JSON（裸 NaN token）', stored: '{"pageSize":NaN}', expectedPageSize: 50 },
  ])('$name → 默认 50', ({ stored, expectedPageSize }) => {
    seed(stored)
    expect(loadDownloadListPrefs().pageSize).toBe(expectedPageSize)
  })

  it.each([50, 100, 200])('合法每页条数 %i 原样保留', (size) => {
    expect(DOWNLOAD_PAGE_SIZES).toContain(size)
    seed({ pageSize: size, sortMode: 'title_asc' })
    expect(loadDownloadListPrefs()).toEqual({ sortMode: 'title_asc', pageSize: size })
  })
})

describe('loadDownloadListPrefs — 存储缺失/损坏回落默认', () => {
  it('键不存在 → 默认值', () => {
    expect(localStorage.getItem(DOWNLOAD_UI_KEY)).toBeNull()
    expect(loadDownloadListPrefs()).toEqual(DEFAULT_DOWNLOAD_LIST_PREFS)
  })

  it.each([
    { name: '非 JSON 文本', raw: 'not-json{{' },
    { name: '"null" 字面量（属性访问抛错路径）', raw: 'null' },
    { name: 'JSON 数字标量', raw: '42' },
    { name: 'JSON 数组', raw: '[]' },
  ])('$name → 默认值', ({ raw }) => {
    seed(raw)
    expect(loadDownloadListPrefs()).toEqual(DEFAULT_DOWNLOAD_LIST_PREFS)
  })

  it('每次调用返回全新对象，互不影响也不污染默认常量', () => {
    const first = loadDownloadListPrefs()
    first.sortMode = 'title_desc'
    first.pageSize = 1
    expect(loadDownloadListPrefs()).toEqual(DEFAULT_DOWNLOAD_LIST_PREFS)
    expect(DEFAULT_DOWNLOAD_LIST_PREFS).toEqual({ sortMode: 'time_desc', pageSize: 50 })
  })
})

describe('isDownloadPageSize', () => {
  it.each([
    { value: 50, expected: true },
    { value: 100, expected: true },
    { value: 200, expected: true },
    { value: 37, expected: false },
    { value: '50', expected: false },
    { value: null, expected: false },
    { value: undefined, expected: false },
  ])('$value → $expected', ({ value, expected }) => {
    expect(isDownloadPageSize(value)).toBe(expected)
  })
})
