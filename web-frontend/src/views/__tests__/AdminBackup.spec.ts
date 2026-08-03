import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import AdminBackup from '../admin/AdminBackup.vue'
import { backupApi } from '@/api/backup'

vi.mock('@/api/backup', () => ({
  backupApi: {
    exportBackup: vi.fn(),
    restoreBackup: vi.fn(),
    getBackupState: vi.fn(),
  },
}))

describe('AdminBackup (R4-2 还原待重启横幅)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    vi.mocked(backupApi.getBackupState).mockResolvedValue({ restorePending: false })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
    vi.restoreAllMocks()
  })

  async function mountView() {
    wrapper = mount(AdminBackup)
    await flushPromises()
    return wrapper
  }

  it('queries the restore state on mount', async () => {
    await mountView()
    expect(backupApi.getBackupState).toHaveBeenCalledTimes(1)
  })

  it('shows the persistent restart banner when restorePending is true (zh + en)', async () => {
    vi.mocked(backupApi.getBackupState).mockResolvedValue({ restorePending: true })
    const w = await mountView()

    const banner = w.find('.backup__restart-banner')
    expect(banner.exists()).toBe(true)
    expect(banner.attributes('role')).toBe('alert')
    // 中文 + 英文文案同时呈现（项目当前无 i18n 机制）。
    expect(banner.text()).toContain('还原已完成，重启服务后生效')
    expect(banner.text()).toContain('Restore complete')
    // 含复制重启命令按钮。
    expect(w.find('.backup__restart-copy').exists()).toBe(true)
  })

  it('does not show the banner when restorePending is false', async () => {
    vi.mocked(backupApi.getBackupState).mockResolvedValue({ restorePending: false })
    const w = await mountView()
    expect(w.find('.backup__restart-banner').exists()).toBe(false)
  })

  it('stays hidden when the state endpoint is unavailable (legacy server)', async () => {
    vi.mocked(backupApi.getBackupState).mockRejectedValue(new Error('404'))
    const w = await mountView()
    expect(w.find('.backup__restart-banner').exists()).toBe(false)
  })

  it('copies the restart command to the clipboard', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
    })
    vi.mocked(backupApi.getBackupState).mockResolvedValue({ restorePending: true })
    const w = await mountView()

    await w.find('.backup__restart-copy').trigger('click')
    await flushPromises()
    expect(writeText).toHaveBeenCalledWith('./stop.sh && ./start.sh')
  })

  it('shows the banner immediately after a successful restore', async () => {
    vi.mocked(backupApi.getBackupState).mockResolvedValue({ restorePending: false })
    vi.mocked(backupApi.restoreBackup).mockResolvedValue({ success: true, message: '还原成功，重启后生效' })
    const w = await mountView()
    expect(w.find('.backup__restart-banner').exists()).toBe(false)

    // 触发还原：选文件 + 输入确认词 RESTORE，再点还原。
    // happy-dom 不允许对 type="file" setValue，改为注入 files 后触发 change。
    const file = new File(['zipdata'], 'backup.zip', { type: 'application/zip' })
    const fileInput = w.find('input[type="file"]')
    Object.defineProperty(fileInput.element, 'files', { value: [file], configurable: true })
    await fileInput.trigger('change')
    await w.find('input[aria-label="还原确认词"]').setValue('RESTORE')
    await flushPromises()
    await w.find('.backup__restore-btn').trigger('click')
    await flushPromises()

    expect(backupApi.restoreBackup).toHaveBeenCalledTimes(1)
    expect(w.find('.backup__restart-banner').exists()).toBe(true)
  })
})
