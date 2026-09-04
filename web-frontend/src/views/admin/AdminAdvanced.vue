<!--
  AdminAdvanced.vue — 管理面板「高级」页（Wave 6）.

  复用 AdminLayout 内容区与 settings 页的偏好分组 / 对话框样式：

    - 界面语言：项目当前无 i18n 机制，仅将偏好写入 localStorage
      （`anotherviewer-admin-advanced-ui`），文案后续接入 i18n 时再消费；
    - 保存解析错误日志：同样落在 localStorage（settingsApi / 后端
      serverConfig 均无对应字段）；
    - 导出 / 导入数据（F-UX4）：接入既有备份 REST（BackupController）——
      导出 = `GET /api/v1/backup/export` blob 下载（元数据备份，不含下载
      内容；含下载内容的开关留在专门的「备份与还原」页），导入 =
      `POST /api/v1/backup/restore` multipart 上传。还原成功后复用 R4-2
      运行态机制（`GET /api/v1/backup/state` → restorePending）显示
      「重启后生效」持久横幅，与 AdminBackup 同一套语义；
    - 清除本地数据：confirm 后删除全部 `anotherviewer-` 前缀的 localStorage
      条目（保留 token / username，避免意外登出）。
-->
<template>
  <div class="advanced">
    <div class="advanced__column">
      <header class="advanced__header">
        <h1 class="advanced__title">高级</h1>
      </header>

      <!-- R4-2 复用：导入（还原）成功后 restorePending=true，与「备份与还原」
           页同款持久警示横幅——数据已还原但需重启服务器才生效。 -->
      <div v-if="restorePending" class="advanced__restart-banner" role="alert">
        <div class="advanced__restart-text">
          <strong class="advanced__restart-zh">还原已完成，重启服务后生效</strong>
          <span class="advanced__restart-en">Restore complete — restart the server to apply</span>
        </div>
        <button
          type="button"
          class="advanced__restart-copy"
          aria-label="复制重启命令"
          @click="copyRestartCommand"
        >
          {{ restartCopied ? '已复制 Copied' : '复制重启命令 Copy restart command' }}
        </button>
      </div>

      <!-- ═══ 通用 ══════════════════════════════════════════════════════ -->
      <section>
        <SectionHeader title="通用" />
        <PrefCard>
          <PrefRow icon="settings-dark" title="界面语言" summary="尚未接入 i18n，仅记录偏好">
            <AppSelect
              :model-value="ui.language"
              :options="LANGUAGE_OPTIONS"
              @update:model-value="onLanguageValue"
            />
          </PrefRow>
          <PrefRow icon="bug-black" title="保存解析错误日志" summary="将页面解析失败记录到本地日志">
            <AppSwitch
              :model-value="ui.saveParseErrors"
              aria-label="保存解析错误日志"
              @update:model-value="toggleParseErrors"
            />
          </PrefRow>
        </PrefCard>
      </section>

      <!-- ═══ 隐私（打码模式）════════════════════════════════════════════ -->
      <section>
        <SectionHeader title="隐私" />
        <PrefCard>
          <PrefRow
            icon="sec-primary"
            title="内容打码模式"
            summary="标题以内容序列号 #gid 显示，图片替换为占位符——便于截图协作"
          >
            <AppSwitch
              :model-value="privacyMaskEnabled"
              aria-label="内容打码模式"
              @update:model-value="togglePrivacyMask"
            />
          </PrefRow>
        </PrefCard>
      </section>

      <!-- ═══ 同步策略（Wave-2 / ADR-0003）═══════════════════════════════ -->
      <section>
        <SectionHeader title="同步策略" />
        <PrefCard>
          <PrefRow
            icon="settings-dark"
            title="冲突仲裁策略"
            summary="App 为权威：WebUI 的修改将被下次 App 同步覆盖（D2）"
          >
            <AppSelect
              :model-value="policy?.conflictStrategy ?? 'device_priority'"
              :options="STRATEGY_OPTIONS"
              aria-label="冲突仲裁策略"
              @update:model-value="onStrategyChange"
            />
          </PrefRow>
          <PrefRow
            icon="refresh-dark"
            title="自动同步间隔（秒）"
            summary="App 进入本网络后的周期同步间隔；0=仅网络变化时同步"
          >
            <AppSelect
              :model-value="policy?.autoSyncIntervalSec ?? 900"
              :options="INTERVAL_OPTIONS"
              aria-label="自动同步间隔"
              @update:model-value="onIntervalChange"
            />
          </PrefRow>
        </PrefCard>
      </section>

      <!-- ═══ 数据 ══════════════════════════════════════════════════════ -->
      <section>
        <SectionHeader title="数据" />
        <PrefCard>
          <PrefRow icon="file-find-primary" title="导出数据" summary="打包服务器数据库与配置为 zip 下载">
            <button
              type="button"
              class="advanced__action"
              aria-label="导出数据"
              :disabled="exporting"
              @click="onExport"
            >
              <span class="advanced__badge">{{ exporting ? '导出中…' : '导出' }}</span>
            </button>
          </PrefRow>
          <PrefRow icon="folder-add-dark" title="导入数据" summary="从备份 zip 恢复（覆盖数据库，重启后生效）">
            <button
              type="button"
              class="advanced__action"
              aria-label="导入数据"
              :disabled="importing"
              @click="pickImportFile"
            >
              <span class="advanced__badge">{{ importing ? '导入中…' : '导入' }}</span>
            </button>
          </PrefRow>
          <PrefRow icon="clear-all-dark" title="清除本地数据" summary="删除此浏览器中存储的全部本地数据">
            <button type="button" class="advanced__action" aria-label="清除本地数据" @click="confirmClearLocal">
              <AppIcon name="delete-dark" size="20px" />
            </button>
          </PrefRow>
        </PrefCard>
        <input
          ref="importInput"
          type="file"
          accept=".zip,application/zip,application/x-zip-compressed"
          class="advanced__file"
          aria-label="选择备份文件"
          @change="onImportFile"
        />
      </section>
    </div>

    <!-- Confirm dialog. -->
    <Transition name="dialog">
      <div v-if="confirmOpen" class="dialog-scrim" @click.self="confirmOpen = false">
        <div class="dialog" role="dialog" aria-modal="true" aria-label="清除本地数据">
          <h2 class="dialog__title">清除本地数据</h2>
          <p class="dialog__message">
            删除此浏览器中所有本地的设置、缓存与搜索历史？登录状态不受影响。
          </p>
          <div class="dialog__actions">
            <button type="button" class="btn-text" @click="confirmOpen = false">取消</button>
            <button type="button" class="btn-primary btn-primary--danger" @click="clearLocalData">
              清除
            </button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Snackbar. -->
    <Transition name="snack">
      <div v-if="snack" class="advanced__snackbar" role="status">{{ snack }}</div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import AppIcon from '@/components/atoms/AppIcon.vue'
import { AppSelect, AppSwitch, PrefCard, PrefRow, SectionHeader } from '@/components/form'
import { syncApi, type SyncPolicy } from '@/api/sync'
import { backupApi } from '@/api/backup'
import { jobsApi, type Job } from '@/api/jobs'
import { privacyApi } from '@/api/privacy'
import { privacyMaskEnabled, setPrivacyMaskEnabled } from '@/utils/privacyMask'

/* ------------------------------ local settings ---------------------------- */

interface AdvancedUi {
  language: string
  saveParseErrors: boolean
}

const UI_STORAGE_KEY = 'anotherviewer-admin-advanced-ui'

/** 登录凭证——清除本地数据时保留，避免意外登出。 */
const AUTH_KEYS = new Set(['token', 'username'])

const LANGUAGE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: 'zh-CN', label: '简体中文' },
  { value: 'zh-TW', label: '繁體中文' },
  { value: 'en-US', label: 'English' },
  { value: 'ja-JP', label: '日本語' },
]

const DEFAULT_UI: AdvancedUi = {
  language: 'zh-CN',
  saveParseErrors: false,
}

function loadUi(): AdvancedUi {
  try {
    const raw = localStorage.getItem(UI_STORAGE_KEY)
    if (raw) {
      return { ...DEFAULT_UI, ...(JSON.parse(raw) as Partial<AdvancedUi>) }
    }
  } catch {
    // Corrupt/unavailable storage — fall back to defaults.
  }
  return { ...DEFAULT_UI }
}

const ui = reactive<AdvancedUi>(loadUi())

function persistUi(): void {
  try {
    localStorage.setItem(UI_STORAGE_KEY, JSON.stringify(ui))
  } catch {
    // ignore write failures
  }
}

function onLanguageValue(value: string | number): void {
  ui.language = String(value)
  persistUi()
}

function toggleParseErrors(): void {
  ui.saveParseErrors = !ui.saveParseErrors
  persistUi()
}

/** 内容打码：乐观切换本地展示层，权威持久化在服务端（对 Agent 等
 *  无头客户端同样生效）；失败回滚并提示。 */
function togglePrivacyMask(): void {
  const next = !privacyMaskEnabled.value
  setPrivacyMaskEnabled(next)
  privacyApi.setMask(next).catch((error) => {
    console.error('[AdminAdvanced] failed to persist privacy mask', error)
    setPrivacyMaskEnabled(!next)
    showSnack('打码状态保存失败', 5000)
  })
}

/* ------------------------- sync policy (ADR-0003) ------------------------- */

const STRATEGY_OPTIONS: Array<{ value: string; label: string }> = [
  { value: 'device_priority', label: 'Android 优先（默认）' },
  { value: 'lww', label: '最后写入胜出' },
  { value: 'web_priority', label: 'WebUI 优先' },
]

const INTERVAL_OPTIONS: Array<{ value: number; label: string }> = [
  { value: 0, label: '仅网络变化时' },
  { value: 300, label: '5 分钟' },
  { value: 900, label: '15 分钟（默认）' },
  { value: 1800, label: '30 分钟' },
  { value: 3600, label: '1 小时' },
]

const policy = ref<SyncPolicy | null>(null)

onMounted(() => {
  syncApi
    .getPolicy()
    .then((p) => {
      policy.value = p
    })
    .catch(() => {
      // Legacy/unreachable server — the panel keeps contract defaults.
    })
  // R4-2 运行态：挂载时读取 restore 状态，导入（还原）已成功待重启时显示横幅。
  // 失败（如旧服务器无该端点）则静默不显示，与 AdminBackup 同语义。
  backupApi
    .getBackupState()
    .then((state) => {
      restorePending.value = state.restorePending
    })
    .catch(() => {
      // ignore — banner stays hidden
    })
})

async function persistPolicy(next: SyncPolicy): Promise<void> {
  try {
    policy.value = await syncApi.updatePolicy(next)
    showSnack('同步策略已保存')
  } catch {
    showSnack('同步策略保存失败')
  }
}

function onStrategyChange(value: string | number): void {
  const current = policy.value ?? {
    conflictStrategy: 'device_priority' as const,
    clientTier: 1 as const,
    autoSyncIntervalSec: 900,
  }
  void persistPolicy({ ...current, conflictStrategy: String(value) as SyncPolicy['conflictStrategy'] })
}

function onIntervalChange(value: string | number): void {
  const current = policy.value ?? {
    conflictStrategy: 'device_priority' as const,
    clientTier: 1 as const,
    autoSyncIntervalSec: 900,
  }
  void persistPolicy({ ...current, autoSyncIntervalSec: Number(value) })
}

/* --------------------------------- data ops ------------------------------- */
/*
 * F-UX4: 导出/导入接入既有备份 REST（BackupController），替换原 TODO 占位。
 *   导出 → GET  /api/v1/backup/export（blob 下载；本页固定不含下载内容，
 *          「包含下载内容」开关留在专门的「备份与还原」页，避免误导体积）
 *   导入 → POST /api/v1/backup/restore（multipart file；破坏性操作，先
 *          confirm，50MB 上限与 AdminBackup 一致——WebUI 还原面向元数据备份）
 * 成功后复用 R4-2 运行态横幅机制（restorePending → 「重启后生效」）。
 */

const importInput = ref<HTMLInputElement | null>(null)
const confirmOpen = ref(false)

const exporting = ref(false)
const importing = ref(false)

/** R4-2: 还原待重启运行态；挂载时读取 /backup/state，导入成功后本地置 true。 */
const restorePending = ref(false)
const restartCopied = ref(false)
let restartCopiedTimer: number | undefined

/** R4-2: 重启命令（与仓库根 start.sh/stop.sh 对应），供横幅一键复制。 */
const RESTART_COMMAND = './stop.sh && ./start.sh'

/** WebUI 还原面向元数据备份；含下载内容的大备份请手动解包/拷贝 data-dir。 */
const MAX_RESTORE_BYTES = 50 * 1024 * 1024

async function onExport(): Promise<void> {
  if (exporting.value) return
  exporting.value = true
  try {
    const blob = await backupApi.exportBackup(false)
    triggerDownload(blob)
    showSnack('备份已生成，下载已开始')
  } catch (error) {
    console.error('[AdminAdvanced] failed to export backup', error)
    showSnack('导出失败，请稍后重试', 5000)
  } finally {
    exporting.value = false
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

function pickImportFile(): void {
  if (importing.value) return
  importInput.value?.click()
}

function onImportFile(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  // 立即重置，保证同一文件可再次选中触发 change。
  input.value = ''
  if (!file || importing.value) return
  if (file.size > MAX_RESTORE_BYTES) {
    showSnack('WebUI 还原面向元数据备份（≤50MB）；含下载内容的大备份请手动解包/拷贝 data-dir', 7000)
    return
  }
  if (!window.confirm('导入备份将覆盖当前服务器数据库（旧文件保留为 .bak），需要重启生效。继续？')) return
  void restoreFromBackup(file)
}

async function restoreFromBackup(file: File): Promise<void> {
  importing.value = true
  try {
    const job = await backupApi.restoreBackup(file)
    // restore 已异步化：202 返回 jobId，轮询 GET /jobs/{jobId} 到终态
    // （任务在服务端坚持跑完，刷新/离开页面不影响；跨刷新恢复由 AdminBackup
    //  页的 active 查询覆盖，本页只等本次提交的任务）。
    const done = await waitJobTerminal(job.jobId)
    if (done.state === 'COMPLETED') {
      const result = done.result as { message?: string } | null
      showSnack(result?.message || '导入成功，需重启服务器生效', 7000)
      // R4-2: 导入成功后立即显示待重启横幅（服务端 state 亦已置 true）。
      restorePending.value = true
    } else {
      showSnack(done.error || '导入失败', 7000)
    }
  } catch (error) {
    showSnack(importErrorMessageOf(error), 7000)
  } finally {
    importing.value = false
  }
}

/** 轮询任务到终态（COMPLETED/FAILED），最多 5 分钟；超时/任务消失抛错。 */
async function waitJobTerminal(jobId: string, timeoutMs = 5 * 60 * 1000): Promise<Job> {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const job = await jobsApi.getJob(jobId)
    if (job.state === 'COMPLETED' || job.state === 'FAILED') return job
    await new Promise((resolve) => window.setTimeout(resolve, 1000))
  }
  throw new Error('导入超时，请到「备份与还原」页查看任务状态')
}

function importErrorMessageOf(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string } | undefined
    if (data?.message) return data.message
  }
  if (error instanceof Error && error.message) return error.message
  return '导入失败，请检查网络后重试'
}

async function copyRestartCommand(): Promise<void> {
  try {
    await navigator.clipboard.writeText(RESTART_COMMAND)
    restartCopied.value = true
    if (restartCopiedTimer) window.clearTimeout(restartCopiedTimer)
    restartCopiedTimer = window.setTimeout(() => {
      restartCopied.value = false
    }, 2000)
  } catch (error) {
    console.error('[AdminAdvanced] failed to copy restart command', error)
    showSnack('复制失败，请手动执行：' + RESTART_COMMAND, 7000)
  }
}

function confirmClearLocal(): void {
  confirmOpen.value = true
}

function clearLocalData(): void {
  confirmOpen.value = false
  let cleared = 0
  for (let i = localStorage.length - 1; i >= 0; i--) {
    const key = localStorage.key(i)
    if (key?.startsWith('anotherviewer-') && !AUTH_KEYS.has(key)) {
      localStorage.removeItem(key)
      cleared++
    }
  }
  showSnack(cleared > 0 ? `已清除 ${cleared} 项本地数据` : '本地数据已为空')
}

/* --------------------------------- chrome --------------------------------- */

const snack = ref('')
let snackTimer: number | undefined

function showSnack(message: string, duration = 2600): void {
  snack.value = message
  if (snackTimer) window.clearTimeout(snackTimer)
  snackTimer = window.setTimeout(() => {
    snack.value = ''
  }, duration)
}

onBeforeUnmount(() => {
  if (snackTimer) window.clearTimeout(snackTimer)
  if (restartCopiedTimer) window.clearTimeout(restartCopiedTimer)
})
</script>

<style scoped>
.advanced {
  min-height: 100%;
  background: var(--color-bg);
}

.advanced__column {
  max-width: 760px;
  margin: 0 auto;
  padding: 4px var(--keyline-margin) calc(56px + var(--safe-area-bottom));
}

/* ---------------------------------- header --------------------------------- */

.advanced__header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 4px 4px;
}

.advanced__title {
  margin: 0;
  font-size: clamp(17px, 20px, 24px);
  font-weight: 600;
  letter-spacing: 0.01em;
  color: var(--text-color-primary);
}

/* ----------------------------- preference group --------------------------- */

.advanced__badge {
  flex: 0 0 auto;
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--text-color-secondary);
  font-size: clamp(10px, 11px, 13px);
  font-weight: 700;
  letter-spacing: 0.08em;
}

.advanced__action {
  display: inline-flex;
  align-items: center;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--text-color-secondary);
  cursor: pointer;
}

.advanced__action:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

/* ------------------------- R4-2 待重启警示横幅 ------------------------- */

.advanced__restart-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 8px 0 4px;
  padding: 14px 18px;
  border-radius: var(--card-radius);
  border: 1px solid color-mix(in srgb, var(--color-warning, #f5a623) 60%, transparent);
  background: color-mix(in srgb, var(--color-warning, #f5a623) 14%, transparent);
}

.advanced__restart-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.advanced__restart-zh {
  font-size: clamp(13px, 14px, 16px);
  font-weight: 700;
  color: var(--text-color-primary);
}

.advanced__restart-en {
  font-size: var(--text-super-small);
  color: var(--text-color-secondary);
}

.advanced__restart-copy {
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

.advanced__file {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
}

/* ---------------------------------- dialogs -------------------------------- */

.dialog-scrim {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--black-overlay);
}

.dialog {
  width: min(420px, 100%);
  padding: 20px 20px 12px;
  border-radius: var(--card-radius);
  background: var(--color-background-floating);
  box-shadow: 0 8px 24px var(--shadow-color);
}

.dialog__title {
  margin: 0 0 12px;
  font-size: clamp(16px, 18px, 22px);
  font-weight: 700;
  color: var(--text-color-primary);
}

.dialog__message {
  margin: 0 0 8px;
  font-size: clamp(13px, 14px, 16px);
  line-height: 1.5;
  color: var(--text-color-secondary);
}

.dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  margin-top: 16px;
  padding-top: 8px;
  border-top: 1px solid var(--color-divider);
}

.dialog-enter-active,
.dialog-leave-active {
  transition: opacity var(--duration-scene-opacity) var(--ease-decelerate-quart);
}

.dialog-enter-active .dialog,
.dialog-leave-active .dialog {
  transition:
    transform var(--duration-scene-translate) var(--ease-decelerate-quint),
    opacity var(--duration-scene-opacity) var(--ease-decelerate-quart);
}

.dialog-enter-from,
.dialog-leave-to {
  opacity: 0;
}

.dialog-enter-from .dialog,
.dialog-leave-to .dialog {
  transform: translateY(16px) scale(0.97);
  opacity: 0;
}

/* --------------------------------- buttons --------------------------------- */

.btn-primary {
  padding: 9px 22px;
  border: none;
  border-radius: var(--card-radius);
  background: var(--color-primary);
  color: var(--color-white);
  font-size: clamp(13px, 14px, 16px);
  font-weight: 700;
  letter-spacing: 0.02em;
  cursor: pointer;
  box-shadow: 0 1px 3px var(--shadow-color);
  transition:
    background-color 150ms var(--ease-decelerate-quart),
    transform 120ms var(--ease-decelerate-quart);
}

.btn-primary:hover {
  background: var(--color-primary-dark);
}

.btn-primary:active {
  transform: scale(0.97);
}

.btn-primary--danger {
  background: var(--color-red-500);
}

.btn-primary--danger:hover {
  background: var(--color-red-500);
  filter: brightness(0.92);
}

.btn-text {
  padding: 9px 14px;
  border: none;
  border-radius: var(--card-radius);
  background: transparent;
  color: var(--text-color-theme-primary);
  font-size: clamp(13px, 14px, 16px);
  font-weight: 700;
  cursor: pointer;
  transition: background-color 150ms var(--ease-decelerate-quart);
}

.btn-text:hover {
  background: var(--color-surface);
}

/* --------------------------------- snackbar -------------------------------- */

.advanced__snackbar {
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
  .dialog-enter-active .dialog,
  .dialog-leave-active .dialog,
  .snack-enter-active,
  .snack-leave-active {
    transition: none;
  }
}
</style>
