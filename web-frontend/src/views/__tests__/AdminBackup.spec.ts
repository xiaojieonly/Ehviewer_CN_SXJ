import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import AdminBackup from '../admin/AdminBackup.vue'
import { backupApi, type EhImportResult } from '@/api/backup'
import { jobsApi, type Job, type JobType } from '@/api/jobs'
import type { JobWsEnvelope } from '@/composables/useWebSocket'

vi.mock('@/api/backup', () => ({
  backupApi: {
    importEhViewer: vi.fn(),
    exportBackupAsync: vi.fn(),
    downloadExport: vi.fn(),
    restoreBackup: vi.fn(),
    getBackupState: vi.fn(),
  },
}))

vi.mock('@/api/jobs', () => ({
  jobsApi: { getJob: vi.fn(), getActiveJob: vi.fn() },
}))

// JobProgressPanel 与 F1a 并行开发，用桩隔离避免依赖其实现（plan B3/任务说明）。
vi.mock('@/components/jobs/JobProgressPanel.vue', () => ({
  default: { props: ['job', 'title'], template: '<div data-testid="job-panel" />' },
}))

const ws = vi.hoisted(() => ({
  subscribeJob: vi.fn((_jobId: string, _cb: unknown) => vi.fn()),
}))
vi.mock('@/composables/useWebSocket', () => ({
  useWebSocket: vi.fn(() => ws),
}))

/** A1 Job schema 最小构造器。 */
function job(partial: Partial<Job> & { jobId: string; type: JobType }): Job {
  return {
    state: 'RUNNING',
    stage: null,
    percent: 0,
    processed: 0,
    total: 0,
    startedAt: null,
    completedAt: null,
    error: null,
    result: null,
    ...partial,
  }
}

/** B3 计数契约示例（IMPORT Job 的 result 载荷）。 */
const importResult: EhImportResult = {
  success: true,
  imported: {
    downloads: 12,
    history: 3,
    filters: 0,
    quickSearches: 2,
    labels: 1,
    bookmarks: 0,
    favorites: 4,
    dirnames: 0,
    blackList: 0,
    galleryTags: 0,
  },
  cookies: { imported: 2, siteDomain: 1 },
  skipped: 1,
}

/** M-6 错误信封：409 + error.code=CONFLICT（活跃同类型任务时后端返回）。 */
function conflictError(): unknown {
  const error = new Error('Request failed with status code 409') as unknown as Record<string, unknown>
  Object.assign(error, {
    isAxiosError: true,
    response: {
      status: 409,
      data: { error: { code: 'CONFLICT', message: '已有导入进行中', traceId: 't', status: 409 } },
    },
  })
  return error
}

describe('AdminBackup (R4-2 还原待重启横幅 + Job 进度)', () => {
  let wrapper: VueWrapper
  let createObjectURL: ReturnType<typeof vi.fn>

  beforeEach(() => {
    localStorage.clear()
    vi.mocked(backupApi.getBackupState).mockResolvedValue({ restorePending: false })
    vi.mocked(jobsApi.getActiveJob).mockRejectedValue(new Error('404'))
    vi.mocked(jobsApi.getJob).mockRejectedValue(new Error('404'))
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    ws.subscribeJob.mockClear()
    createObjectURL = vi.fn(() => 'blob:mock')
    Object.defineProperty(URL, 'createObjectURL', { value: createObjectURL, configurable: true })
    Object.defineProperty(URL, 'revokeObjectURL', { value: vi.fn(), configurable: true })
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

  /** 最近一次 subscribeJob 的 WS 回调（组件经 useWebSocket().subscribeJob(jobId, cb) 注册）。 */
  function jobCallback(): (event: JobWsEnvelope) => void {
    return ws.subscribeJob.mock.calls[0]![1] as unknown as (event: JobWsEnvelope) => void
  }

  /** 选 .db 文件并点击导入。 */
  async function submitImport(w: VueWrapper): Promise<void> {
    const file = new File(['db'], 'eh.db')
    const input = w.find('input[accept=".db"]')
    Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
    await input.trigger('change')
    await w.find('[aria-label="导入 EhViewer 备份"]').trigger('click')
    await flushPromises()
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

  /* ------------------------------ 导入（Job） ------------------------------ */

  it('submits an EhViewer import, shows the progress panel, and renders counts on completion', async () => {
    vi.mocked(backupApi.importEhViewer).mockResolvedValue(
      job({ jobId: 'job-import-1', type: 'IMPORT', state: 'PENDING' }),
    )
    const w = await mountView()
    await submitImport(w)

    expect(backupApi.importEhViewer).toHaveBeenCalledTimes(1)
    expect(w.find('[data-testid="job-panel"]').exists()).toBe(true)

    // WS 进度事件推进面板（不落终态）。
    jobCallback()({
      type: 'job.progress',
      timestamp: 1,
      version: '1.1',
      payload: { jobId: 'job-import-1', type: 'IMPORT', stage: '写入数据库', percent: 50, processed: 6, total: 12 },
    })
    await flushPromises()
    expect(w.find('[data-testid="job-panel"]').exists()).toBe(true)

    // WS 完成 → 面板隐藏，落既有 B3 计数结果面板。
    jobCallback()({
      type: 'job.completed',
      timestamp: 2,
      version: '1.1',
      payload: { jobId: 'job-import-1', type: 'IMPORT', result: importResult },
    })
    await flushPromises()

    expect(w.find('[data-testid="job-panel"]').exists()).toBe(false)
    const result = w.find('.backup__import-result')
    expect(result.exists()).toBe(true)
    expect(result.text()).toContain('导入完成')
    expect(result.text()).toContain('跳过冲突 1 条')
    expect(result.text()).toContain('12') // downloads 计数
    expect(result.text()).toContain('Cookie 导入 2 条')
  })

  it('shows the error message when the import job fails', async () => {
    vi.mocked(backupApi.importEhViewer).mockResolvedValue(
      job({ jobId: 'job-import-1', type: 'IMPORT', state: 'RUNNING' }),
    )
    const w = await mountView()
    await submitImport(w)

    jobCallback()({
      type: 'job.failed',
      timestamp: 1,
      version: '1.1',
      payload: { jobId: 'job-import-1', type: 'IMPORT', error: '数据库文件损坏' },
    })
    await flushPromises()

    expect(w.find('[data-testid="job-panel"]').exists()).toBe(false)
    expect(w.find('.backup__import-error').text()).toContain('数据库文件损坏')
  })

  it('attaches to the active import job when submitting conflicts (409)', async () => {
    vi.mocked(backupApi.importEhViewer).mockRejectedValue(conflictError())
    // 挂载恢复先 404（无活跃），409 后自动挂回时返回活跃任务。
    let importCalls = 0
    vi.mocked(jobsApi.getActiveJob).mockImplementation(async (type) => {
      if (type === 'IMPORT') {
        importCalls += 1
        if (importCalls > 1) {
          return job({ jobId: 'job-active-1', type: 'IMPORT', state: 'RUNNING', stage: '写入数据库' })
        }
      }
      throw new Error('404')
    })
    const w = await mountView()
    await submitImport(w)

    expect(w.text()).toContain('已有导入进行中')
    expect(jobsApi.getActiveJob).toHaveBeenCalledWith('IMPORT')
    expect(ws.subscribeJob).toHaveBeenCalledWith('job-active-1', expect.any(Function))
    expect(w.find('[data-testid="job-panel"]').exists()).toBe(true)
  })

  /* ------------------------------ 导出（Job） ------------------------------ */

  it('submits an async export and downloads the artifact when it completes', async () => {
    vi.mocked(backupApi.exportBackupAsync).mockResolvedValue(
      job({ jobId: 'job-export-1', type: 'EXPORT', state: 'PENDING' }),
    )
    vi.mocked(backupApi.downloadExport).mockResolvedValue(new Blob(['zip']))
    const w = await mountView()

    await w.find('[aria-label="导出备份"]').trigger('click')
    await flushPromises()

    expect(backupApi.exportBackupAsync).toHaveBeenCalledWith(false)
    expect(w.find('[data-testid="job-panel"]').exists()).toBe(true)

    jobCallback()({
      type: 'job.completed',
      timestamp: 1,
      version: '1.1',
      payload: { jobId: 'job-export-1', type: 'EXPORT', result: null },
    })
    await flushPromises()

    expect(backupApi.downloadExport).toHaveBeenCalledWith('job-export-1')
    expect(createObjectURL).toHaveBeenCalled()
    expect(w.text()).toContain('备份已生成，下载已开始')
  })

  /* ------------------------------ 还原（Job） ------------------------------ */

  it('shows the restart banner after the restore job completes', async () => {
    vi.mocked(backupApi.restoreBackup).mockResolvedValue(
      job({ jobId: 'job-restore-1', type: 'RESTORE', state: 'PENDING' }),
    )
    const w = await mountView()

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
    expect(w.find('[data-testid="job-panel"]').exists()).toBe(true)
    expect(w.find('.backup__restart-banner').exists()).toBe(false)

    jobCallback()({
      type: 'job.completed',
      timestamp: 1,
      version: '1.1',
      payload: { jobId: 'job-restore-1', type: 'RESTORE', result: { success: true, message: 'ok' } },
    })
    await flushPromises()

    expect(w.find('[data-testid="job-panel"]').exists()).toBe(false)
    expect(w.find('.backup__restart-banner').exists()).toBe(true)
  })

  /* --------------------------- 跨刷新恢复（Job） --------------------------- */

  it('re-attaches to a running job from the server on mount', async () => {
    vi.mocked(jobsApi.getActiveJob).mockImplementation(async (type) => {
      if (type === 'IMPORT') {
        return job({ jobId: 'job-running-1', type: 'IMPORT', state: 'RUNNING', stage: '写入数据库', percent: 40, processed: 5, total: 12 })
      }
      throw new Error('404')
    })
    const w = await mountView()

    expect(w.find('[data-testid="job-panel"]').exists()).toBe(true)
    expect(ws.subscribeJob).toHaveBeenCalledWith('job-running-1', expect.any(Function))
  })

  it('restores a recently completed import from localStorage on mount', async () => {
    localStorage.setItem('anotherviewer-last-job-import', 'job-import-old')
    vi.mocked(jobsApi.getJob).mockResolvedValue(
      job({ jobId: 'job-import-old', type: 'IMPORT', state: 'COMPLETED', completedAt: Date.now() - 60_000, result: importResult }),
    )
    const w = await mountView()

    expect(jobsApi.getJob).toHaveBeenCalledWith('job-import-old')
    expect(w.find('.backup__import-result').exists()).toBe(true)
    expect(w.text()).toContain('导入完成')
  })

  it('ignores a stale completed job from localStorage', async () => {
    localStorage.setItem('anotherviewer-last-job-import', 'job-import-old')
    vi.mocked(jobsApi.getJob).mockResolvedValue(
      job({ jobId: 'job-import-old', type: 'IMPORT', state: 'COMPLETED', completedAt: Date.now() - 10 * 60_000, result: importResult }),
    )
    const w = await mountView()

    expect(w.find('.backup__import-result').exists()).toBe(false)
    expect(w.find('[data-testid="job-panel"]').exists()).toBe(false)
  })
})
