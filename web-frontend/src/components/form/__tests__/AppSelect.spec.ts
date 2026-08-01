import { afterEach, describe, it, expect } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import AppSelect from '../AppSelect.vue'

const OPTIONS = [
  { value: 'home', label: '首页' },
  { value: 'search', label: '搜索' },
  { value: 'favorites', label: '收藏' },
  { value: 'history', label: '历史' },
  { value: 'downloads', label: '下载' },
]

const DISABLED_OPTIONS = [
  { value: 'a', label: 'A' },
  { value: 'b', label: 'B', disabled: true },
  { value: 'c', label: 'C' },
]

const wrappers: VueWrapper[] = []

function mountSelect(props: Record<string, unknown> = {}) {
  const wrapper = mount(AppSelect, {
    props: { modelValue: 'home', options: OPTIONS, ...props },
  })
  wrappers.push(wrapper)
  return wrapper
}

afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
  document.body.innerHTML = ''
})

function menu() {
  return document.body.querySelector('.app-select__menu') as HTMLUListElement | null
}

function options(): HTMLLIElement[] {
  return Array.from(document.body.querySelectorAll('.app-select__option')) as HTMLLIElement[]
}

describe('AppSelect (trigger)', () => {
  it('renders a combobox trigger showing the selected option label (UX-03)', () => {
    const wrapper = mountSelect()
    const trigger = wrapper.find('button.app-select__trigger')
    expect(trigger.attributes('role')).toBe('combobox')
    expect(trigger.attributes('aria-haspopup')).toBe('listbox')
    expect(trigger.attributes('aria-expanded')).toBe('false')
    expect(trigger.find('.app-select__value').text()).toBe('首页')
  })

  it('shows the placeholder when the value matches no option', () => {
    const wrapper = mountSelect({ modelValue: 'nowhere', placeholder: '选择页面' })
    expect(wrapper.find('.app-select__value').text()).toBe('选择页面')
  })

  it('shows an empty value only when placeholder is absent and no option matches', () => {
    const wrapper = mountSelect({ modelValue: 'nowhere' })
    expect(wrapper.find('.app-select__value').text()).toBe('')
  })

  it('does not render the menu while closed', () => {
    mountSelect()
    expect(menu()).toBeNull()
  })

  it('renders the chevron arrow', () => {
    const wrapper = mountSelect()
    expect(wrapper.find('.app-select__arrow').exists()).toBe(true)
  })
})

describe('AppSelect (menu)', () => {
  it('opens the teleported listbox on trigger click and sets aria-expanded', async () => {
    const wrapper = mountSelect()
    await wrapper.find('button.app-select__trigger').trigger('click')
    expect(wrapper.find('button.app-select__trigger').attributes('aria-expanded')).toBe('true')
    expect(menu()).not.toBeNull()
    expect(menu()!.getAttribute('role')).toBe('listbox')
    expect(options()).toHaveLength(5)
  })

  it('renders every option label with role=option', async () => {
    const wrapper = mountSelect()
    await wrapper.find('button.app-select__trigger').trigger('click')
    const labels = options().map((option) => option.textContent)
    expect(labels).toEqual(['首页', '搜索', '收藏', '历史', '下载'])
    expect(options().every((option) => option.getAttribute('role') === 'option')).toBe(true)
  })

  it('marks the selected option with aria-selected and the selected class', async () => {
    const wrapper = mountSelect()
    await wrapper.find('button.app-select__trigger').trigger('click')
    const list = options()
    expect(list[0].getAttribute('aria-selected')).toBe('true')
    expect(list[0].classList.contains('app-select__option--selected')).toBe(true)
    expect(list[1].getAttribute('aria-selected')).toBe('false')
  })
})

describe('AppSelect (selection)', () => {
  it('emits the clicked option value and closes the menu', async () => {
    const wrapper = mountSelect()
    await wrapper.find('button.app-select__trigger').trigger('click')
    options()[2].click()
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
    expect(wrapper.emitted('update:modelValue')![0][0]).toBe('favorites')
    expect(wrapper.find('button.app-select__trigger').attributes('aria-expanded')).toBe('false')
    expect(menu()).toBeNull()
  })

  it('does not select a disabled option', async () => {
    const wrapper = mount(AppSelect, {
      props: { modelValue: 'a', options: DISABLED_OPTIONS },
    })
    wrappers.push(wrapper)
    await wrapper.find('button.app-select__trigger').trigger('click')
    const list = options()
    expect(list[1].getAttribute('aria-disabled')).toBe('true')
    list[1].click()
    expect(wrapper.emitted('update:modelValue')).toBeFalsy()
  })
})

describe('AppSelect (keyboard)', () => {
  it('opens the menu with Enter', async () => {
    const wrapper = mountSelect()
    await wrapper.find('button.app-select__trigger').trigger('keydown', { key: 'Enter' })
    expect(wrapper.find('button.app-select__trigger').attributes('aria-expanded')).toBe('true')
  })

  it('opens the menu with Space', async () => {
    const wrapper = mountSelect()
    await wrapper.find('button.app-select__trigger').trigger('keydown', { key: ' ' })
    expect(wrapper.find('button.app-select__trigger').attributes('aria-expanded')).toBe('true')
  })

  it('opens the menu with ArrowDown and moves the highlight', async () => {
    const wrapper = mountSelect()
    const trigger = wrapper.find('button.app-select__trigger')
    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await trigger.trigger('keydown', { key: 'ArrowDown' })
    const highlighted = document.body.querySelector('.app-select__option--highlighted')
    expect(highlighted).not.toBeNull()
    expect(highlighted!.textContent).toBe('搜索')
  })

  it('moves the highlight with ArrowUp and wraps to the last option', async () => {
    const wrapper = mountSelect()
    const trigger = wrapper.find('button.app-select__trigger')
    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await trigger.trigger('keydown', { key: 'ArrowUp' })
    const highlighted = document.body.querySelector('.app-select__option--highlighted')
    expect(highlighted!.textContent).toBe('下载')
  })

  it('selects the highlighted option with Enter', async () => {
    const wrapper = mountSelect()
    const trigger = wrapper.find('button.app-select__trigger')
    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await trigger.trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('update:modelValue')![0][0]).toBe('favorites')
  })

  it('selects the highlighted option with Space', async () => {
    const wrapper = mountSelect()
    const trigger = wrapper.find('button.app-select__trigger')
    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await trigger.trigger('keydown', { key: ' ' })
    expect(wrapper.emitted('update:modelValue')![0][0]).toBe('search')
  })

  it('closes the menu with Escape', async () => {
    const wrapper = mountSelect()
    const trigger = wrapper.find('button.app-select__trigger')
    await trigger.trigger('keydown', { key: 'Enter' })
    expect(menu()).not.toBeNull()
    await trigger.trigger('keydown', { key: 'Escape' })
    expect(menu()).toBeNull()
  })

  it('skips disabled options while moving the highlight', async () => {
    const wrapper = mount(AppSelect, {
      props: { modelValue: 'a', options: DISABLED_OPTIONS },
    })
    wrappers.push(wrapper)
    const trigger = wrapper.find('button.app-select__trigger')
    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await trigger.trigger('keydown', { key: 'ArrowDown' })
    const highlighted = document.body.querySelector('.app-select__option--highlighted')
    expect(highlighted!.textContent).toBe('C')
  })
})

describe('AppSelect (dismissal)', () => {
  it('closes when clicking outside the component', async () => {
    const wrapper = mountSelect()
    await wrapper.find('button.app-select__trigger').trigger('click')
    expect(menu()).not.toBeNull()
    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await wrapper.vm.$nextTick()
    expect(menu()).toBeNull()
  })

  it('keeps the menu open when clicking the trigger again toggles it closed', async () => {
    const wrapper = mountSelect()
    const trigger = wrapper.find('button.app-select__trigger')
    await trigger.trigger('click')
    expect(menu()).not.toBeNull()
    await trigger.trigger('click')
    expect(menu()).toBeNull()
  })
})

describe('AppSelect (real-browser click path)', () => {
  it('does NOT close the menu on mousedown inside a teleported option', async () => {
    // Regression: the listbox is teleported to <body>, so a mousedown on an
    // option used to be treated as outside, closing the menu and detaching
    // the <li> before the browser dispatches click — selection was lost.
    const wrapper = mountSelect()
    await wrapper.find('button.app-select__trigger').trigger('click')
    const menuItem = document.body.querySelector('.app-select__option') as HTMLElement
    expect(menuItem).toBeTruthy()

    menuItem.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }))
    await wrapper.vm.$nextTick()
    expect(menu()).not.toBeNull()

    menuItem.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true }))
    menuItem.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }))
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
    expect(menu()).toBeNull()
  })

  it('still closes the menu on mousedown outside both trigger and menu', async () => {
    const wrapper = mountSelect()
    await wrapper.find('button.app-select__trigger').trigger('click')
    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await wrapper.vm.$nextTick()
    expect(menu()).toBeNull()
  })
})

describe('AppSelect (disabled)', () => {
  it('does not open when disabled', async () => {
    const wrapper = mountSelect({ disabled: true })
    const trigger = wrapper.find('button.app-select__trigger')
    expect(wrapper.find('.app-select').classes()).toContain('app-select--disabled')
    await trigger.trigger('click')
    expect(wrapper.find('button.app-select__trigger').attributes('aria-expanded')).toBe('false')
    expect(menu()).toBeNull()
    expect(trigger.attributes('disabled')).toBeDefined()
  })
})
