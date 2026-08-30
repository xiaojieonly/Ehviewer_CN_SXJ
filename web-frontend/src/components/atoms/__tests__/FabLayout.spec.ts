import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import FabLayout from '../FabLayout.vue'
import type { FabAction } from '@/types/components'

const actions: FabAction[] = [
  { id: 'refresh', icon: 'refresh-dark', label: 'Refresh' },
  { id: 'random', icon: 'random-dark', label: 'Random' },
  { id: 'hidden', icon: 'delete-dark', label: 'Hidden', visible: false },
]

describe('FabLayout', () => {
  it('renders the primary FAB and only visible secondary FABs', () => {
    const wrapper = mount(FabLayout, { props: { actions } })
    expect(wrapper.findAll('.fab--primary')).toHaveLength(1)
    // 3 actions, 1 hidden → 2 visible mini FABs.
    expect(wrapper.findAll('.fab--mini')).toHaveLength(2)
  })

  it('renders nothing for an empty action list but keeps the primary', () => {
    const wrapper = mount(FabLayout, { props: { actions: [] } })
    expect(wrapper.findAll('.fab--mini')).toHaveLength(0)
    expect(wrapper.findAll('.fab--primary')).toHaveLength(1)
  })

  it('expands by default (contract default expanded=true)', () => {
    const wrapper = mount(FabLayout, { props: { actions } })
    expect(wrapper.classes()).toContain('fab-layout--expanded')
    // Secondaries are not collapsed when expanded.
    expect(wrapper.find('.fab--mini').classes()).not.toContain('fab--collapsed')
  })

  it('collapses secondary FABs when expanded=false', () => {
    const wrapper = mount(FabLayout, { props: { actions, expanded: false } })
    for (const mini of wrapper.findAll('.fab--mini')) {
      expect(mini.classes()).toContain('fab--collapsed')
    }
  })

  it('toggles expansion when the primary FAB is clicked', async () => {
    const wrapper = mount(FabLayout, { props: { actions, expanded: true } })
    await wrapper.find('.fab--primary').trigger('click')
    expect(wrapper.emitted('update:expanded')![0]).toEqual([false])
    expect(wrapper.emitted('expand')![0]).toEqual([false])
    expect(wrapper.emitted('click-primary')).toBeTruthy()
  })

  it('emits click-secondary with the action and its index', async () => {
    const wrapper = mount(FabLayout, { props: { actions } })
    const minis = wrapper.findAll('.fab--mini')
    await minis[1].trigger('click')
    const emitted = wrapper.emitted('click-secondary')!
    expect(emitted[0][0]).toEqual(actions[1])
    expect(emitted[0][1]).toBe(1)
  })

  it('shows the backdrop only when expanded and autoCancel', () => {
    const expanded = mount(FabLayout, { props: { actions, expanded: true } })
    expect(expanded.find('.fab-layout__backdrop').classes()).toContain(
      'fab-layout__backdrop--visible',
    )

    const collapsed = mount(FabLayout, { props: { actions, expanded: false } })
    expect(collapsed.find('.fab-layout__backdrop').classes()).not.toContain(
      'fab-layout__backdrop--visible',
    )

    const noCancel = mount(FabLayout, { props: { actions, expanded: true, autoCancel: false } })
    expect(noCancel.find('.fab-layout__backdrop').classes()).not.toContain(
      'fab-layout__backdrop--visible',
    )
  })

  it('collapses when the backdrop is clicked (autoCancel)', async () => {
    const wrapper = mount(FabLayout, { props: { actions, expanded: true } })
    await wrapper.find('.fab-layout__backdrop').trigger('click')
    expect(wrapper.emitted('update:expanded')![0]).toEqual([false])
  })

  it('does not collapse on backdrop click when autoCancel=false', async () => {
    const wrapper = mount(FabLayout, {
      props: { actions, expanded: true, autoCancel: false },
    })
    await wrapper.find('.fab-layout__backdrop').trigger('click')
    expect(wrapper.emitted('update:expanded')).toBeUndefined()
  })

  it('hides the primary FAB when hidePrimaryFab and collapsed', () => {
    const wrapper = mount(FabLayout, {
      props: { actions, expanded: false, hidePrimaryFab: true },
    })
    // v-show sets display:none on the primary button.
    const primary = wrapper.find('.fab--primary')
    expect(primary.attributes('style')).toContain('display: none')
  })

  it('keeps the primary visible when expanded even with hidePrimaryFab', () => {
    const wrapper = mount(FabLayout, {
      props: { actions, expanded: true, hidePrimaryFab: true },
    })
    const primary = wrapper.find('.fab--primary')
    expect(primary.attributes('style') ?? '').not.toContain('display: none')
  })

  it('labels secondary FABs for accessibility', () => {
    const wrapper = mount(FabLayout, { props: { actions } })
    const minis = wrapper.findAll('.fab--mini')
    expect(minis[0].attributes('aria-label')).toBe('Refresh')
    expect(minis[1].attributes('aria-label')).toBe('Random')
  })

  // ── alwaysVisible（无 speed-dial 的常驻模式） ─────────────────

  it('alwaysVisible keeps secondary FABs visible even when expanded=false', () => {
    const wrapper = mount(FabLayout, {
      props: { actions: [actions[0]], expanded: false, alwaysVisible: true },
    })
    for (const mini of wrapper.findAll('.fab--mini')) {
      expect(mini.classes()).not.toContain('fab--collapsed')
    }
    // 常驻按钮必须可聚焦（不因折叠被移出 tab 序列）。
    for (const mini of wrapper.findAll('.fab--mini')) {
      expect(mini.attributes('tabindex')).toBe('0')
    }
  })

  it('alwaysVisible never renders the backdrop', () => {
    const expanded = mount(FabLayout, {
      props: { actions, expanded: true, alwaysVisible: true },
    })
    expect(expanded.find('.fab-layout__backdrop').exists()).toBe(false)

    const collapsed = mount(FabLayout, {
      props: { actions, expanded: false, alwaysVisible: true },
    })
    expect(collapsed.find('.fab-layout__backdrop').exists()).toBe(false)
  })

  it('alwaysVisible primary click reports click-primary without toggling expansion', async () => {
    const wrapper = mount(FabLayout, {
      props: { actions: [actions[0]], expanded: false, alwaysVisible: true },
    })
    await wrapper.find('.fab--primary').trigger('click')
    expect(wrapper.emitted('click-primary')).toBeTruthy()
    expect(wrapper.emitted('update:expanded')).toBeUndefined()
    expect(wrapper.emitted('expand')).toBeUndefined()
  })
})
