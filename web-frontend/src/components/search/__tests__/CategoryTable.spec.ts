import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import CategoryTable from '../CategoryTable.vue'
import type { GalleryCategory } from '@/types/components'
import { CATEGORY_LABELS, CATEGORY_ORDER } from '@/types/components'

const ALL: GalleryCategory[] = [...CATEGORY_ORDER]

function blockFor(wrapper: ReturnType<typeof mount>, category: GalleryCategory) {
  const index = CATEGORY_ORDER.indexOf(category)
  return wrapper.findAll('.category-table__block')[index]
}

describe('CategoryTable', () => {
  it('renders all 10 categories in CATEGORY_ORDER with their labels', () => {
    const wrapper = mount(CategoryTable, { props: { selected: ALL } })
    const blocks = wrapper.findAll('.category-table__block')
    expect(blocks).toHaveLength(10)
    expect(CATEGORY_ORDER).toHaveLength(10)
    blocks.forEach((block, index) => {
      expect(block.text()).toBe(CATEGORY_LABELS[CATEGORY_ORDER[index]])
    })
  })

  it('colors every block from the tokens.css category variables', () => {
    const wrapper = mount(CategoryTable, { props: { selected: ALL } })
    wrapper.findAll('.category-table__block').forEach((block, index) => {
      const suffix = CATEGORY_ORDER[index].replace(/_/g, '-')
      expect(block.attributes('style')).toContain(`var(--color-cat-${suffix})`)
    })
  })

  it('toggles an unselected block on tap (positive semantics)', async () => {
    const wrapper = mount(CategoryTable, { props: { selected: [] } })
    await blockFor(wrapper, 'manga').trigger('click')
    expect(wrapper.emitted('update:selected')?.[0]).toEqual([['manga']])
  })

  it('toggles a selected block off on tap', async () => {
    const wrapper = mount(CategoryTable, { props: { selected: ['manga', 'misc'] } })
    await blockFor(wrapper, 'manga').trigger('click')
    expect(wrapper.emitted('update:selected')?.[0]).toEqual([['misc']])
  })

  it('supports multi-select across taps (verified via remount)', async () => {
    // First tap selects doujinshi…
    const first = mount(CategoryTable, { props: { selected: [] } })
    await blockFor(first, 'doujinshi').trigger('click')
    const afterFirst = first.emitted('update:selected')?.[0][0] as GalleryCategory[]
    expect(afterFirst).toEqual(['doujinshi'])

    // …second tap (on the updated state) adds western.
    const second = mount(CategoryTable, { props: { selected: afterFirst } })
    await blockFor(second, 'western').trigger('click')
    expect(second.emitted('update:selected')?.[0]).toEqual([['doujinshi', 'western']])
  })

  it('"All" selects every category, and clears when everything is selected', async () => {
    const empty = mount(CategoryTable, { props: { selected: [] } })
    await empty.find('.category-table__all').trigger('click')
    expect(empty.emitted('update:selected')?.[0]).toEqual([ALL])

    const full = mount(CategoryTable, { props: { selected: ALL } })
    await full.find('.category-table__all').trigger('click')
    expect(full.emitted('update:selected')?.[0]).toEqual([[]])
  })

  it('long-press on an included block selects ONLY it (Android inversion rule)', async () => {
    const wrapper = mount(CategoryTable, { props: { selected: ALL } })
    await blockFor(wrapper, 'cosplay').trigger('contextmenu')
    expect(wrapper.emitted('update:selected')?.[0]).toEqual([['cosplay']])
    expect(wrapper.emitted('long-press')?.[0]).toEqual(['cosplay'])
  })

  it('long-press on an excluded block selects everything BUT it', async () => {
    // Only misc is included, so doujinshi is excluded here.
    const wrapper = mount(CategoryTable, { props: { selected: ['misc'] } })
    await blockFor(wrapper, 'doujinshi').trigger('contextmenu')
    expect(wrapper.emitted('update:selected')?.[0]).toEqual([
      CATEGORY_ORDER.filter((c) => c !== 'doujinshi'),
    ])
  })

  it('exposes selection state via aria-pressed and the is-selected class', () => {
    const wrapper = mount(CategoryTable, { props: { selected: ['non_h'] } })
    const selected = blockFor(wrapper, 'non_h')
    expect(selected.attributes('aria-pressed')).toBe('true')
    expect(selected.classes()).toContain('is-selected')

    const unselected = blockFor(wrapper, 'doujinshi')
    expect(unselected.attributes('aria-pressed')).toBe('false')
    expect(unselected.classes()).not.toContain('is-selected')
  })

  it('shows a check mark only on selected blocks', () => {
    const wrapper = mount(CategoryTable, { props: { selected: ['image_set'] } })
    expect(blockFor(wrapper, 'image_set').find('.category-table__check').exists()).toBe(true)
    expect(blockFor(wrapper, 'manga').find('.category-table__check').exists()).toBe(false)
  })
})
