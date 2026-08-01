import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import SettingsLayout from '../settings/SettingsLayout.vue'

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/settings/general', component: { template: '<div />' } },
      { path: '/settings/reader', component: { template: '<div />' } },
      { path: '/settings/privacy', component: { template: '<div />' } },
      { path: '/settings/transfer', component: { template: '<div />' } },
    ],
  })
}

describe('SettingsLayout (UX-04)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    Element.prototype.scrollIntoView = vi.fn()
  })

  afterEach(() => {
    wrapper?.unmount()
  })

  it('renders the tab bar container used for horizontal scrolling', async () => {
    const router = makeRouter()
    await router.push('/settings/general')
    await router.isReady()
    wrapper = mount(SettingsLayout, { global: { plugins: [router] } })
    const nav = wrapper.find('.settings-layout__nav')
    expect(nav.exists()).toBe(true)
    expect(wrapper.findAll('.settings-layout__link')).toHaveLength(4)
  })

  it('marks the tab matching the current route as active', async () => {
    const router = makeRouter()
    await router.push('/settings/reader')
    await router.isReady()
    wrapper = mount(SettingsLayout, { global: { plugins: [router] } })
    const active = wrapper
      .findAll('.settings-layout__link')
      .filter((link) => link.classes().includes('is-active'))
    expect(active).toHaveLength(1)
    expect(active[0].text()).toBe('阅读器')
  })

  it('scrolls the active tab into view on mount and on route change', async () => {
    const scrollIntoView = vi.fn()
    Element.prototype.scrollIntoView = scrollIntoView

    const router = makeRouter()
    await router.push('/settings/general')
    await router.isReady()
    wrapper = mount(SettingsLayout, { global: { plugins: [router] } })
    await flushPromises()
    expect(scrollIntoView).toHaveBeenCalledTimes(1)
    expect(scrollIntoView).toHaveBeenCalledWith({ inline: 'center', block: 'nearest' })

    await router.push('/settings/reader')
    await flushPromises()
    expect(scrollIntoView).toHaveBeenCalledTimes(2)
  })
})
