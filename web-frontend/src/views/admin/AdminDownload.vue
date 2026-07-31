<!--
  AdminDownload.vue — 管理面板 · 下载设置（Wave 6）。

  服务端设置（PUT /settings，防抖提交）:
    - download.path / workerCount / downloadDelay / downloadTimeout
    - download.maxConcurrentGalleries / maxConcurrentImages

  设备本地设置（localStorage `ehviewer-admin-download-ui`，仅本设备生效）:
    - 预加载图片数、下载列表分页、排序方向、自动开始下载

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
        <section class="pref-group">
          <h2 class="pref-group__title">基础设置</h2>
          <div class="pref-card">
            <button type="button" class="pref pref--action" @click="openPathDialog">
              <AppIcon name="folder-share-dark" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">下载路径</span>
                <span class="pref__summary">{{ server?.download.path || '未设置' }}</span>
              </div>
              <AppIcon name="pencil-dark" class="pref__chevron" size="20px" />
            </button>
            <div class="pref-divider" />
            <div class="pref">
              <AppIcon name="download-dark" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">并发线程数</span>
                <span class="pref__summary">获取图片的工作线程数（1–10）</span>
              </div>
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
            </div>
            <div class="pref-divider" />
            <div class="pref">
              <AppIcon name="pause-dark" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">下载延迟</span>
                <span class="pref__summary">两次下载请求之间的间隔（毫秒）</span>
              </div>
              <label class="num-field">
                <input
                  v-model.number="serverDelay"
                  type="number"
                  min="0"
                  aria-label="下载延迟（毫秒）"
                  :disabled="!server"
                />
              </label>
            </div>
            <div class="pref-divider" />
            <div class="pref">
              <AppIcon name="refresh-dark" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">下载超时</span>
                <span class="pref__summary">单个图片下载超时时间（毫秒）</span>
              </div>
              <label class="num-field">
                <input
                  v-model.number="serverTimeout"
                  type="number"
                  min="0"
                  aria-label="下载超时（毫秒）"
                  :disabled="!server"
                />
              </label>
            </div>
          </div>
        </section>

        <!-- ═══ 并发限制 ═════════════════════════════════════════════════ -->
        <section class="pref-group">
          <h2 class="pref-group__title">并发限制</h2>
          <div class="pref-card">
            <div class="pref">
              <AppIcon name="folder-add-dark" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">最大并发画廊数</span>
                <span class="pref__summary">同时下载的画廊数量上限（1–20）</span>
              </div>
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
            </div>
            <div class="pref-divider" />
            <div class="pref">
              <AppIcon name="download-primary" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">最大并发图片数</span>
                <span class="pref__summary">每个画廊同时获取的图片数量上限（1–20）</span>
              </div>
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
            </div>
            <div class="pref-divider" />
            <div class="pref">
              <AppIcon name="download-box-dark" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">预加载图片数</span>
                <span class="pref__summary">提前加载的后续图片数量（1–20） · 仅本设备生效</span>
              </div>
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
            </div>
          </div>
        </section>

        <!-- ═══ 列表与行为 ═══════════════════════════════════════════════ -->
        <section class="pref-group">
          <h2 class="pref-group__title">列表与行为</h2>
          <div class="pref-card">
            <div class="pref">
              <AppIcon name="reorder" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">下载列表分页</span>
                <span class="pref__summary">下载列表按页加载而非一次载入全部 · 仅本设备生效</span>
              </div>
              <button
                type="button"
                class="switch"
                role="switch"
                :aria-checked="local.paginated"
                aria-label="下载列表分页"
                @click="local.paginated = !local.paginated"
              >
                <span class="switch__thumb" />
              </button>
            </div>
            <div class="pref-divider" />
            <div class="pref">
              <AppIcon name="check-all-dark" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">排序方向</span>
                <span class="pref__summary">
                  {{ local.sortAscending ? '升序（按添加时间先后）' : '降序（最新在前）' }} ·
                  仅本设备生效
                </span>
              </div>
              <button
                type="button"
                class="switch"
                role="switch"
                :aria-checked="local.sortAscending"
                aria-label="排序方向"
                @click="local.sortAscending = !local.sortAscending"
              >
                <span class="switch__thumb" />
              </button>
            </div>
            <div class="pref-divider" />
            <div class="pref">
              <AppIcon name="play-dark" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">自动开始下载</span>
                <span class="pref__summary">立即恢复排队中的下载 · 仅本设备生效</span>
              </div>
              <button
                type="button"
                class="switch"
                role="switch"
                :aria-checked="local.autoStart"
                aria-label="自动开始下载"
                @click="local.autoStart = !local.autoStart"
              >
                <span class="switch__thumb" />
              </button>
            </div>
          </div>
        </section>

        <!-- ═══ 维护 ═════════════════════════════════════════════════════ -->
        <section class="pref-group">
          <h2 class="pref-group__title">维护</h2>
          <div class="pref-card">
            <!-- TODO: downloadApi 暂未提供批量清理冗余文件接口，待后端补充后接入。 -->
            <button type="button" class="pref pref--action" @click="notImplemented">
              <AppIcon name="clear-all-dark" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">清理冗余文件</span>
                <span class="pref__summary">删除不再被任何下载引用的临时文件 · TODO：待后端接口</span>
              </div>
            </button>
            <div class="pref-divider" />
            <!-- TODO: downloadApi 暂未提供批量清理无效下载接口，待后端补充后接入。 -->
            <button type="button" class="pref pref--action" @click="notImplemented">
              <AppIcon name="delete-dark" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">清理无效下载</span>
                <span class="pref__summary">移除状态异常且无法恢复的下载记录 · TODO：待后端接口</span>
              </div>
            </button>
          </div>
        </section>
      </div>
    </main>

    <!-- Edit download path dialog. -->
    <Transition name="dialog">
      <div v-if="pathDialogOpen" class="dialog-scrim" @click.self="pathDialogOpen = false">
        <div class="dialog" role="dialog" aria-modal="true" aria-label="下载路径">
          <h2 class="dialog__title">下载路径</h2>
          <label class="field">
            <input
              v-model="pathDraft"
              type="text"
              placeholder=" "
              @keydown.enter.prevent="savePath"
            />
            <span class="field__label">服务器端路径</span>
          </label>
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
import { onMounted, reactive, ref, watch, type Ref } from 'vue'
import type { Settings } from '@/api/settings'
import { settingsApi } from '@/api/settings'
import AppIcon from '@/components/atoms/AppIcon.vue'

/* ----------------------------- server settings ---------------------------- */

const server = ref<Settings | null>(null)
const savedFlash = ref(false)
let savedTimer: number | undefined
let saveTimer: number | undefined

/** Debounced PUT /settings — mirrors SettingsView committing prefs on change. */
function scheduleServerSave(): void {
  if (saveTimer) window.clearTimeout(saveTimer)
  saveTimer = window.setTimeout(async () => {
    if (!server.value) return
    try {
      await settingsApi.update(server.value)
      flashSaved()
    } catch (error) {
      console.error('[AdminDownload] failed to persist settings', error)
      showSnack('无法在服务器上保存设置')
    }
  }, 600)
}

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

interface LocalSettings {
  preloadImages: number
  paginated: boolean
  sortAscending: boolean
  autoStart: boolean
}

const LOCAL_SETTINGS_KEY = 'ehviewer-admin-download-ui'

const DEFAULT_LOCAL_SETTINGS: LocalSettings = {
  preloadImages: 2,
  paginated: true,
  sortAscending: false,
  autoStart: true,
}

function loadLocalSettings(): LocalSettings {
  try {
    const raw = localStorage.getItem(LOCAL_SETTINGS_KEY)
    if (raw) {
      return { ...DEFAULT_LOCAL_SETTINGS, ...(JSON.parse(raw) as Partial<LocalSettings>) }
    }
  } catch {
    // Corrupt/unavailable storage — fall back to defaults.
  }
  return { ...DEFAULT_LOCAL_SETTINGS }
}

const local = reactive<LocalSettings>(loadLocalSettings())

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

.pref-group__title {
  margin: 22px 4px 8px;
  font-size: clamp(12px, 14px, 16px); /* 14sp ideal — Android preference category */
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-primary);
}

/* Card surface per roadmap §卡片规范 — 2dp radius / elevation. */
.pref-card {
  background: var(--color-background-floating);
  border-radius: var(--card-radius);
  box-shadow:
    0 var(--card-elevation) 4px var(--shadow-color),
    0 0 1px var(--shadow-color);
  overflow: hidden;
}

.pref-divider {
  height: 1px;
  margin: 0 var(--keyline-margin);
  background: var(--color-divider);
}

/* ------------------------------ preference row ---------------------------- */

.pref {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 16px;
  min-height: 48px; /* Android preference item height */
  padding: 10px var(--keyline-margin);
}

button.pref {
  width: 100%;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
  font: inherit;
  transition: background-color 120ms var(--ease-decelerate-quart);
}

button.pref:hover {
  background: var(--color-surface);
}

button.pref:active {
  background: var(--color-surface-activated);
}

.pref__icon {
  flex: 0 0 24px;
  color: var(--drawable-color-primary);
}

.pref__text {
  flex: 1 1 160px;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.pref__title {
  font-size: clamp(14px, 16px, 18px); /* 16sp — Android preference title */
  color: var(--text-color-primary);
}

.pref__summary {
  font-size: clamp(11px, 12px, 14px); /* 12sp — Android preference summary */
  color: var(--text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pref__chevron {
  flex: 0 0 20px;
  color: var(--drawable-color-secondary);
}

/* ---------------------------------- switch --------------------------------- */

.switch {
  position: relative;
  flex: 0 0 36px;
  width: 36px;
  height: 20px;
  border: none;
  border-radius: 999px;
  background: var(--widget-color);
  cursor: pointer;
  transition: background-color 200ms var(--ease-decelerate-quart);
}

.switch[aria-checked='true'] {
  background: color-mix(in srgb, var(--color-accent) 40%, transparent);
}

.switch__thumb {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--color-background-floating);
  box-shadow: 0 1px 2px var(--shadow-color);
  transition:
    transform 200ms var(--ease-decelerate-quart),
    background-color 200ms var(--ease-decelerate-quart);
}

.switch[aria-checked='true'] .switch__thumb {
  transform: translateX(16px);
  background: var(--color-accent);
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

/* Outlined material field (divider border → primary on focus). */
.field {
  position: relative;
  display: block;
}

.field input {
  width: 100%;
  padding: 14px 12px 10px;
  border: 1px solid var(--color-divider);
  border-radius: var(--card-radius);
  background: transparent;
  font-size: clamp(14px, 16px, 18px);
  color: var(--text-color-primary);
  outline: none;
  transition: border-color 150ms var(--ease-decelerate-quart);
}

.field input:focus {
  border-color: var(--color-primary);
}

.field__label {
  position: absolute;
  left: 10px;
  top: 50%;
  translate: 0 -50%;
  padding: 0 4px;
  font-size: clamp(14px, 16px, 18px);
  color: var(--text-color-secondary);
  pointer-events: none;
  transition:
    top 150ms var(--ease-decelerate-quart),
    font-size 150ms var(--ease-decelerate-quart),
    color 150ms var(--ease-decelerate-quart);
}

.field input:focus + .field__label,
.field input:not(:placeholder-shown) + .field__label {
  top: 0;
  font-size: clamp(10px, 12px, 13px);
  color: var(--color-primary);
  background: var(--color-background-floating);
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
