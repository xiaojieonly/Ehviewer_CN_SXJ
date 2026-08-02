import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import TransferSettings from '../settings/TransferSettings.vue'
import { preferencesApi, type Preferences } from '@/api/preferences'
import { PrefCard, PrefRow, SectionHeader } from '@/components/form'

vi.mock('@/api/preferences', () => ({
  preferencesApi: { get: vi.fn(), update: vi.fn() },
}))

function defaultPrefs(): Preferences {
  return {
    general: {
      theme: 'dark',
      themeAutoSwitch: false,
      launchPage: 'homepage',
      listMode: 'list',
      showReadProgress: true,
      detailSize: 'long',
      thumbSize: 'middle',
      historyInfoSize: 100,
      showJpnTitle: false,
      showGalleryPages: false,
      showTagTranslations: true,
      showGalleryComment: true,
      showGalleryRating: true,
      showSiteEvents: true,
      showSiteLimits: true,
    },
    reader: {
      readingDirection: 'rtl',
      pageMode: 'dual',
      firstPageCover: true,
      pageScaling: 'fit',
      startPosition: 'top_right',
      autoPlayIntervalSec: 2,
      showProgress: true,
      showPageInterval: true,
      fullscreen: true,
      brightness: 0,
    },
    privacy: { enableAnalytics: true },
  }
}

describe('TransferSettings (传输模块)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    vi.mocked(preferencesApi.get).mockResolvedValue(defaultPrefs())
    vi.mocked(preferencesApi.update).mockResolvedValue(defaultPrefs())
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
  })

  function mountView() {
    wrapper = mount(TransferSettings)
    return wrapper
  }

  it('renders the shared form primitives (SectionHeader + PrefCard + PrefRow)', () => {
    const w = mountView()
    expect(w.findComponent(SectionHeader).text()).toContain('配置传输')
    expect(w.findComponent(PrefCard).exists()).toBe(true)
    const rows = w.findAllComponents(PrefRow)
    expect(rows).toHaveLength(2)
    expect(rows[0].props('title')).toBe('导出设置')
    expect(rows[1].props('title')).toBe('导入设置')
  })

  it('exports the preferences as a JSON file download', async () => {
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const create = vi.fn(() => 'blob:mock')
    Object.defineProperty(URL, 'createObjectURL', { value: create, configurable: true })
    Object.defineProperty(URL, 'revokeObjectURL', { value: vi.fn(), configurable: true })

    const w = mountView()
    const buttons = w.findAll('.btn-primary')
    await buttons[0].trigger('click')
    await flushPromises()

    expect(preferencesApi.get).toHaveBeenCalled()
    expect(create).toHaveBeenCalled()
    expect(click).toHaveBeenCalled()
    click.mockRestore()
  })

  it('imports a valid preferences JSON file via PUT', async () => {
    const w = mountView()
    const file = new File([JSON.stringify(defaultPrefs())], 'prefs.json', {
      type: 'application/json',
    })
    Object.defineProperty(file, 'text', {
      value: () => Promise.resolve(JSON.stringify(defaultPrefs())),
    })
    const input = w.find('[data-testid="import-file"]')
    ;(input.element as HTMLInputElement).files = [file] as unknown as FileList
    await input.trigger('change')
    await flushPromises()

    expect(preferencesApi.update).toHaveBeenCalledWith(expect.objectContaining({ general: expect.any(Object) }))
    expect(w.text()).toContain('设置已导入')
  })

  it('rejects a malformed JSON file without calling the server', async () => {
    const w = mountView()
    const file = new File(['{not valid json'], 'bad.json', { type: 'application/json' })
    Object.defineProperty(file, 'text', { value: () => Promise.resolve('{not valid json') })
    const input = w.find('[data-testid="import-file"]')
    ;(input.element as HTMLInputElement).files = [file] as unknown as FileList
    await input.trigger('change')
    await flushPromises()

    expect(preferencesApi.update).not.toHaveBeenCalled()
    expect(w.text()).toContain('导入失败')
  })
})
