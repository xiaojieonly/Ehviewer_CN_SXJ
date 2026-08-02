<!--
  AdminAdvanced.vue — 管理面板「高级」页（Wave 6）.

  复用 AdminLayout 内容区与 settings 页的偏好分组 / 对话框样式：

    - 界面语言：项目当前无 i18n 机制，仅将偏好写入 localStorage
      （`anotherviewer-admin-advanced-ui`），文案后续接入 i18n 时再消费；
    - 保存解析错误日志：同样落在 localStorage（settingsApi / 后端
      serverConfig 均无对应字段）；
    - 导出 / 导入数据：后端 `GET /api/v1/export`、`POST /api/v1/import`
      尚未实现，按钮仅给出 TODO 提示；
    - 清除本地数据：confirm 后删除全部 `anotherviewer-` 前缀的 localStorage
      条目（保留 token / username，避免意外登出）。
-->
<template>
  <div class="advanced">
    <div class="advanced__column">
      <header class="advanced__header">
        <h1 class="advanced__title">高级</h1>
      </header>

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

      <!-- ═══ 数据 ══════════════════════════════════════════════════════ -->
      <section>
        <SectionHeader title="数据" />
        <PrefCard>
          <PrefRow icon="file-find-primary" title="导出数据" summary="导出全部设置与本地数据">
            <button type="button" class="advanced__action" aria-label="导出数据" @click="onExport">
              <span class="advanced__badge" title="GET /api/v1/export 尚未实现">TODO</span>
            </button>
          </PrefRow>
          <PrefRow icon="folder-add-dark" title="导入数据" summary="从导出文件恢复设置与数据">
            <button type="button" class="advanced__action" aria-label="导入数据" @click="pickImportFile">
              <span class="advanced__badge" title="POST /api/v1/import 尚未实现">TODO</span>
            </button>
          </PrefRow>
          <PrefRow icon="clear-all-dark" title="清除本地数据" summary="删除此浏览器中存储的全部本地数据">
            <button type="button" class="advanced__action" aria-label="清除本地数据" @click="confirmClearLocal">
              <AppIcon name="go-to-dark" size="20px" />
            </button>
          </PrefRow>
        </PrefCard>
        <input
          ref="importInput"
          type="file"
          accept=".json,application/json"
          class="advanced__file"
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
import { reactive, ref } from 'vue'
import AppIcon from '@/components/atoms/AppIcon.vue'
import { AppSelect, AppSwitch, PrefCard, PrefRow, SectionHeader } from '@/components/form'

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
