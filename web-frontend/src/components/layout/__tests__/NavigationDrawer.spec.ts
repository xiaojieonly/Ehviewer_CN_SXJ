import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import NavigationDrawer, { DEFAULT_NAV_ITEMS } from '../NavigationDrawer.vue'
import type { NavItem } from '@/types/components'

describe('NavigationDrawer', () => {
  describe('menu items', () => {
    it('renders exactly the canonical 9 menu items in order', () => {
      const wrapper = mount(NavigationDrawer, { props: { open: true } })
      const items = wrapper.findAll('[data-testid="drawer-item"]')
      expect(items).toHaveLength(9)
      expect(items.map((i) => i.text())).toEqual([
        '首页',
        '订阅',
        '热门',
        '排行榜',
        '收藏',
        '历史',
        '下载',
        '设置',
        '管理面板',
      ])
    })

    it('uses the Android nav_drawer_main.xml ids', () => {
      expect(DEFAULT_NAV_ITEMS.map((i) => i.id)).toEqual([
        'homepage',
        'subscription',
        'whats_hot',
        'top_lists',
        'favourite',
        'history',
        'downloads',
        'settings',
        'admin',
      ])
    })

    it('renders an icon for every item', () => {
      const wrapper = mount(NavigationDrawer, { props: { open: true } })
      const icons = wrapper.findAll('[data-testid="drawer-item"] .app-icon')
      expect(icons).toHaveLength(9)
    })

    it('accepts custom items via the items prop', () => {
      const custom: NavItem[] = [{ id: 'only', label: '唯一', icon: 'heart' }]
      const wrapper = mount(NavigationDrawer, { props: { open: true, items: custom } })
      const items = wrapper.findAll('[data-testid="drawer-item"]')
      expect(items).toHaveLength(1)
      expect(items[0].text()).toBe('唯一')
    })
  })

  describe('selection', () => {
    it('emits select + update:modelValue and requests close on item tap', async () => {
      const wrapper = mount(NavigationDrawer, { props: { open: true } })
      await wrapper.findAll('[data-testid="drawer-item"]')[4].trigger('click') // 收藏

      const select = wrapper.emitted('select')
      expect(select).toHaveLength(1)
      expect((select![0][0] as NavItem).id).toBe('favourite')
      expect(wrapper.emitted('update:modelValue')![0]).toEqual(['favourite'])
      // Android closes the drawer after a navigation selection.
      expect(wrapper.emitted('update:open')![0]).toEqual([false])
    })

    it('highlights the item matching activeItemId', () => {
      const wrapper = mount(NavigationDrawer, {
        props: { open: true, activeItemId: 'history' },
      })
      const active = wrapper
        .findAll('[data-testid="drawer-item"]')
        .filter((i) => i.classes().includes('is-active'))
      expect(active).toHaveLength(1)
      expect(active[0].text()).toBe('历史')
      expect(active[0].attributes('aria-checked')).toBe('true')
    })

    it('supports modelValue as the active route id (takes precedence)', () => {
      const wrapper = mount(NavigationDrawer, {
        props: { open: true, modelValue: 'downloads', activeItemId: 'history' },
      })
      const active = wrapper
        .findAll('[data-testid="drawer-item"]')
        .filter((i) => i.classes().includes('is-active'))
      expect(active).toHaveLength(1)
      expect(active[0].text()).toBe('下载')
    })

    it('marks no item active when nothing is selected', () => {
      const wrapper = mount(NavigationDrawer, { props: { open: true } })
      expect(
        wrapper.findAll('[data-testid="drawer-item"]').filter((i) => i.classes().includes('is-active')),
      ).toHaveLength(0)
    })
  })

  describe('header', () => {
    it('renders username and avatar image', () => {
      const wrapper = mount(NavigationDrawer, {
        props: { open: true, username: 'bob', avatarUrl: 'https://example.com/a.png' },
      })
      expect(wrapper.find('.navigation-drawer__username').text()).toBe('bob')
      expect(wrapper.find('img.navigation-drawer__avatar').attributes('src')).toBe(
        'https://example.com/a.png',
      )
    })

    it('falls back to an initial avatar without avatarUrl', () => {
      const wrapper = mount(NavigationDrawer, {
        props: { open: true, username: 'bob' },
      })
      expect(wrapper.find('img.navigation-drawer__avatar').exists()).toBe(false)
      expect(wrapper.find('.navigation-drawer__avatar--fallback').text()).toBe('B')
    })
  })

  describe('footer', () => {
    it('emits both theme-toggle events from the footer button', async () => {
      const wrapper = mount(NavigationDrawer, { props: { open: true } })
      await wrapper.find('[data-testid="drawer-theme-toggle"]').trigger('click')
      expect(wrapper.emitted('toggle-theme')).toHaveLength(1)
      expect(wrapper.emitted('theme-toggle')).toHaveLength(1)
    })

    it('renders the quota widget and emits refresh-quota', async () => {
      const wrapper = mount(NavigationDrawer, {
        props: { open: true, quota: { current: 12, total: 5000 } },
      })
      const quota = wrapper.find('[data-testid="drawer-quota"]')
      expect(quota.exists()).toBe(true)
      expect(quota.text()).toContain('12')
      expect(quota.text()).toContain('5000')
      await quota.find('button').trigger('click')
      expect(wrapper.emitted('refresh-quota')).toHaveLength(1)
    })

    it('hides the quota widget when quota is null', () => {
      const wrapper = mount(NavigationDrawer, { props: { open: true, quota: null } })
      expect(wrapper.find('[data-testid="drawer-quota"]').exists()).toBe(false)
    })
  })

  describe('open / scrim', () => {
    it('shows the scrim while open and emits update:open=false on tap', async () => {
      const wrapper = mount(NavigationDrawer, { props: { open: true } })
      const scrim = wrapper.find('[data-testid="drawer-scrim"]')
      expect(scrim.exists()).toBe(true)
      await scrim.trigger('click')
      expect(wrapper.emitted('update:open')![0]).toEqual([false])
    })

    it('omits the scrim and slides the panel away when closed', () => {
      const wrapper = mount(NavigationDrawer, { props: { open: false } })
      expect(wrapper.find('[data-testid="drawer-scrim"]').exists()).toBe(false)
      expect(wrapper.classes()).not.toContain('is-open')
    })
  })
})
