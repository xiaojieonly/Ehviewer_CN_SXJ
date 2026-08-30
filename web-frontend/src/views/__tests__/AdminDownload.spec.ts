import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import AdminDownload from '../admin/AdminDownload.vue'
import { settingsApi, type Settings } from '@/api/settings'
import { downloadApi } from '@/api/download'
import type { MaintenancePreviewResponse } from '@/api/download'
import { AppSelect, AppSwitch, AppTextField, PrefRow, SectionHeader } from '@/components/form'

vi.mock('@/api/settings', () => ({
  settingsApi: { get: vi.fn(), update: vi.fn() },
}))

vi.mock('@/api/download', () => ({
  downloadApi: {
    previewMaintenance: vi.fn(),
    cleanMaintenance: vi.fn(),
  },
}))

/** W2-DL F2 维护扫描夹具：1 个冗余文件 + 1 条无效下载。 */
function maintenancePreview(): MaintenancePreviewResponse {
  return {
    redundantFiles: [{ path: '777', sizeBytes: 1024 * 1024 }],
    invalidDownloads: [{ id: 9, gid: 600, title: 'Broken Gallery', reason: 'content_dir_missing' }],
  }
}

function fullSettings(): Settings {
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
    proxy: { enabled: false, type: 'http', host: '', port: 0, username: '', password: '' },
  }
}

describe('AdminDownload (下载设置)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    localStorage.clear()
    vi.mocked(settingsApi.get).mockResolvedValue(fullSettings())
    vi.mocked(settingsApi.update).mockResolvedValue(true)
    vi.mocked(downloadApi.previewMaintenance).mockResolvedValue(maintenancePreview())
    vi.mocked(downloadApi.cleanMaintenance).mockResolvedValue({
      kind: 'REDUNDANT_FILES',
      removedFiles: 1,
      removedDownloads: 0,
      freedBytes: 1024 * 1024,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.useRealTimers()
    vi.clearAllMocks()
  })

  async function mountView() {
    wrapper = mount(AdminDownload)
    await flushPromises()
    return wrapper
  }

  it('renders shared primitives; steppers/num-fields kept native, switches use AppSwitch', async () => {
    const w = await mountView()
    expect(w.findAllComponents(SectionHeader).map((h) => h.props('title'))).toEqual([
      '基础设置',
      '并发限制',
      '列表与行为',
      '维护',
    ])

    const titles = w.findAllComponents(PrefRow).map((r) => r.props('title'))
    expect(titles).toContain('下载路径')
    expect(titles).toContain('并发线程数')
    expect(titles).toContain('下载延迟')
    expect(titles).toContain('下载超时')
    expect(titles).toContain('最大并发画廊数')
    expect(titles).toContain('最大并发图片数')
    expect(titles).toContain('预加载图片数')
    expect(titles).not.toContain('下载列表分页')
    expect(titles).toContain('排序模式')
    expect(titles).toContain('每页条数')
    expect(titles).toContain('自动开始下载')
    expect(titles).toContain('清理冗余文件')
    expect(titles).toContain('清理无效下载')

    expect(w.find('select').exists()).toBe(false)
    expect(w.find('.switch').exists()).toBe(false)
    expect(w.findAll('.stepper').length).toBe(4)
    expect(w.findAll('.num-field').length).toBe(2)

    // 排序/每页条数用 AppSelect 下拉，自动开始仍为 AppSwitch。
    const selects = w.findAllComponents(AppSelect)
    expect(selects.length).toBe(2)
    expect(selects.map((s) => s.attributes('aria-label'))).toEqual(['排序模式', '每页条数'])
    expect(selects[0].props('options').length).toBe(4)
    // plan-2026-08-30 §3.4.0.1：与 Android perPageCountChoices 对齐。
    expect(selects[1].props('options').map((o) => o.value)).toEqual([50, 100, 200, 300, 500])

    const switches = w.findAllComponents(AppSwitch)
    expect(switches.length).toBe(1)
    expect(switches[0].attributes('aria-label')).toBe('自动开始下载')
  })

  it('migrates the legacy sortAscending boolean into sortMode', async () => {
    // 旧版 sortAscending=true 表示"最早在前" → 迁移为 time_asc。
    localStorage.setItem(
      'anotherviewer-admin-download-ui',
      JSON.stringify({ sortAscending: true, preloadImages: 3, autoStart: true }),
    )
    const w = await mountView()
    const select = w.findAllComponents(AppSelect)[0]
    expect(select.props('modelValue')).toBe('time_asc')
  })

  it('tolerates legacy localStorage entries with the removed paginated key', async () => {
    localStorage.setItem(
      'anotherviewer-admin-download-ui',
      JSON.stringify({ paginated: true, preloadImages: 5, autoStart: false }),
    )
    const w = await mountView()
    const switches = w.findAllComponents(AppSwitch)
    expect(switches.length).toBe(1)
    // Remaining local fields are still applied; the removed row is gone.
    expect(w.findAllComponents(PrefRow).map((r) => r.props('title'))).not.toContain('下载列表分页')
    expect(switches[0].attributes('aria-checked')).toBe('false')
    // The next persist drops the unknown legacy key.
    await switches[0].trigger('click')
    const raw = localStorage.getItem('anotherviewer-admin-download-ui')
    expect(JSON.parse(raw!)).not.toHaveProperty('paginated')
    expect(JSON.parse(raw!).autoStart).toBe(true)
  })

  it('bumps the worker count stepper and persists via debounce', async () => {
    const w = await mountView()
    vi.useFakeTimers()
    const row = w.findAllComponents(PrefRow).find((r) => r.props('title') === '并发线程数')!
    const plus = row.findAll('.stepper__btn').find((b) => b.attributes('aria-label') === '增加并发线程数')!
    await plus.trigger('click')
    expect(settingsApi.update).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(700)
    expect(settingsApi.update).toHaveBeenCalledWith(
      expect.objectContaining({ download: expect.objectContaining({ workerCount: 5 }) }),
    )
  })

  it('toggles a local switch and persists it to localStorage', async () => {
    const w = await mountView()
    const sw = w.findAllComponents(AppSwitch).find((s) => s.attributes('aria-label') === '自动开始下载')!
    expect(sw.attributes('aria-checked')).toBe('true')
    await sw.trigger('click')
    expect(sw.attributes('aria-checked')).toBe('false')
    const raw = localStorage.getItem('anotherviewer-admin-download-ui')
    expect(JSON.parse(raw!).autoStart).toBe(false)
  })

  it('edits the download path in the dialog and saves it', async () => {
    const w = await mountView()
    await w.find('[aria-label="修改下载路径"]').trigger('click')
    const field = w.findComponent(AppTextField)
    expect(field.props('label')).toBe('服务器端路径')
    await field.find('input').setValue('/new/path')

    vi.useFakeTimers()
    const save = w.findAll('button').find((b) => b.text() === '保存')!
    await save.trigger('click')
    await vi.advanceTimersByTimeAsync(700)
    expect(settingsApi.update).toHaveBeenCalledWith(
      expect.objectContaining({ download: expect.objectContaining({ path: '/new/path' }) }),
    )
  })

  /* ---------------- W2-DL F2 维护两端点接线（预览 → 确认 → 反馈） ---------------- */

  it('previews redundant files in a dialog and cleans them after confirmation', async () => {
    const w = await mountView()

    // 第一段：点击按钮 → 只读扫描，弹窗展示将删清单。
    await w.find('[aria-label="清理冗余文件"]').trigger('click')
    expect(downloadApi.previewMaintenance).toHaveBeenCalledTimes(1)
    await flushPromises()
    const dialog = w.find('[aria-label="清理冗余文件"].dialog')
    expect(dialog.exists()).toBe(true)
    expect(dialog.text()).toContain('777')
    expect(dialog.text()).toContain('共 1 项将被删除')

    // 第二段：确认执行 → cleanMaintenance(kind) + 完成反馈 toast。
    await w.findAll('button').find((b) => b.text() === '确认清理 1 项')!.trigger('click')
    await flushPromises()
    expect(downloadApi.cleanMaintenance).toHaveBeenCalledWith('REDUNDANT_FILES')
    expect(w.text()).toContain('已清理 1 个冗余条目，释放 1.0 MB')
    expect(w.find('.dialog-scrim').exists()).toBe(false)
  })

  it('previews invalid downloads and sends the INVALID_DOWNLOADS kind on confirm', async () => {
    vi.mocked(downloadApi.cleanMaintenance).mockResolvedValue({
      kind: 'INVALID_DOWNLOADS',
      removedFiles: 0,
      removedDownloads: 1,
      freedBytes: 0,
    })
    const w = await mountView()

    await w.find('[aria-label="清理无效下载"]').trigger('click')
    expect(downloadApi.previewMaintenance).toHaveBeenCalledTimes(1)
    await flushPromises()
    // 弹窗按类别取对应侧：无效下载条目可见、冗余文件不可见。
    const dialog = w.find('[aria-label="清理无效下载"].dialog')
    expect(dialog.exists()).toBe(true)
    expect(dialog.text()).toContain('Broken Gallery')
    expect(dialog.text()).toContain('本地内容目录缺失')
    expect(dialog.text()).not.toContain('777')

    await w.findAll('button').find((b) => b.text() === '确认清理 1 项')!.trigger('click')
    await flushPromises()
    expect(downloadApi.cleanMaintenance).toHaveBeenCalledWith('INVALID_DOWNLOADS')
    expect(w.text()).toContain('已移除 1 条无效下载')
  })

  it('shows an empty-result dialog and never calls clean when nothing to remove', async () => {
    vi.mocked(downloadApi.previewMaintenance).mockResolvedValue({
      redundantFiles: [],
      invalidDownloads: [],
    })
    const w = await mountView()

    await w.find('[aria-label="清理冗余文件"]').trigger('click')
    await flushPromises()
    expect(w.find('.dialog-scrim').text()).toContain('没有发现需要清理的条目')
    expect(w.text()).not.toContain('确认清理')
    // 无可删项时取消关闭弹窗，不触发执行端点。
    await w.findAll('button').find((b) => b.text() === '关闭')!.trigger('click')
    expect(w.find('.dialog-scrim').exists()).toBe(false)
    expect(downloadApi.cleanMaintenance).not.toHaveBeenCalled()
  })

  it('reports scan failures via snackbar without opening the dialog', async () => {
    vi.mocked(downloadApi.previewMaintenance).mockRejectedValue(new Error('boom'))
    const w = await mountView()

    await w.find('[aria-label="清理冗余文件"]').trigger('click')
    await flushPromises()
    expect(w.find('.dialog-scrim').exists()).toBe(false)
    expect(w.text()).toContain('扫描失败，请稍后重试')
  })

  it('reports clean failures via snackbar', async () => {
    vi.mocked(downloadApi.cleanMaintenance).mockRejectedValue(new Error('boom'))
    const w = await mountView()

    await w.find('[aria-label="清理无效下载"]').trigger('click')
    await flushPromises()
    await w.findAll('button').find((b) => b.text() === '确认清理 1 项')!.trigger('click')
    await flushPromises()
    expect(w.find('.dialog-scrim').exists()).toBe(false)
    expect(w.text()).toContain('清理失败，请稍后重试')
  })
})
