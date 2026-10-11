import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import AdminServer from '../admin/AdminServer.vue'
import { settingsApi, type Settings } from '@/api/settings'
import { imageApi } from '@/api/image'
import { smbApi, type SmbConfig } from '@/api/smb'
import { jobsApi, type Job, type JobType } from '@/api/jobs'
import type { JobWsEnvelope } from '@/composables/useWebSocket'
import { AppSwitch, AppTextField, PrefRow, SectionHeader } from '@/components/form'

vi.mock('@/api/settings', () => ({
  settingsApi: { get: vi.fn(), update: vi.fn() },
}))

vi.mock('@/api/image', () => ({
  imageApi: { getCacheStatus: vi.fn(), clearCacheAsync: vi.fn() },
}))

vi.mock('@/api/smb', () => ({
  smbApi: { getConfig: vi.fn() },
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
  // WP8 任务 3：AdminServer onMounted 显式 connect()（Job 进度 WS 需要 active 连接）。
  connect: vi.fn(),
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

function fullSettings(): Settings {
  return {
    download: {
      path: '/data',
      downloadDelay: 1000,
      downloadTimeout: 30000,
      maxConcurrentGalleries: 2,
      maxConcurrentImages: 8,
    },
    cache: { path: '/cache', sizeMb: 5120 },
    smb: { enabled: false },
    security: { requireAuth: false, sessionTimeout: 86400 },
    processing: { enabled: false, defaultType: 'UPSCALE_2X', outputFormat: 'png', outputQuality: 90, entrypointUrl: 'http://192.168.6.141:9800', entrypointTokenSet: false, automationEnabled: false, periodicEnabled: false, periodicIntervalMinutes: 60 },
    proxy: { enabled: false, type: 'http', host: '', port: 0, username: '', password: '' },
  }
}

/** F2：真实开关在 /smb/config（SmbConfigResponse.enabled）；本页只读展示。 */
function smbConfig(enabled: boolean): SmbConfig {
  return { id: 1, host: 'nas', port: 445, share: 'backup', path: null, loginMode: 'GUEST', username: null, enabled }
}

describe('AdminServer (缓存与存储)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    vi.mocked(settingsApi.get).mockResolvedValue(fullSettings())
    vi.mocked(settingsApi.update).mockResolvedValue(true)
    vi.mocked(imageApi.getCacheStatus).mockResolvedValue({ cacheSize: 5 })
    // 缺省：无已配置连接（后端返回 null body）→ 按「已停用」展示。
    vi.mocked(smbApi.getConfig).mockResolvedValue(null)
    vi.mocked(jobsApi.getActiveJob).mockRejectedValue(new Error('404'))
    vi.mocked(jobsApi.getJob).mockRejectedValue(new Error('404'))
    ws.subscribeJob.mockClear()
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.useRealTimers()
    vi.clearAllMocks()
  })

  async function mountView() {
    wrapper = mount(AdminServer, {
      global: { stubs: { RouterLink: true } },
    })
    await flushPromises()
    return wrapper
  }

  /** 最近一次 subscribeJob 的 WS 回调（组件经 useWebSocket().subscribeJob(jobId, cb) 注册）。 */
  function jobCallback(): (event: JobWsEnvelope) => void {
    return ws.subscribeJob.mock.calls[0]![1] as unknown as (event: JobWsEnvelope) => void
  }

  it('renders shared primitives with AppTextField and no SMB switch', async () => {
    const w = await mountView()
    expect(w.find('.server__title').text()).toBe('缓存与存储')
    expect(w.findAllComponents(SectionHeader).map((h) => h.props('title'))).toEqual(['缓存', 'SMB 备份'])
    const titles = w.findAllComponents(PrefRow).map((r) => r.props('title'))
    // F2：SMB 开关行改为只读状态行；「前往备份页面」常驻。
    expect(titles).toEqual(['缓存路径', '缓存大小 (MB)', '缓存统计', '清除缓存', 'SMB 备份', '前往备份页面'])
    expect(w.find('select').exists()).toBe(false)
    expect(w.find('.switch').exists()).toBe(false)
    // 本页不再有任何开关（真实 SMB 开关在「备份」页）。
    expect(w.findComponent(AppSwitch).exists()).toBe(false)

    const fields = w.findAllComponents(AppTextField)
    expect(fields.length).toBe(2)
    expect(fields.map((f) => f.props('ariaLabel'))).toEqual(['缓存路径', '缓存大小'])
    expect(fields[1].props('type')).toBe('number')
  })

  it('persists the cache path after typing pauses', async () => {
    const w = await mountView()
    vi.useFakeTimers()
    const field = w.findAllComponents(AppTextField).find((f) => f.props('ariaLabel') === '缓存路径')!
    await field.find('input').setValue('/new/cache')
    await vi.advanceTimersByTimeAsync(600)
    expect(settingsApi.update).toHaveBeenCalledWith(
      expect.objectContaining({ cache: expect.objectContaining({ path: '/new/cache' }) }),
    )
  })

  it('persists the cache size as a clamped number', async () => {
    const w = await mountView()
    vi.useFakeTimers()
    const field = w.findAllComponents(AppTextField).find((f) => f.props('ariaLabel') === '缓存大小')!
    await field.find('input').setValue('10240')
    await vi.advanceTimersByTimeAsync(600)
    expect(settingsApi.update).toHaveBeenCalledWith(
      expect.objectContaining({ cache: expect.objectContaining({ sizeMb: 10240 }) }),
    )
  })

  it('shows the real SMB enabled state read from GET /smb/config', async () => {
    vi.mocked(smbApi.getConfig).mockResolvedValue(smbConfig(true))
    const w = await mountView()
    expect(smbApi.getConfig).toHaveBeenCalledWith()
    expect(w.text()).toContain('已启用，备份到 SMB 共享')
    expect(w.text()).toContain('SMB 备份已开启')
    expect(w.find('.server__link').exists()).toBe(true)
    // F2：settings 的 smb 字段（config.smb.enabled）无消费方，不再写入。
    expect(settingsApi.update).not.toHaveBeenCalledWith(expect.objectContaining({ smb: expect.anything() }))
  })

  it('shows the disabled state when no SMB config exists', async () => {
    const w = await mountView()
    expect(w.text()).toContain('已停用（到备份页配置）')
    expect(w.text()).toContain('SMB 备份已关闭')
    expect(w.find('.server__link').exists()).toBe(true)
  })

  it('degrades silently to an unknown state when the SMB config request fails', async () => {
    vi.mocked(smbApi.getConfig).mockRejectedValue(new Error('offline'))
    const w = await mountView()
    expect(w.text()).toContain('状态未知')
    // 页头状态徽标在未知态不渲染，也不打扰用户（本页主功能是缓存）。
    expect(w.find('.server__status').exists()).toBe(false)
    expect(w.text()).not.toContain('SMB 备份已')
  })

  it('loads and renders the cache size on mount', async () => {
    vi.mocked(imageApi.getCacheStatus).mockResolvedValue({ cacheSize: 42 })
    const w = await mountView()
    expect(imageApi.getCacheStatus).toHaveBeenCalledWith()
    expect(w.text()).toContain('42 张')
  })

  it('renders an em dash when the cache status request fails', async () => {
    vi.mocked(imageApi.getCacheStatus).mockRejectedValue(new Error('offline'))
    const w = await mountView()
    expect(w.text()).toContain('—')
  })

  it('submits an async cache clear, shows the progress panel, and refreshes stats on completion', async () => {
    vi.mocked(imageApi.clearCacheAsync).mockResolvedValue(
      job({ jobId: 'job-clear-1', type: 'CACHE_CLEAR', state: 'PENDING' }),
    )
    const w = await mountView()

    await w.find('[aria-label="清除缓存"]').trigger('click')
    await flushPromises()

    expect(imageApi.clearCacheAsync).toHaveBeenCalledTimes(1)
    expect(w.find('[data-testid="job-panel"]').exists()).toBe(true)

    // WS 进度事件推进面板。
    jobCallback()({
      type: 'job.progress',
      timestamp: 1,
      version: '1.1',
      payload: { jobId: 'job-clear-1', type: 'CACHE_CLEAR', stage: '删除缓存文件', percent: 50, processed: 5, total: 10 },
    })
    await flushPromises()
    expect(w.find('[data-testid="job-panel"]').exists()).toBe(true)

    // WS 完成 → 成功 snack + 刷新缓存统计。
    jobCallback()({
      type: 'job.completed',
      timestamp: 2,
      version: '1.1',
      payload: { jobId: 'job-clear-1', type: 'CACHE_CLEAR', result: { removed: 10, total: 10 } },
    })
    await flushPromises()

    expect(w.find('[data-testid="job-panel"]').exists()).toBe(false)
    expect(w.text()).toContain('缓存已清除')
    expect(imageApi.getCacheStatus).toHaveBeenCalledTimes(2)
  })

  it('shows an error snackbar when the clear job fails', async () => {
    vi.mocked(imageApi.clearCacheAsync).mockResolvedValue(
      job({ jobId: 'job-clear-1', type: 'CACHE_CLEAR', state: 'RUNNING' }),
    )
    const w = await mountView()

    await w.find('[aria-label="清除缓存"]').trigger('click')
    await flushPromises()

    jobCallback()({
      type: 'job.failed',
      timestamp: 1,
      version: '1.1',
      payload: { jobId: 'job-clear-1', type: 'CACHE_CLEAR', error: '磁盘错误' },
    })
    await flushPromises()

    expect(w.text()).toContain('清除缓存失败：磁盘错误')
  })

  it('shows an error snackbar when submitting the clear fails', async () => {
    vi.mocked(imageApi.clearCacheAsync).mockRejectedValue(new Error('boom'))
    const w = await mountView()

    await w.find('[aria-label="清除缓存"]').trigger('click')
    await flushPromises()

    expect(w.text()).toContain('无法清除缓存')
    expect(ws.subscribeJob).not.toHaveBeenCalled()
  })

  it('re-attaches to a running clear job on mount', async () => {
    vi.mocked(jobsApi.getActiveJob).mockResolvedValue(
      job({ jobId: 'job-clear-run', type: 'CACHE_CLEAR', state: 'RUNNING', stage: '删除缓存文件', percent: 30, processed: 3, total: 10 }),
    )
    const w = await mountView()

    expect(ws.subscribeJob).toHaveBeenCalledWith('job-clear-run', expect.any(Function))
    expect(w.find('[data-testid="job-panel"]').exists()).toBe(true)
  })
})
