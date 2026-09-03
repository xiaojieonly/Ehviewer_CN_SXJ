import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import AdminAdvanced from '../admin/AdminAdvanced.vue'
import { AppSelect, AppSwitch, PrefRow, SectionHeader } from '@/components/form'
import { backupApi } from '@/api/backup'
import { jobsApi, type Job } from '@/api/jobs'
import { setPrivacyMaskEnabled } from '@/utils/privacyMask'

vi.mock('@/api/backup', () => ({
  backupApi: {
    exportBackup: vi.fn(),
    restoreBackup: vi.fn(),
    getBackupState: vi.fn(),
  },
}))

vi.mock('@/api/jobs', () => ({
  jobsApi: {
    getJob: vi.fn(),
  },
}))

const UI_KEY = 'anotherviewer-admin-advanced-ui'

/** 最小 COMPLETED 还原任务。 */
function completedRestoreJob(message = '还原成功，重启后生效'): Job {
  return {
    jobId: 'job-restore01',
    type: 'RESTORE',
    state: 'COMPLETED',
    stage: null,
    percent: 100,
    processed: 1,
    total: 1,
    startedAt: 1,
    completedAt: 2,
    error: null,
    result: { success: true, message },
  }
}

describe('AdminAdvanced (高级)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    localStorage.clear()
    // 挂载时会读取 R4-2 restore 运行态——缺省置 false，避免未 mock 的
    // vi.fn() 返回 undefined 破坏 onMounted 链。
    vi.mocked(backupApi.getBackupState).mockResolvedValue({ restorePending: false })
    // 打码是模块级状态，会跨用例泄漏——统一复位。
    setPrivacyMaskEnabled(false)
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.restoreAllMocks()
  })

  it('renders shared primitives and no native select', async () => {
    wrapper = mount(AdminAdvanced)
    expect(wrapper.findAllComponents(SectionHeader).map((h) => h.props('title'))).toEqual(['通用', '隐私', '同步策略', '数据'])
    expect(wrapper.findAllComponents(PrefRow).map((r) => r.props('title'))).toEqual([
      '界面语言',
      '保存解析错误日志',
      '内容打码模式',
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

  it('toggles the privacy-mask switch and persists to localStorage', async () => {
    wrapper = mount(AdminAdvanced)
    const maskSwitch = wrapper
      .findAllComponents(AppSwitch)
      .find((s) => s.attributes('aria-label') === '内容打码模式')
    expect(maskSwitch).toBeTruthy()
    expect(maskSwitch!.attributes('aria-checked')).toBe('false')
    await maskSwitch!.trigger('click')
    expect(maskSwitch!.attributes('aria-checked')).toBe('true')
    expect(localStorage.getItem('anotherviewer-privacy-mask')).toBe('1')
    await maskSwitch!.trigger('click')
    expect(localStorage.getItem('anotherviewer-privacy-mask')).toBeNull()
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

  it('uses a trash icon for 清除本地数据 (F-UX4 icon semantics)', () => {
    wrapper = mount(AdminAdvanced)
    const button = wrapper.find('[aria-label="清除本地数据"]')
    expect(button.find('[aria-label="delete-dark"]').exists()).toBe(true)
  })

  /* ------------------- F-UX4: 数据区接入备份 REST ------------------- */

  it('removes the TODO badges and shows export/import action states', async () => {
    wrapper = mount(AdminAdvanced)
    expect(wrapper.text()).not.toContain('TODO')
    expect(wrapper.text()).toContain('导出')
    expect(wrapper.text()).toContain('导入')
  })

  it('exports via backup REST and triggers a blob download', async () => {
    const createObjectURL = vi.fn(() => 'blob:mock')
    const revokeObjectURL = vi.fn()
    Object.defineProperty(URL, 'createObjectURL', { value: createObjectURL, configurable: true })
    Object.defineProperty(URL, 'revokeObjectURL', { value: revokeObjectURL, configurable: true })
    vi.mocked(backupApi.exportBackup).mockResolvedValue(new Blob(['zipdata']))

    wrapper = mount(AdminAdvanced)
    await wrapper.find('[aria-label="导出数据"]').trigger('click')
    await flushPromises()

    // 本页固定元数据备份（includeDownloads=false）；含下载内容留在专门页面。
    expect(backupApi.exportBackup).toHaveBeenCalledWith(false)
    expect(createObjectURL).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('备份已生成，下载已开始')
  })

  it('surfaces a snack when the export fails', async () => {
    vi.mocked(backupApi.exportBackup).mockRejectedValue(new Error('boom'))
    wrapper = mount(AdminAdvanced)
    await wrapper.find('[aria-label="导出数据"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('导出失败，请稍后重试')
  })

  it('imports a backup zip and shows the restart banner afterwards', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(backupApi.restoreBackup).mockResolvedValue({ jobId: 'job-restore01', state: 'PENDING' } as Job)
    vi.mocked(jobsApi.getJob).mockResolvedValue(completedRestoreJob())
    wrapper = mount(AdminAdvanced)
    expect(wrapper.find('.advanced__restart-banner').exists()).toBe(false)

    // happy-dom 不允许对 type="file" setValue，注入 files 后触发 change。
    const file = new File(['zipdata'], 'backup.zip', { type: 'application/zip' })
    const input = wrapper.find('input[type="file"]')
    Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
    await input.trigger('change')
    await flushPromises()

    expect(backupApi.restoreBackup).toHaveBeenCalledTimes(1)
    expect(jobsApi.getJob).toHaveBeenCalledWith('job-restore01')
    const banner = wrapper.find('.advanced__restart-banner')
    expect(banner.exists()).toBe(true)
    expect(banner.attributes('role')).toBe('alert')
    expect(banner.text()).toContain('还原已完成，重启服务后生效')
  })

  it('shows a snack when the restore job fails', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(backupApi.restoreBackup).mockResolvedValue({ jobId: 'job-restore01', state: 'PENDING' } as Job)
    vi.mocked(jobsApi.getJob).mockResolvedValue({
      ...completedRestoreJob(),
      state: 'FAILED',
      error: '解包失败',
    })
    wrapper = mount(AdminAdvanced)

    const file = new File(['zipdata'], 'backup.zip', { type: 'application/zip' })
    const input = wrapper.find('input[type="file"]')
    Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
    await input.trigger('change')
    await flushPromises()

    expect(wrapper.find('.advanced__restart-banner').exists()).toBe(false)
    expect(wrapper.text()).toContain('解包失败')
  })

  it('aborts the import when the destructive-op confirm is cancelled', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    wrapper = mount(AdminAdvanced)

    const file = new File(['zipdata'], 'backup.zip', { type: 'application/zip' })
    const input = wrapper.find('input[type="file"]')
    Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
    await input.trigger('change')
    await flushPromises()

    expect(backupApi.restoreBackup).not.toHaveBeenCalled()
    expect(wrapper.find('.advanced__restart-banner').exists()).toBe(false)
  })

  it('rejects backup files above the 50MB metadata cap', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    wrapper = mount(AdminAdvanced)

    const file = new File(['x'], 'big.zip', { type: 'application/zip' })
    Object.defineProperty(file, 'size', { value: 51 * 1024 * 1024 })
    const input = wrapper.find('input[type="file"]')
    Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
    await input.trigger('change')
    await flushPromises()

    expect(backupApi.restoreBackup).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('≤50MB')
  })

  it('shows the restart banner on mount when the server reports restorePending (R4-2)', async () => {
    vi.mocked(backupApi.getBackupState).mockResolvedValue({ restorePending: true })
    wrapper = mount(AdminAdvanced)
    await flushPromises()
    expect(backupApi.getBackupState).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.advanced__restart-banner').exists()).toBe(true)
  })

  it('copies the restart command from the banner', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
    })
    vi.mocked(backupApi.getBackupState).mockResolvedValue({ restorePending: true })
    wrapper = mount(AdminAdvanced)
    await flushPromises()

    await wrapper.find('.advanced__restart-copy').trigger('click')
    await flushPromises()
    expect(writeText).toHaveBeenCalledWith('./stop.sh && ./start.sh')
  })
})
