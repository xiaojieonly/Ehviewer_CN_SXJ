<!--
  AdminBackup.vue — 管理面板「备份与还原」页（T12）.

  对接 T11 BackupController：
    - GET  /api/v1/backup/export?includeDownloads=<bool> → zip 流
      （压缩分片生成耗时，导出期间按钮置 loading 等待下载）；
    - POST /api/v1/backup/restore → multipart 字段 file → { success, message }。

  还原是破坏性操作（覆盖当前数据库，旧库保留为 .bak，需重启生效）：
  上传前检查文件大小（>50MB 阻止），随后弹确认框，且必须输入确认词
  RESTORE 才能启用还原按钮（grill Q3 决策）。
-->
<template>
  <div class="backup">
    <div class="backup__column">
      <header class="backup__header">
        <h1 class="backup__title">备份与还原</h1>
      </header>

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
import { computed, onBeforeUnmount, ref } from 'vue'
import axios from 'axios'
import { backupApi } from '@/api/backup'
import { AppSwitch, AppTextField, PrefCard, PrefRow, SectionHeader } from '@/components/form'

/** WebUI 还原面向元数据备份；含下载内容的大备份请手动解包/拷贝 data-dir。 */
const MAX_RESTORE_BYTES = 50 * 1024 * 1024

const includeDownloads = ref(false)
const exporting = ref(false)

const selectedFile = ref<File | null>(null)
const confirmWord = ref('')
const restoring = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const snack = ref('')
let snackTimer: number | undefined

const canRestore = computed(
  () => selectedFile.value !== null && confirmWord.value.trim() === 'RESTORE' && !restoring.value,
)

function showSnack(message: string, duration = 2600): void {
  snack.value = message
  if (snackTimer) window.clearTimeout(snackTimer)
  snackTimer = window.setTimeout(() => {
    snack.value = ''
  }, duration)
}

/* --------------------------------- 导出 --------------------------------- */

async function handleExport(): Promise<void> {
  if (exporting.value) return
  exporting.value = true
  try {
    const blob = await backupApi.exportBackup(includeDownloads.value)
    triggerDownload(blob)
    showSnack('备份已生成，下载已开始')
  } catch (error) {
    console.error('[AdminBackup] failed to export backup', error)
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
  restoring.value = true
  try {
    const result = await backupApi.restoreBackup(file)
    if (result.success) {
      showSnack(result.message || '还原成功，需重启服务器生效', 7000)
      selectedFile.value = null
      confirmWord.value = ''
      if (fileInput.value) fileInput.value.value = ''
    } else {
      showSnack(result.message || '还原失败', 7000)
    }
  } catch (error) {
    showSnack(errorMessageOf(error), 7000)
  } finally {
    restoring.value = false
  }
}

function errorMessageOf(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string } | undefined
    if (data?.message) return data.message
  }
  return '还原失败，请检查网络后重试'
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

onBeforeUnmount(() => {
  if (snackTimer) window.clearTimeout(snackTimer)
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
