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
      <section>
        <SectionHeader title="处理设置" />
        <PrefCard>
          <PrefRow icon="similar-primary" title="启用图像处理" summary="开启后按默认类型处理页面图片">
            <AppSwitch
              :model-value="processing.enabled"
              aria-label="启用图像处理"
              @update:model-value="toggleEnabled"
            />
          </PrefRow>
          <PrefRow icon="similar-primary" title="默认处理类型" summary="对图片应用的默认增强方式">
            <AppSelect
              :model-value="processing.defaultType"
              :options="TYPE_OPTIONS"
              @update:model-value="(v) => onSelectValue('defaultType', v)"
            />
          </PrefRow>
          <PrefRow icon="similar-primary" title="输出格式" summary="处理完成后图片的保存格式">
            <AppSelect
              :model-value="processing.outputFormat"
              :options="FORMAT_OPTIONS"
              @update:model-value="(v) => onSelectValue('outputFormat', v)"
            />
          </PrefRow>
          <PrefRow icon="similar-primary" title="输出质量" summary="有损格式的编码质量，1–100">
            <span class="processing__quality-value" aria-hidden="true">
              {{ processing.outputQuality }}
            </span>
            <input
              v-model.number="qualityDraft"
              type="range"
              min="1"
              max="100"
              step="1"
              class="processing__slider"
              aria-label="输出质量"
              @change="commitQuality"
            />
          </PrefRow>
        </PrefCard>
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
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { settingsApi, type ProcessingSettings } from '@/api/settings'
import { AppSelect, AppSwitch, PrefCard, PrefRow, SectionHeader } from '@/components/form'

/* ------------------------------ option lists ----------------------------- */

/** 后端 ImageProcessor.kt ProcessingType 枚举，defaultType 以字符串存储。 */
type ProcessingType = 'UPSCALE_2X' | 'UPSCALE_4X' | 'DENOISE' | 'DENOISE_UPSCALE'
type OutputFormat = 'png' | 'jpeg' | 'webp'

const TYPE_OPTIONS: Array<{ value: ProcessingType; label: string }> = [
  { value: 'UPSCALE_2X', label: '2X 放大' },
  { value: 'UPSCALE_4X', label: '4X 放大' },
  { value: 'DENOISE', label: '降噪' },
  { value: 'DENOISE_UPSCALE', label: '降噪 + 放大' },
]

const FORMAT_OPTIONS: Array<{ value: OutputFormat; label: string }> = [
  { value: 'png', label: 'PNG' },
  { value: 'jpeg', label: 'JPEG' },
  { value: 'webp', label: 'WebP' },
]

/* ------------------------- processing settings --------------------------- */

/* processing 段类型直接取自 api/settings.ts（后端 SettingsResponse.processing），
 * 不再本地重复声明——局部 ProcessingType/OutputFormat 字面量联合是其 string
 * 字段的子集，仅用于下拉选项列表。 */

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
let pendingPayload: { processing: ProcessingSettings } | null = null

/** 始终提交完整的 processing 段——后端会一次性写入全部四个字段。 */
function persistProcessing(): void {
  pendingPayload = { processing: { ...processing } }
  if (saveTimer) window.clearTimeout(saveTimer)
  saveTimer = window.setTimeout(() => {
    saveTimer = undefined
    const payload = pendingPayload
    pendingPayload = null
    if (payload) void saveProcessing(payload)
  }, 600)
}

async function saveProcessing(payload: { processing: ProcessingSettings }): Promise<void> {
  try {
    await settingsApi.update(payload)
  } catch (error) {
    console.error('[AdminProcessing] failed to persist processing settings', error)
    Object.assign(processing, payload.processing)
    showSnack('无法在服务器上保存设置')
  }
}

/** 卸载前冲刷待提交的防抖保存，避免导航时丢失编辑。 */
function flushPendingSave(): void {
  if (saveTimer) window.clearTimeout(saveTimer)
  saveTimer = undefined
  const payload = pendingPayload
  pendingPayload = null
  if (payload) {
    settingsApi.update(payload).catch((error) => {
      console.error('[AdminProcessing] failed to persist processing settings on unmount', error)
    })
  }
}

onBeforeUnmount(() => {
  flushPendingSave()
  if (snackTimer) window.clearTimeout(snackTimer)
})

function toggleEnabled(): void {
  processing.enabled = !processing.enabled
  persistProcessing()
}

function onSelectValue(key: 'defaultType' | 'outputFormat', value: string | number): void {
  processing[key] = value as never
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
    const settings = await settingsApi.get()
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
