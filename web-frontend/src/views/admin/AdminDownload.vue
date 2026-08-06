<!--
  AdminDownload.vue — 管理面板 · 下载设置（Wave 6）。

  服务端设置（PUT /settings，防抖提交）:
    - download.path / workerCount / downloadDelay / downloadTimeout
    - download.maxConcurrentGalleries / maxConcurrentImages

  设备本地设置（localStorage `anotherviewer-admin-download-ui`，仅本设备生效）:
    - 预加载图片数、排序方向、自动开始下载（旧版遗留键如 paginated 读取时忽略）

  维护操作（清理冗余文件 / 清理无效下载）: downloadApi 暂未提供对应批量清理
  接口，按钮置为 TODO，等待后端补充。
-->
<template>
  <div class="admin-download">
    <header class="admin-download__toolbar">
      <h1 class="admin-download__title">下载设置</h1>
      <Transition name="saved">
        <span v-if="savedFlash" class="admin-download__saved" role="status">已保存</span>
      </Transition>
    </header>

    <main class="admin-download__body">
      <div class="admin-download__column">
        <!-- ═══ 基础设置 ══════════════════════════════════════════════════ -->
        <section>
          <SectionHeader title="基础设置" />
          <PrefCard>
            <PrefRow
              icon="folder-share-dark"
              title="下载路径"
              :summary="server?.download.path || '未设置'"
            >
              <button type="button" class="pref-action-btn" aria-label="修改下载路径" @click="openPathDialog">
                <AppIcon name="pencil-dark" size="20px" />
              </button>
            </PrefRow>
            <PrefRow icon="download-dark" title="并发线程数" summary="获取图片的工作线程数（1–10）">
              <div class="stepper">
                <button
                  type="button"
                  class="stepper__btn"
                  aria-label="减少并发线程数"
                  :disabled="!server || server.download.workerCount <= 1"
                  @click="bump('workerCount', -1, 1, 10)"
                >
                  −
                </button>
                <span class="stepper__value">{{ server?.download.workerCount ?? '–' }}</span>
                <button
                  type="button"
                  class="stepper__btn"
                  aria-label="增加并发线程数"
                  :disabled="!server || server.download.workerCount >= 10"
                  @click="bump('workerCount', 1, 1, 10)"
                >
                  +
                </button>
              </div>
            </PrefRow>
            <PrefRow icon="pause-dark" title="下载延迟" summary="两次下载请求之间的间隔（毫秒）">
              <label class="num-field">
                <input
                  v-model.number="serverDelay"
                  type="number"
                  min="0"
                  aria-label="下载延迟（毫秒）"
                  :disabled="!server"
                />
              </label>
            </PrefRow>
            <PrefRow icon="refresh-dark" title="下载超时" summary="单个图片下载超时时间（毫秒）">
              <label class="num-field">
                <input
                  v-model.number="serverTimeout"
                  type="number"
                  min="0"
                  aria-label="下载超时（毫秒）"
                  :disabled="!server"
                />
              </label>
            </PrefRow>
          </PrefCard>
        </section>

        <!-- ═══ 并发限制 ═════════════════════════════════════════════════ -->
        <section>
          <SectionHeader title="并发限制" />
          <PrefCard>
            <PrefRow icon="folder-add-dark" title="最大并发画廊数" summary="同时下载的画廊数量上限（1–20）">
              <div class="stepper">
                <button
                  type="button"
                  class="stepper__btn"
                  aria-label="减少最大并发画廊数"
                  :disabled="!server || server.download.maxConcurrentGalleries <= 1"
                  @click="bump('maxConcurrentGalleries', -1, 1, 20)"
                >
                  −
                </button>
                <span class="stepper__value">
                  {{ server?.download.maxConcurrentGalleries ?? '–' }}
                </span>
                <button
                  type="button"
                  class="stepper__btn"
                  aria-label="增加最大并发画廊数"
                  :disabled="!server || server.download.maxConcurrentGalleries >= 20"
                  @click="bump('maxConcurrentGalleries', 1, 1, 20)"
                >
                  +
                </button>
              </div>
            </PrefRow>
            <PrefRow icon="download-primary" title="最大并发图片数" summary="每个画廊同时获取的图片数量上限（1–20）">
              <div class="stepper">
                <button
                  type="button"
                  class="stepper__btn"
                  aria-label="减少最大并发图片数"
                  :disabled="!server || server.download.maxConcurrentImages <= 1"
                  @click="bump('maxConcurrentImages', -1, 1, 20)"
                >
                  −
                </button>
                <span class="stepper__value">
                  {{ server?.download.maxConcurrentImages ?? '–' }}
                </span>
                <button
                  type="button"
                  class="stepper__btn"
                  aria-label="增加最大并发图片数"
                  :disabled="!server || server.download.maxConcurrentImages >= 20"
                  @click="bump('maxConcurrentImages', 1, 1, 20)"
                >
                  +
                </button>
              </div>
            </PrefRow>
            <PrefRow icon="download-box-dark" title="预加载图片数" summary="提前加载的后续图片数量（1–20） · 仅本设备生效">
              <div class="stepper">
                <button
                  type="button"
                  class="stepper__btn"
                  aria-label="减少预加载图片数"
                  :disabled="local.preloadImages <= 1"
                  @click="local.preloadImages = clamp(local.preloadImages - 1, 1, 20)"
                >
                  −
                </button>
                <span class="stepper__value">{{ local.preloadImages }}</span>
                <button
                  type="button"
                  class="stepper__btn"
                  aria-label="增加预加载图片数"
                  :disabled="local.preloadImages >= 20"
                  @click="local.preloadImages = clamp(local.preloadImages + 1, 1, 20)"
                >
                  +
                </button>
              </div>
            </PrefRow>
          </PrefCard>
        </section>

        <!-- ═══ 列表与行为 ═══════════════════════════════════════════════ -->
        <section>
          <SectionHeader title="列表与行为" />
          <PrefCard>
            <PrefRow
              icon="check-all-dark"
              title="排序模式"
              :summary="`${sortModeLabel} · 仅本设备生效`"
            >
              <AppSelect
                :model-value="local.sortMode"
                :options="DOWNLOAD_SORT_OPTIONS"
                aria-label="排序模式"
                @update:model-value="(v) => (local.sortMode = v as DownloadSort)"
              />
            </PrefRow>
            <PrefRow
              icon="reorder"
              title="每页条数"
              :summary="`下载列表按页加载（与 Android 端一致）· 仅本设备生效`"
            >
              <AppSelect
                :model-value="local.pageSize"
                :options="DOWNLOAD_PAGE_SIZES.map((s) => ({ value: s, label: `${s} 条/页` }))"
                aria-label="每页条数"
                @update:model-value="(v) => (local.pageSize = Number(v))"
              />
            </PrefRow>
            <PrefRow icon="play-dark" title="自动开始下载" summary="立即恢复排队中的下载 · 仅本设备生效">
              <AppSwitch
                :model-value="local.autoStart"
                aria-label="自动开始下载"
                @update:model-value="(v) => (local.autoStart = v)"
              />
            </PrefRow>
          </PrefCard>
        </section>

        <!-- ═══ 维护 ═════════════════════════════════════════════════════ -->
        <section>
          <SectionHeader title="维护" />
          <PrefCard>
            <!-- TODO: downloadApi 暂未提供批量清理冗余文件接口，待后端补充后接入。 -->
            <PrefRow icon="clear-all-dark" title="清理冗余文件" summary="删除不再被任何下载引用的临时文件 · TODO：待后端接口">
              <button type="button" class="pref-action-btn" aria-label="清理冗余文件" @click="notImplemented">
                <AppIcon name="go-to-dark" size="20px" />
              </button>
            </PrefRow>
            <!-- TODO: downloadApi 暂未提供批量清理无效下载接口，待后端补充后接入。 -->
            <PrefRow icon="delete-dark" title="清理无效下载" summary="移除状态异常且无法恢复的下载记录 · TODO：待后端接口">
              <button type="button" class="pref-action-btn" aria-label="清理无效下载" @click="notImplemented">
                <AppIcon name="go-to-dark" size="20px" />
              </button>
            </PrefRow>
          </PrefCard>
        </section>
      </div>
    </main>

    <!-- Edit download path dialog. -->
    <Transition name="dialog">
      <div v-if="pathDialogOpen" class="dialog-scrim" @click.self="pathDialogOpen = false">
        <div class="dialog" role="dialog" aria-modal="true" aria-label="下载路径">
          <h2 class="dialog__title">下载路径</h2>
          <AppTextField v-model="pathDraft" label="服务器端路径" @keydown.enter.prevent="savePath" />
          <div class="dialog__actions">
            <button type="button" class="btn-text" @click="pathDialogOpen = false">取消</button>
            <button type="button" class="btn-primary" @click="savePath">保存</button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Snackbar. -->
    <Transition name="snack">
      <div v-if="snack" class="snackbar" role="status">{{ snack }}</div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch, type Ref } from 'vue'
import type { Settings } from '@/api/settings'
import { settingsApi } from '@/api/settings'
import AppIcon from '@/components/atoms/AppIcon.vue'
import { AppSelect, AppSwitch, AppTextField, PrefCard, PrefRow, SectionHeader } from '@/components/form'
import {
  DOWNLOAD_PAGE_SIZES,
  DOWNLOAD_SORT_OPTIONS,
  DOWNLOAD_UI_KEY,
  DEFAULT_DOWNLOAD_LIST_PREFS,
  loadDownloadListPrefs,
  type DownloadListPrefs,
  type DownloadSort,
} from '@/utils/downloadListSettings'

/* ----------------------------- server settings ---------------------------- */

const server = ref<Settings | null>(null)
const savedFlash = ref(false)
let savedTimer: number | undefined
let saveTimer: number | undefined
let pendingPayload: Settings | null = null

/** Debounced PUT /settings — mirrors the settings page committing prefs on change. */
function scheduleServerSave(): void {
  if (!server.value) return
  pendingPayload = server.value
  if (saveTimer) window.clearTimeout(saveTimer)
  saveTimer = window.setTimeout(() => {
    saveTimer = undefined
    const payload = pendingPayload
    pendingPayload = null
    if (payload) void saveSettings(payload)
  }, 600)
}

async function saveSettings(payload: Settings): Promise<void> {
  try {
    await settingsApi.update(payload)
    flashSaved()
  } catch (error) {
    console.error('[AdminDownload] failed to persist settings', error)
    showSnack('无法在服务器上保存设置')
  }
}

/** Flush a pending debounced save so edits are not lost when navigating away. */
function flushPendingSave(): void {
  if (saveTimer) window.clearTimeout(saveTimer)
  saveTimer = undefined
  const payload = pendingPayload
  pendingPayload = null
  if (payload) {
    settingsApi.update(payload).catch((error) => {
      console.error('[AdminDownload] failed to persist settings on unmount', error)
    })
  }
}

onBeforeUnmount(() => {
  flushPendingSave()
  if (savedTimer) window.clearTimeout(savedTimer)
  if (snackTimer) window.clearTimeout(snackTimer)
})

function flashSaved(): void {
  savedFlash.value = true
  if (savedTimer) window.clearTimeout(savedTimer)
  savedTimer = window.setTimeout(() => {
    savedFlash.value = false
  }, 1600)
}

function bump(
  field: 'workerCount' | 'maxConcurrentGalleries' | 'maxConcurrentImages',
  delta: number,
  min: number,
  max: number,
): void {
  if (!server.value) return
  server.value.download[field] = clamp(server.value.download[field] + delta, min, max)
  scheduleServerSave()
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}

const serverDelay = numberField({
  get: () => server.value?.download.downloadDelay,
  set: (value) => {
    if (!server.value) return
    server.value.download.downloadDelay = value
    scheduleServerSave()
  },
})

const serverTimeout = numberField({
  get: () => server.value?.download.downloadTimeout,
  set: (value) => {
    if (!server.value) return
    server.value.download.downloadTimeout = value
    scheduleServerSave()
  },
})

/** Writable numeric ref that tolerates empty input while typing. */
function numberField(options: { get: () => number | undefined; set: (value: number) => void }): Ref<number | undefined> {
  const draft = ref<number | undefined>(options.get())
  watch(
    () => options.get(),
    (next) => {
      if (next !== undefined) draft.value = next
    },
  )
  watch(draft, (next) => {
    if (next === undefined) return
    if (next === options.get()) return
    options.set(next)
  })
  return draft
}

/* ----------------------------- local settings ----------------------------- */

interface LocalSettings extends DownloadListPrefs {
  preloadImages: number
  autoStart: boolean
}

const LOCAL_SETTINGS_KEY = DOWNLOAD_UI_KEY

const DEFAULT_LOCAL_SETTINGS: LocalSettings = {
  ...DEFAULT_DOWNLOAD_LIST_PREFS,
  preloadImages: 2,
  autoStart: true,
}

/** Pick only the known local keys — legacy/unknown entries (e.g. the removed
    `paginated` switch, 旧 `sortAscending` 布尔) are migrated/ignored and
    dropped on the next persist. */
function loadLocalSettings(): LocalSettings {
  try {
    const raw = localStorage.getItem(LOCAL_SETTINGS_KEY)
    if (raw) {
      const stored = JSON.parse(raw) as Partial<LocalSettings>
      const prefs = loadDownloadListPrefs()
      return {
        ...prefs,
        preloadImages: stored.preloadImages ?? DEFAULT_LOCAL_SETTINGS.preloadImages,
        autoStart: stored.autoStart ?? DEFAULT_LOCAL_SETTINGS.autoStart,
      }
    }
  } catch {
    // Corrupt/unavailable storage — fall back to defaults.
  }
  return { ...DEFAULT_LOCAL_SETTINGS }
}

const local = reactive<LocalSettings>(loadLocalSettings())

/** 当前排序模式的展示文案（PrefRow summary 用）。 */
const sortModeLabel = computed(() => {
  const option = DOWNLOAD_SORT_OPTIONS.find((o) => o.value === local.sortMode)
  return option?.label ?? DEFAULT_DOWNLOAD_LIST_PREFS.sortMode
})

watch(
  local,
  (next) => {
    try {
      localStorage.setItem(LOCAL_SETTINGS_KEY, JSON.stringify(next))
    } catch {
      // ignore write failures
    }
  },
  { deep: true },
)

/* ------------------------------- dialogs --------------------------------- */

const pathDialogOpen = ref(false)
const pathDraft = ref('')

function openPathDialog(): void {
  pathDraft.value = server.value?.download.path ?? ''
  pathDialogOpen.value = true
}

function savePath(): void {
  if (!server.value) return
  server.value.download.path = pathDraft.value.trim()
  pathDialogOpen.value = false
  scheduleServerSave()
}

/* -------------------------------- actions --------------------------------- */

function notImplemented(): void {
  showSnack('后端暂未提供此接口，待实现')
}

/* --------------------------------- chrome --------------------------------- */

const snack = ref('')
let snackTimer: number | undefined

function showSnack(message: string): void {
  snack.value = message
  if (snackTimer) window.clearTimeout(snackTimer)
  snackTimer = window.setTimeout(() => {
    snack.value = ''
  }, 2600)
}

/* ---------------------------------- boot ---------------------------------- */

onMounted(async () => {
  try {
    server.value = await settingsApi.get()
  } catch (error) {
    console.error('[AdminDownload] failed to load settings', error)
    showSnack('无法加载服务器设置')
  }
})
</script>

<style scoped>
/* Scene shell — content column lives inside AdminLayout. */
.admin-download {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--color-bg);
}

/* --------------------------------- toolbar -------------------------------- */

.admin-download__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
  padding: 16px var(--keyline-margin) 0;
}

.admin-download__title {
  margin: 0;
  font-size: clamp(17px, 20px, 24px);
  font-weight: 600;
  letter-spacing: 0.01em;
  color: var(--text-color-primary);
}

.admin-download__saved {
  margin-left: auto;
  margin-right: 8px;
  padding: 4px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-primary) 14%, transparent);
  color: var(--color-primary);
  font-size: clamp(11px, 12px, 14px);
  font-weight: 700;
  letter-spacing: 0.04em;
}

.saved-enter-active,
.saved-leave-active {
  transition: opacity 200ms var(--ease-decelerate-quart);
}

.saved-enter-from,
.saved-leave-to {
  opacity: 0;
}

/* ---------------------------------- body ---------------------------------- */

.admin-download__body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.admin-download__column {
  max-width: 760px;
  margin: 0 auto;
  padding: 4px var(--keyline-margin) calc(56px + var(--safe-area-bottom));
}

/* ----------------------------- preference group --------------------------- */

/* Row-level action button (download path / maintenance rows). */
.pref-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--drawable-color-secondary);
  cursor: pointer;
  transition: background-color 150ms var(--ease-decelerate-quart);
}

.pref-action-btn:hover {
  background: var(--color-surface);
}

/* ---------------------------------- stepper -------------------------------- */

.stepper {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.stepper__btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--text-color-theme-primary);
  font-size: clamp(16px, 18px, 22px);
  line-height: 1;
  cursor: pointer;
  transition:
    background-color 150ms var(--ease-decelerate-quart),
    color 150ms var(--ease-decelerate-quart);
}

.stepper__btn:hover:not(:disabled) {
  background: var(--color-surface);
}

.stepper__btn:active:not(:disabled) {
  background: var(--color-surface-activated);
}

.stepper__btn:disabled {
  color: var(--drawable-color-secondary);
  cursor: default;
}

.stepper__value {
  min-width: 42px;
  text-align: center;
  font-size: clamp(13px, 14px, 16px);
  font-weight: 700;
  color: var(--text-color-primary);
  font-variant-numeric: tabular-nums;
}

/* ------------------------------- number input ------------------------------ */

.num-field input {
  width: 92px;
  padding: 8px 10px;
  border: 1px solid var(--color-divider);
  border-radius: var(--card-radius);
  background: transparent;
  font-size: clamp(13px, 14px, 16px);
  font-variant-numeric: tabular-nums;
  color: var(--text-color-primary);
  outline: none;
  transition: border-color 150ms var(--ease-decelerate-quart);
}

.num-field input:focus {
  border-color: var(--color-primary);
}

.num-field input:disabled {
  color: var(--drawable-color-secondary);
  cursor: default;
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

.snackbar {
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
