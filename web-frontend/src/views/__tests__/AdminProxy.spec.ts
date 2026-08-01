import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import AdminProxy from '../admin/AdminProxy.vue'
import { settingsApi, type Settings } from '@/api/settings'
import client from '@/api/client'

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

  it('loads the saved proxy settings into the form', async () => {
    vi.mocked(settingsApi.get).mockResolvedValue(
      settingsWithProxy({ enabled: true, type: 'socks5', host: '127.0.0.1', port: 1080 }),
    )
    const w = await mountView()
    const input = w.findAll('.pref__input').find((i) => (i.element as HTMLInputElement).placeholder === '127.0.0.1')
    expect((input!.element as HTMLInputElement).value).toBe('127.0.0.1')
    expect((w.find('select').element as HTMLSelectElement).value).toBe('socks5')
  })

  it('saves the form values via settingsApi', async () => {
    const w = await mountView()
    const host = w.findAll('.pref__input').find((i) => (i.element as HTMLInputElement).placeholder === '127.0.0.1')!
    await host.setValue('proxy.example.com')
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
