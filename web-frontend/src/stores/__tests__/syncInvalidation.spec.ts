import { describe, it, expect, beforeEach } from 'vitest'
import { nextTick, watch } from 'vue'
import { syncInvalidation } from '../syncInvalidation'

beforeEach(() => {
  // 模块级单例——用例间重置，避免计数跨用例泄漏。
  syncInvalidation.versionMap = {}
})

describe('syncInvalidation (WP8 实时对账)', () => {
  it('bump 逐实体 +1（单调递增）', () => {
    syncInvalidation.bump(['history'])
    expect(syncInvalidation.consume('history')).toBe(1)
    syncInvalidation.bump(['history'])
    expect(syncInvalidation.consume('history')).toBe(2)
  })

  it('consume 未 bump 过的实体缺省 0', () => {
    expect(syncInvalidation.consume('favorites')).toBe(0)
  })

  it('多实体互不影响：bump favorites 不动 history', () => {
    syncInvalidation.bump(['favorites'])
    syncInvalidation.bump(['history', 'downloads'])
    expect(syncInvalidation.consume('favorites')).toBe(1)
    expect(syncInvalidation.consume('history')).toBe(1)
    expect(syncInvalidation.consume('downloads')).toBe(1)
    expect(syncInvalidation.consume('bookmarks')).toBe(0)
  })

  it('一次 bump 携带多实体时各自独立累计', () => {
    syncInvalidation.bump(['downloads', 'downloadLabels'])
    syncInvalidation.bump(['downloads'])
    expect(syncInvalidation.consume('downloads')).toBe(2)
    expect(syncInvalidation.consume('downloadLabels')).toBe(1)
  })

  it('consume 可被 watch 追踪（getter 响应式）', async () => {
    const seen: number[] = []
    const stop = watch(
      () => syncInvalidation.consume('history'),
      (n) => seen.push(n),
    )
    syncInvalidation.bump(['history'])
    await nextTick()
    syncInvalidation.bump(['history'])
    await nextTick()
    stop()
    // watch 默认非 immediate——只记 bump 后的值。
    expect(seen).toEqual([1, 2])
  })

  it('consume 是只读 getter：不产生键、不改变计数', () => {
    expect(syncInvalidation.consume('preferences')).toBe(0)
    expect(syncInvalidation.versionMap).toEqual({})
  })
})
