import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
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
})
