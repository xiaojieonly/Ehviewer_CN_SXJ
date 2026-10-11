import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import ConnectionBanner from '../ConnectionBanner.vue'
// 命中 vi.mock 的工厂导出——refs 在工厂里创建（vi.hoisted 阶段拿不到 vue），
// 测试经导入直接读写同一实例。
import { authError, connect, connectionState, lastError } from '@/composables/useWebSocket'
import type { ConnectionState } from '@/composables/useWebSocket'

vi.mock('@/composables/useWebSocket', async () => {
  const { ref } = await import('vue')
  const connectionState = ref<ConnectionState>('disconnected')
  const lastError = ref<string | null>(null)
  const authError = ref<string | null>(null)
  return {
    connectionState,
    lastError,
    authError,
    connect: vi.fn(),
    TOPIC_SYNC: '/topic/sync',
    useWebSocket: vi.fn(),
  }
})

beforeEach(() => {
  connectionState.value = 'disconnected'
  lastError.value = null
  authError.value = null
  vi.mocked(connect).mockClear()
})

describe('ConnectionBanner (WP8 实时连接可见化)', () => {
  let wrapper: VueWrapper

  afterEach(() => {
    wrapper?.unmount()
  })

  it('连接中 / 正常连接不渲染横幅', () => {
    const states: ConnectionState[] = ['connecting', 'connected', 'disconnected']
    for (const state of states) {
      connectionState.value = state
      wrapper = mount(ConnectionBanner)
      expect(wrapper.find('[data-testid="connection-banner"]').exists()).toBe(false)
      wrapper.unmount()
    }
  })

  it('connectionState=error → 断开文案（含 lastError）+「重试」按钮调显式 connect()', async () => {
    connectionState.value = 'error'
    lastError.value = 'Connection failed 3 times in a row'
    wrapper = mount(ConnectionBanner)

    const banner = wrapper.find('[data-testid="connection-banner"]')
    expect(banner.exists()).toBe(true)
    expect(banner.text()).toContain('实时连接已断开：Connection failed 3 times in a row')

    await wrapper.find('[data-testid="connection-banner-retry"]').trigger('click')
    expect(connect).toHaveBeenCalledTimes(1)
  })

  it('authError（非 error 态）→ 鉴权失败文案，无重试按钮', () => {
    authError.value = 'bad or missing token'
    wrapper = mount(ConnectionBanner)

    const banner = wrapper.find('[data-testid="connection-banner"]')
    expect(banner.exists()).toBe(true)
    expect(banner.text()).toContain('实时连接鉴权失败：bad or missing token')
    expect(wrapper.find('[data-testid="connection-banner-retry"]').exists()).toBe(false)
  })
})
