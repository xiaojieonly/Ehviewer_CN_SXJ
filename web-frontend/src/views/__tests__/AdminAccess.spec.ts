import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import AdminAccess from '../admin/AdminAccess.vue'
import { settingsApi, type Settings } from '@/api/settings'
import { authApi } from '@/api/auth'
import { AppSwitch, AppTextField, PrefRow, SectionHeader } from '@/components/form'

vi.mock('@/api/settings', () => ({
  settingsApi: { get: vi.fn(), update: vi.fn() },
}))

vi.mock('@/api/auth', () => ({
  authApi: { changePassword: vi.fn() },
}))

function settingsWithSecurity(overrides: Partial<Settings['security']> = {}): Settings {
  return {
    download: {
      path: '/data',
      workerCount: 4,
      downloadDelay: 1000,
      downloadTimeout: 30000,
      maxConcurrentGalleries: 2,
      maxConcurrentImages: 8,
    },
    cache: { path: '/cache', sizeMb: 5120 },
    smb: { enabled: false },
    security: { requireAuth: false, sessionTimeout: 86400, ...overrides },
    processing: { enabled: false, defaultType: 'UPSCALE_2X', outputFormat: 'png', outputQuality: 90 },
    proxy: { enabled: false, type: 'http', host: '', port: 0, username: '', password: '' },
  }
}

describe('AdminAccess (访问控制)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    vi.mocked(settingsApi.get).mockResolvedValue(settingsWithSecurity())
    vi.mocked(settingsApi.update).mockResolvedValue(true)
    vi.mocked(authApi.changePassword).mockResolvedValue({ success: true, message: 'Password changed' })
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
  })

  async function mountView() {
    wrapper = mount(AdminAccess)
    await flushPromises()
    return wrapper
  }

  it('renders shared primitives with password AppTextFields', async () => {
    const w = await mountView()
    expect(w.findAllComponents(SectionHeader).map((h) => h.props('title'))).toEqual(['登录', '修改密码'])
    expect(w.findAllComponents(PrefRow).map((r) => r.props('title'))).toEqual([
      '需要登录',
      'Session 超时（秒）',
    ])
    expect(w.find('select').exists()).toBe(false)
    expect(w.find('.switch').exists()).toBe(false)

    const sw = w.findComponent(AppSwitch)
    expect(sw.exists()).toBe(true)
    expect(sw.attributes('aria-label')).toBe('需要登录')

    const fields = w.findAllComponents(AppTextField)
    expect(fields.length).toBe(3)
    expect(fields.map((f) => f.props('label'))).toEqual(['旧密码', '新密码', '确认新密码'])
    expect(fields.every((f) => f.props('type') === 'password')).toBe(true)
  })

  it('toggles requireAuth via AppSwitch and persists security', async () => {
    const w = await mountView()
    const sw = w.findComponent(AppSwitch)
    expect(sw.attributes('aria-checked')).toBe('false')
    expect(w.text()).toContain('关闭后，局域网内任何人都可以访问此服务器')

    await sw.trigger('click')
    await flushPromises()
    expect(settingsApi.update).toHaveBeenCalledWith(
      expect.objectContaining({ security: expect.objectContaining({ requireAuth: true }) }),
    )
    expect(w.text()).not.toContain('关闭后，局域网内任何人都可以访问此服务器')
  })

  it('keeps the warning visible when auth is off after load', async () => {
    vi.mocked(settingsApi.get).mockResolvedValue(settingsWithSecurity({ requireAuth: true }))
    const w = await mountView()
    expect(w.findComponent(AppSwitch).attributes('aria-checked')).toBe('true')
    expect(w.text()).not.toContain('关闭后，局域网内任何人都可以访问此服务器')
  })

  it('persists a new session timeout from the number field', async () => {
    const w = await mountView()
    const input = w.find('.pref__number')
    ;(input.element as HTMLInputElement).value = '7200'
    await input.trigger('change')
    await flushPromises()
    expect(settingsApi.update).toHaveBeenCalledWith(
      expect.objectContaining({ security: expect.objectContaining({ sessionTimeout: 7200 }) }),
    )
  })

  async function fillPasswordForm(w: VueWrapper, oldPw: string, newPw: string) {
    const [oldField, newField, confirmField] = w.findAllComponents(AppTextField)
    await oldField.setValue(oldPw)
    await newField.setValue(newPw)
    await confirmField.setValue(newPw)
  }

  it('submits change-password, shows success and clears the fields', async () => {
    const w = await mountView()
    const btn = w.find('.access-form .btn-primary')
    expect(btn.attributes('disabled')).toBeUndefined()

    await fillPasswordForm(w, 'old-pass-1', 'new-pass-1')
    await btn.trigger('click')
    await flushPromises()

    expect(authApi.changePassword).toHaveBeenCalledWith('old-pass-1', 'new-pass-1')
    expect(w.text()).toContain('Password changed')
    const fields = w.findAllComponents(AppTextField)
    expect(fields.map((f) => f.props('modelValue'))).toEqual(['', '', ''])
  })

  it('shows the backend message when the request fails', async () => {
    vi.mocked(authApi.changePassword).mockResolvedValue({
      success: false,
      message: 'Old password is incorrect',
    })
    const w = await mountView()

    await fillPasswordForm(w, 'wrong-pass', 'new-pass-1')
    await w.find('.access-form .btn-primary').trigger('click')
    await flushPromises()

    expect(authApi.changePassword).toHaveBeenCalledWith('wrong-pass', 'new-pass-1')
    expect(w.text()).toContain('Old password is incorrect')
    expect(w.findAllComponents(AppTextField).map((f) => f.props('modelValue'))).toEqual([
      'wrong-pass',
      'new-pass-1',
      'new-pass-1',
    ])
  })

  it('rejects empty fields client-side without calling the API', async () => {
    const w = await mountView()

    await w.find('.access-form .btn-primary').trigger('click')
    await flushPromises()

    expect(authApi.changePassword).not.toHaveBeenCalled()
    expect(w.text()).toContain('请填写完整的密码信息')
  })

  it('rejects mismatched confirmation client-side without calling the API', async () => {
    const w = await mountView()
    const [oldField, newField, confirmField] = w.findAllComponents(AppTextField)
    await oldField.setValue('old-pass-1')
    await newField.setValue('new-pass-1')
    await confirmField.setValue('different-pass')

    await w.find('.access-form .btn-primary').trigger('click')
    await flushPromises()

    expect(authApi.changePassword).not.toHaveBeenCalled()
    expect(w.text()).toContain('两次输入的新密码不一致')
  })
})
