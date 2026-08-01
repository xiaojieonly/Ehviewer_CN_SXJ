import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import PrivacySettings from '../settings/PrivacySettings.vue'
import { preferencesApi, type Preferences } from '@/api/preferences'
import { usePreferencesStore } from '@/stores/preferences'
import { AppSwitch, PrefCard, PrefRow, SectionHeader } from '@/components/form'

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

describe('PrivacySettings (隐私设置)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(preferencesApi.get).mockResolvedValue(defaultPrefs())
    vi.mocked(preferencesApi.update).mockResolvedValue(defaultPrefs())
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
  })

  async function mountView() {
    wrapper = mount(PrivacySettings)
    await flushPromises()
    return wrapper
  }

  it('renders the shared card structure (SectionHeader + PrefCard + one PrefRow)', async () => {
    const w = await mountView()
    expect(w.findComponent(SectionHeader).text()).toContain('隐私')
    expect(w.findComponent(PrefCard).exists()).toBe(true)
    const row = w.findComponent(PrefRow)
    expect(row.props('title')).toBe('启用统计')
    expect(row.props('summary')).toBe('帮助改进应用体验')
    expect(row.props('icon')).toBe('sec-primary')
  })

  it('renders the analytics AppSwitch with the original aria-label', async () => {
    const w = await mountView()
    const toggle = w.findComponent(AppSwitch)
    expect(toggle.attributes('aria-label')).toBe('启用统计')
    expect(toggle.attributes('aria-checked')).toBe('true')
  })

  it('flips the analytics preference when the switch is clicked', async () => {
    const w = await mountView()
    const store = usePreferencesStore()
    expect(store.prefs!.privacy.enableAnalytics).toBe(true)
    await w.findComponent(AppSwitch).find('button').trigger('click')
    expect(store.prefs!.privacy.enableAnalytics).toBe(false)
    expect(store.prefs!.general.theme).toBe('dark')
  })

  it('shows the privacy note below the card', async () => {
    const w = await mountView()
    expect(w.find('.privacy-settings__note').text()).toContain('统计数据仅用于改进应用体验')
  })
})
