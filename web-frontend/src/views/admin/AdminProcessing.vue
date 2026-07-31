<!--
  AdminProcessing.vue — 管理面板「图像处理」页（Wave 6）.

  复用 AdminLayout 内容区与 settings 页的偏好分组样式
  （.pref-group / .pref-card / .pref / .switch / .select），持久化走
  PUT /settings 的 processing 段（与 AdminAccess 相同的本地类型 + 断言模式）：

    - 启用图像处理 → settingsApi.update({ processing: { enabled } })；
    - 默认处理类型 → settingsApi.update({ processing: { defaultType } })；
    - 输出格式     → settingsApi.update({ processing: { outputFormat } })；
    - 输出质量     → settingsApi.update({ processing: { outputQuality } })。

  字段名与枚举值对齐后端 SettingsDto.kt / ImageProcessor.kt：
  defaultType ∈ UPSCALE_2X | UPSCALE_4X | DENOISE | DENOISE_UPSCALE，
  outputFormat ∈ png | jpeg | webp，outputQuality ∈ 1..100。
  修改经 600ms 防抖提交，失败时回滚本地值并提示。
-->
<template>
  <div class="processing">
    <div class="processing__column">
      <header class="processing__header">
        <h1 class="processing__title">图像处理</h1>
        <span v-if="processing" class="processing__status" role="status">
          {{ processing.enabled ? '已启用' : '已停用' }}
        </span>
      </header>

      <!-- ═══ 处理设置 ═════════════════════════════════════════════════ -->
      <section class="pref-group">
        <h2 class="pref-group__title">处理设置</h2>
        <div class="pref-card">
          <div class="pref">
            <AppIcon name="similar-primary" class="pref__icon" />
            <div class="pref__text">
              <span class="pref__title">启用图像处理</span>
              <span class="pref__summary">开启后按默认类型处理页面图片</span>
            </div>
            <button
              type="button"
              class="switch"
              role="switch"
              :aria-checked="processing?.enabled ?? false"
              aria-label="启用图像处理"
              :disabled="!processing"
              @click="toggleEnabled"
            >
              <span class="switch__thumb" />
            </button>
          </div>
          <div class="pref-divider" />
          <div class="pref">
            <AppIcon name="similar-primary" class="pref__icon" />
            <div class="pref__text">
              <span class="pref__title">默认处理类型</span>
              <span class="pref__summary">对图片应用的默认增强方式</span>
            </div>
            <label class="select">
              <span class="select__label">默认处理类型</span>
              <select
                :value="processing?.defaultType"
                aria-label="默认处理类型"
                :disabled="!processing"
                @change="onSelect('defaultType', $event)"
              >
                <option
                  v-for="option in TYPE_OPTIONS"
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
            <AppIcon name="similar-primary" class="pref__icon" />
            <div class="pref__text">
              <span class="pref__title">输出格式</span>
              <span class="pref__summary">处理完成后图片的保存格式</span>
            </div>
            <label class="select">
              <span class="select__label">输出格式</span>
              <select
                :value="processing?.outputFormat"
                aria-label="输出格式"
                :disabled="!processing"
                @change="onSelect('outputFormat', $event)"
              >
                <option
                  v-for="option in FORMAT_OPTIONS"
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
            <AppIcon name="similar-primary" class="pref__icon" />
            <div class="pref__text">
              <span class="pref__title">输出质量</span>
              <span class="pref__summary">有损格式的编码质量，1–100</span>
            </div>
            <span class="processing__quality-value" aria-hidden="true">
              {{ processing?.outputQuality ?? '–' }}
            </span>
            <input
              v-model.number="qualityDraft"
              type="range"
              min="1"
              max="100"
              step="1"
              class="processing__slider"
              aria-label="输出质量"
              :disabled="!processing"
              @change="commitQuality"
            />
          </div>
        </div>
      </section>

      <!-- ═══ 说明 ═════════════════════════════════════════════════════ -->
      <p class="processing__note">
        处理任务在服务器后台队列中执行，可在处理任务列表中查看进度。
      </p>
    </div>

    <!-- Snackbar. -->
    <Transition name="snack">
      <div v-if="snack" class="processing__snackbar" role="status">{{ snack }}</div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import type { Settings } from '@/api/settings'
import { settingsApi } from '@/api/settings'
import AppIcon from '@/components/atoms/AppIcon.vue'

/* ------------------------------ option lists ----------------------------- */

/** 后端 ImageProcessor.kt ProcessingType 枚举，defaultType 以字符串存储。 */
type ProcessingType = 'UPSCALE_2X' | 'UPSCALE_4X' | 'DENOISE' | 'DENOISE_UPSCALE'
type OutputFormat = 'png' | 'jpeg' | 'webp'

const TYPE_OPTIONS: ReadonlyArray<{ value: ProcessingType; label: string }> = [
  { value: 'UPSCALE_2X', label: '2X 放大' },
  { value: 'UPSCALE_4X', label: '4X 放大' },
  { value: 'DENOISE', label: '降噪' },
  { value: 'DENOISE_UPSCALE', label: '降噪 + 放大' },
]

const FORMAT_OPTIONS: ReadonlyArray<{ value: OutputFormat; label: string }> = [
  { value: 'png', label: 'PNG' },
  { value: 'jpeg', label: 'JPEG' },
  { value: 'webp', label: 'WebP' },
]

/* ------------------------- processing settings --------------------------- */

/** 后端 SettingsResponse.processing 的字段（settings.ts 尚未声明，见组件头注释）。 */
interface ProcessingSettings {
  enabled: boolean
  defaultType: ProcessingType
  outputFormat: OutputFormat
  outputQuality: number
}

interface SettingsWithProcessing extends Settings {
  processing: ProcessingSettings
}

const DEFAULT_PROCESSING: ProcessingSettings = {
  enabled: false,
  defaultType: 'UPSCALE_2X',
  outputFormat: 'png',
  outputQuality: 90,
}

const processing = reactive<ProcessingSettings>({ ...DEFAULT_PROCESSING })

/** 滑块拖动中的草稿值（仅 change 提交后持久化）。 */
const qualityDraft = ref(DEFAULT_PROCESSING.outputQuality)

watch(
  () => processing.outputQuality,
  (value) => {
    qualityDraft.value = value
  },
)

/* -------------------------------- persistence ---------------------------- */

let saveTimer: number | undefined

/** 始终提交完整的 processing 段——后端会一次性写入全部四个字段。 */
function persistProcessing(): void {
  if (saveTimer) window.clearTimeout(saveTimer)
  saveTimer = window.setTimeout(async () => {
    const snapshot = { ...processing }
    try {
      await settingsApi.update({ processing: snapshot } as Partial<Settings>)
    } catch (error) {
      console.error('[AdminProcessing] failed to persist processing settings', error)
      Object.assign(processing, snapshot)
      showSnack('无法在服务器上保存设置')
    }
  }, 600)
}

function toggleEnabled(): void {
  processing.enabled = !processing.enabled
  persistProcessing()
}

function onSelect(key: 'defaultType' | 'outputFormat', event: Event): void {
  processing[key] = (event.target as HTMLSelectElement).value as never
  persistProcessing()
}

function commitQuality(): void {
  processing.outputQuality = Math.min(100, Math.max(1, qualityDraft.value))
  persistProcessing()
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
    const settings = (await settingsApi.get()) as SettingsWithProcessing
    Object.assign(processing, DEFAULT_PROCESSING, settings.processing)
  } catch (error) {
    console.error('[AdminProcessing] failed to load settings', error)
    showSnack('无法加载服务器设置')
  }
})
</script>

<style scoped>
.processing {
  min-height: 100%;
  background: var(--color-bg);
}

.processing__column {
  max-width: 760px;
  margin: 0 auto;
  padding: 4px var(--keyline-margin) calc(56px + var(--safe-area-bottom));
}

/* ---------------------------------- header --------------------------------- */

.processing__header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 4px 4px;
}

.processing__title {
  margin: 0;
  font-size: clamp(17px, 20px, 24px);
  font-weight: 600;
  letter-spacing: 0.01em;
  color: var(--text-color-primary);
}

.processing__status {
  margin-left: auto;
  padding: 4px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-accent) 14%, transparent);
  color: var(--text-color-theme-primary);
  font-size: clamp(11px, 12px, 14px);
  font-weight: 700;
  letter-spacing: 0.04em;
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

.switch:disabled {
  opacity: 0.5;
  cursor: default;
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
  min-width: 136px;
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

.select select:disabled {
  opacity: 0.5;
  cursor: default;
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

/* ---------------------------------- slider --------------------------------- */

.processing__quality-value {
  min-width: 28px;
  text-align: right;
  font-size: clamp(13px, 14px, 16px);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--text-color-primary);
}

.processing__slider {
  flex: 0 0 200px;
  max-width: 100%;
  accent-color: var(--color-primary);
  cursor: pointer;
}

.processing__slider:disabled {
  opacity: 0.5;
  cursor: default;
}

/* ---------------------------------- note ----------------------------------- */

.processing__note {
  margin: 18px 4px 0;
  font-size: clamp(11px, 12px, 14px);
  line-height: 1.5;
  color: var(--text-color-secondary);
}

/* --------------------------------- snackbar -------------------------------- */

.processing__snackbar {
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
