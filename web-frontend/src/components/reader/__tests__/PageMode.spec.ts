import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import PageMode from '../PageMode.vue'
import { usePreferencesStore } from '@/stores/preferences'
import {
  DEFAULT_PREFERENCES,
  DEFAULT_READER_PREFERENCES,
} from '@/api/preferences'

function prefsWithScaling(pageScaling: string): void {
  const store = usePreferencesStore()
  store.prefs = {
    general: { ...DEFAULT_PREFERENCES.general },
    reader: { ...DEFAULT_READER_PREFERENCES, pageScaling },
    privacy: { ...DEFAULT_PREFERENCES.privacy },
  }
}

function mountPageMode(zoom = 1) {
  return mount(PageMode, {
    props: {
      gid: 123456,
      page: 0,
      totalPages: 10,
      direction: 'ltr',
      zoom,
    },
    attachTo: document.body,
  })
}

describe('PageMode — reader.pageScaling 接入', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('默认 fit：img 带适应屏幕类，舞台不可平移', () => {
    prefsWithScaling('fit')
    const wrapper = mountPageMode()
    const img = wrapper.find('.page-mode__img')
    expect(img.classes()).toContain('page-mode__img--fit')
    expect(img.classes()).not.toContain('page-mode__img--original')
    expect(wrapper.find('.page-mode').classes()).not.toContain('page-mode--zoomed')
  })

  it('original：img 带原始大小类，且舞台进入可平移状态', () => {
    prefsWithScaling('original')
    const wrapper = mountPageMode()
    expect(wrapper.find('.page-mode__img').classes()).toContain('page-mode__img--original')
    expect(wrapper.find('.page-mode').classes()).toContain('page-mode--zoomed')
  })

  it('width：适应宽度类 + 可平移', () => {
    prefsWithScaling('width')
    const wrapper = mountPageMode()
    expect(wrapper.find('.page-mode__img').classes()).toContain('page-mode__img--width')
    expect(wrapper.find('.page-mode').classes()).toContain('page-mode--zoomed')
  })

  it('height：适应高度类 + 可平移', () => {
    prefsWithScaling('height')
    const wrapper = mountPageMode()
    expect(wrapper.find('.page-mode__img').classes()).toContain('page-mode__img--height')
    expect(wrapper.find('.page-mode').classes()).toContain('page-mode--zoomed')
  })

  it('非 fit 缩放下不发送 srcset（避免 DPR 候选与布局尺寸脱节）', () => {
    prefsWithScaling('original')
    const wrapper = mountPageMode()
    expect(wrapper.find('.page-mode__img').attributes('srcset')).toBeUndefined()
  })
})

describe('PageMode — 放大平移（既有语义回归）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    prefsWithScaling('fit')
  })

  it('zoom > 1 时舞台可平移', () => {
    const wrapper = mountPageMode(2)
    expect(wrapper.find('.page-mode').classes()).toContain('page-mode--zoomed')
  })
})
