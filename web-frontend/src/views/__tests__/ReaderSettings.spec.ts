import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ReaderSettings from '../settings/ReaderSettings.vue'
import { preferencesApi, type Preferences } from '@/api/preferences'
import { usePreferencesStore } from '@/stores/preferences'
import { AppSelect, AppSegmented, AppSwitch, PrefCard, PrefRow, SectionHeader } from '@/components/form'

vi.mock('@/api/preferences', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/preferences')>()
  return {
    ...actual,
    preferencesApi: { get: vi.fn(), update: vi.fn() },
  }
})

function defaultPrefs(): Preferences {
  return {
    general: {
      theme: 'dark',
      themeAutoSwitch: false,
      launchPage: 'homepage',
      listMode: 'grid',
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
      // Wave-1 B 组
      showUploader: false,
      showPostedTime: false,
      defaultFavoriteSlot: 0,
      favoriteSlotNames: '',
      recentSearchMax: 10,
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
      // Wave-1 A 组
      backgroundColor: 'black',
      tapZoneScheme: 'threeZone',
      keyboardPaging: true,
      zoomStep: 1.5,
      maxZoom: 5,
      dualPageGap: 8,
      splitWidePages: false,
      preloadCount: 2,
      pageTransition: 'slide',
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

  function numberInput(w: VueWrapper, ariaLabel: string) {
    return w.find(`input[aria-label="${ariaLabel}"]`)
  }

  it('renders the shared card structure (SectionHeader + PrefCard + PrefRow)', async () => {
    const w = await mountView()
    const headers = w.findAllComponents(SectionHeader)
    expect(headers.map((h) => h.text())).toEqual(['翻页', '显示', '交互', '双页', '性能'])
    expect(w.findAllComponents(PrefCard)).toHaveLength(5)
    expect(w.findAllComponents(PrefRow)).toHaveLength(19)
  })

  it('renders 6 AppSelect controls with their option lists', async () => {
    const w = await mountView()
    const selects = w.findAllComponents(AppSelect)
    expect(selects).toHaveLength(6)

    const expectedCounts = [4, 4, 5, 3, 3, 3]
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

  it('renders 6 AppSwitch toggles with the original aria-labels', async () => {
    const w = await mountView()
    const switches = w.findAllComponents(AppSwitch)
    expect(switches).toHaveLength(6)
    const labels = switches.map((s) => s.attributes('aria-label'))
    expect(labels).toEqual(['首页作为封面', '显示进度', '显示页间隔', '全屏阅读', '键盘翻页', '拆分宽页'])
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

  // ---- Wave-1 A 组: 交互 ----

  it('persists backgroundColor / tapZoneScheme / pageTransition selects', async () => {
    const w = await mountView()
    const store = usePreferencesStore()
    const selects = w.findAllComponents(AppSelect)

    // backgroundColor（第 4 个 select）→ 白色
    let trigger = selects[3].find('button.app-select__trigger')
    await trigger.trigger('click')
    expect(menuOptions().map((option) => option.textContent)).toEqual(['黑色', '灰色', '白色'])
    menuOptions()[2].click()
    await wrapper.vm.$nextTick()
    expect(store.prefs!.reader.backgroundColor).toBe('white')

    // tapZoneScheme（第 5 个）→ 仅边缘
    trigger = selects[4].find('button.app-select__trigger')
    await trigger.trigger('click')
    menuOptions()[1].click()
    await wrapper.vm.$nextTick()
    expect(store.prefs!.reader.tapZoneScheme).toBe('edgeOnly')

    // pageTransition（第 6 个）→ 无
    trigger = selects[5].find('button.app-select__trigger')
    await trigger.trigger('click')
    menuOptions()[2].click()
    await wrapper.vm.$nextTick()
    expect(store.prefs!.reader.pageTransition).toBe('none')
  })

  it('flips the keyboardPaging and splitWidePages switches', async () => {
    const w = await mountView()
    const store = usePreferencesStore()
    const switches = w.findAllComponents(AppSwitch)
    const keyboard = switches.find((s) => s.attributes('aria-label') === '键盘翻页')!
    const split = switches.find((s) => s.attributes('aria-label') === '拆分宽页')!

    expect(store.prefs!.reader.keyboardPaging).toBe(true)
    await keyboard.find('button').trigger('click')
    expect(store.prefs!.reader.keyboardPaging).toBe(false)

    expect(store.prefs!.reader.splitWidePages).toBe(false)
    await split.find('button').trigger('click')
    expect(store.prefs!.reader.splitWidePages).toBe(true)
  })

  it('clamps zoomStep above 1', async () => {
    const w = await mountView()
    const store = usePreferencesStore()
    const input = numberInput(w, '缩放步进')

    await input.setValue('0.5')
    await input.trigger('change')
    expect(store.prefs!.reader.zoomStep).toBe(1.1)

    await input.setValue('99')
    await input.trigger('change')
    expect(store.prefs!.reader.zoomStep).toBe(10)

    await input.setValue('2')
    await input.trigger('change')
    expect(store.prefs!.reader.zoomStep).toBe(2)
  })

  it('clamps maxZoom to at least 1', async () => {
    const w = await mountView()
    const store = usePreferencesStore()
    const input = numberInput(w, '最大缩放')

    await input.setValue('0.5')
    await input.trigger('change')
    expect(store.prefs!.reader.maxZoom).toBe(1)

    await input.setValue('999')
    await input.trigger('change')
    expect(store.prefs!.reader.maxZoom).toBe(50)
  })

  // ---- Wave-1 A 组: 双页 / 性能 ----

  it('clamps dualPageGap to non-negative', async () => {
    const w = await mountView()
    const store = usePreferencesStore()
    const input = numberInput(w, '双页间距')

    await input.setValue('-2')
    await input.trigger('change')
    expect(store.prefs!.reader.dualPageGap).toBe(0)

    await input.setValue('16')
    await input.trigger('change')
    expect(store.prefs!.reader.dualPageGap).toBe(16)
  })

  it('clamps preloadCount to 0..20', async () => {
    const w = await mountView()
    const store = usePreferencesStore()
    const input = numberInput(w, '预加载页数')

    await input.setValue('-1')
    await input.trigger('change')
    expect(store.prefs!.reader.preloadCount).toBe(0)

    await input.setValue('25')
    await input.trigger('change')
    expect(store.prefs!.reader.preloadCount).toBe(20)
  })
})
