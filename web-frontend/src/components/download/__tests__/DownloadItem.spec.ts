import { describe, expect, it, vi, afterEach, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import DownloadItem from '../DownloadItem.vue'
import type { DownloadItem as DownloadItemType } from '@/api/download'

// W5/W7: 行组件读取偏好 store（showReadProgress）→ 全局提供 pinia。
beforeEach(() => {
  setActivePinia(createPinia())
})

/** Build a download fixture; `overrides` patches individual fields. */
function makeDownload(overrides: Partial<DownloadItemType> = {}): DownloadItemType {
  return {
    id: 1,
    gid: 9001,
    token: 'tokA',
    title: 'Dl Alpha',
    titleJpn: null,
    thumb: null,
    category: 0,
    state: 2, // STATE_DOWNLOAD
    total: 30,
    done: 30,
    label: 0,
    downloadDir: null,
    error: null,
    ...overrides,
  }
}

describe('DownloadItem 点击分区（Android 端逻辑：缩略图→详情，主体→阅读）', () => {
  it('thumb click emits open (detail) and not read', async () => {
    const wrapper = mount(DownloadItem, { props: { item: makeDownload() } })
    await wrapper.find('.download-item__thumb').trigger('click')
    expect(wrapper.emitted('open')).toEqual([[9001]])
    expect(wrapper.emitted('read')).toBeUndefined()
  })

  it('body click outside select mode emits read (direct reader)', async () => {
    const wrapper = mount(DownloadItem, { props: { item: makeDownload() } })
    await wrapper.find('.download-item__body').trigger('click')
    expect(wrapper.emitted('read')).toEqual([[9001]])
    expect(wrapper.emitted('open')).toBeUndefined()
  })

  it('action buttons do not bubble into body read/open', async () => {
    const wrapper = mount(DownloadItem, {
      props: { item: makeDownload({ state: 2, total: 10, done: 3 }) },
    })
    await wrapper.find('button[aria-label="Pause download"]').trigger('click')
    expect(wrapper.emitted('pause')).toEqual([[wrapper.props('item').id]])
    expect(wrapper.emitted('read')).toBeUndefined()
    expect(wrapper.emitted('open')).toBeUndefined()
  })
})

describe('DownloadItem (thumbnail handling, E2E-9 / E2E-3)', () => {
  it('renders title, progress, percent and buttons when thumb is null', () => {
    const wrapper = mount(DownloadItem, {
      props: { item: makeDownload() },
    })

    expect(wrapper.find('.download-item__title').text()).toBe('Dl Alpha')
    expect(wrapper.find('.download-item__pages').text()).toBe('30/30 pages')
    expect(wrapper.find('.download-item__percent').text()).toBe('100%')
    expect(wrapper.find('.download-item__track').exists()).toBe(true)
    expect(wrapper.find('.download-item__fill').attributes('style')).toContain('width: 100%')
    // Downloading row: Pause + Stop + Delete, no Start.
    expect(wrapper.find('button[aria-label="Pause download"]').exists()).toBe(true)
    expect(wrapper.find('button[aria-label="Stop download"]').exists()).toBe(true)
    expect(wrapper.find('button[aria-label="Delete download"]').exists()).toBe(true)
    expect(wrapper.find('button[aria-label="Start download"]').exists()).toBe(false)
  })

  it('renders the icon placeholder instead of an <img> when thumb is null', () => {
    const wrapper = mount(DownloadItem, {
      props: { item: makeDownload({ thumb: null }) },
    })
    expect(wrapper.find('.download-item__thumb img').exists()).toBe(false)
    const placeholder = wrapper.find('.download-item__thumb-placeholder')
    expect(placeholder.exists()).toBe(true)
    // No alt/title text may leak into the placeholder (E2E-3).
    expect(placeholder.text()).toBe('')
    expect(placeholder.find('svg').exists()).toBe(true)
  })

  it('shows the row content for idle rows without a thumbnail', () => {
    const wrapper = mount(DownloadItem, {
      props: { item: makeDownload({ state: 0, total: 10, done: 0 }) },
    })
    expect(wrapper.find('.download-item__title').text()).toBe('Dl Alpha')
    expect(wrapper.find('.download-item__percent').text()).toBe('0%')
    expect(wrapper.find('button[aria-label="Start download"]').exists()).toBe(true)
  })

  it('swaps a failed thumbnail to the placeholder (no alt leak)', async () => {
    const wrapper = mount(DownloadItem, {
      props: { item: makeDownload({ thumb: 'https://ehgt.org/t/9001/cover.jpg' }) },
    })
    const img = wrapper.find('.download-item__thumb img')
    expect(img.exists()).toBe(true)
    expect(img.attributes('alt')).toBe('Dl Alpha')

    await img.trigger('error')
    expect(wrapper.find('.download-item__thumb img').exists()).toBe(false)
    const placeholder = wrapper.find('.download-item__thumb-placeholder')
    expect(placeholder.exists()).toBe(true)
    expect(placeholder.text()).toBe('')
    // The card content stays fully visible after the swap.
    expect(wrapper.find('.download-item__title').text()).toBe('Dl Alpha')
    expect(wrapper.find('.download-item__percent').text()).toBe('100%')
  })

  it('rewrites external http(s) thumbs through the WebUI image proxy (A7)', () => {
    const thumb = 'https://ehgt.org/t/9001/cover.jpg'
    const wrapper = mount(DownloadItem, {
      props: { item: makeDownload({ thumb }) },
    })
    const img = wrapper.find('.download-item__thumb img')
    expect(img.attributes('src')).toBe(`/api/v1/image/proxy?url=${encodeURIComponent(thumb)}`)
  })

  it('keeps non-external thumb sources unchanged (A7)', () => {
    const wrapper = mount(DownloadItem, {
      props: { item: makeDownload({ thumb: '/thumbs/9001/cover.jpg' }) },
    })
    const img = wrapper.find('.download-item__thumb img')
    expect(img.attributes('src')).toBe('/thumbs/9001/cover.jpg')
  })
})

/* ---------------- multi-select (Android choice mode) ---------------- */

describe('DownloadItem (multi-select: contextmenu / long-press / toggle)', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('emits menu on right-click and suppresses the native context menu', () => {
    const wrapper = mount(DownloadItem, { props: { item: makeDownload() } })
    const article = wrapper.find('.download-item')
    article.trigger('contextmenu')
    expect(wrapper.emitted('menu')).toHaveLength(1)
    expect(wrapper.emitted('menu')![0]).toEqual([1])
  })

  it('emits menu after a 500ms long-press', async () => {
    vi.useFakeTimers()
    const wrapper = mount(DownloadItem, { props: { item: makeDownload() } })
    const article = wrapper.find('.download-item')

    article.trigger('touchstart', { touches: [{ clientX: 10, clientY: 10 }] })
    vi.advanceTimersByTime(499)
    expect(wrapper.emitted('menu')).toBeUndefined()
    vi.advanceTimersByTime(2)
    expect(wrapper.emitted('menu')).toHaveLength(1)
    expect(wrapper.emitted('menu')![0]).toEqual([1])
  })

  it('cancels the long-press on early touch end or big movement', () => {
    vi.useFakeTimers()
    const wrapper = mount(DownloadItem, { props: { item: makeDownload() } })
    const article = wrapper.find('.download-item')

    // Short touch: touchend before 500ms → no menu.
    article.trigger('touchstart', { touches: [{ clientX: 0, clientY: 0 }] })
    article.trigger('touchend')
    vi.advanceTimersByTime(600)
    expect(wrapper.emitted('menu')).toBeUndefined()

    // Movement >10px while pressing cancels the timer.
    article.trigger('touchstart', { touches: [{ clientX: 0, clientY: 0 }] })
    article.trigger('touchmove', { touches: [{ clientX: 30, clientY: 0 }] })
    vi.advanceTimersByTime(600)
    expect(wrapper.emitted('menu')).toBeUndefined()
  })

  it('click on the body toggles selection only in select mode', () => {
    const wrapper = mount(DownloadItem, {
      props: { item: makeDownload(), selectable: true, selected: false },
    })

    wrapper.find('.download-item').trigger('click')
    expect(wrapper.emitted('select')).toHaveLength(1)
    expect(wrapper.emitted('select')![0]).toEqual([1])

    // 点按钮不触发 select（closest('button') 守卫），仍走原按钮事件（Stop → cancel）。
    wrapper.find('button[aria-label="Stop download"]').trigger('click')
    expect(wrapper.emitted('select')).toHaveLength(1)
    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('does not toggle on body click when not in select mode', () => {
    const wrapper = mount(DownloadItem, { props: { item: makeDownload() } })
    wrapper.find('.download-item').trigger('click')
    expect(wrapper.emitted('select')).toBeUndefined()
  })

  it('renders the checked indicator and selected style in select mode', () => {
    const wrapper = mount(DownloadItem, {
      props: { item: makeDownload(), selectable: true, selected: true },
    })
    expect(wrapper.find('.download-item__check').exists()).toBe(true)
    expect(wrapper.find('.download-item__check--on').exists()).toBe(true)
    expect(wrapper.find('.download-item').classes()).toContain('download-item--selected')
    expect(wrapper.find('.download-item').attributes('aria-selected')).toBe('true')
  })
})

/* ═══════════════════════════════════════════════════════════════════════
 * W5/W7 additions (plan-2026-09-02 §5.3 W7 / §8) — 下载行阅读进度角标：
 * 与 GalleryCard 同语义（showReadProgress 开 + readProgress > 0 才显示，
 * 格式 N/MP / NP）。W7 的映射点：/download/list 行 DTO 直接进本组件，
 * readProgress 随 DownloadItem 透传。
 * ═══════════════════════════════════════════════════════════════════════ */

import { usePreferencesStore } from '@/stores/preferences'
import { DEFAULT_PREFERENCES } from '@/api/preferences'

function seedGeneral(showReadProgress: boolean): void {
  const store = usePreferencesStore()
  store.prefs = {
    ...DEFAULT_PREFERENCES,
    general: { ...DEFAULT_PREFERENCES.general, showReadProgress },
  }
}

describe('DownloadItem W5/W7 — 阅读进度角标', () => {
  it('shows N/MP when showReadProgress is on and readProgress > 0', () => {
    seedGeneral(true)
    const wrapper = mount(DownloadItem, {
      props: { item: makeDownload({ readProgress: 3, total: 30 }) },
    })
    const badge = wrapper.find('[data-testid="read-progress-badge"]')
    expect(badge.exists()).toBe(true)
    expect(badge.text()).toBe('4/30P')
    // 下载进度文案（30/30 pages）不受影响。
    expect(wrapper.find('.download-item__pages').text()).toBe('30/30 pages')
  })

  it('formats NP when the total page count is unknown', () => {
    seedGeneral(true)
    const wrapper = mount(DownloadItem, {
      props: { item: makeDownload({ readProgress: 2, total: 0 }) },
    })
    expect(wrapper.find('[data-testid="read-progress-badge"]').text()).toBe('3P')
  })

  it('hides the badge when showReadProgress is off', () => {
    seedGeneral(false)
    const wrapper = mount(DownloadItem, {
      props: { item: makeDownload({ readProgress: 3, total: 30 }) },
    })
    expect(wrapper.find('[data-testid="read-progress-badge"]').exists()).toBe(false)
  })

  it('hides the badge when readProgress is missing (legacy server) or 0', () => {
    seedGeneral(true)
    const missing = mount(DownloadItem, { props: { item: makeDownload() } })
    expect(missing.find('[data-testid="read-progress-badge"]').exists()).toBe(false)
    const zero = mount(DownloadItem, { props: { item: makeDownload({ readProgress: 0 }) } })
    expect(zero.find('[data-testid="read-progress-badge"]').exists()).toBe(false)
  })
})
