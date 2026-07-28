import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import SearchBar from '../SearchBar.vue'
import type { SearchSuggestion } from '@/types/components'

const SUGGESTIONS: SearchSuggestion[] = [
  { text: 'naruto', hint: 'manga' },
  { text: 'one piece' },
]

describe('SearchBar', () => {
  describe('title mode (state="normal")', () => {
    it('renders the page title and no input', () => {
      const wrapper = mount(SearchBar, { props: { state: 'normal', title: 'Homepage' } })
      expect(wrapper.find('.search-bar__title').text()).toBe('Homepage')
      expect(wrapper.find('input').exists()).toBe(false)
    })

    it('emits click-title when the title is tapped', async () => {
      const wrapper = mount(SearchBar, { props: { state: 'normal', title: 'Homepage' } })
      await wrapper.find('.search-bar__title').trigger('click')
      expect(wrapper.emitted('click-title')).toHaveLength(1)
    })

    it('renders menu and action icons that emit click-menu / click-action', async () => {
      const wrapper = mount(SearchBar, { props: { state: 'normal', title: 'Homepage' } })
      const icons = wrapper.findAll('.search-bar__icon')
      expect(icons).toHaveLength(2)
      await icons[0].trigger('click')
      await icons[1].trigger('click')
      expect(wrapper.emitted('click-menu')).toHaveLength(1)
      expect(wrapper.emitted('click-action')).toHaveLength(1)
    })

    it('hides the menu icon when leftIcon is null but keeps the 48px margin', () => {
      const wrapper = mount(SearchBar, {
        props: { state: 'normal', title: 'Homepage', leftIcon: null },
      })
      expect(wrapper.findAll('.search-bar__icon')).toHaveLength(1) // action only
      expect(wrapper.find('.search-bar__icon-spacer').exists()).toBe(true)
    })
  })

  describe('input mode (state="search")', () => {
    it('renders the edit text and hides the title', () => {
      const wrapper = mount(SearchBar, { props: { state: 'search', title: 'Homepage' } })
      expect(wrapper.find('input').exists()).toBe(true)
      expect(wrapper.find('.search-bar__title').exists()).toBe(false)
    })

    it('emits update:query while typing', async () => {
      const wrapper = mount(SearchBar, { props: { state: 'search' } })
      await wrapper.find('input').setValue('naruto')
      expect(wrapper.emitted('update:query')?.[0]).toEqual(['naruto'])
    })

    it('emits search with the current query on Enter', async () => {
      const wrapper = mount(SearchBar, { props: { state: 'search', query: 'bleach' } })
      await wrapper.find('input').trigger('keydown.enter')
      expect(wrapper.emitted('search')?.[0]).toEqual(['bleach'])
    })

    it('allows an empty search by default', async () => {
      const wrapper = mount(SearchBar, { props: { state: 'search', query: '' } })
      await wrapper.find('input').trigger('keydown.enter')
      expect(wrapper.emitted('search')?.[0]).toEqual([''])
    })

    it('blocks an empty search when allowEmptySearch is false', async () => {
      const wrapper = mount(SearchBar, {
        props: { state: 'search', query: '', allowEmptySearch: false },
      })
      await wrapper.find('input').trigger('keydown.enter')
      expect(wrapper.emitted('search')).toBeUndefined()
    })

    it('shows a clear button that empties the query', async () => {
      const wrapper = mount(SearchBar, { props: { state: 'search', query: 'bleach' } })
      const clear = wrapper.find('.search-bar__clear')
      expect(clear.exists()).toBe(true)
      await clear.trigger('click')
      expect(wrapper.emitted('update:query')?.[0]).toEqual([''])
    })

    it('hides the clear button when the query is empty', () => {
      const wrapper = mount(SearchBar, { props: { state: 'search', query: '' } })
      expect(wrapper.find('.search-bar__clear').exists()).toBe(false)
    })

    it('emits back on Escape', async () => {
      const wrapper = mount(SearchBar, { props: { state: 'search', query: 'x' } })
      await wrapper.find('input').trigger('keydown.esc')
      expect(wrapper.emitted('back')).toHaveLength(1)
    })

    it('emits back on Backspace only when the query is empty', async () => {
      const withText = mount(SearchBar, { props: { state: 'search', query: 'x' } })
      await withText.find('input').trigger('keydown.backspace')
      expect(withText.emitted('back')).toBeUndefined()

      const empty = mount(SearchBar, { props: { state: 'search', query: '' } })
      await empty.find('input').trigger('keydown.backspace')
      expect(empty.emitted('back')).toHaveLength(1)
    })
  })

  describe('suggestion list (state="search-list")', () => {
    it('renders suggestion rows with text and optional hint', () => {
      const wrapper = mount(SearchBar, {
        props: { state: 'search-list', suggestions: SUGGESTIONS },
      })
      const rows = wrapper.findAll('.search-bar__suggestion')
      expect(rows).toHaveLength(2)
      expect(rows[0].text()).toContain('naruto')
      expect(rows[0].text()).toContain('manga')
      expect(rows[1].text()).toBe('one piece')
    })

    it('opens the clip only in search-list state', () => {
      const open = mount(SearchBar, {
        props: { state: 'search-list', suggestions: SUGGESTIONS },
      })
      expect(open.find('.search-bar__list-clip').classes()).toContain('is-open')

      const closed = mount(SearchBar, {
        props: { state: 'search', suggestions: SUGGESTIONS },
      })
      expect(closed.find('.search-bar__list-clip').classes()).not.toContain('is-open')
    })

    it('emits select-suggestion with the row and index on tap', async () => {
      const wrapper = mount(SearchBar, {
        props: { state: 'search-list', suggestions: SUGGESTIONS },
      })
      await wrapper.findAll('.search-bar__suggestion')[1].trigger('click')
      expect(wrapper.emitted('select-suggestion')?.[0]).toEqual([SUGGESTIONS[1], 1])
    })

    it('emits dismiss-suggestion on long-press (contextmenu)', async () => {
      const wrapper = mount(SearchBar, {
        props: { state: 'search-list', suggestions: SUGGESTIONS },
      })
      await wrapper.findAll('.search-bar__suggestion')[0].trigger('contextmenu')
      expect(wrapper.emitted('dismiss-suggestion')?.[0]).toEqual([SUGGESTIONS[0], 0])
    })
  })

  it('floats as an elevated card (margins + shadow from tokens)', () => {
    const wrapper = mount(SearchBar, { props: { state: 'normal', title: 'Homepage' } })
    expect(wrapper.find('.search-bar').exists()).toBe(true)
    expect(wrapper.find('.search-bar__card').exists()).toBe(true)
  })
})
