<!--
  ReaderSettings.vue — 设置 · 阅读器（对齐管理面板的页面逻辑：页头 + 保存
  反馈 + 图标行 + 偏好分组卡片）.

  选项值与枚举对齐 `src/components/reader/PageMode.vue`
  （READING_DIRECTION_LTR/RTL/VERTICAL、auto/single/dual/scroll）。
  Wave-1 A 组（1c）新增「交互/双页/性能」三组：backgroundColor、
  tapZoneScheme、keyboardPaging、pageTransition、zoomStep、maxZoom、
  dualPageGap、splitWidePages、preloadCount（ReaderView 消费接线见 P2）。
  所有变更通过 preferencesStore.updateReader 合并到 reader 偏好并由 store
  防抖持久化；保存成功后页头闪现「已保存」。
-->
<template>
  <div class="reader-settings">
    <div class="reader-settings__column">
      <header class="reader-settings__header">
        <h1 class="reader-settings__title">阅读器</h1>
        <Transition name="saved">
          <span v-if="savedFlash" class="reader-settings__saved" role="status">已保存</span>
        </Transition>
      </header>

      <div v-if="preferencesStore.loading" class="reader-settings__loading">加载中…</div>

      <template v-else-if="reader">
        <!-- ═══ 翻页 ═══════════════════════════════════════════════════ -->
        <section>
          <SectionHeader title="翻页" />
          <PrefCard>
            <PrefRow icon="mobile-hand-left" title="阅读方向" :summary="directionLabel">
              <AppSegmented
                :model-value="reader.readingDirection"
                :options="DIRECTION_OPTIONS"
                aria-label="阅读方向"
                @update:model-value="(v) => updateReader({ readingDirection: v })"
              />
            </PrefRow>
            <PrefRow icon="book-open-primary" title="翻页模式" :summary="pageModeLabel">
              <AppSelect
                :model-value="reader.pageMode"
                :options="PAGE_MODE_OPTIONS"
                @update:model-value="(v) => onSelectValueChange('pageMode', v)"
              />
            </PrefRow>
            <PrefRow icon="info-outline-dark" title="首页作为封面" summary="将第一页作为画廊封面使用">
              <AppSwitch
                :model-value="reader.firstPageCover"
                aria-label="首页作为封面"
                @update:model-value="() => toggleSwitch('firstPageCover')"
              />
            </PrefRow>
            <PrefRow icon="magnify-dark" title="页面缩放" :summary="pageScalingLabel">
              <AppSelect
                :model-value="reader.pageScaling"
                :options="PAGE_SCALING_OPTIONS"
                @update:model-value="(v) => onSelectValueChange('pageScaling', v)"
              />
            </PrefRow>
            <PrefRow icon="go-to-dark" title="起始位置" :summary="startPositionLabel">
              <AppSelect
                :model-value="reader.startPosition"
                :options="START_POSITION_OPTIONS"
                @update:model-value="(v) => onSelectValueChange('startPosition', v)"
              />
            </PrefRow>
            <PrefRow icon="play-dark" title="自动播放间隔" summary="自动播放时每页停留秒数">
              <div class="stepper">
                <button
                  type="button"
                  class="stepper__btn"
                  aria-label="减少间隔"
                  :disabled="reader.autoPlayIntervalSec <= 1"
                  @click="bumpInterval(-1)"
                >
                  −
                </button>
                <span class="stepper__value">{{ reader.autoPlayIntervalSec }}s</span>
                <button
                  type="button"
                  class="stepper__btn"
                  aria-label="增加间隔"
                  :disabled="reader.autoPlayIntervalSec >= 15"
                  @click="bumpInterval(1)"
                >
                  +
                </button>
              </div>
            </PrefRow>
          </PrefCard>
        </section>

        <!-- ═══ 显示 ═══════════════════════════════════════════════════ -->
        <section>
          <SectionHeader title="显示" />
          <PrefCard>
            <PrefRow icon="chart-accent" title="显示进度" summary="在阅读器中显示当前页码与进度">
              <AppSwitch
                :model-value="reader.showProgress"
                aria-label="显示进度"
                @update:model-value="() => toggleSwitch('showProgress')"
              />
            </PrefRow>
            <PrefRow icon="slider-bubble" title="显示页间隔" summary="在进度条上显示页与页之间的间隔">
              <AppSwitch
                :model-value="reader.showPageInterval"
                aria-label="显示页间隔"
                @update:model-value="() => toggleSwitch('showPageInterval')"
              />
            </PrefRow>
            <PrefRow icon="refresh-dark" title="全屏阅读" summary="进入阅读器时自动隐藏界面全屏显示">
              <AppSwitch
                :model-value="reader.fullscreen"
                aria-label="全屏阅读"
                @update:model-value="() => toggleSwitch('fullscreen')"
              />
            </PrefRow>
            <PrefRow icon="settings-dark" title="亮度" summary="0 表示跟随系统亮度">
              <div class="slider-wrap">
                <input
                  type="range"
                  class="slider"
                  min="0"
                  max="100"
                  step="1"
                  :value="reader.brightness"
                  aria-label="亮度"
                  :style="{ '--brightness-fill': `${reader.brightness}%` }"
                  @input="onBrightnessInput"
                />
                <span class="slider-wrap__value">
                  {{ reader.brightness === 0 ? '系统' : `${reader.brightness}%` }}
                </span>
              </div>
            </PrefRow>
          </PrefCard>
        </section>

        <!-- ═══ 交互（Wave-1 A 组） ══════════════════════════════════ -->
        <section>
          <SectionHeader title="交互" />
          <PrefCard>
            <PrefRow icon="settings-black" title="背景颜色" summary="阅读器画布的背景色">
              <AppSelect
                :model-value="reader.backgroundColor"
                :options="BACKGROUND_OPTIONS"
                @update:model-value="(v) => onSelectValueChange('backgroundColor', v)"
              />
            </PrefRow>
            <PrefRow icon="mobile-hand-left" title="点击区域" summary="点按屏幕翻页的区域方案">
              <AppSelect
                :model-value="reader.tapZoneScheme"
                :options="TAP_ZONE_OPTIONS"
                @update:model-value="(v) => onSelectValueChange('tapZoneScheme', v)"
              />
            </PrefRow>
            <PrefRow icon="go-to-dark" title="键盘翻页" summary="使用方向键 / PageUp / PageDown 翻页">
              <AppSwitch
                :model-value="reader.keyboardPaging"
                aria-label="键盘翻页"
                @update:model-value="() => toggleSwitch('keyboardPaging')"
              />
            </PrefRow>
            <PrefRow icon="play-dark" title="翻页过渡" summary="翻页时页面切换的动画效果">
              <AppSelect
                :model-value="reader.pageTransition"
                :options="PAGE_TRANSITION_OPTIONS"
                @update:model-value="(v) => onSelectValueChange('pageTransition', v)"
              />
            </PrefRow>
            <PrefRow icon="magnify-dark" title="缩放步进" summary="每次缩放的倍数，需大于 1">
              <label class="num-field">
                <input
                  type="number"
                  min="1.1"
                  max="10"
                  step="0.1"
                  :value="reader.zoomStep"
                  aria-label="缩放步进"
                  @change="onReaderNumberChange('zoomStep', $event)"
                />
              </label>
            </PrefRow>
            <PrefRow icon="magnify" title="最大缩放" summary="允许的最大缩放倍数">
              <label class="num-field">
                <input
                  type="number"
                  min="1"
                  max="50"
                  step="0.5"
                  :value="reader.maxZoom"
                  aria-label="最大缩放"
                  @change="onReaderNumberChange('maxZoom', $event)"
                />
              </label>
            </PrefRow>
          </PrefCard>
        </section>

        <!-- ═══ 双页（Wave-1 A 组） ══════════════════════════════════ -->
        <section>
          <SectionHeader title="双页" />
          <PrefCard>
            <PrefRow icon="reorder" title="双页间距" summary="双页模式下两页之间的间隔（像素）">
              <label class="num-field">
                <input
                  type="number"
                  min="0"
                  max="100"
                  step="1"
                  :value="reader.dualPageGap"
                  aria-label="双页间距"
                  @change="onReaderNumberChange('dualPageGap', $event)"
                />
              </label>
            </PrefRow>
            <PrefRow icon="book-open" title="拆分宽页" summary="将宽幅页面拆分为两页显示">
              <AppSwitch
                :model-value="reader.splitWidePages"
                aria-label="拆分宽页"
                @update:model-value="() => toggleSwitch('splitWidePages')"
              />
            </PrefRow>
          </PrefCard>
        </section>

        <!-- ═══ 性能（Wave-1 A 组） ══════════════════════════════════ -->
        <section>
          <SectionHeader title="性能" />
          <PrefCard>
            <PrefRow icon="download-dark" title="预加载页数" summary="提前加载当前页之后的页数，0 表示关闭">
              <label class="num-field">
                <input
                  type="number"
                  min="0"
                  max="20"
                  step="1"
                  :value="reader.preloadCount"
                  aria-label="预加载页数"
                  @change="onReaderNumberChange('preloadCount', $event)"
                />
              </label>
            </PrefRow>
          </PrefCard>
        </section>
      </template>
    </div>

    <!-- Snackbar. -->
    <Transition name="snack">
      <div v-if="snack" class="reader-settings__snackbar" role="status">{{ snack }}</div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { ReaderPreferences } from '@/api/preferences'
import { usePreferencesStore } from '@/stores/preferences'
import { AppSelect, AppSegmented, AppSwitch, PrefCard, PrefRow, SectionHeader } from '@/components/form'

const preferencesStore = usePreferencesStore()

/* ------------------------------ option lists ----------------------------- */

const DIRECTION_OPTIONS: Array<{ value: string; label: string }> = [
  { value: 'ltr', label: '左到右' },
  { value: 'rtl', label: '右到左' },
  { value: 'vertical', label: '纵向' },
]

const PAGE_MODE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: 'auto', label: '自动' },
  { value: 'single', label: '单页' },
  { value: 'dual', label: '双页' },
  { value: 'scroll', label: '滚动' },
]

const PAGE_SCALING_OPTIONS: Array<{ value: string; label: string }> = [
  { value: 'fit', label: '适应' },
  { value: 'width', label: '宽度' },
  { value: 'height', label: '高度' },
  { value: 'original', label: '原始' },
]

// UX-03: keep a label for the backend default snake_case value too.
const START_POSITION_OPTIONS: Array<{ value: string; label: string }> = [
  { value: 'top-right', label: '右上' },
  { value: 'top_right', label: '右上' },
  { value: 'top-left', label: '左上' },
  { value: 'bottom-right', label: '右下' },
  { value: 'bottom-left', label: '左下' },
]

/* --------------------------- Wave-1 A 组选项 ---------------------------- */

const BACKGROUND_OPTIONS: Array<{ value: string; label: string }> = [
  { value: 'black', label: '黑色' },
  { value: 'gray', label: '灰色' },
  { value: 'white', label: '白色' },
]

const TAP_ZONE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: 'threeZone', label: '三分区' },
  { value: 'edgeOnly', label: '仅边缘' },
  { value: 'disabled', label: '禁用' },
]

const PAGE_TRANSITION_OPTIONS: Array<{ value: string; label: string }> = [
  { value: 'slide', label: '滑动' },
  { value: 'fade', label: '淡入淡出' },
  { value: 'none', label: '无' },
]

/** 数字键的 clamp 范围（与后端 @field 校验一致，输入侧先 clamp） */
const READER_NUMBER_BOUNDS: Partial<
  Record<keyof ReaderPreferences, { min: number; max: number; fallback: number; decimals?: number }>
> = {
  zoomStep: { min: 1.1, max: 10, fallback: 1.5, decimals: 1 },
  maxZoom: { min: 1, max: 50, fallback: 5, decimals: 1 },
  dualPageGap: { min: 0, max: 100, fallback: 8 },
  preloadCount: { min: 0, max: 20, fallback: 2 },
}

const START_POSITION_LABELS: Readonly<Record<string, string>> = {
  'top-right': '从右上角开始',
  'top_right': '从右上角开始',
  'top-left': '从左上角开始',
  'bottom-right': '从右下角开始',
  'bottom-left': '从左下角开始',
}

const PAGE_SCALING_LABELS: Readonly<Record<string, string>> = {
  fit: '适应屏幕',
  width: '适应宽度',
  height: '适应高度',
  original: '原始大小',
}

/* -------------------------------- computed -------------------------------- */

const reader = computed<ReaderPreferences | undefined>(() => preferencesStore.prefs?.reader)

const directionLabel = computed<string>(
  () => DIRECTION_OPTIONS.find((option) => option.value === reader.value?.readingDirection)?.label ?? '',
)

const pageModeLabel = computed<string>(
  () => PAGE_MODE_OPTIONS.find((option) => option.value === reader.value?.pageMode)?.label ?? '',
)

const pageScalingLabel = computed<string>(
  () => PAGE_SCALING_LABELS[reader.value?.pageScaling ?? ''] ?? '适应屏幕',
)

const startPositionLabel = computed<string>(
  () => START_POSITION_LABELS[reader.value?.startPosition ?? ''] ?? '',
)

/* -------------------------------- handlers -------------------------------- */

function updateReader(patch: Partial<ReaderPreferences>): void {
  preferencesStore.updateReader(patch)
}

/** 裸值版本（AppSelect 的 @update:model-value），替代原先从 Event 取值的 onSelectChange。 */
function onSelectValueChange(key: keyof ReaderPreferences, value: string | number): void {
  updateReader({ [key]: String(value) })
}

function toggleSwitch(
  key:
    | 'firstPageCover'
    | 'showProgress'
    | 'showPageInterval'
    | 'fullscreen'
    | 'keyboardPaging'
    | 'splitWidePages',
): void {
  const current = reader.value?.[key]
  if (typeof current !== 'boolean') return
  updateReader({ [key]: !current })
}

/** 数字输入 @change：按 READER_NUMBER_BOUNDS clamp 后写回。 */
function onReaderNumberChange(key: keyof ReaderPreferences, event: Event): void {
  const bounds = READER_NUMBER_BOUNDS[key]
  if (!bounds) return
  const target = event.target as HTMLInputElement
  const parsed = Number.parseFloat(target.value)
  const clamped = Number.isNaN(parsed) ? bounds.fallback : Math.min(bounds.max, Math.max(bounds.min, parsed))
  const value = bounds.decimals ? Number(clamped.toFixed(bounds.decimals)) : Math.round(clamped)
  target.value = String(value)
  updateReader({ [key]: value })
}

function bumpInterval(delta: number): void {
  const current = reader.value?.autoPlayIntervalSec ?? 5
  updateReader({ autoPlayIntervalSec: Math.min(15, Math.max(1, current + delta)) })
}

function onBrightnessInput(event: Event): void {
  updateReader({ brightness: Number((event.target as HTMLInputElement).value) })
}

/* ------------------------------- save feedback ---------------------------- */

const savedFlash = ref(false)
let savedTimer: number | undefined

watch(
  () => preferencesStore.saveSeq,
  () => {
    savedFlash.value = true
    if (savedTimer) window.clearTimeout(savedTimer)
    savedTimer = window.setTimeout(() => {
      savedFlash.value = false
    }, 1600)
  },
)

const snack = ref('')
let snackTimer: number | undefined

function showSnack(message: string): void {
  snack.value = message
  if (snackTimer) window.clearTimeout(snackTimer)
  snackTimer = window.setTimeout(() => {
    snack.value = ''
  }, 2600)
}

watch(
  () => preferencesStore.saveError,
  (error) => {
    if (error) showSnack('无法在服务器上保存设置')
  },
)

/* ---------------------------------- boot --------------------------------- */

onMounted(async () => {
  await preferencesStore.load()
  if (preferencesStore.loadError) showSnack('无法加载设置')
})

onBeforeUnmount(() => {
  if (savedTimer) window.clearTimeout(savedTimer)
  if (snackTimer) window.clearTimeout(snackTimer)
})
</script>

<style scoped>
.reader-settings {
  min-height: 100%;
  background: var(--color-bg);
}

.reader-settings__column {
  max-width: 760px;
  margin: 0 auto;
  padding: 4px var(--keyline-margin) calc(56px + var(--safe-area-bottom));
}

.reader-settings__loading {
  padding: 32px 0;
  text-align: center;
  font-size: var(--text-small);
  color: var(--text-color-secondary);
}

/* ---------------------------------- header --------------------------------- */

.reader-settings__header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 4px 4px;
}

.reader-settings__title {
  margin: 0;
  font-size: clamp(17px, 20px, 24px);
  font-weight: 600;
  letter-spacing: 0.01em;
  color: var(--text-color-primary);
}

.reader-settings__saved {
  margin-left: auto;
  padding: 4px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-primary) 14%, transparent);
  color: var(--color-primary-text);
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

/* ---------------------------------- stepper ------------------------------- */

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
  color: var(--color-primary-text);
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

/* ---------------------------------- slider -------------------------------- */

.slider-wrap {
  flex: 1 1 160px;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.slider {
  --brightness-fill: 0%;
  -webkit-appearance: none;
  appearance: none;
  flex: 1;
  height: 32px;
  margin: 0;
  background: transparent;
  cursor: pointer;
}

.slider:focus {
  outline: none;
}

.slider:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
  border-radius: 2px;
}

.slider::-webkit-slider-runnable-track {
  height: 2px;
  border-radius: 1px;
  background: linear-gradient(
    to right,
    var(--color-primary) 0,
    var(--color-primary) var(--brightness-fill),
    var(--widget-color) var(--brightness-fill),
    var(--widget-color) 100%
  );
}

.slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 14px;
  height: 14px;
  margin-top: -6px;
  border: none;
  border-radius: 50%;
  background: var(--color-primary);
  transition: transform 120ms var(--ease-decelerate-quart);
}

.slider:hover::-webkit-slider-thumb {
  transform: scale(1.2);
}

.slider::-moz-range-track {
  height: 2px;
  border-radius: 1px;
  background: var(--widget-color);
}

.slider::-moz-range-progress {
  height: 2px;
  border-radius: 1px;
  background: var(--color-primary);
}

.slider::-moz-range-thumb {
  width: 14px;
  height: 14px;
  border: none;
  border-radius: 50%;
  background: var(--color-primary);
}

.slider-wrap__value {
  flex: 0 0 48px;
  color: var(--text-color-secondary);
  font-size: clamp(12px, 13px, 14px);
  font-variant-numeric: tabular-nums;
  text-align: right;
}

/* ------------------------------- number input ----------------------------- */

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

/* --------------------------------- snackbar -------------------------------- */

.reader-settings__snackbar {
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
