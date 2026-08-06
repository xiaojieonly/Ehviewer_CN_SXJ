<!--
  AdminServer.vue — 管理面板「服务器」页（Wave 6）.

  复用 AdminLayout 内容区（本组件渲染在其 <router-view /> 内）与 settings 页
  的偏好分组样式（.pref-group / .pref-card / .pref / .switch），并沿用其
  服务端设置持久化模式（PUT /settings）：

    - 缓存路径 / 缓存大小 → settingsApi.update({ cache: { ... } })；
    - SMB 备份开关 → settingsApi.update({ smb: { enabled } })，
      开启后展示「前往备份页面」链接（/smb-backup）；
    - 缓存统计 → imageApi.getCacheStatus()（缓存条目数）；
    - 清除缓存 → imageApi.clearCacheAsync()：202 提交 CACHE_CLEAR Job，
      WS 进度（JobProgressPanel「清缓存」）+ jobsApi 轮询兜底，完成后刷新统计。

  缓存路径与缓存大小在编辑完成（change 事件）后保存，失败时回滚本地值。
-->
<template>
  <div class="server">
    <div class="server__column">
      <header class="server__header">
        <h1 class="server__title">服务器</h1>
        <span v-if="server" class="server__status" role="status">
          {{ server.smb.enabled ? 'SMB 备份已开启' : 'SMB 备份已关闭' }}
        </span>
      </header>

      <!-- ═══ 缓存 ═══════════════════════════════════════════════════════ -->
      <section>
        <SectionHeader title="缓存" />
        <PrefCard>
          <PrefRow icon="folder-share-dark" title="缓存路径" summary="服务器上缓存文件的存放目录">
            <div class="server__field">
              <AppTextField
                :model-value="pathDraft"
                placeholder="/cache"
                :disabled="!server"
                aria-label="缓存路径"
                @update:model-value="(v) => (pathDraft = v)"
              />
            </div>
          </PrefRow>
          <PrefRow icon="settings-dark" title="缓存大小 (MB)" summary="为缓存预留的磁盘空间上限">
            <div class="server__field server__field--number">
              <AppTextField
                :model-value="sizeDraft === null ? '' : String(sizeDraft)"
                type="number"
                :disabled="!server"
                aria-label="缓存大小"
                @update:model-value="(v) => (sizeDraft = v === '' ? null : Number(v))"
              />
            </div>
          </PrefRow>
          <PrefRow icon="info-outline-dark" title="缓存统计" summary="当前缓存占用与条目数">
            <span v-if="cacheSize !== null" class="server__badge">{{ cacheSize }} 张</span>
            <span v-else class="server__badge">—</span>
          </PrefRow>
          <PrefRow icon="clear-all-dark" title="清除缓存" summary="删除服务器上的全部缓存文件">
            <button
              type="button"
              class="server__action"
              aria-label="清除缓存"
              :disabled="clearingCache"
              @click="clearCache"
            >
              <span class="server__badge">{{ clearingCache ? '清除中…' : '清除' }}</span>
            </button>
          </PrefRow>
          <JobProgressPanel v-if="clearingCache" :job="activeJob" title="清缓存" />
        </PrefCard>
      </section>

      <!-- ═══ SMB 备份 ══════════════════════════════════════════════════ -->
      <section>
        <SectionHeader title="SMB 备份" />
        <PrefCard>
          <PrefRow
            icon="history-black"
            title="SMB 备份"
            :summary="server?.smb.enabled ? '已开启，备份到 SMB 共享' : '已关闭'"
          >
            <AppSwitch
              :model-value="server?.smb.enabled ?? false"
              aria-label="SMB 备份"
              :disabled="!server"
              @update:model-value="toggleSmb"
            />
          </PrefRow>
          <template v-if="server?.smb.enabled">
            <PrefRow icon="go-to-dark" title="前往备份页面" summary="配置连接并手动触发同步">
              <router-link to="/smb-backup" class="server__link" aria-label="前往备份页面">
                <AppIcon name="go-to-dark" size="20px" />
              </router-link>
            </PrefRow>
          </template>
        </PrefCard>
      </section>
    </div>

    <!-- Snackbar. -->
    <Transition name="snack">
      <div v-if="snack" class="server__snackbar" role="status">{{ snack }}</div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { Settings } from '@/api/settings'
import { settingsApi } from '@/api/settings'
import { imageApi } from '@/api/image'
import { jobsApi, type Job, type JobType } from '@/api/jobs'
import JobProgressPanel from '@/components/jobs/JobProgressPanel.vue'
import { useWebSocket } from '@/composables/useWebSocket'
import type { JobWsEnvelope } from '@/composables/useWebSocket'
import axios from 'axios'
import AppIcon from '@/components/atoms/AppIcon.vue'
import { AppSwitch, AppTextField, PrefCard, PrefRow, SectionHeader } from '@/components/form'

const server = ref<Settings | null>(null)
const pathDraft = ref('')
const sizeDraft = ref<number | null>(null)
const cacheSize = ref<number | null>(null)

const snack = ref('')
let snackTimer: number | undefined

function showSnack(message: string): void {
  snack.value = message
  if (snackTimer) window.clearTimeout(snackTimer)
  snackTimer = window.setTimeout(() => {
    snack.value = ''
  }, 2600)
}

/* ---------------------- Job 进度跟踪器（plan A6） ---------------------- */

const { subscribeJob } = useWebSocket()

/** 当前跟踪的 CACHE_CLEAR 任务（RUNNING 时渲染 JobProgressPanel「清缓存」）。 */
const activeJob = ref<Job | null>(null)
let pollTimer: number | undefined
let jobUnsubscribe: (() => void) | undefined

const clearingCache = computed(() => isJobActive('CACHE_CLEAR'))

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

/** 跨刷新恢复：挂活跃 CACHE_CLEAR 任务（plan A6）。 */
async function recoverJob(
  type: JobType,
  onCompleted: (job: Job) => void,
  onFailed: (job: Job) => void,
): Promise<void> {
  try {
    const active = await jobsApi.getActiveJob(type)
    trackJob(type, active, onCompleted, onFailed)
  } catch {
    // 404：无活跃任务 → 无需恢复。
  }
}

/** M-6 错误信封：HTTP 409 或 error.code === 'CONFLICT'。 */
function isConflict(error: unknown): boolean {
  if (!axios.isAxiosError(error)) return false
  const code = (error.response?.data as { error?: { code?: string } } | undefined)?.error?.code
  return error.response?.status === 409 || code === 'CONFLICT'
}

/** 409（已有清缓存任务）→ 自动挂到该任务。 */
async function attachActiveJob(
  type: JobType,
  onCompleted: (job: Job) => void,
  onFailed: (job: Job) => void,
): Promise<void> {
  try {
    const active = await jobsApi.getActiveJob(type)
    trackJob(type, active, onCompleted, onFailed)
  } catch (error) {
    console.error('[AdminServer] failed to attach to active job', error)
    showSnack('已有清缓存任务进行中，但无法获取任务详情，请稍后刷新重试')
  }
}

function onClearCompleted(): void {
  showSnack('缓存已清除')
  void refreshCacheSize()
}

function onClearFailed(job: Job): void {
  showSnack(job.error ? `清除缓存失败：${job.error}` : '无法清除缓存')
}

/* ------------------------------- 缓存设置 -------------------------------- */

let cachePathTimer: number | undefined
let cacheSizeTimer: number | undefined

/** AppTextField 只在输入时发裸值；在编辑停顿后提交（等价原 change 保存语义）。 */
watch(pathDraft, () => {
  if (cachePathTimer) window.clearTimeout(cachePathTimer)
  cachePathTimer = window.setTimeout(saveCachePath, 500)
})

watch(sizeDraft, () => {
  if (cacheSizeTimer) window.clearTimeout(cacheSizeTimer)
  cacheSizeTimer = window.setTimeout(saveCacheSize, 500)
})

onBeforeUnmount(() => {
  stopJobTracking()
  if (cachePathTimer) window.clearTimeout(cachePathTimer)
  if (cacheSizeTimer) window.clearTimeout(cacheSizeTimer)
  if (snackTimer) window.clearTimeout(snackTimer)
})

async function saveCachePath(): Promise<void> {
  if (!server.value) return
  const next = pathDraft.value.trim()
  if (next === server.value.cache.path) return
  const previous = server.value.cache.path
  server.value.cache.path = next
  try {
    await settingsApi.update({ cache: { ...server.value.cache, path: next } })
    showSnack('缓存路径已保存')
  } catch (error) {
    server.value.cache.path = previous
    pathDraft.value = previous
    console.error('[AdminServer] failed to save cache path', error)
    showSnack('无法在服务器上保存缓存路径')
  }
}

async function saveCacheSize(): Promise<void> {
  if (!server.value || sizeDraft.value === null) return
  const next = Math.max(1, Math.floor(sizeDraft.value))
  if (next === server.value.cache.sizeMb) return
  const previous = server.value.cache.sizeMb
  sizeDraft.value = next
  server.value.cache.sizeMb = next
  try {
    await settingsApi.update({ cache: { ...server.value.cache, sizeMb: next } })
    showSnack('缓存大小已保存')
  } catch (error) {
    server.value.cache.sizeMb = previous
    sizeDraft.value = previous
    console.error('[AdminServer] failed to save cache size', error)
    showSnack('无法在服务器上保存缓存大小')
  }
}

async function refreshCacheSize(): Promise<void> {
  try {
    const { cacheSize: size } = await imageApi.getCacheStatus()
    cacheSize.value = size
  } catch (error) {
    cacheSize.value = null
    console.error('[AdminServer] failed to load cache stats', error)
  }
}

async function clearCache(): Promise<void> {
  if (clearingCache.value) return
  try {
    const job = await imageApi.clearCacheAsync()
    trackJob('CACHE_CLEAR', job, onClearCompleted, onClearFailed)
  } catch (error) {
    if (isConflict(error)) {
      showSnack('已有清缓存任务进行中，已自动挂到该任务')
      await attachActiveJob('CACHE_CLEAR', onClearCompleted, onClearFailed)
    } else {
      console.error('[AdminServer] failed to submit cache clear', error)
      showSnack('无法清除缓存')
    }
  }
}

/* ------------------------------- SMB 备份 -------------------------------- */

async function toggleSmb(): Promise<void> {
  if (!server.value) return
  const next = !server.value.smb.enabled
  server.value.smb.enabled = next
  try {
    await settingsApi.update({ smb: { enabled: next } })
    showSnack(next ? 'SMB 备份已开启' : 'SMB 备份已关闭')
  } catch (error) {
    server.value.smb.enabled = !next
    console.error('[AdminServer] failed to save SMB settings', error)
    showSnack('无法在服务器上保存 SMB 备份设置')
  }
}

/* ---------------------------------- boot ---------------------------------- */

onMounted(async () => {
  try {
    server.value = await settingsApi.get()
    pathDraft.value = server.value.cache.path
    sizeDraft.value = server.value.cache.sizeMb
  } catch (error) {
    console.error('[AdminServer] failed to load settings', error)
    showSnack('无法加载服务器设置')
  }
  void refreshCacheSize()
  await recoverJob('CACHE_CLEAR', onClearCompleted, onClearFailed)
})
</script>

<style scoped>
/* Content column — rendered inside AdminLayout's scrollable content area. */
.server__column {
  max-width: 760px;
  margin: 0 auto;
  padding: 4px var(--keyline-margin) calc(24px + var(--safe-area-bottom));
}

.server__header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 16px var(--keyline-margin) 4px;
}

.server__title {
  margin: 0;
  font-size: clamp(18px, 22px, 26px);
  font-weight: 700;
  letter-spacing: 0.01em;
  color: var(--text-color-primary);
}

.server__status {
  font-size: clamp(11px, 12px, 14px);
  color: var(--text-color-secondary);
}

/* ------------------------------- 输入框 --------------------------------- */

.server__field {
  width: 220px;
}

.server__field--number {
  width: 140px;
}

/* -------------------------------- TODO 徽标 --------------------------------- */

.server__badge {
  flex: 0 0 auto;
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--text-color-secondary);
  font-size: clamp(10px, 11px, 13px);
  font-weight: 700;
  letter-spacing: 0.05em;
}

.server__action {
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
}

.server__action:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.server__link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 6px;
  border-radius: 50%;
  color: var(--drawable-color-secondary);
}

/* ---------------------------------- snackbar -------------------------------- */

.server__snackbar {
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
