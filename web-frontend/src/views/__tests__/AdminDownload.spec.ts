import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import AdminDownload from '../admin/AdminDownload.vue'
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

describe('AdminDownload (下载设置)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    localStorage.clear()
    vi.mocked(settingsApi.get).mockResolvedValue(fullSettings())
    vi.mocked(settingsApi.update).mockResolvedValue(true)
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.useRealTimers()
    vi.clearAllMocks()
  })

  async function mountView() {
    wrapper = mount(AdminDownload)
    await flushPromises()
    return wrapper
  }

  it('renders shared primitives; steppers/num-fields kept native, switches use AppSwitch', async () => {
    const w = await mountView()
    expect(w.findAllComponents(SectionHeader).map((h) => h.props('title'))).toEqual([
      '基础设置',
      '并发限制',
      '列表与行为',
      '维护',
    ])

    const titles = w.findAllComponents(PrefRow).map((r) => r.props('title'))
    expect(titles).toContain('下载路径')
    expect(titles).toContain('并发线程数')
    expect(titles).toContain('下载延迟')
    expect(titles).toContain('下载超时')
    expect(titles).toContain('最大并发画廊数')
    expect(titles).toContain('最大并发图片数')
    expect(titles).toContain('预加载图片数')
    expect(titles).not.toContain('下载列表分页')
    expect(titles).toContain('排序方向')
    expect(titles).toContain('自动开始下载')
    expect(titles).toContain('清理冗余文件')
    expect(titles).toContain('清理无效下载')

    expect(w.find('select').exists()).toBe(false)
    expect(w.find('.switch').exists()).toBe(false)
    expect(w.findAll('.stepper').length).toBe(4)
    expect(w.findAll('.num-field').length).toBe(2)

    const switches = w.findAllComponents(AppSwitch)
    expect(switches.length).toBe(2)
    expect(switches.map((s) => s.attributes('aria-label'))).toEqual([
      '排序方向',
      '自动开始下载',
    ])
  })

  it('tolerates legacy localStorage entries with the removed paginated key', async () => {
    localStorage.setItem(
      'anotherviewer-admin-download-ui',
      JSON.stringify({ paginated: true, preloadImages: 5, autoStart: false }),
    )
    const w = await mountView()
    const switches = w.findAllComponents(AppSwitch)
    expect(switches.length).toBe(2)
    // Remaining local fields are still applied; the removed row is gone.
    expect(w.findAllComponents(PrefRow).map((r) => r.props('title'))).not.toContain('下载列表分页')
    expect(switches.find((s) => s.attributes('aria-label') === '自动开始下载')!.attributes('aria-checked')).toBe('false')
    // The next persist drops the unknown legacy key.
    await switches.find((s) => s.attributes('aria-label') === '自动开始下载')!.trigger('click')
    const raw = localStorage.getItem('anotherviewer-admin-download-ui')
    expect(JSON.parse(raw!)).not.toHaveProperty('paginated')
    expect(JSON.parse(raw!).autoStart).toBe(true)
  })

  it('bumps the worker count stepper and persists via debounce', async () => {
    const w = await mountView()
    vi.useFakeTimers()
    const row = w.findAllComponents(PrefRow).find((r) => r.props('title') === '并发线程数')!
    const plus = row.findAll('.stepper__btn').find((b) => b.attributes('aria-label') === '增加并发线程数')!
    await plus.trigger('click')
    expect(settingsApi.update).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(700)
    expect(settingsApi.update).toHaveBeenCalledWith(
      expect.objectContaining({ download: expect.objectContaining({ workerCount: 5 }) }),
    )
  })

  it('toggles a local switch and persists it to localStorage', async () => {
    const w = await mountView()
    const sw = w.findAllComponents(AppSwitch).find((s) => s.attributes('aria-label') === '自动开始下载')!
    expect(sw.attributes('aria-checked')).toBe('true')
    await sw.trigger('click')
    expect(sw.attributes('aria-checked')).toBe('false')
    const raw = localStorage.getItem('anotherviewer-admin-download-ui')
    expect(JSON.parse(raw!).autoStart).toBe(false)
  })

  it('edits the download path in the dialog and saves it', async () => {
    const w = await mountView()
    await w.find('[aria-label="修改下载路径"]').trigger('click')
    const field = w.findComponent(AppTextField)
    expect(field.props('label')).toBe('服务器端路径')
    await field.find('input').setValue('/new/path')

    vi.useFakeTimers()
    const save = w.findAll('button').find((b) => b.text() === '保存')!
    await save.trigger('click')
    await vi.advanceTimersByTimeAsync(700)
    expect(settingsApi.update).toHaveBeenCalledWith(
      expect.objectContaining({ download: expect.objectContaining({ path: '/new/path' }) }),
    )
  })

  it('shows a TODO snackbar for unimplemented maintenance actions', async () => {
    const w = await mountView()
    await w.find('[aria-label="清理冗余文件"]').trigger('click')
    expect(w.text()).toContain('后端暂未提供此接口，待实现')
  })
})
