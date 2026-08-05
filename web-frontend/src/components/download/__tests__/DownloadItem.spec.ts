import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import DownloadItem from '../DownloadItem.vue'
import type { DownloadItem as DownloadItemType } from '@/api/download'

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
})
