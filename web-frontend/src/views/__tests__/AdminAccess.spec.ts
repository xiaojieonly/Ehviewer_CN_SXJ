import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import AdminAccess from '../admin/AdminAccess.vue'
import { settingsApi, type Settings } from '@/api/settings'
import { AppSwitch, AppTextField, PrefRow, SectionHeader } from '@/components/form'

vi.mock('@/api/settings', () => ({
  settingsApi: { get: vi.fn(), update: vi.fn() },
}))

function settingsWithSecurity(overrides: Partial<Settings['security']> = {}): Settings {
  return {
    download: {
      path: '/data',
      workerCount: 4,
      downloadDelay: 1000,
      downloadTimeout: 30000,
      maxConcurrentGalleries: 2,
      maxConcurrentImages: 8,
    },
    cache: { path: '/cache', sizeMb: 5120 },
    smb: { enabled: false },
    security: { requireAuth: false, sessionTimeout: 86400, ...overrides },
    processing: { enabled: false, defaultType: 'UPSCALE_2X', outputFormat: 'png', outputQuality: 90 },
    proxy: { enabled: false, type: 'http', host: '', port: 0, username: '', password: '' },
  }
}

describe('AdminAccess (访问控制)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    vi.mocked(settingsApi.get).mockResolvedValue(settingsWithSecurity())
    vi.mocked(settingsApi.update).mockResolvedValue(true)
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
  })

  async function mountView() {
    wrapper = mount(AdminAccess)
    await flushPromises()
    return wrapper
  }

  it('renders shared primitives with password AppTextFields', async () => {
    const w = await mountView()
    expect(w.findAllComponents(SectionHeader).map((h) => h.props('title'))).toEqual(['登录', '修改密码'])
    expect(w.findAllComponents(PrefRow).map((r) => r.props('title'))).toEqual([
      '需要登录',
      'Session 超时（秒）',
    ])
    expect(w.find('select').exists()).toBe(false)
    expect(w.find('.switch').exists()).toBe(false)

    const sw = w.findComponent(AppSwitch)
    expect(sw.exists()).toBe(true)
    expect(sw.attributes('aria-label')).toBe('需要登录')

    const fields = w.findAllComponents(AppTextField)
    expect(fields.length).toBe(3)
    expect(fields.map((f) => f.props('label'))).toEqual(['旧密码', '新密码', '确认新密码'])
    expect(fields.every((f) => f.props('type') === 'password')).toBe(true)
  })

  it('toggles requireAuth via AppSwitch and persists security', async () => {
    const w = await mountView()
    const sw = w.findComponent(AppSwitch)
    expect(sw.attributes('aria-checked')).toBe('false')
    expect(w.text()).toContain('关闭后，局域网内任何人都可以访问此服务器')

    await sw.trigger('click')
    await flushPromises()
    expect(settingsApi.update).toHaveBeenCalledWith(
      expect.objectContaining({ security: expect.objectContaining({ requireAuth: true }) }),
    )
    expect(w.text()).not.toContain('关闭后，局域网内任何人都可以访问此服务器')
  })

  it('keeps the warning visible when auth is off after load', async () => {
    vi.mocked(settingsApi.get).mockResolvedValue(settingsWithSecurity({ requireAuth: true }))
    const w = await mountView()
    expect(w.findComponent(AppSwitch).attributes('aria-checked')).toBe('true')
    expect(w.text()).not.toContain('关闭后，局域网内任何人都可以访问此服务器')
  })

  it('persists a new session timeout from the number field', async () => {
    const w = await mountView()
    const input = w.find('.pref__number')
    ;(input.element as HTMLInputElement).value = '7200'
    await input.trigger('change')
    await flushPromises()
    expect(settingsApi.update).toHaveBeenCalledWith(
      expect.objectContaining({ security: expect.objectContaining({ sessionTimeout: 7200 }) }),
    )
  })
})
