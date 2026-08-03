import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import AdminAdvanced from '../admin/AdminAdvanced.vue'
import { AppSelect, AppSwitch, PrefRow, SectionHeader } from '@/components/form'

const UI_KEY = 'anotherviewer-admin-advanced-ui'

describe('AdminAdvanced (高级)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.restoreAllMocks()
  })

  it('renders shared primitives and no native select', async () => {
    wrapper = mount(AdminAdvanced)
    expect(wrapper.findAllComponents(SectionHeader).map((h) => h.props('title'))).toEqual(['通用', '同步策略', '数据'])
    expect(wrapper.findAllComponents(PrefRow).map((r) => r.props('title'))).toEqual([
      '界面语言',
      '保存解析错误日志',
      '冲突仲裁策略',
      '自动同步间隔（秒）',
      '导出数据',
      '导入数据',
      '清除本地数据',
    ])
    expect(wrapper.find('select').exists()).toBe(false)
    expect(wrapper.find('.switch').exists()).toBe(false)

    const select = wrapper.findComponent(AppSelect)
    expect(select.exists()).toBe(true)
    expect(select.props('options')).toEqual([
      { value: 'zh-CN', label: '简体中文' },
      { value: 'zh-TW', label: '繁體中文' },
      { value: 'en-US', label: 'English' },
      { value: 'ja-JP', label: '日本語' },
    ])
    expect(select.text()).toContain('简体中文')

    const sw = wrapper.findComponent(AppSwitch)
    expect(sw.exists()).toBe(true)
    expect(sw.attributes('aria-label')).toBe('保存解析错误日志')
  })

  it('changes the UI language via AppSelect and persists to localStorage', async () => {
    wrapper = mount(AdminAdvanced, { attachTo: document.body })
    await wrapper.find('.app-select__trigger').trigger('click')
    const option = [...document.body.querySelectorAll<HTMLElement>('.app-select__option')].find(
      (el) => el.textContent === 'English',
    )
    expect(option).toBeTruthy()
    option!.click()

    expect(JSON.parse(localStorage.getItem(UI_KEY)!).language).toBe('en-US')
    wrapper.unmount()
  })

  it('toggles the parse-error switch and persists to localStorage', async () => {
    wrapper = mount(AdminAdvanced)
    const sw = wrapper.findComponent(AppSwitch)
    expect(sw.attributes('aria-checked')).toBe('false')
    await sw.trigger('click')
    expect(sw.attributes('aria-checked')).toBe('true')
    expect(JSON.parse(localStorage.getItem(UI_KEY)!).saveParseErrors).toBe(true)
  })

  it('clears local data after confirmation, keeping auth keys', async () => {
    localStorage.setItem('token', 'keep-me')
    localStorage.setItem('username', 'bob')
    localStorage.setItem('anotherviewer-search-history', 'x')
    localStorage.setItem('anotherviewer-admin-download-ui', '{}')
    vi.spyOn(window, 'confirm')

    wrapper = mount(AdminAdvanced)
    await wrapper.find('[aria-label="清除本地数据"]').trigger('click')
    expect(wrapper.text()).toContain('删除此浏览器中所有本地的设置、缓存与搜索历史？')

    const clear = wrapper.findAll('button').find((b) => b.text() === '清除')!
    await clear.trigger('click')
    expect(localStorage.getItem('token')).toBe('keep-me')
    expect(localStorage.getItem('username')).toBe('bob')
    expect(localStorage.getItem('anotherviewer-search-history')).toBeNull()
    expect(localStorage.getItem('anotherviewer-admin-download-ui')).toBeNull()
    expect(wrapper.text()).toContain('已清除 2 项本地数据')
  })
})
