import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import AdminLayout from '../admin/AdminLayout.vue'

const ADMIN_PATHS = [
  '/admin/download',
  '/admin/server',
  '/admin/devices',
  '/admin/proxy',
  '/admin/access',
  '/admin/processing',
  '/admin/advanced',
  '/admin/about',
]

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: ADMIN_PATHS.map((path) => ({ path, component: { template: '<div />' } })),
  })
}

describe('AdminLayout (UX-04)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    Element.prototype.scrollIntoView = vi.fn()
  })

  afterEach(() => {
    wrapper?.unmount()
  })

  it('renders the tab bar container used for horizontal scrolling', async () => {
    const router = makeRouter()
    await router.push('/admin/download')
    await router.isReady()
    wrapper = mount(AdminLayout, { global: { plugins: [router] } })
    const nav = wrapper.find('.admin-layout__nav')
    expect(nav.exists()).toBe(true)
    expect(wrapper.findAll('.admin-layout__link')).toHaveLength(8)
  })

  it('marks the tab matching the current route as active', async () => {
    const router = makeRouter()
    await router.push('/admin/proxy')
    await router.isReady()
    wrapper = mount(AdminLayout, { global: { plugins: [router] } })
    const active = wrapper
      .findAll('.admin-layout__link')
      .filter((link) => link.classes().includes('is-active'))
    expect(active).toHaveLength(1)
    expect(active[0].text()).toBe('代理')
  })

  it('scrolls the active tab into view on mount and on route change', async () => {
    const scrollIntoView = vi.fn()
    Element.prototype.scrollIntoView = scrollIntoView

    const router = makeRouter()
    await router.push('/admin/download')
    await router.isReady()
    wrapper = mount(AdminLayout, { global: { plugins: [router] } })
    await flushPromises()
    expect(scrollIntoView).toHaveBeenCalledTimes(1)
    expect(scrollIntoView).toHaveBeenCalledWith({ inline: 'center', block: 'nearest' })

    await router.push('/admin/advanced')
    await flushPromises()
    expect(scrollIntoView).toHaveBeenCalledTimes(2)
  })
})
