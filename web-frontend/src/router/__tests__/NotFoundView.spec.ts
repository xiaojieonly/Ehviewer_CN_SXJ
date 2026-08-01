import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import NotFoundView from '@/views/NotFoundView.vue'

const routerMock = vi.hoisted(() => ({
  push: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => routerMock,
}))

describe('NotFoundView (UX-05)', () => {
  beforeEach(() => {
    routerMock.push.mockReset()
  })

  it('shows the 404 heading and 页面不存在 copy', () => {
    const wrapper = mount(NotFoundView)
    expect(wrapper.find('.not-found__code').text()).toBe('404')
    expect(wrapper.text()).toContain('页面不存在')
  })

  it('navigates home when the 返回首页 button is clicked', async () => {
    const wrapper = mount(NotFoundView)
    await wrapper.find('.not-found__action').trigger('click')
    expect(routerMock.push).toHaveBeenCalledWith('/')
  })
})
