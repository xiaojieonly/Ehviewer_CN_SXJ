<!--
  AdminBackup.vue — 管理面板「备份与还原」页（T12）.

  对接 T11 BackupController（plan-2026-08-06 A2，大数据操作异步化为 Job）：
    - POST /api/v1/backup/export/async → 202 Job → WS 进度 → 完成后下载产物
      （GET /api/v1/backup/export/{jobId}）；
    - POST /api/v1/backup/restore → 202 Job → 完成后置 restorePending（重启横幅）；
    - POST /api/v1/backup/import-ehviewer → 202 Job → 完成后展示 B3 计数面板；
    - GET  /api/v1/backup/state → { restorePending }（R4-2）。

  进度经 STOMP /topic/jobs/{jobId} 订阅 + jobsApi.getJob 1s 轮询兜底（WS 断线），
  终态/失败后停；提交时把 jobId 写入 localStorage（anotherviewer-last-job-{type}），
  挂载时按活跃任务 / 最近任务恢复进度面板或终态结果。

  还原是破坏性操作（覆盖当前数据库，旧库保留为 .bak，需重启生效）：
  上传前检查文件大小（>50MB 阻止），随后弹确认框，且必须输入确认词
  RESTORE 才能启用还原按钮（grill Q3 决策）。
  EhViewer 备份导入为增量写（gid 冲突默认跳过），选 .db（+ 可选 cookie 文件）后
  直接确认导入，结果以内联计数面板展示。
-->
<template>
  <div class="backup">
    <div class="backup__column">
      <header class="backup__header">
        <h1 class="backup__title">备份与还原</h1>
      </header>

      <!-- R4-2: 还原待重启持久警示横幅（restorePending=true 时显示） -->
      <div v-if="restorePending" class="backup__restart-banner" role="alert">
        <div class="backup__restart-text">
          <strong class="backup__restart-zh">还原已完成，重启服务后生效</strong>
          <span class="backup__restart-en">Restore complete — restart the server to apply</span>
        </div>
        <button
          type="button"
          class="backup__restart-copy"
          aria-label="复制重启命令"
          @click="copyRestartCommand"
        >
          {{ restartCopied ? '已复制 Copied' : '复制重启命令 Copy restart command' }}
        </button>
      </div>

      <!-- ═══ 导出 ═══════════════════════════════════════════════════════ -->
      <section>
        <SectionHeader title="导出" />
        <PrefCard>
          <PrefRow icon="folder-share-dark" title="包含下载内容" summary="包含下载内容时备份体积可能巨大">
            <AppSwitch
              :model-value="includeDownloads"
              aria-label="包含下载内容"
              @update:model-value="(v) => (includeDownloads = v)"
            />
          </PrefRow>
          <PrefRow icon="download-box-dark" title="导出备份" summary="打包数据库与配置为 zip 下载（压缩分片，生成耗时）">
            <button
              type="button"
              class="backup__action"
              aria-label="导出备份"
              :disabled="exporting"
              @click="handleExport"
            >
              <span class="backup__badge">{{ exporting ? '导出中…' : '导出' }}</span>
            </button>
          </PrefRow>
          <JobProgressPanel v-if="exporting" :job="activeJob" title="导出备份" />
        </PrefCard>
      </section>

      <!-- ═══ 还原 ══════════════════════════════════════════════════════ -->
      <section>
        <SectionHeader title="还原" />
        <PrefCard>
          <PrefRow icon="file-find-primary" title="选择备份文件" summary="仅支持元数据备份 zip（≤50MB）">
            <label class="backup__file-btn" aria-label="选择备份文件">
              选择文件
              <input
                ref="fileInput"
                type="file"
                accept=".zip"
                class="backup__file-input"
                :disabled="restoring"
                @change="onFileChange"
              />
            </label>
            <template #below>
              <div v-if="selectedFile" class="backup__file-info" role="status">
                {{ selectedFile.name }}（{{ formatBytes(selectedFile.size) }}）
              </div>
            </template>
          </PrefRow>
          <PrefRow icon="alert-red" title="输入确认词" summary="输入 RESTORE 以启用还原按钮">
            <div class="backup__field">
              <AppTextField
                :model-value="confirmWord"
                placeholder="RESTORE"
                :disabled="restoring"
                aria-label="还原确认词"
                @update:model-value="(v) => (confirmWord = v)"
              />
            </div>
          </PrefRow>
          <PrefRow icon="delete-red" title="执行还原" summary="覆盖当前数据库，旧文件保留为 .bak，需重启服务器生效">
            <button
              type="button"
              class="backup__restore-btn"
              aria-label="执行还原"
              :disabled="!canRestore"
              @click="handleRestore"
            >
              {{ restoring ? '还原中…' : '还原' }}
            </button>
          </PrefRow>
          <JobProgressPanel v-if="restoring" :job="activeJob" title="还原备份" />
        </PrefCard>
      </section>

      <!-- ═══ 导入 EhViewer ═════════════════════════════════════════════ -->
      <section>
        <SectionHeader title="导入 EhViewer 备份" />
        <PrefCard>
          <PrefRow icon="file-find-primary" title="选择备份文件" summary="原版 EhViewer 导出的 .db 数据库（必选）">
            <label class="backup__file-btn" aria-label="选择 EhViewer 备份文件">
              选择文件
              <input
                ref="dbInput"
                type="file"
                accept=".db"
                class="backup__file-input"
                :disabled="importing"
                @change="onDbChange"
              />
            </label>
            <template #below>
              <div v-if="dbFile" class="backup__file-info" role="status">
                {{ dbFile.name }}（{{ formatBytes(dbFile.size) }}）
              </div>
            </template>
          </PrefRow>
          <PrefRow icon="cookie-brown" title="选择 Cookie 文件" summary="okhttp3-cookie.db 或 JSON cookie 数组（可选，用于登录授权；仅存于进程内会话，服务器重启即失效，登录态不参与同步）">
            <label class="backup__file-btn" aria-label="选择 Cookie 文件">
              选择文件
              <input
                ref="cookieInput"
                type="file"
                accept=".db,.json,application/json"
                class="backup__file-input"
                :disabled="importing"
                @change="onCookieChange"
              />
            </label>
            <template #below>
              <div v-if="cookieFile" class="backup__file-info" role="status">
                {{ cookieFile.name }}（{{ formatBytes(cookieFile.size) }}）
              </div>
            </template>
          </PrefRow>
          <PrefRow icon="download-box-dark" title="执行导入" summary="gid 冲突默认跳过，不覆盖现有数据">
            <button
              type="button"
              class="backup__import-btn"
              aria-label="导入 EhViewer 备份"
              :disabled="!canImport"
              @click="handleEhImport"
            >
              {{ importing ? '导入中…' : '导入' }}
            </button>
          </PrefRow>

          <JobProgressPanel v-if="importing" :job="activeJob" title="导入 EhViewer" />

          <!-- 导入结果 / 失败信息。 -->
          <Transition name="banner">
            <div v-if="importError || importResult" class="backup__import-result" role="status">
              <p v-if="importError" class="backup__import-error">{{ importError }}</p>
              <template v-else-if="importResult">
                <p class="backup__import-headline">
                  导入完成 Imported
                  <span v-if="importResult.skipped" class="backup__import-skipped">
                    跳过冲突 {{ importResult.skipped }} 条
                  </span>
                </p>
                <dl class="backup__import-grid">
                  <div v-for="item in importCountRows" :key="item.key" class="backup__import-item">
                    <dt class="backup__import-label">{{ item.label }}</dt>
                    <dd class="backup__import-value">{{ item.value }}</dd>
                  </div>
                </dl>
                <p v-if="importResult.cookies.imported" class="backup__import-cookies">
                  Cookie 导入 {{ importResult.cookies.imported }} 条
                  （站点域命中 {{ importResult.cookies.siteDomain }}）
                </p>
              </template>
            </div>
          </Transition>
        </PrefCard>
      </section>
    </div>

    <!-- Snackbar. -->
    <Transition name="snack">
      <div v-if="snack" class="backup__snackbar" role="status">{{ snack }}</div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import axios from 'axios'
import { backupApi, type EhImportResult } from '@/api/backup'
import { jobsApi, type Job, type JobType } from '@/api/jobs'
import JobProgressPanel from '@/components/jobs/JobProgressPanel.vue'
import { useWebSocket } from '@/composables/useWebSocket'
import type { JobWsEnvelope } from '@/composables/useWebSocket'
import { AppSwitch, AppTextField, PrefCard, PrefRow, SectionHeader } from '@/components/form'

/** WebUI 还原面向元数据备份；含下载内容的大备份请手动解包/拷贝 data-dir。 */
const MAX_RESTORE_BYTES = 50 * 1024 * 1024

/** R4-2: 重启命令（与仓库根 start.sh/stop.sh 对应），供横幅一键复制。 */
const RESTART_COMMAND = './stop.sh && ./start.sh'

/** 跨刷新恢复：最近一次任务的 localStorage 键前缀（plan A6）+ 5 分钟窗口。 */
const LAST_JOB_KEY_PREFIX = 'anotherviewer-last-job-'
const RECENT_JOB_MS = 5 * 60 * 1000

const includeDownloads = ref(false)

const selectedFile = ref<File | null>(null)
const confirmWord = ref('')
const fileInput = ref<HTMLInputElement | null>(null)

/** B5: EhViewer 备份导入状态。 */
const dbFile = ref<File | null>(null)
const cookieFile = ref<File | null>(null)
const importResult = ref<EhImportResult | null>(null)
const importError = ref('')
const dbInput = ref<HTMLInputElement | null>(null)
const cookieInput = ref<HTMLInputElement | null>(null)

/** R4-2: 还原待重启运行态；挂载时读取 /backup/state，还原本地成功后也置 true。 */
const restorePending = ref(false)
const restartCopied = ref(false)
let restartCopiedTimer: number | undefined

const snack = ref('')
let snackTimer: number | undefined

/* ---------------------- Job 进度跟踪器（plan A6） ---------------------- */

const { subscribeJob } = useWebSocket()

/** 当前跟踪的任务（RUNNING 时渲染 JobProgressPanel，终态落各结果面板）。 */
const activeJob = ref<Job | null>(null)
let pollTimer: number | undefined
let jobUnsubscribe: (() => void) | undefined

const exporting = computed(() => isJobActive('EXPORT'))
const restoring = computed(() => isJobActive('RESTORE'))
const importing = computed(() => isJobActive('IMPORT'))

const canRestore = computed(
  () => selectedFile.value !== null && confirmWord.value.trim() === 'RESTORE' && !restoring.value,
)

const canImport = computed(() => dbFile.value !== null && !importing.value)

/** 该类型任务是否仍在进行（进度面板据此显示，按钮据此禁用）。 */
function isJobActive(type: JobType): boolean {
  const job = activeJob.value
  return job !== null && job.type === type && (job.state === 'PENDING' || job.state === 'RUNNING')
}

function stopJobTracking(): void {
  if (pollTimer) {
    window.clearInterval(pollTimer)
    pollTimer = undefined
  }
  jobUnsubscribe?.()
  jobUnsubscribe = undefined
}

/**
 * 挂跟踪器：WS 订阅 + 1s 轮询兜底（WS 断线时完成/失败可能收不到），
 * 收到终态（或任务消失）即停；WS 与轮询先到者生效，后到者被 settled 忽略。
 */
function trackJob(
  type: JobType,
  job: Job,
  onCompleted: (job: Job) => void,
  onFailed: (job: Job) => void,
): void {
  // 重复提交被服务端 409 后自动挂回同一任务 → 已在跟踪则跳过。
  if (activeJob.value?.jobId === job.jobId && pollTimer !== undefined) return
  stopJobTracking()
  activeJob.value = { ...job, type }
  let settled = false
  const settle = (): void => {
    if (settled) return
    settled = true
    stopJobTracking()
  }
  const complete = (done: Job): void => {
    if (settled) return
    settled = true
    activeJob.value = done
    stopJobTracking()
    onCompleted(done)
  }
  const fail = (failed: Job): void => {
    if (settled) return
    settled = true
    activeJob.value = failed
    stopJobTracking()
    onFailed(failed)
  }
  jobUnsubscribe = subscribeJob(job.jobId, (event: JobWsEnvelope) => {
    const payload = event.payload
    if (event.type === 'job.started' || event.type === 'job.progress') {
      activeJob.value = {
        ...(activeJob.value ?? job),
        type,
        state: 'RUNNING',
        stage: payload.stage ?? null,
        percent: payload.percent ?? 0,
        processed: payload.processed ?? 0,
        total: payload.total ?? 0,
      }
    } else if (event.type === 'job.completed') {
      complete({
        ...(activeJob.value ?? job),
        type,
        state: 'COMPLETED',
        result: payload.result ?? null,
      })
    } else if (event.type === 'job.failed') {
      fail({
        ...(activeJob.value ?? job),
        type,
        state: 'FAILED',
        error: payload.error ?? '任务执行失败',
      })
    }
  })
  pollTimer = window.setInterval(() => {
    void jobsApi
      .getJob(job.jobId)
      .then((current) => {
        if (current.state === 'COMPLETED') complete(current)
        else if (current.state === 'FAILED') fail(current)
        else activeJob.value = current
      })
      .catch(() => settle())
  }, 1000)
}

function lastJobKey(type: JobType): string {
  return `${LAST_JOB_KEY_PREFIX}${type.toLowerCase()}`
}

/** 每次提交时记录 jobId，供刷新后恢复（尽力而为，存储不可用则跳过）。 */
function writeLastJob(type: JobType, jobId: string): void {
  try {
    localStorage.setItem(lastJobKey(type), jobId)
  } catch (error) {
    console.error('[AdminBackup] failed to persist last job', error)
  }
}

/**
 * 跨刷新恢复：优先挂活跃任务；否则读最近一次 jobId——终态且在 5 分钟内则
 * 恢复结果面板，仍在运行则重挂跟踪器（plan A6）。
 */
async function recoverJob(
  type: JobType,
  onCompleted: (job: Job) => void,
  onFailed: (job: Job) => void,
): Promise<void> {
  try {
    const active = await jobsApi.getActiveJob(type)
    trackJob(type, active, onCompleted, onFailed)
    return
  } catch {
    // 404：无活跃任务 → 落 localStorage 恢复。
  }
  let stored: string | null = null
  try {
    stored = localStorage.getItem(lastJobKey(type))
  } catch {
    return
  }
  if (!stored) return
  let job: Job
  try {
    job = await jobsApi.getJob(stored)
  } catch {
    return
  }
  if (job.state === 'PENDING' || job.state === 'RUNNING') {
    trackJob(type, job, onCompleted, onFailed)
    return
  }
  if (job.completedAt === null || Date.now() - job.completedAt > RECENT_JOB_MS) return
  if (job.state === 'COMPLETED') onCompleted(job)
  else if (job.state === 'FAILED') onFailed(job)
}

/** 409（已有同类型活跃任务）→ 提示并自动挂到该任务。 */
async function attachActiveJob(
  type: JobType,
  busyLabel: string,
  onCompleted: (job: Job) => void,
  onFailed: (job: Job) => void,
): Promise<void> {
  try {
    const active = await jobsApi.getActiveJob(type)
    writeLastJob(type, active.jobId)
    trackJob(type, active, onCompleted, onFailed)
  } catch (error) {
    console.error('[AdminBackup] failed to attach to active job', error)
    showSnack(`已有${busyLabel}进行中，但无法获取任务详情，请稍后刷新重试`, 7000)
  }
}

/** M-6 错误信封：HTTP 409 或 error.code === 'CONFLICT'。 */
function isConflict(error: unknown): boolean {
  if (!axios.isAxiosError(error)) return false
  const code = (error.response?.data as { error?: { code?: string } } | undefined)?.error?.code
  return error.response?.status === 409 || code === 'CONFLICT'
}

/* -------------------- Job 终态处理器（按类型复用） -------------------- */

function onImportCompleted(job: Job): void {
  importResult.value = job.result as EhImportResult
  showSnack('EhViewer 备份导入完成', 4000)
}

function onImportFailed(job: Job): void {
  importError.value = job.error || '导入失败，请检查文件后重试'
}

function onExportCompleted(job: Job): void {
  void downloadExportJob(job)
}

function onExportFailed(job: Job): void {
  showSnack(job.error ? `导出失败：${job.error}` : '导出失败，请稍后重试', 5000)
}

function onRestoreCompleted(): void {
  // R4-2: 还原本地成功后立即显示待重启横幅（服务端 state 亦已置 true）。
  restorePending.value = true
  selectedFile.value = null
  confirmWord.value = ''
  if (fileInput.value) fileInput.value.value = ''
  showSnack('还原成功，需重启服务器生效', 7000)
}

function onRestoreFailed(job: Job): void {
  showSnack(job.error || '还原失败', 7000)
}

/** B5: imported 计数展示行（zh 标签 + 值）。 */
const importCountRows = computed(() => {
  const c = importResult.value?.imported
  if (!c) return []
  const labels: ReadonlyArray<[keyof typeof c, string]> = [
    ['downloads', '下载'],
    ['history', '历史'],
    ['filters', '过滤'],
    ['labels', '标签'],
    ['bookmarks', '书签'],
    ['favorites', '收藏'],
    ['dirnames', '下载目录'],
    ['quickSearches', '快速搜索'],
    ['blackList', '黑名单'],
    ['galleryTags', '作品标签'],
  ]
  return labels.map(([key, label]) => ({ key, label, value: c[key] }))
})

function showSnack(message: string, duration = 2600): void {
  snack.value = message
  if (snackTimer) window.clearTimeout(snackTimer)
  snackTimer = window.setTimeout(() => {
    snack.value = ''
  }, duration)
}

/* ------------------------- R4-2 还原待重启横幅 ------------------------- */

/** 挂载时读取 restore 运行态；失败（如旧服务器无该端点）则静默不显示横幅。 */
onMounted(async () => {
  try {
    const state = await backupApi.getBackupState()
    restorePending.value = state.restorePending
  } catch (error) {
    console.error('[AdminBackup] failed to read backup state', error)
  }
  // 跨刷新恢复：活跃任务 / 最近 5 分钟内的终态任务。
  await recoverJob('IMPORT', onImportCompleted, onImportFailed)
  await recoverJob('EXPORT', onExportCompleted, onExportFailed)
  await recoverJob('RESTORE', onRestoreCompleted, onRestoreFailed)
})

async function copyRestartCommand(): Promise<void> {
  try {
    await navigator.clipboard.writeText(RESTART_COMMAND)
    restartCopied.value = true
    if (restartCopiedTimer) window.clearTimeout(restartCopiedTimer)
    restartCopiedTimer = window.setTimeout(() => {
      restartCopied.value = false
    }, 2000)
  } catch (error) {
    console.error('[AdminBackup] failed to copy restart command', error)
    showSnack('复制失败，请手动执行：' + RESTART_COMMAND, 7000)
  }
}

/* --------------------------------- 导出 --------------------------------- */

async function handleExport(): Promise<void> {
  if (exporting.value) return
  try {
    const job = await backupApi.exportBackupAsync(includeDownloads.value)
    writeLastJob('EXPORT', job.jobId)
    trackJob('EXPORT', job, onExportCompleted, onExportFailed)
  } catch (error) {
    if (isConflict(error)) {
      showSnack('已有导出进行中，已自动挂到该任务')
      await attachActiveJob('EXPORT', '导出', onExportCompleted, onExportFailed)
    } else {
      console.error('[AdminBackup] failed to submit export', error)
      showSnack('导出失败，请稍后重试', 5000)
    }
  }
}

/** EXPORT Job 完成后下载产物（GET /backup/export/{jobId}）触发浏览器下载。 */
async function downloadExportJob(job: Job): Promise<void> {
  try {
    const blob = await backupApi.downloadExport(job.jobId)
    triggerDownload(blob)
    showSnack('备份已生成，下载已开始')
  } catch (error) {
    console.error('[AdminBackup] failed to download export', error)
    showSnack('下载备份失败，请稍后重试', 5000)
  }
}

function triggerDownload(blob: Blob): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `anotherviewer-backup-${backupStamp()}.zip`
  document.body.appendChild(a)
  a.click()
  a.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

function backupStamp(): string {
  const d = new Date()
  const pad = (n: number): string => String(n).padStart(2, '0')
  return `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}-${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`
}

/* --------------------------------- 还原 --------------------------------- */

function onFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  selectedFile.value = file
  if (file) confirmWord.value = ''
}

async function handleRestore(): Promise<void> {
  const file = selectedFile.value
  if (!file) return
  if (file.size > MAX_RESTORE_BYTES) {
    showSnack('WebUI 还原面向元数据备份（≤50MB）；含下载内容的大备份请手动解包/拷贝 data-dir', 7000)
    return
  }
  if (!window.confirm('将覆盖当前数据库，旧文件保留为 .bak，需要重启生效。确认还原？')) return
  try {
    const job = await backupApi.restoreBackup(file)
    writeLastJob('RESTORE', job.jobId)
    trackJob('RESTORE', job, onRestoreCompleted, onRestoreFailed)
  } catch (error) {
    if (isConflict(error)) {
      showSnack('已有还原进行中，已自动挂到该任务')
      await attachActiveJob('RESTORE', '还原', onRestoreCompleted, onRestoreFailed)
    } else {
      showSnack(errorMessageOf(error), 7000)
    }
  }
}

function errorMessageOf(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string } | undefined
    if (data?.message) return data.message
  }
  return '还原失败，请检查网络后重试'
}

/* ---------------------------- 导入 EhViewer（B5） ---------------------------- */

function onDbChange(event: Event): void {
  const input = event.target as HTMLInputElement
  dbFile.value = input.files?.[0] ?? null
  importResult.value = null
  importError.value = ''
}

function onCookieChange(event: Event): void {
  const input = event.target as HTMLInputElement
  cookieFile.value = input.files?.[0] ?? null
  importResult.value = null
  importError.value = ''
}

async function handleEhImport(): Promise<void> {
  const file = dbFile.value
  if (!file) return
  if (!window.confirm('将把 EhViewer 备份导入当前账号，gid 冲突默认跳过。确认导入？')) return
  importError.value = ''
  importResult.value = null
  try {
    const job = await backupApi.importEhViewer(file, cookieFile.value)
    writeLastJob('IMPORT', job.jobId)
    trackJob('IMPORT', job, onImportCompleted, onImportFailed)
  } catch (error) {
    if (isConflict(error)) {
      // 已有导入进行中 → 自动挂到该任务继续看进度。
      showSnack('已有导入进行中，已自动挂到该任务')
      await attachActiveJob('IMPORT', '导入', onImportCompleted, onImportFailed)
    } else {
      console.error('[AdminBackup] failed to submit EhViewer import', error)
      importError.value = ehImportErrorMessageOf(error)
    }
  }
}

/** 后端失败返回统一 M-6 错误信封 `{error:{code,message,...}}`。 */
function ehImportErrorMessageOf(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as
      | { error?: { message?: string }; message?: string }
      | undefined
    const message = data?.error?.message ?? data?.message
    if (message) return message
  }
  return '导入失败，请检查文件后重试'
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

onBeforeUnmount(() => {
  stopJobTracking()
  if (snackTimer) window.clearTimeout(snackTimer)
  if (restartCopiedTimer) window.clearTimeout(restartCopiedTimer)
})
</script>

<style scoped>
/* Content column — rendered inside AdminLayout's scrollable content area. */
.backup__column {
  max-width: 760px;
  margin: 0 auto;
  padding: 4px var(--keyline-margin) calc(24px + var(--safe-area-bottom));
}

.backup__header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 16px var(--keyline-margin) 4px;
}

.backup__title {
  margin: 0;
  font-size: clamp(18px, 22px, 26px);
  font-weight: 700;
  letter-spacing: 0.01em;
  color: var(--text-color-primary);
}

/* ------------------------- R4-2 待重启警示横幅 ------------------------- */

.backup__restart-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 8px var(--keyline-margin) 4px;
  padding: 14px 18px;
  border-radius: var(--card-radius);
  border: 1px solid color-mix(in srgb, var(--color-warning, #f5a623) 60%, transparent);
  background: color-mix(in srgb, var(--color-warning, #f5a623) 14%, transparent);
}

.backup__restart-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.backup__restart-zh {
  font-size: clamp(13px, 14px, 16px);
  font-weight: 700;
  color: var(--text-color-primary);
}

.backup__restart-en {
  font-size: var(--text-super-small);
  color: var(--text-color-secondary);
}

.backup__restart-copy {
  flex: 0 0 auto;
  padding: 8px 14px;
  border: 1px solid color-mix(in srgb, var(--color-warning, #f5a623) 60%, transparent);
  border-radius: 999px;
  background: transparent;
  color: var(--text-color-primary);
  font-size: var(--text-super-small);
  font-weight: 600;
  cursor: pointer;
}

/* ------------------------------- 导出按钮 --------------------------------- */

.backup__action {
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
}

.backup__action:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.backup__badge {
  flex: 0 0 auto;
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--text-color-secondary);
  font-size: clamp(10px, 11px, 13px);
  font-weight: 700;
  letter-spacing: 0.05em;
}

/* ------------------------------- 文件选择 --------------------------------- */

.backup__file-btn {
  display: inline-flex;
  padding: 8px 16px;
  border: 1px solid color-mix(in srgb, var(--color-primary) 55%, transparent);
  border-radius: 999px;
  background: transparent;
  color: var(--color-primary);
  font-size: var(--text-super-small);
  font-weight: 600;
  cursor: pointer;
}

.backup__file-input {
  display: none;
}

.backup__file-info {
  padding: 4px 2px 0;
  font-size: var(--text-super-small);
  color: var(--text-color-secondary);
}

/* ------------------------------- 还原按钮 --------------------------------- */

.backup__field {
  width: 220px;
}

.backup__restore-btn {
  flex: 0 0 auto;
  padding: 8px 16px;
  border: 1px solid color-mix(in srgb, var(--color-danger, #e5484d) 55%, transparent);
  border-radius: 999px;
  background: transparent;
  color: var(--color-danger, #e5484d);
  font-size: var(--text-super-small);
  font-weight: 600;
  cursor: pointer;
}

.backup__restore-btn:disabled {
  opacity: 0.6;
  cursor: default;
}

/* --------------------------- 导入 EhViewer（B5） --------------------------- */

.backup__import-btn {
  flex: 0 0 auto;
  padding: 8px 16px;
  border: 1px solid color-mix(in srgb, var(--color-primary) 55%, transparent);
  border-radius: 999px;
  background: transparent;
  color: var(--color-primary);
  font-size: var(--text-super-small);
  font-weight: 600;
  cursor: pointer;
}

.backup__import-btn:disabled {
  opacity: 0.6;
  cursor: default;
}

.backup__import-result {
  padding: 12px var(--keyline-margin, 16px) 14px;
  font-size: clamp(13px, 14px, 16px);
}

.backup__import-error {
  margin: 0;
  padding: 10px 12px;
  border-radius: var(--card-radius);
  background: color-mix(in srgb, var(--color-danger, #e5484d) 12%, transparent);
  color: var(--color-danger, #e5484d);
  line-height: 1.5;
  word-break: break-word;
}

.backup__import-headline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 0 0 10px;
  font-weight: 700;
  color: var(--text-color-primary);
}

.backup__import-skipped {
  font-size: var(--text-super-small);
  font-weight: 600;
  color: var(--text-color-secondary);
}

.backup__import-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 8px 16px;
  margin: 0;
}

.backup__import-item {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-radius: var(--card-radius);
  background: var(--color-surface);
}

.backup__import-label {
  font-size: var(--text-super-small);
  color: var(--text-color-secondary);
}

.backup__import-value {
  margin: 0;
  font-size: clamp(14px, 15px, 17px);
  font-weight: 800;
  color: var(--text-color-primary);
  font-variant-numeric: tabular-nums;
}

.backup__import-cookies {
  margin: 10px 0 0;
  font-size: var(--text-super-small);
  color: var(--color-primary);
}

.banner-enter-active,
.banner-leave-active {
  transition:
    opacity var(--duration-scene-opacity) var(--ease-decelerate-quart),
    transform var(--duration-scene-opacity) var(--ease-decelerate-quart);
}

.banner-enter-from,
.banner-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* ---------------------------------- snackbar -------------------------------- */

.backup__snackbar {
  position: fixed;
  left: 50%;
  bottom: calc(24px + var(--safe-area-bottom));
  translate: -50% 0;
  z-index: 300;
  max-width: min(480px, calc(100vw - 32px));
  padding: 12px 20px;
  border-radius: var(--card-radius);
  background: var(--gallery-slider-background);
  color: var(--color-white);
  font-size: clamp(13px, 14px, 16px);
  box-shadow: 0 4px 12px var(--shadow-color);
}

.snack-enter-active,
.snack-leave-active {
  transition:
    opacity var(--duration-scene-opacity) var(--ease-decelerate-quart),
    translate var(--duration-scene-translate) var(--ease-decelerate-quint);
}

.snack-enter-from,
.snack-leave-to {
  opacity: 0;
  translate: -50% 12px;
}

@media (prefers-reduced-motion: reduce) {
  .snack-enter-active,
  .snack-leave-active {
    transition: none;
  }
}
</style>
