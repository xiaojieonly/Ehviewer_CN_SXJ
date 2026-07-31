<!--
  ReaderSettings.vue — 阅读器偏好设置（/settings/reader）。

  结构沿用原设置页的 preference 样式约定（pref-group / pref-card /
  pref / segment / switch / stepper），选项值与枚举对齐
  `src/components/reader/PageMode.vue`（READING_DIRECTION_LTR/RTL/VERTICAL、
  auto/single/dual/scroll）。所有变更通过 preferencesStore.updateReader
  合并到 reader 偏好并由 store 防抖持久化。
-->
<template>
  <div v-if="reader" class="settings-column">
    <!-- ═══ 翻页 ═══════════════════════════════════════════════════════ -->
    <section class="pref-group">
      <h2 class="pref-group__title">翻页</h2>
      <div class="pref-card">
        <div class="pref">
          <AppIcon name="mobile-hand-left" class="pref__icon" />
          <div class="pref__text">
            <span class="pref__title">阅读方向</span>
            <span class="pref__summary">{{ directionLabel }}</span>
          </div>
          <div class="segment" role="radiogroup" aria-label="阅读方向">
            <button
              v-for="option in DIRECTION_OPTIONS"
              :key="option.value"
              type="button"
              class="segment__btn"
              role="radio"
              :aria-checked="reader.readingDirection === option.value"
              @click="updateReader({ readingDirection: option.value })"
            >
              {{ option.label }}
            </button>
          </div>
        </div>
        <div class="pref-divider" />
        <div class="pref">
          <AppIcon name="book-open-primary" class="pref__icon" />
          <div class="pref__text">
            <span class="pref__title">翻页模式</span>
            <span class="pref__summary">{{ pageModeLabel }}</span>
          </div>
          <select
            class="select"
            aria-label="翻页模式"
            :value="reader.pageMode"
            @change="onSelectChange($event, 'pageMode')"
          >
            <option v-for="option in PAGE_MODE_OPTIONS" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </div>
        <div class="pref-divider" />
        <div class="pref">
          <AppIcon name="info-outline-dark" class="pref__icon" />
          <div class="pref__text">
            <span class="pref__title">首页作为封面</span>
            <span class="pref__summary">将第一页作为画廊封面使用</span>
          </div>
          <button
            type="button"
            class="switch"
            role="switch"
            :aria-checked="reader.firstPageCover"
            aria-label="首页作为封面"
            @click="toggleSwitch('firstPageCover')"
          >
            <span class="switch__thumb" />
          </button>
        </div>
        <div class="pref-divider" />
        <div class="pref">
          <AppIcon name="reorder" class="pref__icon" />
          <div class="pref__text">
            <span class="pref__title">页面缩放</span>
            <span class="pref__summary">{{ pageScalingLabel }}</span>
          </div>
          <select
            class="select"
            aria-label="页面缩放"
            :value="reader.pageScaling"
            @change="onSelectChange($event, 'pageScaling')"
          >
            <option v-for="option in PAGE_SCALING_OPTIONS" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </div>
        <div class="pref-divider" />
        <div class="pref">
          <AppIcon name="go-to-dark" class="pref__icon" />
          <div class="pref__text">
            <span class="pref__title">起始位置</span>
            <span class="pref__summary">{{ startPositionLabel }}</span>
          </div>
          <select
            class="select"
            aria-label="起始位置"
            :value="reader.startPosition"
            @change="onSelectChange($event, 'startPosition')"
          >
            <option v-for="option in START_POSITION_OPTIONS" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </div>
        <div class="pref-divider" />
        <div class="pref">
          <AppIcon name="play-dark" class="pref__icon" />
          <div class="pref__text">
            <span class="pref__title">自动播放间隔</span>
            <span class="pref__summary">自动播放时每页停留秒数</span>
          </div>
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
        </div>
      </div>
    </section>

    <!-- ═══ 显示 ═══════════════════════════════════════════════════════ -->
    <section class="pref-group">
      <h2 class="pref-group__title">显示</h2>
      <div class="pref-card">
        <div class="pref">
          <AppIcon name="chart-accent" class="pref__icon" />
          <div class="pref__text">
            <span class="pref__title">显示进度</span>
            <span class="pref__summary">在阅读器中显示当前页码与进度</span>
          </div>
          <button
            type="button"
            class="switch"
            role="switch"
            :aria-checked="reader.showProgress"
            aria-label="显示进度"
            @click="toggleSwitch('showProgress')"
          >
            <span class="switch__thumb" />
          </button>
        </div>
        <div class="pref-divider" />
        <div class="pref">
          <AppIcon name="slider-bubble" class="pref__icon" />
          <div class="pref__text">
            <span class="pref__title">显示页间隔</span>
            <span class="pref__summary">在进度条上显示页与页之间的间隔</span>
          </div>
          <button
            type="button"
            class="switch"
            role="switch"
            :aria-checked="reader.showPageInterval"
            aria-label="显示页间隔"
            @click="toggleSwitch('showPageInterval')"
          >
            <span class="switch__thumb" />
          </button>
        </div>
        <div class="pref-divider" />
        <div class="pref">
          <AppIcon name="refresh-dark" class="pref__icon" />
          <div class="pref__text">
            <span class="pref__title">全屏阅读</span>
            <span class="pref__summary">进入阅读器时自动隐藏界面全屏显示</span>
          </div>
          <button
            type="button"
            class="switch"
            role="switch"
            :aria-checked="reader.fullscreen"
            aria-label="全屏阅读"
            @click="toggleSwitch('fullscreen')"
          >
            <span class="switch__thumb" />
          </button>
        </div>
        <div class="pref-divider" />
        <div class="pref">
          <AppIcon name="settings-dark" class="pref__icon" />
          <div class="pref__text">
            <span class="pref__title">亮度</span>
            <span class="pref__summary">0 表示跟随系统亮度</span>
          </div>
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
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import type { ReaderPreferences } from '@/api/preferences'
import { usePreferencesStore } from '@/stores/preferences'
import AppIcon from '@/components/atoms/AppIcon.vue'

const preferencesStore = usePreferencesStore()

/* ------------------------------ option lists ----------------------------- */

const DIRECTION_OPTIONS: ReadonlyArray<{ value: string; label: string }> = [
  { value: 'ltr', label: '左到右' },
  { value: 'rtl', label: '右到左' },
  { value: 'vertical', label: '纵向' },
]

const PAGE_MODE_OPTIONS: ReadonlyArray<{ value: string; label: string }> = [
  { value: 'auto', label: '自动' },
  { value: 'single', label: '单页' },
  { value: 'dual', label: '双页' },
  { value: 'scroll', label: '滚动' },
]

const PAGE_SCALING_OPTIONS: ReadonlyArray<{ value: string; label: string }> = [
  { value: 'fit', label: '适应' },
  { value: 'width', label: '宽度' },
  { value: 'height', label: '高度' },
  { value: 'original', label: '原始' },
]

const START_POSITION_OPTIONS: ReadonlyArray<{ value: string; label: string }> = [
  { value: 'top-right', label: '右上' },
  { value: 'top-left', label: '左上' },
  { value: 'bottom-right', label: '右下' },
  { value: 'bottom-left', label: '左下' },
]

const START_POSITION_LABELS: Readonly<Record<string, string>> = {
  'top-right': '从右上角开始',
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

function onSelectChange(event: Event, key: keyof ReaderPreferences): void {
  updateReader({ [key]: (event.target as HTMLSelectElement).value })
}

function toggleSwitch(key: 'firstPageCover' | 'showProgress' | 'showPageInterval' | 'fullscreen'): void {
  const current = reader.value?.[key]
  if (typeof current !== 'boolean') return
  updateReader({ [key]: !current })
}

function bumpInterval(delta: number): void {
  const current = reader.value?.autoPlayIntervalSec ?? 5
  updateReader({ autoPlayIntervalSec: Math.min(15, Math.max(1, current + delta)) })
}

function onBrightnessInput(event: Event): void {
  updateReader({ brightness: Number((event.target as HTMLInputElement).value) })
}

/* ---------------------------------- boot ---------------------------------- */

onMounted(() => {
  void preferencesStore.load()
})
</script>

<style scoped>
/* --------------------------------- column --------------------------------- */

.settings-column {
  max-width: 760px;
  margin: 0 auto;
  padding: 4px var(--keyline-margin) calc(56px + var(--safe-area-bottom));
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

/* ------------------------------ segment control --------------------------- */

.segment {
  display: inline-flex;
  gap: 2px;
  padding: 2px;
  border-radius: 999px;
  background: var(--color-surface);
}

.segment__btn {
  padding: 6px 14px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: var(--text-color-secondary);
  font-size: clamp(11px, 12px, 14px);
  white-space: nowrap;
  cursor: pointer;
  transition:
    background-color 150ms var(--ease-decelerate-quart),
    color 150ms var(--ease-decelerate-quart);
}

.segment__btn:hover {
  color: var(--text-color-primary);
}

.segment__btn[aria-checked='true'] {
  background: var(--content-color-theme-primary);
  color: var(--color-white);
  font-weight: 700;
  box-shadow: 0 1px 2px var(--shadow-color);
}

/* ---------------------------------- select -------------------------------- */

.select {
  flex: 0 0 auto;
  max-width: 140px;
  padding: 7px 32px 7px 12px;
  border: 1px solid var(--color-divider);
  border-radius: var(--card-radius);
  background:
    var(--color-surface)
    url("data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='%23888' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E")
    no-repeat right 10px center;
  color: var(--text-color-primary);
  font-size: clamp(12px, 13px, 14px);
  appearance: none;
  -webkit-appearance: none;
  cursor: pointer;
  outline: none;
  transition: border-color 150ms var(--ease-decelerate-quart);
}

.select:focus {
  border-color: var(--color-primary);
}

/* ---------------------------------- switch -------------------------------- */

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
</style>
