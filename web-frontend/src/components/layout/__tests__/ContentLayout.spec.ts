import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick, ref, KeepAlive } from 'vue'
import ContentLayout from '../ContentLayout.vue'

describe('ContentLayout', () => {
  describe('state rendering', () => {
    it('renders the loading state with a large progress spinner', () => {
      const wrapper = mount(ContentLayout, { props: { state: 'loading' } })
      expect(wrapper.find('[data-testid="content-state-loading"]').exists()).toBe(true)
      expect(wrapper.find('.pl-spinner--large svg').exists()).toBe(true)
      // Only one state view is mounted at a time.
      expect(wrapper.find('[data-testid="content-state-content"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="content-state-empty"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="content-state-error"]').exists()).toBe(false)
    })

    it('renders the empty state with sadpanda icon and emptyText', () => {
      const wrapper = mount(ContentLayout, {
        props: { state: 'empty', emptyText: '这里什么都没有' },
      })
      const empty = wrapper.find('[data-testid="content-state-empty"]')
      expect(empty.exists()).toBe(true)
      expect(empty.text()).toContain('这里什么都没有')
      expect(wrapper.find('.content-layout__tip-icon svg').exists()).toBe(true)
    })

    it('falls back to the default emptyText "No hint"', () => {
      const wrapper = mount(ContentLayout, { props: { state: 'empty' } })
      expect(wrapper.find('[data-testid="content-state-empty"]').text()).toContain('No hint')
    })

    it('renders the error state with errorText and a retry button', () => {
      const wrapper = mount(ContentLayout, {
        props: { state: 'error', errorText: '网络错误' },
      })
      const error = wrapper.find('[data-testid="content-state-error"]')
      expect(error.exists()).toBe(true)
      expect(error.text()).toContain('网络错误')
      expect(wrapper.find('[data-testid="content-retry"]').exists()).toBe(true)
    })

    it('renders the content state with the default slot', () => {
      const wrapper = mount(ContentLayout, {
        props: { state: 'content' },
        slots: { default: '<p class="gallery-item">gallery</p>' },
      })
      expect(wrapper.find('[data-testid="content-state-content"]').exists()).toBe(true)
      expect(wrapper.find('.gallery-item').text()).toBe('gallery')
    })
  })

  describe('events', () => {
    it('tapping the empty tip emits retry (Android tip onClick)', async () => {
      const wrapper = mount(ContentLayout, { props: { state: 'empty' } })
      await wrapper.find('[data-testid="content-state-empty"]').trigger('click')
      expect(wrapper.emitted('retry')).toHaveLength(1)
    })

    it('the error retry button emits refresh', async () => {
      const wrapper = mount(ContentLayout, { props: { state: 'error' } })
      await wrapper.find('[data-testid="content-retry"]').trigger('click')
      expect(wrapper.emitted('refresh')).toHaveLength(1)
    })
  })

  describe('content composition', () => {
    it('embeds the FastScroller by default', () => {
      const wrapper = mount(ContentLayout, {
        props: { state: 'content' },
        slots: { default: '<p>x</p>' },
      })
      expect(wrapper.find('.fast-scroller').exists()).toBe(true)
      expect(wrapper.find('.content-layout__plain-scroll').exists()).toBe(false)
    })

    it('uses a plain scroll container when fastScroll is disabled', () => {
      const wrapper = mount(ContentLayout, {
        props: { state: 'content', fastScroll: false },
        slots: { default: '<p>x</p>' },
      })
      expect(wrapper.find('.fast-scroller').exists()).toBe(false)
      expect(wrapper.find('.content-layout__plain-scroll').exists()).toBe(true)
    })

    it('mounts a pull-to-refresh header inside the content body', () => {
      const wrapper = mount(ContentLayout, {
        props: { state: 'content' },
        slots: { default: '<p>x</p>' },
      })
      const header = wrapper.find('.content-layout__refresh')
      expect(header.exists()).toBe(true)
      // Parked above the viewport when idle.
      expect(header.attributes('style')).toContain('translateY(-56px)')
    })

    it('shows the footer spinner while loadingMore', () => {
      const wrapper = mount(ContentLayout, {
        props: { state: 'content', loadingMore: true },
        slots: { default: '<p>x</p>' },
      })
      expect(wrapper.find('[data-testid="content-loading-more"]').exists()).toBe(true)
    })

    it('hides the footer spinner when not loadingMore', () => {
      const wrapper = mount(ContentLayout, {
        props: { state: 'content', loadingMore: false },
        slots: { default: '<p>x</p>' },
      })
      expect(wrapper.find('[data-testid="content-loading-more"]').exists()).toBe(false)
    })

    it('shows no load-more button by default (hasMore unset = pure auto-load)', () => {
      const wrapper = mount(ContentLayout, {
        props: { state: 'content' },
        slots: { default: '<p>x</p>' },
      })
      expect(wrapper.find('[data-testid="content-load-more"]').exists()).toBe(false)
    })

    it('shows the load-more fallback button when hasMore is true (B3)', () => {
      const wrapper = mount(ContentLayout, {
        props: { state: 'content', hasMore: true },
        slots: { default: '<p>x</p>' },
      })
      expect(wrapper.find('[data-testid="content-load-more"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="content-loading-more"]').exists()).toBe(false)
    })

    it('clicking the load-more button emits load-more (B3)', async () => {
      const wrapper = mount(ContentLayout, {
        props: { state: 'content', hasMore: true },
        slots: { default: '<p>x</p>' },
      })
      await wrapper.find('[data-testid="content-load-more"]').trigger('click')
      expect(wrapper.emitted('load-more')).toHaveLength(1)
    })

    it('swaps the button back to the spinner while loadingMore (B3)', () => {
      const wrapper = mount(ContentLayout, {
        props: { state: 'content', hasMore: true, loadingMore: true },
        slots: { default: '<p>x</p>' },
      })
      expect(wrapper.find('[data-testid="content-loading-more"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="content-load-more"]').exists()).toBe(false)
    })

    it('renders the load-more button in the plain-scroll branch too (B3)', () => {
      const wrapper = mount(ContentLayout, {
        props: { state: 'content', hasMore: true, fastScroll: false },
        slots: { default: '<p>x</p>' },
      })
      expect(wrapper.find('[data-testid="content-load-more"]').exists()).toBe(true)
    })

    it('parks the refresh header open while refreshing is true', async () => {
      const wrapper = mount(ContentLayout, {
        props: { state: 'content', refreshing: true },
        slots: { default: '<p>x</p>' },
      })
      const header = wrapper.find('.content-layout__refresh')
      expect(header.attributes('style')).toContain('translateY(0px)')
      expect(header.attributes('style')).toContain('opacity: 1')

      await wrapper.setProps({ refreshing: false })
      expect(wrapper.find('.content-layout__refresh').attributes('style')).toContain(
        'translateY(-56px)',
      )
    })
  })

  /**
   * KeepAlive 滚动记忆（Android Scene 栈等价）：列表被缓存停用后 DOM 脱离
   * 文档，scrollTop 会丢——按路由记住离开时的位置，重新激活时还原。
   * 用真实 <KeepAlive> + v-if 开合模拟「离开列表 → 返回列表」。
   */
  describe('scroll memory (KeepAlive 滚动还原)', () => {
    /**
     * jsdom 无布局：像 DownloadView.spec 一样用 own property 顶掉原型 getter；
     * 同时给出非零 scrollHeight（还原的就绪门控在真实浏览器里等虚拟列表
     * 测量完成，jsdom 里直接视为就绪）。
     */
    function stubScrollTop(el: HTMLElement) {
      let value = 0
      const writes: number[] = []
      Object.defineProperty(el, 'scrollTop', {
        configurable: true,
        get: () => value,
        set: (v: number) => {
          value = v
          writes.push(v)
        },
      })
      Object.defineProperty(el, 'scrollHeight', { configurable: true, get: () => 2000 })
      Object.defineProperty(el, 'clientHeight', { configurable: true, get: () => 800 })
      return { writes, set: (v: number) => (value = v) }
    }

    function mountToggledList() {
      const show = ref(true)
      const Host = defineComponent({
        setup() {
          return () =>
            h(KeepAlive, () =>
              show.value
                ? h(
                    ContentLayout,
                    { state: 'content', fastScroll: false },
                    { default: () => h('div', { style: 'height: 2000px' }) },
                  )
                : null,
            )
        },
      })
      return { show, wrapper: mount(Host, { attachTo: document.body }) }
    }

    beforeEach(() => {
      vi.useFakeTimers()
    })

    afterEach(() => {
      vi.useRealTimers()
    })

    it('reactivation restores the remembered scroll position', async () => {
      const { show, wrapper } = mountToggledList()
      const el = wrapper.find('.content-layout__plain-scroll').element as HTMLElement
      const scroll = stubScrollTop(el)

      scroll.set(500)
      el.dispatchEvent(new Event('scroll'))
      await vi.advanceTimersByTimeAsync(250) // trailing 节流落账

      show.value = false
      await nextTick()
      show.value = true
      await nextTick()
      await nextTick() // 还原走 onActivated + nextTick

      expect(scroll.writes).toContain(500)
      wrapper.unmount()
    })

    it('a pending save firing while detached does not poison the memory', async () => {
      const { show, wrapper } = mountToggledList()
      const el = wrapper.find('.content-layout__plain-scroll').element as HTMLElement
      const scroll = stubScrollTop(el)

      // 第一段滚动正常落账：800 进记忆。
      scroll.set(800)
      el.dispatchEvent(new Event('scroll'))
      await vi.advanceTimersByTimeAsync(250)

      // 第二段滚动后立刻离开：200ms 节流窗口未到 DOM 就已脱离文档——计时器
      // 再落账时 isConnected 守卫必须跳过（脱离后 scrollTop 恒 0，否则记忆
      // 被毒化成 0，回来直接被拽到列表顶部）。
      scroll.set(850)
      el.dispatchEvent(new Event('scroll'))
      show.value = false
      await nextTick()
      await vi.advanceTimersByTimeAsync(250)

      show.value = true
      await nextTick()
      await nextTick()

      expect(scroll.writes).toContain(800)
      expect(scroll.writes).not.toContain(0)
      wrapper.unmount()
    })

    it('does not restore on the initial mount, but does on later reactivations', async () => {
      // 独立 location key，避免模块级 Map 的跨用例残留干扰断言。
      history.replaceState(null, '', '/scroll-skip-first')
      // 原型级 stub：既能捕获新元素上的还原写入，又给所有元素统一的读值；
      // scrollHeight 一并给足（就绪门控直接通过，否则 jsdom 里永远不落位、
      // 断言空转）。
      const writes: number[] = []
      const protoDesc = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollTop')
      const scrollHeightDesc = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollHeight')
      const clientHeightDesc = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'clientHeight')
      Object.defineProperty(HTMLElement.prototype, 'scrollTop', {
        configurable: true,
        get: () => 0,
        set: (v: number) => writes.push(v),
      })
      Object.defineProperty(HTMLElement.prototype, 'scrollHeight', {
        configurable: true,
        get: () => 2000,
      })
      Object.defineProperty(HTMLElement.prototype, 'clientHeight', {
        configurable: true,
        get: () => 800,
      })
      try {
        const Host = defineComponent({
          setup() {
            return () =>
              h(KeepAlive, () =>
                h(
                  ContentLayout,
                  { state: 'content', fastScroll: false },
                  { default: () => h('div', { style: 'height: 2000px' }) },
                ),
              )
          },
        })
        const mountList = () => mount(Host, { attachTo: document.body })

        // 第一段：滚到 600 并落账。
        const wrapper = mountList()
        const el = wrapper.find('.content-layout__plain-scroll').element as HTMLElement
        let value = 0
        Object.defineProperty(el, 'scrollTop', {
          configurable: true,
          get: () => value,
          set: (v: number) => {
            value = v
            writes.push(v)
          },
        })
        await nextTick()
        expect(writes).toEqual([]) // 首挂载不还原
        value = 600
        el.dispatchEvent(new Event('scroll'))
        await vi.advanceTimersByTimeAsync(250)
        const writesAfterSave = writes.length
        wrapper.unmount()

        // 第二段：同 key 全新实例的首挂载——firstActivation 必须跳过还原
        // （记忆里已有 600；若跳过失效，新元素上会出现一条 600 写入）。
        const wrapper2 = mountList()
        await nextTick()
        await nextTick()
        expect(writes.slice(writesAfterSave)).toEqual([])
        wrapper2.unmount()
      } finally {
        if (protoDesc) Object.defineProperty(HTMLElement.prototype, 'scrollTop', protoDesc)
        if (scrollHeightDesc)
          Object.defineProperty(HTMLElement.prototype, 'scrollHeight', scrollHeightDesc)
        if (clientHeightDesc)
          Object.defineProperty(HTMLElement.prototype, 'clientHeight', clientHeightDesc)
        history.replaceState(null, '', '/')
      }
    })
  })
})
