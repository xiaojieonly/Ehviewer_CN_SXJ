import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import AdminServer from '../admin/AdminServer.vue'
import { settingsApi, type Settings } from '@/api/settings'
import { AppSwitch, AppTextField, PrefRow, SectionHeader } from '@/components/form'

vi.mock('@/api/settings', () => ({
  settingsApi: { get: vi.fn(), update: vi.fn() },
}))

function fullSettings(): Settings {
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
    proxy: { enabled: false, type: 'http', host: '', port: 0, username: '', password: '' },
  }
}

describe('AdminServer (服务器)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    vi.mocked(settingsApi.get).mockResolvedValue(fullSettings())
    vi.mocked(settingsApi.update).mockResolvedValue(true)
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.useRealTimers()
    vi.clearAllMocks()
  })

  async function mountView() {
    wrapper = mount(AdminServer, {
      global: { stubs: { RouterLink: true } },
    })
    await flushPromises()
    return wrapper
  }

  it('renders shared primitives with AppTextField and AppSwitch', async () => {
    const w = await mountView()
    expect(w.findAllComponents(SectionHeader).map((h) => h.props('title'))).toEqual(['缓存', 'SMB 备份'])
    const titles = w.findAllComponents(PrefRow).map((r) => r.props('title'))
    expect(titles).toEqual(['缓存路径', '缓存大小 (MB)', '缓存统计', '清除缓存', 'SMB 备份'])
    expect(w.find('select').exists()).toBe(false)
    expect(w.find('.switch').exists()).toBe(false)

    const fields = w.findAllComponents(AppTextField)
    expect(fields.length).toBe(2)
    expect(fields.map((f) => f.props('ariaLabel'))).toEqual(['缓存路径', '缓存大小'])
    expect(fields[1].props('type')).toBe('number')

    const sw = w.findComponent(AppSwitch)
    expect(sw.exists()).toBe(true)
    expect(sw.attributes('aria-label')).toBe('SMB 备份')
    expect(sw.attributes('aria-checked')).toBe('false')
  })

  it('persists the cache path after typing pauses', async () => {
    const w = await mountView()
    vi.useFakeTimers()
    const field = w.findAllComponents(AppTextField).find((f) => f.props('ariaLabel') === '缓存路径')!
    await field.find('input').setValue('/new/cache')
    await vi.advanceTimersByTimeAsync(600)
    expect(settingsApi.update).toHaveBeenCalledWith(
      expect.objectContaining({ cache: expect.objectContaining({ path: '/new/cache' }) }),
    )
  })

  it('persists the cache size as a clamped number', async () => {
    const w = await mountView()
    vi.useFakeTimers()
    const field = w.findAllComponents(AppTextField).find((f) => f.props('ariaLabel') === '缓存大小')!
    await field.find('input').setValue('10240')
    await vi.advanceTimersByTimeAsync(600)
    expect(settingsApi.update).toHaveBeenCalledWith(
      expect.objectContaining({ cache: expect.objectContaining({ sizeMb: 10240 }) }),
    )
  })

  it('toggles SMB backup and reveals the backup page link', async () => {
    const w = await mountView()
    const sw = w.findComponent(AppSwitch)
    await sw.trigger('click')
    await flushPromises()
    expect(settingsApi.update).toHaveBeenCalledWith(expect.objectContaining({ smb: { enabled: true } }))
    expect(w.text()).toContain('前往备份页面')
    expect(w.find('.server__link').exists()).toBe(true)
  })

  it('shows a TODO snackbar for clearing the cache', async () => {
    const w = await mountView()
    await w.find('[aria-label="清除缓存"]').trigger('click')
    expect(w.text()).toContain('TODO：后端尚未提供清除缓存接口')
  })
})
