import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import AdminProcessing from '../admin/AdminProcessing.vue'
import { settingsApi, type Settings } from '@/api/settings'
import { AppSelect, AppSwitch, PrefRow, SectionHeader } from '@/components/form'

vi.mock('@/api/settings', () => ({
  settingsApi: { get: vi.fn(), update: vi.fn() },
}))

function settingsWithProcessing(overrides: Partial<Settings['processing']> = {}): Settings {
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
    processing: {
      enabled: false,
      defaultType: 'UPSCALE_2X',
      outputFormat: 'png',
      outputQuality: 90,
      ...overrides,
    },
    proxy: { enabled: false, type: 'http', host: '', port: 0, username: '', password: '' },
  }
}

describe('AdminProcessing (图像处理)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    vi.mocked(settingsApi.get).mockResolvedValue(settingsWithProcessing())
    vi.mocked(settingsApi.update).mockResolvedValue(true)
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.useRealTimers()
    vi.clearAllMocks()
  })

  async function mountView() {
    wrapper = mount(AdminProcessing)
    await flushPromises()
    return wrapper
  }

  it('renders shared primitives; selects via AppSelect, slider kept native', async () => {
    const w = await mountView()
    expect(w.findAllComponents(SectionHeader).map((h) => h.props('title'))).toEqual(['处理设置'])
    expect(w.findAllComponents(PrefRow).map((r) => r.props('title'))).toEqual([
      '启用图像处理',
      '默认处理类型',
      '输出格式',
      '输出质量',
    ])
    expect(w.find('select').exists()).toBe(false)
    expect(w.find('.switch').exists()).toBe(false)

    const selects = w.findAllComponents(AppSelect)
    expect(selects.length).toBe(2)
    expect(selects[0].props('options')).toEqual([
      { value: 'UPSCALE_2X', label: '2X 放大' },
      { value: 'UPSCALE_4X', label: '4X 放大' },
      { value: 'DENOISE', label: '降噪' },
      { value: 'DENOISE_UPSCALE', label: '降噪 + 放大' },
    ])
    expect(selects[1].props('options')).toEqual([
      { value: 'png', label: 'PNG' },
      { value: 'jpeg', label: 'JPEG' },
      { value: 'webp', label: 'WebP' },
    ])
    expect(selects[0].text()).toContain('2X 放大')

    expect(w.findComponent(AppSwitch).attributes('aria-label')).toBe('启用图像处理')
    expect(w.find('.processing__slider').exists()).toBe(true)
  })

  it('selects a processing type via the dropdown and persists it', async () => {
    wrapper = mount(AdminProcessing, { attachTo: document.body })
    await flushPromises()
    vi.useFakeTimers()

    const select = wrapper.findAllComponents(AppSelect)[0]
    await select.find('.app-select__trigger').trigger('click')
    const option = [...document.body.querySelectorAll<HTMLElement>('.app-select__option')].find(
      (el) => el.textContent === '4X 放大',
    )
    expect(option).toBeTruthy()
    option!.click()
    await vi.advanceTimersByTimeAsync(700)

    expect(settingsApi.update).toHaveBeenCalledWith(
      expect.objectContaining({ processing: expect.objectContaining({ defaultType: 'UPSCALE_4X' }) }),
    )
    wrapper.unmount()
  })

  it('toggles the processing switch and persists it', async () => {
    const w = await mountView()
    vi.useFakeTimers()
    const sw = w.findComponent(AppSwitch)
    expect(sw.attributes('aria-checked')).toBe('false')
    await sw.trigger('click')
    expect(sw.attributes('aria-checked')).toBe('true')
    await vi.advanceTimersByTimeAsync(700)
    expect(settingsApi.update).toHaveBeenCalledWith(
      expect.objectContaining({ processing: expect.objectContaining({ enabled: true }) }),
    )
  })
})
