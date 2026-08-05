import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import GeneralSettings from '../settings/GeneralSettings.vue'
import { preferencesApi, type Preferences } from '@/api/preferences'
import { usePreferencesStore } from '@/stores/preferences'
import { useThemeStore } from '@/stores/theme'
import { AppSelect, AppSegmented, AppSwitch, AppTextField, PrefCard, PrefRow, SectionHeader } from '@/components/form'

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

describe('GeneralSettings (通用设置)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.mocked(preferencesApi.get).mockResolvedValue(defaultPrefs())
    vi.mocked(preferencesApi.update).mockResolvedValue(defaultPrefs())
  })

  afterEach(() => {
    wrapper?.unmount()
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  async function mountView() {
    wrapper = mount(GeneralSettings)
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
    expect(headers.map((h) => h.text())).toEqual(['通用', '浏览', '布局', '画廊'])
    expect(w.findAllComponents(PrefCard)).toHaveLength(4)
    expect(w.findAllComponents(PrefRow)).toHaveLength(20)
  })

  it('renders 4 AppSelect controls with complete option lists', async () => {
    const w = await mountView()
    const selects = w.findAllComponents(AppSelect)
    expect(selects).toHaveLength(4)

    const expectedCounts = [9, 2, 2, 3]
    for (let i = 0; i < selects.length; i++) {
      const trigger = selects[i].find('button.app-select__trigger')
      await trigger.trigger('click')
      expect(menuOptions()).toHaveLength(expectedCounts[i])
      await trigger.trigger('click')
    }
  })

  it('includes every main route in the launch page options (UX-03)', async () => {
    const w = await mountView()
    const trigger = w.findAllComponents(AppSelect)[0].find('button.app-select__trigger')
    await trigger.trigger('click')
    const labels = menuOptions().map((option) => option.textContent)
    expect(labels).toEqual(expect.arrayContaining(['首页', '搜索', '收藏', '历史', '下载']))
  })

  it('shows a visible label for the stored launchPage value in the AppSelect trigger (UX-03)', async () => {
    const w = await mountView()
    const launchSelect = w.findAllComponents(AppSelect)[0]
    expect(launchSelect.props('modelValue')).toBe('homepage')
    expect(launchSelect.find('.app-select__value').text()).toBe('首页')
  })

  it('persists a launch page change through the store', async () => {
    const w = await mountView()
    const store = usePreferencesStore()
    const trigger = w.findAllComponents(AppSelect)[0].find('button.app-select__trigger')
    await trigger.trigger('click')
    menuOptions()[2].click()
    await wrapper.vm.$nextTick()
    expect(store.prefs!.general.launchPage).toBe('search')
  })

  it('renders 11 AppSwitch toggles with the original aria-labels', async () => {
    const w = await mountView()
    const switches = w.findAllComponents(AppSwitch)
    expect(switches).toHaveLength(11)
    const labels = switches.map((s) => s.attributes('aria-label'))
    expect(labels).toContain('跟随系统主题')
    expect(labels).toContain('显示阅读进度')
    expect(labels).toContain('显示上传者')
    expect(labels).toContain('显示发布时间')
    expect(labels).toContain('显示日文标题')
    expect(labels).toContain('显示画廊页数')
    expect(labels).toContain('显示标签翻译')
    expect(labels).toContain('显示评论')
    expect(labels).toContain('显示评分')
    expect(labels).toContain('显示 EH 事件')
    expect(labels).toContain('显示 EH 限额')
  })

  it('flips a preference when an AppSwitch is clicked', async () => {
    const w = await mountView()
    const store = usePreferencesStore()
    expect(store.prefs!.general.themeAutoSwitch).toBe(false)
    await w.findAllComponents(AppSwitch)[0].find('button').trigger('click')
    expect(store.prefs!.general.themeAutoSwitch).toBe(true)
  })

  it('renders the theme segmented control with the active theme highlighted', async () => {
    const w = await mountView()
    const segmented = w.findAllComponents(AppSegmented)
    expect(segmented).toHaveLength(1)
    const buttons = segmented[0].findAll('.app-segmented__btn')
    expect(buttons).toHaveLength(3)
    const active = buttons.find((b) => b.classes().includes('app-segmented__btn--active'))
    expect(active?.text()).toBe('亮色')
  })

  it('switches the theme from the segmented control and persists it', async () => {
    const w = await mountView()
    const store = usePreferencesStore()
    const themeStore = useThemeStore()
    await w.findComponent(AppSegmented).findAll('.app-segmented__btn')[1].trigger('click')
    expect(themeStore.currentTheme).toBe('dark')
    expect(store.prefs!.general.theme).toBe('dark')
  })

  it('clamps the history size number field to 1..100', async () => {
    const w = await mountView()
    const store = usePreferencesStore()
    const input = numberInput(w, '历史记录数')
    await input.setValue('999')
    await input.trigger('change')
    expect(store.prefs!.general.historyInfoSize).toBe(100)
  })

  // ---- Wave-1 B 组「浏览」 ----

  it('shows the listMode select in the 浏览 group with grid first', async () => {
    const w = await mountView()
    const listModeSelect = w.findAllComponents(AppSelect)[1]
    expect(listModeSelect.props('modelValue')).toBe('grid')
    expect(listModeSelect.find('.app-select__value').text()).toBe('网格')

    const trigger = listModeSelect.find('button.app-select__trigger')
    await trigger.trigger('click')
    expect(menuOptions().map((option) => option.textContent)).toEqual(['网格', '列表'])
    await trigger.trigger('click')
  })

  it('persists a listMode change through the store', async () => {
    const w = await mountView()
    const store = usePreferencesStore()
    const trigger = w.findAllComponents(AppSelect)[1].find('button.app-select__trigger')
    await trigger.trigger('click')
    menuOptions()[1].click()
    await wrapper.vm.$nextTick()
    expect(store.prefs!.general.listMode).toBe('list')
  })

  it('flips the showUploader / showPostedTime switches', async () => {
    const w = await mountView()
    const store = usePreferencesStore()
    const switches = w.findAllComponents(AppSwitch)
    const uploader = switches.find((s) => s.attributes('aria-label') === '显示上传者')!
    const posted = switches.find((s) => s.attributes('aria-label') === '显示发布时间')!

    await uploader.find('button').trigger('click')
    expect(store.prefs!.general.showUploader).toBe(true)
    await posted.find('button').trigger('click')
    expect(store.prefs!.general.showPostedTime).toBe(true)
  })

  it('clamps defaultFavoriteSlot to -2..9', async () => {
    const w = await mountView()
    const store = usePreferencesStore()
    const input = numberInput(w, '默认收藏槽')

    await input.setValue('15')
    await input.trigger('change')
    expect(store.prefs!.general.defaultFavoriteSlot).toBe(9)

    await input.setValue('-5')
    await input.trigger('change')
    expect(store.prefs!.general.defaultFavoriteSlot).toBe(-2)

    await input.setValue('3')
    await input.trigger('change')
    expect(store.prefs!.general.defaultFavoriteSlot).toBe(3)
  })

  it('clamps recentSearchMax to 0..100 (0 = 关)', async () => {
    const w = await mountView()
    const store = usePreferencesStore()
    const input = numberInput(w, '最近搜索条数')

    await input.setValue('-3')
    await input.trigger('change')
    expect(store.prefs!.general.recentSearchMax).toBe(0)

    await input.setValue('0')
    await input.trigger('change')
    expect(store.prefs!.general.recentSearchMax).toBe(0)

    await input.setValue('999')
    await input.trigger('change')
    expect(store.prefs!.general.recentSearchMax).toBe(100)
  })

  it('persists favoriteSlotNames through the AppTextField', async () => {
    const w = await mountView()
    const store = usePreferencesStore()
    const field = w.findComponent(AppTextField)
    expect(field.exists()).toBe(true)
    expect(field.props('modelValue')).toBe('')

    await field.find('input').setValue('主用|备用')
    expect(store.prefs!.general.favoriteSlotNames).toBe('主用|备用')
  })
})
