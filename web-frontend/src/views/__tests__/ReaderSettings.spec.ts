import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ReaderSettings from '../settings/ReaderSettings.vue'
import { preferencesApi, type Preferences } from '@/api/preferences'
import { usePreferencesStore } from '@/stores/preferences'
import { AppSelect, AppSegmented, AppSwitch, PrefCard, PrefRow, SectionHeader } from '@/components/form'

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
      showEhEvents: true,
      showEhLimits: true,
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

describe('ReaderSettings (阅读器设置)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(preferencesApi.get).mockResolvedValue(defaultPrefs())
    vi.mocked(preferencesApi.update).mockResolvedValue(defaultPrefs())
  })

  afterEach(() => {
    wrapper?.unmount()
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  async function mountView() {
    wrapper = mount(ReaderSettings)
    await flushPromises()
    return wrapper
  }

  function menuOptions(): HTMLLIElement[] {
    return Array.from(document.body.querySelectorAll('.app-select__option')) as HTMLLIElement[]
  }

  it('renders the shared card structure (SectionHeader + PrefCard + PrefRow)', async () => {
    const w = await mountView()
    const headers = w.findAllComponents(SectionHeader)
    expect(headers.map((h) => h.text())).toEqual(['翻页', '显示'])
    expect(w.findAllComponents(PrefCard)).toHaveLength(2)
    expect(w.findAllComponents(PrefRow)).toHaveLength(10)
  })

  it('renders 3 AppSelect controls with their option lists', async () => {
    const w = await mountView()
    const selects = w.findAllComponents(AppSelect)
    expect(selects).toHaveLength(3)

    const expectedCounts = [4, 4, 5]
    for (let i = 0; i < selects.length; i++) {
      const trigger = selects[i].find('button.app-select__trigger')
      await trigger.trigger('click')
      expect(menuOptions()).toHaveLength(expectedCounts[i])
      await trigger.trigger('click')
    }
  })

  it('shows a visible label for the stored startPosition value (UX-03)', async () => {
    const w = await mountView()
    const startSelect = w.findAllComponents(AppSelect)[2]
    expect(startSelect.props('modelValue')).toBe('top_right')
    expect(startSelect.find('.app-select__value').text()).toBe('右上')
  })

  it('persists a page mode change through the store', async () => {
    const w = await mountView()
    const store = usePreferencesStore()
    const trigger = w.findAllComponents(AppSelect)[0].find('button.app-select__trigger')
    await trigger.trigger('click')
    menuOptions()[3].click()
    await wrapper.vm.$nextTick()
    expect(store.prefs!.reader.pageMode).toBe('scroll')
  })

  it('renders 4 AppSwitch toggles with the original aria-labels', async () => {
    const w = await mountView()
    const switches = w.findAllComponents(AppSwitch)
    expect(switches).toHaveLength(4)
    const labels = switches.map((s) => s.attributes('aria-label'))
    expect(labels).toEqual(['首页作为封面', '显示进度', '显示页间隔', '全屏阅读'])
  })

  it('flips a reader preference when an AppSwitch is clicked', async () => {
    const w = await mountView()
    const store = usePreferencesStore()
    expect(store.prefs!.reader.fullscreen).toBe(true)
    await w.findAllComponents(AppSwitch)[3].find('button').trigger('click')
    expect(store.prefs!.reader.fullscreen).toBe(false)
  })

  it('renders the reading direction segmented control with the active direction highlighted', async () => {
    const w = await mountView()
    const segmented = w.findComponent(AppSegmented)
    const buttons = segmented.findAll('.app-segmented__btn')
    expect(buttons).toHaveLength(3)
    const active = buttons.find((b) => b.classes().includes('app-segmented__btn--active'))
    expect(active?.text()).toBe('右到左')
  })

  it('persists a direction change from the segmented control', async () => {
    const w = await mountView()
    const store = usePreferencesStore()
    await w.findComponent(AppSegmented).findAll('.app-segmented__btn')[2].trigger('click')
    expect(store.prefs!.reader.readingDirection).toBe('vertical')
  })

  it('keeps the native stepper working inside the PrefRow', async () => {
    const w = await mountView()
    const store = usePreferencesStore()
    const stepper = w.find('.stepper')
    await stepper.findAll('button')[1].trigger('click')
    expect(store.prefs!.reader.autoPlayIntervalSec).toBe(3)
    await stepper.findAll('button')[0].trigger('click')
    expect(store.prefs!.reader.autoPlayIntervalSec).toBe(2)
  })

  it('keeps the native brightness slider working inside the PrefRow', async () => {
    const w = await mountView()
    const store = usePreferencesStore()
    const slider = w.find('.slider')
    await slider.setValue('80')
    expect(store.prefs!.reader.brightness).toBe(80)
  })
})
