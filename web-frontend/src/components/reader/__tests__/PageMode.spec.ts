import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import PageMode from '../PageMode.vue'
import { usePreferencesStore } from '@/stores/preferences'
import { markDown, markUnknown } from '@/stores/availability'
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

  // A2（plan-2026-09-05）：非 fit 缩放只是布局基准——不再据此进入可平移态
  // （那会同时吞掉触屏翻页与鼠标点击/滚轮）；只有真正放大（zoom>1）才平移。
  it('original：img 带原始大小类，但不再进入可平移状态（A2）', () => {
    prefsWithScaling('original')
    const wrapper = mountPageMode()
    expect(wrapper.find('.page-mode__img').classes()).toContain('page-mode__img--original')
    expect(wrapper.find('.page-mode').classes()).not.toContain('page-mode--zoomed')
  })

  it('width：适应宽度类 + 不可平移（A2）', () => {
    prefsWithScaling('width')
    const wrapper = mountPageMode()
    expect(wrapper.find('.page-mode__img').classes()).toContain('page-mode__img--width')
    expect(wrapper.find('.page-mode').classes()).not.toContain('page-mode--zoomed')
  })

  it('height：适应高度类 + 不可平移（A2）', () => {
    prefsWithScaling('height')
    const wrapper = mountPageMode()
    expect(wrapper.find('.page-mode__img').classes()).toContain('page-mode__img--height')
    expect(wrapper.find('.page-mode').classes()).not.toContain('page-mode--zoomed')
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

describe('PageMode — 滚轮翻页（plan-2026-09-05 A3）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    prefsWithScaling('fit')
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  /** WheelEvent with deltaY on the stage (jsdom/happy-dom safe). */
  async function wheel(
    wrapper: ReturnType<typeof mountPageMode>,
    deltaY: number,
  ): Promise<void> {
    wrapper.find('.page-mode').element.dispatchEvent(
      new WheelEvent('wheel', { deltaY, cancelable: true }),
    )
    await wrapper.vm.$nextTick()
  }

  it('向下滚 = 下一页，向上滚 = 上一页（物理直觉，不随 RTL 镜像）', async () => {
    const wrapper = mountPageMode()
    await wheel(wrapper, 120)
    expect(wrapper.emitted('next')).toHaveLength(1)

    // 越过节流窗后再向上滚。
    vi.advanceTimersByTime(160)
    await wheel(wrapper, -120)
    expect(wrapper.emitted('prev')).toHaveLength(1)
    expect(wrapper.emitted('next')).toHaveLength(1)
  })

  it('150ms 节流窗内只翻一页（触控板连发只算一次手势）', async () => {
    const wrapper = mountPageMode()
    await wheel(wrapper, 120)
    await wheel(wrapper, 120)
    await wheel(wrapper, 120)
    expect(wrapper.emitted('next')).toHaveLength(1)

    vi.advanceTimersByTime(160)
    await wheel(wrapper, 120)
    expect(wrapper.emitted('next')).toHaveLength(2)
  })

  it('放大状态下滚轮不翻页（拖拽平移优先，A2/A3）', async () => {
    const wrapper = mountPageMode(2)
    await wheel(wrapper, 120)
    expect(wrapper.emitted('next')).toBeUndefined()
  })

  it('翻页事件经 emit 上抛（一次滚动 = 一页语义由节流保证）', async () => {
    const wrapper = mountPageMode()
    await wheel(wrapper, 50)
    await wheel(wrapper, 200)
    expect(wrapper.emitted('next')).toHaveLength(1)
  })
})

describe('PageMode — 边缘热区可视提示（plan-2026-09-05 A4）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    prefsWithScaling('fit')
  })

  it('左右边缘各有一个纯视觉提示层（aria-hidden）', () => {
    const wrapper = mountPageMode()
    const hints = wrapper.findAll('.page-mode__hint')
    expect(hints).toHaveLength(2)
    expect(hints[0].classes()).toContain('page-mode__hint--prev')
    expect(hints[1].classes()).toContain('page-mode__hint--next')
    for (const hint of hints) {
      expect(hint.attributes('aria-hidden')).toBe('true')
      expect(hint.find('svg').exists()).toBe(true)
    }
  })

  it('放大状态下隐藏提示层（热区让位给拖拽平移）', () => {
    const wrapper = mountPageMode(2)
    expect(wrapper.find('.page-mode--zoomed').exists()).toBe(true)
    // 隐藏由 .page-mode--zoomed .page-mode__hint { display:none } 完成，
    // 结构上提示层仍在（触屏设备由 media query 隐藏）。
    expect(wrapper.findAll('.page-mode__hint')).toHaveLength(2)
  })
})

describe('PageMode — EH 熔断：跳过指数退避直接终态（plan-2026-08-30 §0/§3.2）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    prefsWithScaling('fit')
    markUnknown()
  })

  afterEach(() => {
    markUnknown()
    // 任意遗留的指数退避定时器由组件 unmount 清理。
  })

  async function fireImageError(wrapper: ReturnType<typeof mountPageMode>): Promise<void> {
    await wrapper.find('.page-mode__img').trigger('error')
  }

  it('DOWN 时图片失败立即终态（EH 文案），不调度自动重试', async () => {
    markDown('site unreachable')
    const wrapper = mountPageMode()
    const img = wrapper.find('.page-mode__img')
    const srcBefore = img.attributes('src')

    await fireImageError(wrapper)

    const overlay = wrapper.find('.page-mode__overlay--error')
    expect(overlay.exists()).toBe(true)
    expect(overlay.text()).toContain('EH 平台不可达')
    expect(overlay.find('.page-mode__retry').exists()).toBe(true)
    // 未进入指数退避：src 未追加 ?_r= 重试 tick，无自动重试调度。
    expect(img.attributes('src')).toBe(srcBefore)
    wrapper.unmount()
  })

  it('手动重试（终态后）仍然可用', async () => {
    markDown('site unreachable')
    const wrapper = mountPageMode()
    await fireImageError(wrapper)
    expect(wrapper.find('.page-mode__overlay--error').exists()).toBe(true)

    await wrapper.find('.page-mode__retry').trigger('click')
    expect(wrapper.find('.page-mode__overlay--error').exists()).toBe(false)
    // 重试触发新 src（? 后缀）——手动语义保留。
    expect(wrapper.find('.page-mode__img').attributes('src')).toContain('_r=')
    wrapper.unmount()
  })

  it('未知状态仍走指数退避（对照：不直接终态）', async () => {
    const wrapper = mountPageMode()
    await fireImageError(wrapper)
    expect(wrapper.find('.page-mode__overlay--error').exists()).toBe(false)
    wrapper.unmount()
  })
})
