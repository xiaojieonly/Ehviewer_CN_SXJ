import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import AdminEhSession from '../admin/AdminEhSession.vue'
import { authApi, type EhSessionResponse } from '@/api/auth'
import { settingsApi, type Settings } from '@/api/settings'
import client from '@/api/client'
import { AppSegmented, AppSwitch, AppTextField } from '@/components/form'

vi.mock('@/api/auth', () => ({
  authApi: {
    ehLogin: vi.fn(),
    ehLogout: vi.fn(),
    ehSession: vi.fn(),
    setEhSite: vi.fn(),
  },
}))

vi.mock('@/api/settings', () => ({
  settingsApi: { get: vi.fn(), update: vi.fn() },
}))

vi.mock('@/api/client', () => ({
  default: { post: vi.fn() },
}))

const SIGNED_OUT: EhSessionResponse = { signedIn: false, expired: false, gallerySite: 0, cookies: [] }
const EXPIRED: EhSessionResponse = {
  signedIn: false,
  expired: true,
  gallerySite: 0,
  cookies: [
    { name: 'ipb_session_id', value: 'stale', domain: '.e-hentai.org', expiresAt: 0 },
  ],
}
const SIGNED_IN: EhSessionResponse = {
  signedIn: true,
  expired: false,
  gallerySite: 0,
  cookies: [
    { name: 'ipb_session_id', value: 'abc123', domain: '.e-hentai.org', expiresAt: 1893456000000 },
    { name: 'igneous', value: 'xyz', domain: '.e-hentai.org', expiresAt: 1893456000000 },
  ],
}

function settingsWithProxy(overrides: Partial<Settings['proxy']> = {}): Settings {
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
    security: { requireAuth: false, sessionTimeout: 86400 },
    processing: { enabled: false, defaultType: 'UPSCALE_2X', outputFormat: 'png', outputQuality: 90 },
    proxy: {
      enabled: false,
      type: 'http',
      host: '',
      port: 0,
      username: '',
      password: '',
      ...overrides,
    },
  }
}

describe('AdminEhSession (EH 会话)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    vi.mocked(authApi.ehSession).mockResolvedValue(SIGNED_OUT)
    vi.mocked(authApi.ehLogin).mockResolvedValue({ success: true, message: 'ok', username: 'u' })
    vi.mocked(authApi.ehLogout).mockResolvedValue({ success: true, message: 'ok' })
    vi.mocked(authApi.setEhSite).mockResolvedValue({ success: true, message: 'ok' })
    vi.mocked(settingsApi.get).mockResolvedValue(settingsWithProxy())
    vi.mocked(settingsApi.update).mockResolvedValue(true)
    vi.spyOn(window, 'open').mockImplementation(() => null)
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
      configurable: true,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.restoreAllMocks()
    vi.clearAllMocks()
  })

  async function mountView() {
    wrapper = mount(AdminEhSession)
    await flushPromises()
    return wrapper
  }

  function field(ariaLabel: string) {
    return wrapper.findAllComponents(AppTextField).find((f) => f.props('ariaLabel') === ariaLabel)!
  }

  it('loads the session on mount and renders the signed-out badge', async () => {
    const w = await mountView()
    expect(authApi.ehSession).toHaveBeenCalledTimes(1)
    expect(w.text()).toContain('未登录')
    expect(w.text()).toContain('登录')
  })

  it('renders the login form with username and password fields when signed out', async () => {
    const w = await mountView()
    const ehFields = w
      .findAllComponents(AppTextField)
      .filter((f) => (f.props('ariaLabel') ?? '').startsWith('EH'))
    expect(ehFields.map((f) => f.props('ariaLabel'))).toEqual(['EH 用户名', 'EH 密码'])
    expect(field('EH 密码').props('type')).toBe('password')
    expect(w.find('button.eh-session__btn--primary').text()).toBe('登录')
  })

  it('submits ehLogin with the entered credentials and refreshes on success', async () => {
    const w = await mountView()
    await field('EH 用户名').find('input').setValue('myuser')
    await field('EH 密码').find('input').setValue('secret')
    await w.find('button.eh-session__btn--primary').trigger('click')
    await flushPromises()
    expect(authApi.ehLogin).toHaveBeenCalledWith('myuser', 'secret')
    // Mount-time fetch + post-login refresh.
    expect(authApi.ehSession).toHaveBeenCalledTimes(2)
  })

  it('shows the server message when login fails', async () => {
    vi.mocked(authApi.ehLogin).mockResolvedValue({ success: false, message: 'Invalid username or password.' })
    const w = await mountView()
    await field('EH 用户名').find('input').setValue('myuser')
    await field('EH 密码').find('input').setValue('bad')
    await w.find('button.eh-session__btn--primary').trigger('click')
    await flushPromises()
    expect(w.find('[role="alert"]').text()).toContain('Invalid username or password.')
    expect(authApi.ehSession).toHaveBeenCalledTimes(1)
  })

  it('shows a validation error when credentials are missing', async () => {
    const w = await mountView()
    await w.find('button.eh-session__btn--primary').trigger('click')
    await flushPromises()
    expect(w.find('[role="alert"]').text()).toContain('请输入用户名和密码')
    expect(authApi.ehLogin).not.toHaveBeenCalled()
  })

  it('renders cookies and a logout button when signed in', async () => {
    vi.mocked(authApi.ehSession).mockResolvedValue(SIGNED_IN)
    const w = await mountView()
    expect(w.text()).toContain('已登录')
    expect(w.text()).toContain('ipb_session_id')
    expect(w.text()).toContain('abc123')
    expect(w.text()).toContain('igneous')
    expect(w.find('button.eh-session__btn--danger').text()).toBe('登出')
  })

  it('shows the expired badge and renders the login form for an expired session', async () => {
    vi.mocked(authApi.ehSession).mockResolvedValue(EXPIRED)
    const w = await mountView()
    expect(w.text()).toContain('已过期')
    expect(field('EH 用户名').exists()).toBe(true)
  })

  it('calls ehLogout and refreshes the session when logging out', async () => {
    vi.mocked(authApi.ehSession)
      .mockResolvedValueOnce(SIGNED_IN)
      .mockResolvedValue(SIGNED_OUT)
    const w = await mountView()
    await w.find('button.eh-session__btn--danger').trigger('click')
    await flushPromises()
    expect(authApi.ehLogout).toHaveBeenCalledTimes(1)
    // Mount-time fetch + post-logout refresh.
    expect(authApi.ehSession).toHaveBeenCalledTimes(2)
    expect(w.text()).toContain('未登录')
  })

  it('copies a cookie value via navigator.clipboard and shows feedback', async () => {
    vi.mocked(authApi.ehSession).mockResolvedValue(SIGNED_IN)
    const w = await mountView()
    await w.find('[aria-label="复制 ipb_session_id"]').trigger('click')
    await flushPromises()
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('abc123')
    expect(w.text()).toContain('已复制 ipb_session_id')
  })

  it('opens the E-Hentai links in a new window', async () => {
    const w = await mountView()
    await w.find('[aria-label="打开用户设置"]').trigger('click')
    await w.find('[aria-label="打开我的标签"]').trigger('click')
    await w.find('[aria-label="打开 e-hentai 首页"]').trigger('click')
    expect(window.open).toHaveBeenNthCalledWith(1, 'https://e-hentai.org/uconfig.php', '_blank', 'noopener')
    expect(window.open).toHaveBeenNthCalledWith(2, 'https://e-hentai.org/mytags', '_blank', 'noopener')
    expect(window.open).toHaveBeenNthCalledWith(3, 'https://e-hentai.org', '_blank', 'noopener')
  })

  it('initializes the site selector from ehSession().gallerySite', async () => {
    vi.mocked(authApi.ehSession).mockResolvedValue({ ...SIGNED_OUT, gallerySite: 1 })
    const w = await mountView()
    const segmented = w.findComponent(AppSegmented)
    expect(segmented.exists()).toBe(true)
    expect(segmented.props('modelValue')).toBe('1')
    expect(segmented.text()).toContain('exhentai')
  })

  it('switches the site via setEhSite(1) and updates the selection', async () => {
    const w = await mountView()
    expect(w.findComponent(AppSegmented).props('modelValue')).toBe('0')
    await w.findComponent(AppSegmented).findAll('.app-segmented__btn')[1].trigger('click')
    await flushPromises()
    expect(authApi.setEhSite).toHaveBeenCalledWith(1)
    expect(w.text()).toContain('站点已切换')
    expect(w.findComponent(AppSegmented).props('modelValue')).toBe('1')
  })

  it('rolls the site back and reports when the switch fails', async () => {
    vi.mocked(authApi.setEhSite).mockResolvedValue({ success: false, message: 'switch denied' })
    const w = await mountView()
    await w.findComponent(AppSegmented).findAll('.app-segmented__btn')[1].trigger('click')
    await flushPromises()
    expect(w.findComponent(AppSegmented).props('modelValue')).toBe('0')
    expect(w.text()).toContain('switch denied')
  })

  it('uses exhentai URLs for the external links when the site is exhentai', async () => {
    vi.mocked(authApi.ehSession).mockResolvedValue({ ...SIGNED_OUT, gallerySite: 1 })
    const w = await mountView()
    await w.find('[aria-label="打开用户设置"]').trigger('click')
    await w.find('[aria-label="打开我的标签"]').trigger('click')
    await w.find('[aria-label="打开 exhentai 首页"]').trigger('click')
    expect(window.open).toHaveBeenNthCalledWith(1, 'https://exhentai.org/uconfig.php', '_blank', 'noopener')
    expect(window.open).toHaveBeenNthCalledWith(2, 'https://exhentai.org/mytags', '_blank', 'noopener')
    expect(window.open).toHaveBeenNthCalledWith(3, 'https://exhentai.org', '_blank', 'noopener')
  })

  it('renders the proxy section with the enabled switch and host/port inputs', async () => {
    const w = await mountView()
    expect(w.findComponent(AppSwitch).exists()).toBe(true)
    expect(w.findComponent(AppSwitch).attributes('aria-label')).toBe('启用代理')
    expect(field('代理服务器地址').exists()).toBe(true)
    expect(field('代理端口').exists()).toBe(true)
    expect(field('代理用户名').exists()).toBe(true)
    expect(field('代理密码').exists()).toBe(true)
    expect(settingsApi.get).toHaveBeenCalledTimes(1)
  })

  it('loads the saved proxy settings into the form', async () => {
    vi.mocked(settingsApi.get).mockResolvedValue(
      settingsWithProxy({ enabled: true, type: 'socks5', host: '127.0.0.1', port: 1080 }),
    )
    const w = await mountView()
    expect((field('代理服务器地址').find('input').element as HTMLInputElement).value).toBe('127.0.0.1')
    expect((field('代理端口').find('input').element as HTMLInputElement).value).toBe('1080')
    expect(w.findComponent(AppSwitch).attributes('aria-checked')).toBe('true')
  })

  it('saves the proxy via settingsApi.update({ proxy })', async () => {
    const w = await mountView()
    await field('代理服务器地址').find('input').setValue('proxy.example.com')
    const save = w.findAll('button').find((b) => b.text() === '保存并应用')!
    await save.trigger('click')
    await flushPromises()
    expect(settingsApi.update).toHaveBeenCalledWith(
      expect.objectContaining({ proxy: expect.objectContaining({ host: 'proxy.example.com' }) }),
    )
    expect(w.text()).toContain('已保存')
    expect(w.text()).toContain('代理设置已保存并生效')
  })

  it('tests the proxy with the current form values', async () => {
    vi.mocked(client.post).mockResolvedValue({ data: { success: true, latencyMs: 42, error: '' } })
    const w = await mountView()
    const test = w.findAll('button').find((b) => b.text() === '测试连接')!
    await test.trigger('click')
    await flushPromises()
    expect(client.post).toHaveBeenCalledWith('/proxy/test', expect.objectContaining({ enabled: false }))
    expect(w.text()).toContain('连接成功，延迟 42ms')
  })
})
