import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import LoginView from '../LoginView.vue'
import { AppTextField } from '@/components/form'

const authMock = vi.hoisted(() => ({
  login: vi.fn(),
  register: vi.fn(),
  username: null,
}))

const routerMock = vi.hoisted(() => ({
  push: vi.fn(),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authMock,
}))

vi.mock('vue-router', () => ({
  useRouter: () => routerMock,
}))

function loginResult(overrides: Partial<{ success: boolean; message: string }> = {}): {
  success: boolean
  message: string
} {
  return { success: true, message: '', ...overrides }
}

describe('LoginView', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    authMock.login.mockReset()
    authMock.register.mockReset()
    routerMock.push.mockReset()
  })

  afterEach(() => {
    wrapper?.unmount()
  })

  async function typeField(label: string, value: string): Promise<void> {
    const field = wrapper
      .findAllComponents(AppTextField)
      .find((f) => f.props('label') === label)!
    await field.find('input').setValue(value)
  }

  async function submit(): Promise<void> {
    await wrapper.find('form').trigger('submit')
    await flushPromises()
  }

  it('renders username/password fields via AppTextField', () => {
    wrapper = mount(LoginView)
    const fields = wrapper.findAllComponents(AppTextField)
    expect(fields.map((f) => f.props('label'))).toEqual(['Username', 'Password'])
    expect(fields[1].props('type')).toBe('password')
  })

  it('keeps browser autocomplete hints for password managers', () => {
    wrapper = mount(LoginView)
    const fields = wrapper.findAllComponents(AppTextField)
    expect(fields[0].props('autocomplete')).toBe('username')
    expect(fields[1].props('autocomplete')).toBe('current-password')
  })

  it('binds the typed values back via v-model', async () => {
    wrapper = mount(LoginView)
    await typeField('Username', 'bob')
    await typeField('Password', 's3cret')
    const fields = wrapper.findAllComponents(AppTextField)
    expect((fields[0].find('input').element as HTMLInputElement).value).toBe('bob')
    expect((fields[1].find('input').element as HTMLInputElement).value).toBe('s3cret')
  })

  it('submits the credentials to authStore.login and navigates home', async () => {
    authMock.login.mockResolvedValue(loginResult())
    wrapper = mount(LoginView)
    await typeField('Username', 'bob')
    await typeField('Password', 's3cret')
    await submit()
    expect(authMock.login).toHaveBeenCalledWith('bob', 's3cret')
    expect(routerMock.push).toHaveBeenCalledWith('/')
  })

  it('shows the server message when login fails', async () => {
    authMock.login.mockResolvedValue(loginResult({ success: false, message: 'Bad credentials' }))
    wrapper = mount(LoginView)
    await typeField('Username', 'bob')
    await typeField('Password', 'nope')
    await submit()
    expect(routerMock.push).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Bad credentials')
  })

  it('rejects an empty submission with an inline error', async () => {
    wrapper = mount(LoginView)
    await submit()
    expect(authMock.login).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Enter both a username and a password')
  })

  it('registers a new account via authStore.register', async () => {
    authMock.register.mockResolvedValue(loginResult())
    wrapper = mount(LoginView)
    await typeField('Username', 'newbie')
    await typeField('Password', 'pw123')
    await wrapper.find('.btn-register').trigger('click')
    await flushPromises()
    expect(authMock.register).toHaveBeenCalledWith('newbie', 'pw123')
    expect(routerMock.push).toHaveBeenCalledWith('/')
  })
})
