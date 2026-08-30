import { describe, it, expect, vi, beforeEach } from 'vitest'
import { siteApi } from '@/api/site'
import {
  availability,
  loadAvailability,
  markDown,
  markUnknown,
  probeAvailability,
  EH_UNAVAILABLE_MESSAGE,
} from '../availability'

vi.mock('@/api/site', () => ({
  siteApi: { getAvailability: vi.fn(), probeAvailability: vi.fn() },
}))

/** 模块级单例在 spec 间持久——每例重置字段恢复初始状态。 */
function resetStore(): void {
  availability.state = null
  availability.downAt = null
  availability.lastReason = null
  availability.lastLoadedAt = null
}

beforeEach(() => {
  resetStore()
  vi.mocked(siteApi.getAvailability).mockReset()
  vi.mocked(siteApi.probeAvailability).mockReset()
})

describe('availability 单例状态机（plan-2026-08-30 §3.1 前端镜像）', () => {
  it('load from a DOWN server sets state=down with downAt/lastReason', async () => {
    vi.mocked(siteApi.getAvailability).mockResolvedValue({
      state: 'DOWN',
      downAt: 1234,
      lastReason: 'connect timeout',
    })
    await loadAvailability()
    expect(availability.state).toBe('down')
    expect(availability.downAt).toBe(1234)
    expect(availability.lastReason).toBe('connect timeout')
    expect(availability.lastLoadedAt).not.toBeNull()
  })

  it('load from an UP server sets state=up and clears the down markers', async () => {
    markDown('boom')
    vi.mocked(siteApi.getAvailability).mockResolvedValue({ state: 'UP' })
    await loadAvailability()
    expect(availability.state).toBe('up')
    expect(availability.downAt).toBeNull()
    expect(availability.lastReason).toBeNull()
  })

  it('load failure keeps the current knowledge (no markDown from a GET error)', async () => {
    markDown('keep me')
    vi.mocked(siteApi.getAvailability).mockRejectedValue(new Error('server down'))
    const warn = vi.spyOn(console, 'error').mockImplementation(() => {})
    await loadAvailability()
    expect(availability.state).toBe('down')
    expect(availability.lastReason).toBe('keep me')
    warn.mockRestore()
  })

  it('is idempotent: concurrent loads share one in-flight request', async () => {
    let resolveGet: () => void = () => {}
    vi.mocked(siteApi.getAvailability).mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveGet = () => resolve({ state: 'UP' })
        }),
    )
    const first = loadAvailability()
    const second = loadAvailability()
    expect(siteApi.getAvailability).toHaveBeenCalledTimes(1)
    resolveGet()
    await Promise.all([first, second])
    expect(availability.state).toBe('up')
    // 完成后可重新加载（in-flight 已释放）。
    vi.mocked(siteApi.getAvailability).mockResolvedValue({ state: 'DOWN' })
    await loadAvailability()
    expect(availability.state).toBe('down')
  })

  it('markDown immediately flips down with a default reason', () => {
    expect(availability.state).toBeNull()
    markDown()
    expect(availability.state).toBe('down')
    expect(availability.downAt).not.toBeNull()
    expect(availability.lastReason).toBe(EH_UNAVAILABLE_MESSAGE)
    // 幂等：重复 markDown 不覆盖既有 downAt。
    const downAt = availability.downAt
    markDown('other')
    expect(availability.downAt).toBe(downAt)
    expect(availability.lastReason).toBe('other')
  })

  it('markUnknown returns to the un-probed state', () => {
    markDown('boom')
    markUnknown()
    expect(availability.state).toBe('unknown')
    expect(availability.downAt).toBeNull()
    expect(availability.lastReason).toBeNull()
  })
})

describe('probeAvailability — 手动探测（§0.3：只有用户手动动作才探测）', () => {
  it('probe answered UP recovers to state=up and returns true', async () => {
    markDown('boom')
    vi.mocked(siteApi.probeAvailability).mockResolvedValue({ state: 'UP' })
    expect(await probeAvailability()).toBe(true)
    expect(availability.state).toBe('up')
    expect(availability.downAt).toBeNull()
  })

  it('probe answered DOWN keeps down and returns false', async () => {
    markDown('boom')
    vi.mocked(siteApi.probeAvailability).mockResolvedValue({
      state: 'DOWN',
      downAt: 7,
      lastReason: 'still unreachable',
    })
    expect(await probeAvailability()).toBe(false)
    expect(availability.state).toBe('down')
    expect(availability.lastReason).toBe('still unreachable')
  })

  it('probe request failure conservatively marks down', async () => {
    vi.mocked(siteApi.probeAvailability).mockRejectedValue(new Error('server gone'))
    expect(await probeAvailability()).toBe(false)
    expect(availability.state).toBe('down')
  })
})
