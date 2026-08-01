import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import AdminProxy from '../admin/AdminProxy.vue'
import { settingsApi, type Settings } from '@/api/settings'
import client from '@/api/client'
import { AppSelect, AppSwitch, AppTextField, PrefRow } from '@/components/form'

vi.mock('@/api/settings', () => ({
  settingsApi: { get: vi.fn(), update: vi.fn() },
}))

vi.mock('@/api/client', () => ({
  default: { post: vi.fn() },
}))

function settingsWithProxy(overrides: Partial<Settings['proxy']> = {}): Settings {
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
    security: { requireAuth: false, sessionTimeout: 86400 },
    processing: { enabled: false, defaultType: 'UPSCALE_2X', outputFormat: 'png', outputQuality: 90 },
    proxy: {
      enabled: false,
      type: 'http',
      host: '',
      port: 0,
      username: '',
      password: '',
      ...overrides,
    },
  }
}

describe('AdminProxy (出站代理配置)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    vi.mocked(settingsApi.get).mockResolvedValue(settingsWithProxy())
    vi.mocked(settingsApi.update).mockResolvedValue(true)
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
  })

  async function mountView() {
    wrapper = mount(AdminProxy)
    await flushPromises()
    return wrapper
  }

  it('renders the form with shared primitives and no native select (UX-02)', async () => {
    const w = await mountView()
    expect(w.find('select').exists()).toBe(false)
    expect(w.findAllComponents(AppTextField).length).toBe(4)

    const select = w.findComponent(AppSelect)
    expect(select.exists()).toBe(true)
    expect(select.props('options')).toEqual([
      { value: 'http', label: 'HTTP' },
      { value: 'socks5', label: 'SOCKS5' },
    ])

    expect(w.findComponent(AppSwitch).exists()).toBe(true)
    const titles = w.findAllComponents(PrefRow).map((r) => r.props('title'))
    expect(titles).toEqual(['启用代理', '代理类型', '服务器地址', '认证（可选）', '用户名', '密码'])
  })

  it('loads the saved proxy settings into the form', async () => {
    vi.mocked(settingsApi.get).mockResolvedValue(
      settingsWithProxy({ enabled: true, type: 'socks5', host: '127.0.0.1', port: 1080 }),
    )
    const w = await mountView()

    const host = w.findAllComponents(AppTextField).find((f) => f.props('placeholder') === '127.0.0.1')!
    expect((host.find('input').element as HTMLInputElement).value).toBe('127.0.0.1')

    const select = w.findComponent(AppSelect)
    expect(select.props('modelValue')).toBe('socks5')
    expect(select.text()).toContain('SOCKS5')

    expect(w.findComponent(AppSwitch).attributes('aria-checked')).toBe('true')

    const port = w.findAllComponents(AppTextField).find((f) => f.props('placeholder') === '7890')!
    expect((port.find('input').element as HTMLInputElement).value).toBe('1080')
  })

  it('saves the form values via settingsApi', async () => {
    const w = await mountView()
    const host = w.findAllComponents(AppTextField).find((f) => f.props('placeholder') === '127.0.0.1')!
    await host.find('input').setValue('proxy.example.com')
    const buttons = w.findAll('button')
    const save = buttons.find((b) => b.text() === '保存并应用')!
    await save.trigger('click')
    await flushPromises()
    expect(settingsApi.update).toHaveBeenCalledWith(
      expect.objectContaining({ proxy: expect.objectContaining({ host: 'proxy.example.com' }) }),
    )
    expect(w.text()).toContain('已保存')
  })

  it('tests the proxy with the current form values', async () => {
    vi.mocked(client.post).mockResolvedValue({ data: { success: true, latencyMs: 42, error: '' } })
    const w = await mountView()
    const buttons = w.findAll('button')
    const test = buttons.find((b) => b.text() === '测试连接')!
    await test.trigger('click')
    await flushPromises()
    expect(client.post).toHaveBeenCalledWith('/proxy/test', expect.objectContaining({ enabled: false }))
    expect(w.text()).toContain('连接成功，延迟 42ms')
  })

  it('shows an error result when the proxy test fails', async () => {
    vi.mocked(client.post).mockResolvedValue({
      data: { success: false, latencyMs: 0, error: 'Connection refused' },
    })
    const w = await mountView()
    const buttons = w.findAll('button')
    const test = buttons.find((b) => b.text() === '测试连接')!
    await test.trigger('click')
    await flushPromises()
    expect(w.text()).toContain('连接失败：Connection refused')
  })
})
