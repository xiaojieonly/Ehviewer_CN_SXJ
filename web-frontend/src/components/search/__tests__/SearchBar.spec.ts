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

    it('ignores Enter while the IME composition is active (C1)', async () => {
      const wrapper = mount(SearchBar, { props: { state: 'search', query: 'futa' } })
      const input = wrapper.find('input')
      // isComposing（Chrome/Firefox/Safari 组合中）。
      await input.trigger('keydown.enter', { isComposing: true })
      // keyCode 229（Safari 部分 keydown 不带 isComposing）。
      await input.trigger('keydown.enter', { keyCode: 229 })
      expect(wrapper.emitted('search')).toBeUndefined()
    })

    it('ignores Escape while the IME composition is active (C1)', async () => {
      const wrapper = mount(SearchBar, { props: { state: 'search', query: 'futa' } })
      const input = wrapper.find('input')
      await input.trigger('keydown.esc', { isComposing: true })
      await input.trigger('keydown.esc', { keyCode: 229 })
      expect(wrapper.emitted('back')).toBeUndefined()
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

  describe('Wave-1 1a additives — filter button (task A5)', () => {
    it('hides the filter button in normal state and when filterVisible is false', () => {
      const normal = mount(SearchBar, { props: { state: 'normal', filterVisible: true } })
      expect(normal.find('.search-bar__filter').exists()).toBe(false)

      const hidden = mount(SearchBar, { props: { state: 'search', filterVisible: false } })
      expect(hidden.find('.search-bar__filter').exists()).toBe(false)
    })

    it('shows the filter button in search states when filterVisible', async () => {
      const wrapper = mount(SearchBar, { props: { state: 'search', filterVisible: true } })
      const button = wrapper.find('.search-bar__filter')
      expect(button.exists()).toBe(true)
      await button.trigger('click')
      expect(wrapper.emitted('click-filter')).toHaveLength(1)
    })

    it('mirrors filterPanelOpen via aria-pressed and marks filterActive with a dot', () => {
      const closed = mount(SearchBar, {
        props: { state: 'search', filterVisible: true, filterPanelOpen: false, filterActive: true },
      })
      expect(closed.find('.search-bar__filter').attributes('aria-pressed')).toBe('false')
      expect(closed.find('.search-bar__filter-dot').exists()).toBe(true)

      const open = mount(SearchBar, {
        props: { state: 'search', filterVisible: true, filterPanelOpen: true, filterActive: false },
      })
      expect(open.find('.search-bar__filter').attributes('aria-pressed')).toBe('true')
      expect(open.find('.search-bar__filter-dot').exists()).toBe(false)
    })
  })

  describe('Wave-1 1a additives — active filter chip row (task A5)', () => {
    const CHIPS = [
      { id: 'sort', label: 'Sort: Rating — highest first' },
      { id: 'category:4', label: 'Excl. Manga' },
      { id: 'minRating', label: 'Rating ≥ 4★' },
    ]

    it('renders no chip row without chips, one chip per entry otherwise', () => {
      const empty = mount(SearchBar, { props: { state: 'normal' } })
      expect(empty.find('.search-bar__chips').exists()).toBe(false)

      const wrapper = mount(SearchBar, { props: { state: 'normal', filterChips: CHIPS } })
      expect(wrapper.findAll('.search-bar__chip')).toHaveLength(3)
      expect(wrapper.findAll('.search-bar__chip-label').map((c) => c.text())).toEqual([
        'Sort: Rating — highest first',
        'Excl. Manga',
        'Rating ≥ 4★',
      ])
    })

    it('chip × emits remove-filter-chip with that chip id only', async () => {
      const wrapper = mount(SearchBar, { props: { state: 'search', filterChips: CHIPS } })
      const removes = wrapper.findAll('.search-bar__chip-remove')
      await removes[1].trigger('click')
      expect(wrapper.emitted('remove-filter-chip')).toHaveLength(1)
      expect(wrapper.emitted('remove-filter-chip')?.[0]).toEqual(['category:4'])
    })

    it('Clear emits clear-filter-chips', async () => {
      const wrapper = mount(SearchBar, { props: { state: 'search', filterChips: CHIPS } })
      await wrapper.find('.search-bar__chips-clear').trigger('click')
      expect(wrapper.emitted('clear-filter-chips')).toHaveLength(1)
    })
  })

  describe('Wave-1 1a additives — focusInput (task A5)', () => {
    it('exposes focusInput() which focuses the edit text in search state', async () => {
      // focus() only works on attached elements — mount into the document.
      const wrapper = mount(SearchBar, { props: { state: 'search' }, attachTo: document.body })
      const input = wrapper.find('input')
      expect(document.activeElement).not.toBe(input.element)
      ;(wrapper.vm as unknown as { focusInput(): void }).focusInput()
      expect(document.activeElement).toBe(input.element)
      wrapper.unmount()
    })
  })
})
