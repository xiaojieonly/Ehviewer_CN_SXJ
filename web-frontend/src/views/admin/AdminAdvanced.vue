<!--
  AdminAdvanced.vue — 管理面板「高级」页（Wave 6）.

  复用 AdminLayout 内容区与 SettingsView 的偏好分组 / 对话框样式：

    - 界面语言：项目当前无 i18n 机制，仅将偏好写入 localStorage
      （`ehviewer-admin-advanced-ui`），文案后续接入 i18n 时再消费；
    - 保存解析错误日志：同样落在 localStorage（settingsApi / 后端
      serverConfig 均无对应字段）；
    - 导出 / 导入数据：后端 `GET /api/v1/export`、`POST /api/v1/import`
      尚未实现，按钮仅给出 TODO 提示；
    - 清除本地数据：confirm 后删除全部 `ehviewer-` 前缀的 localStorage
      条目（保留 token / username，避免意外登出）。
-->
<template>
  <div class="advanced">
    <div class="advanced__column">
      <header class="advanced__header">
        <h1 class="advanced__title">高级</h1>
      </header>

      <!-- ═══ 通用 ══════════════════════════════════════════════════════ -->
      <section class="pref-group">
        <h2 class="pref-group__title">通用</h2>
        <div class="pref-card">
          <div class="pref">
            <AppIcon name="settings-dark" class="pref__icon" />
            <div class="pref__text">
              <span class="pref__title">界面语言</span>
              <span class="pref__summary">尚未接入 i18n，仅记录偏好</span>
            </div>
            <label class="select">
              <span class="select__label">界面语言</span>
              <select :value="ui.language" aria-label="界面语言" @change="onLanguageChange">
                <option
                  v-for="option in LANGUAGE_OPTIONS"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}
                </option>
              </select>
            </label>
          </div>
          <div class="pref-divider" />
          <div class="pref">
            <AppIcon name="bug-black" class="pref__icon" />
            <div class="pref__text">
              <span class="pref__title">保存解析错误日志</span>
              <span class="pref__summary">将页面解析失败记录到本地日志</span>
            </div>
            <button
              type="button"
              class="switch"
              role="switch"
              :aria-checked="ui.saveParseErrors"
              aria-label="保存解析错误日志"
              @click="toggleParseErrors"
            >
              <span class="switch__thumb" />
            </button>
          </div>
        </div>
      </section>

      <!-- ═══ 数据 ══════════════════════════════════════════════════════ -->
      <section class="pref-group">
        <h2 class="pref-group__title">数据</h2>
        <div class="pref-card">
          <button type="button" class="pref pref--action" @click="onExport">
            <AppIcon name="file-find-primary" class="pref__icon" />
            <div class="pref__text">
              <span class="pref__title">导出数据</span>
              <span class="pref__summary">导出全部设置与本地数据</span>
            </div>
            <span class="advanced__badge" title="GET /api/v1/export 尚未实现">TODO</span>
          </button>
          <div class="pref-divider" />
          <button type="button" class="pref pref--action" @click="pickImportFile">
            <AppIcon name="folder-add-dark" class="pref__icon" />
            <div class="pref__text">
              <span class="pref__title">导入数据</span>
              <span class="pref__summary">从导出文件恢复设置与数据</span>
            </div>
            <span class="advanced__badge" title="POST /api/v1/import 尚未实现">TODO</span>
          </button>
          <input
            ref="importInput"
            type="file"
            accept=".json,application/json"
            class="advanced__file"
            @change="onImportFile"
          />
          <div class="pref-divider" />
          <button type="button" class="pref pref--action" @click="confirmClearLocal">
            <AppIcon name="clear-all-dark" class="pref__icon" />
            <div class="pref__text">
              <span class="pref__title">清除本地数据</span>
              <span class="pref__summary">删除此浏览器中存储的全部本地数据</span>
            </div>
          </button>
        </div>
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
import { reactive, ref } from 'vue'
import AppIcon from '@/components/atoms/AppIcon.vue'

/* ------------------------------ local settings ---------------------------- */

interface AdvancedUi {
  language: string
  saveParseErrors: boolean
}

const UI_STORAGE_KEY = 'ehviewer-admin-advanced-ui'

/** 登录凭证——清除本地数据时保留，避免意外登出。 */
const AUTH_KEYS = new Set(['token', 'username'])

const LANGUAGE_OPTIONS: ReadonlyArray<{ value: string; label: string }> = [
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

function onLanguageChange(event: Event): void {
  ui.language = (event.target as HTMLSelectElement).value
  persistUi()
}

function toggleParseErrors(): void {
  ui.saveParseErrors = !ui.saveParseErrors
  persistUi()
}

/* --------------------------------- data ops ------------------------------- */

const importInput = ref<HTMLInputElement | null>(null)
const confirmOpen = ref(false)

/**
 * TODO: 后端 `GET /api/v1/export` 尚未实现。
 * 接入后：client.get('/export') → 触发浏览器下载 JSON 文件。
 */
function onExport(): void {
  showSnack('导出功能尚未实现（GET /api/v1/export）')
}

function pickImportFile(): void {
  importInput.value?.click()
}

/**
 * TODO: 后端 `POST /api/v1/import` 尚未实现。
 * 接入后：将所选文件内容 PUT/POST 到 /api/v1/import 并刷新页面数据。
 */
function onImportFile(): void {
  if (importInput.value) importInput.value.value = ''
  showSnack('导入功能尚未实现（POST /api/v1/import）')
}

function confirmClearLocal(): void {
  confirmOpen.value = true
}

function clearLocalData(): void {
  confirmOpen.value = false
  let cleared = 0
  for (let i = localStorage.length - 1; i >= 0; i--) {
    const key = localStorage.key(i)
    if (key?.startsWith('ehviewer-') && !AUTH_KEYS.has(key)) {
      localStorage.removeItem(key)
      cleared++
    }
  }
  showSnack(cleared > 0 ? `已清除 ${cleared} 项本地数据` : '本地数据已为空')
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

.pref-group__title {
  margin: 22px 4px 8px;
  font-size: clamp(12px, 14px, 16px);
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-primary);
}

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
  min-height: 48px;
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
  font-size: clamp(14px, 16px, 18px);
  color: var(--text-color-primary);
}

.pref__summary {
  font-size: clamp(11px, 12px, 14px);
  color: var(--text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

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

.advanced__file {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
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

/* ---------------------------------- select --------------------------------- */

.select {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.select select {
  appearance: none;
  min-width: 120px;
  padding: 8px 30px 8px 12px;
  border: 1px solid var(--color-divider);
  border-radius: var(--card-radius);
  background: var(--color-background-floating);
  color: var(--text-color-primary);
  font-size: clamp(13px, 14px, 16px);
  cursor: pointer;
  outline: none;
  transition: border-color 150ms var(--ease-decelerate-quart);
}

.select select:focus {
  border-color: var(--color-primary);
}

.select::after {
  content: '';
  position: absolute;
  right: 12px;
  top: 50%;
  width: 8px;
  height: 8px;
  border-right: 2px solid var(--drawable-color-secondary);
  border-bottom: 2px solid var(--drawable-color-secondary);
  translate: 0 -60%;
  transform: rotate(45deg);
  pointer-events: none;
}

.select__label {
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
