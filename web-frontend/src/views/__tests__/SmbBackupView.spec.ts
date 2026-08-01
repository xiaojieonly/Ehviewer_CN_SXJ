import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import SmbBackupView from '../SmbBackupView.vue'
import { smbApi } from '@/api/smb'
import { AppSelect, AppSwitch, AppTextField } from '@/components/form'

const authMock = vi.hoisted(() => ({ username: 'tester' }))
const themeMock = vi.hoisted(() => ({ currentTheme: 'dark', toggleTheme: vi.fn() }))
const routerMock = vi.hoisted(() => ({ push: vi.fn() }))

vi.mock('@/api/smb', () => ({
  smbApi: {
    getConfig: vi.fn(),
    updateConfig: vi.fn(),
    testConnection: vi.fn(),
    sync: vi.fn(),
    cancel: vi.fn(),
    getProgress: vi.fn(),
  },
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authMock,
}))

vi.mock('@/stores/theme', () => ({
  useThemeStore: () => themeMock,
}))

vi.mock('vue-router', () => ({
  useRouter: () => routerMock,
}))

const IDLE_PROGRESS = {
  state: 'idle',
  totalFiles: 0,
  syncedFiles: 0,
  currentFile: '',
  speed: 0,
}

describe('SmbBackupView (SMB 备份)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    localStorage.clear()
    vi.mocked(smbApi.getConfig).mockResolvedValue(null)
    vi.mocked(smbApi.getProgress).mockResolvedValue(IDLE_PROGRESS)
    vi.mocked(smbApi.updateConfig).mockResolvedValue(true)
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
  })

  async function mountView() {
    wrapper = mount(SmbBackupView)
    await flushPromises()
    return wrapper
  }

  async function openForm() {
    await mountView()
    await wrapper.find('.btn-add').trigger('click')
  }

  it('renders the connection form with shared form primitives', async () => {
    await openForm()
    const labels = wrapper.findAllComponents(AppTextField).map((f) => f.props('label'))
    expect(labels).toEqual(['服务器地址', '端口', '共享文件夹', '远程路径（可选）'])

    const select = wrapper.findComponent(AppSelect)
    expect(select.exists()).toBe(true)
    expect(select.props('options')).toEqual([
      { value: 'GUEST', label: '访客' },
      { value: 'USER', label: '用户名 / 密码' },
    ])

    const sw = wrapper.findComponent(AppSwitch)
    expect(sw.exists()).toBe(true)
    expect(sw.attributes('aria-label')).toBe('启用 SMB 备份')
  })

  it('edits form values through v-model on AppTextField', async () => {
    await openForm()
    const host = wrapper
      .findAllComponents(AppTextField)
      .find((f) => f.props('label') === '服务器地址')!
    await host.find('input').setValue('192.168.1.10')
    expect((host.find('input').element as HTMLInputElement).value).toBe('192.168.1.10')

    const port = wrapper
      .findAllComponents(AppTextField)
      .find((f) => f.props('label') === '端口')!
    await port.find('input').setValue('4450')
    expect((port.find('input').element as HTMLInputElement).value).toBe('4450')
  })

  it('reveals username/password fields when login mode is USER', async () => {
    await openForm()
    const select = wrapper.findComponent(AppSelect)
    select.vm.$emit('update:modelValue', 'USER')
    await flushPromises()

    const labels = wrapper.findAllComponents(AppTextField).map((f) => f.props('label'))
    expect(labels).toContain('用户名')
    expect(labels).toContain('密码（仅本次会话保存）')
  })

  it('selects a login mode via the dropdown UI', async () => {
    wrapper = mount(SmbBackupView, { attachTo: document.body })
    await flushPromises()
    await wrapper.find('.btn-add').trigger('click')
    await wrapper.find('.app-select__trigger').trigger('click')

    const option = [...document.body.querySelectorAll<HTMLElement>('.app-select__option')].find(
      (el) => el.textContent === '用户名 / 密码',
    )
    expect(option).toBeTruthy()
    option!.click()
    await flushPromises()

    const labels = wrapper.findAllComponents(AppTextField).map((f) => f.props('label'))
    expect(labels).toContain('用户名')
    wrapper.unmount()
  })

  it('toggles the backup switch off and back on', async () => {
    await openForm()
    const sw = wrapper.findComponent(AppSwitch)
    expect(sw.attributes('aria-checked')).toBe('true')
    await sw.trigger('click')
    expect(sw.attributes('aria-checked')).toBe('false')
    await sw.trigger('click')
    expect(sw.attributes('aria-checked')).toBe('true')
  })

  it('saves a new connection via smbApi.updateConfig', async () => {
    await openForm()
    const host = wrapper
      .findAllComponents(AppTextField)
      .find((f) => f.props('label') === '服务器地址')!
    await host.find('input').setValue('nas.local')
    const share = wrapper
      .findAllComponents(AppTextField)
      .find((f) => f.props('label') === '共享文件夹')!
    await share.find('input').setValue('media')

    await wrapper.find('.form-actions .btn-primary').trigger('click')
    await flushPromises()

    expect(smbApi.updateConfig).toHaveBeenCalledWith(
      expect.objectContaining({ host: 'nas.local', share: 'media', enabled: true }),
    )
    expect(wrapper.text()).toContain('配置已保存')
  })

  it('restores a syncing progress panel on mount', async () => {
    vi.mocked(smbApi.getProgress).mockResolvedValue({
      ...IDLE_PROGRESS,
      state: 'syncing',
      totalFiles: 100,
      syncedFiles: 40,
      currentFile: 'gallery-42.zip',
      speed: 1024 * 512,
    })
    const w = await mountView()
    expect(w.text()).toContain('同步中')
    expect(w.text()).toContain('40 / 100 个文件')
  })
})
