<!--
  GeneralSettings.vue — 设置 · 通用（对齐管理面板的页面逻辑：页头 + 保存反馈
  + 图标行 + 偏好分组卡片）.

  所有变更通过 preferencesStore（防抖 PUT /preferences）持久化；保存成功后
  页头闪现「已保存」，失败时以 snackbar 提示。外观行额外驱动主题 store，
  让 UI 即时换肤。
-->
<template>
  <div class="general-settings">
    <div class="general-settings__column">
      <header class="general-settings__header">
        <h1 class="general-settings__title">通用</h1>
        <Transition name="saved">
          <span v-if="savedFlash" class="general-settings__saved" role="status">已保存</span>
        </Transition>
      </header>

      <div v-if="preferencesStore.loading" class="general-settings__loading">加载中…</div>

      <template v-else-if="prefs">
        <!-- ═══ 通用 ═══════════════════════════════════════════════════ -->
        <section class="pref-group">
          <h2 class="pref-group__title">通用</h2>
          <div class="pref-card">
            <div class="pref">
              <AppIcon name="settings-dark" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">外观</span>
                <span class="pref__summary">当前：{{ themeLabel }}</span>
              </div>
              <div class="segment" role="radiogroup" aria-label="外观">
                <button
                  v-for="option in THEME_OPTIONS"
                  :key="option.value"
                  type="button"
                  class="segment__btn"
                  role="radio"
                  :aria-checked="themeStore.currentTheme === option.value"
                  @click="setTheme(option.value)"
                >
                  {{ option.label }}
                </button>
              </div>
            </div>
            <div class="pref-divider" />
            <div class="pref">
              <AppIcon name="refresh-dark" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">跟随系统主题</span>
                <span class="pref__summary">根据系统深浅色自动切换主题</span>
              </div>
              <button
                type="button"
                class="switch"
                role="switch"
                :aria-checked="prefs.general.themeAutoSwitch"
                aria-label="跟随系统主题"
                @click="toggleGeneral('themeAutoSwitch')"
              >
                <span class="switch__thumb" />
              </button>
            </div>
            <div class="pref-divider" />
            <div class="pref">
              <AppIcon name="homepage-black" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">启动页</span>
                <span class="pref__summary">打开应用时显示的页面</span>
              </div>
              <label class="select">
                <span class="select__label">启动页</span>
                <select
                  :value="prefs.general.launchPage"
                  aria-label="启动页"
                  @change="updateGeneral('launchPage', $event)"
                >
                  <option v-for="option in LAUNCH_PAGE_OPTIONS" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
              </label>
            </div>
            <div class="pref-divider" />
            <div class="pref">
              <AppIcon name="reorder" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">列表模式</span>
                <span class="pref__summary">画廊列表的默认布局</span>
              </div>
              <label class="select">
                <span class="select__label">列表模式</span>
                <select
                  :value="prefs.general.listMode"
                  aria-label="列表模式"
                  @change="updateGeneral('listMode', $event)"
                >
                  <option v-for="option in LIST_MODE_OPTIONS" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
              </label>
            </div>
            <div class="pref-divider" />
            <div class="pref">
              <AppIcon name="check-all-dark" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">显示阅读进度</span>
                <span class="pref__summary">在画廊卡片上显示阅读进度</span>
              </div>
              <button
                type="button"
                class="switch"
                role="switch"
                :aria-checked="prefs.general.showReadProgress"
                aria-label="显示阅读进度"
                @click="toggleGeneral('showReadProgress')"
              >
                <span class="switch__thumb" />
              </button>
            </div>
          </div>
        </section>

        <!-- ═══ 布局 ═══════════════════════════════════════════════════ -->
        <section class="pref-group">
          <h2 class="pref-group__title">布局</h2>
          <div class="pref-card">
            <div class="pref">
              <AppIcon name="go-to-dark" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">详情栏宽度</span>
                <span class="pref__summary">画廊详情栏的显示宽度</span>
              </div>
              <label class="select">
                <span class="select__label">详情栏宽度</span>
                <select
                  :value="prefs.general.detailSize"
                  aria-label="详情栏宽度"
                  @change="updateGeneral('detailSize', $event)"
                >
                  <option v-for="option in DETAIL_SIZE_OPTIONS" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
              </label>
            </div>
            <div class="pref-divider" />
            <div class="pref">
              <AppIcon name="magnify-dark" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">缩略图大小</span>
                <span class="pref__summary">画廊缩略图展示尺寸</span>
              </div>
              <label class="select">
                <span class="select__label">缩略图大小</span>
                <select
                  :value="prefs.general.thumbSize"
                  aria-label="缩略图大小"
                  @change="updateGeneral('thumbSize', $event)"
                >
                  <option v-for="option in THUMB_SIZE_OPTIONS" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
              </label>
            </div>
            <div class="pref-divider" />
            <div class="pref">
              <AppIcon name="history-black" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">历史记录数</span>
                <span class="pref__summary">保留的历史浏览记录条数</span>
              </div>
              <label class="num-field">
                <input
                  type="number"
                  min="1"
                  max="100"
                  :value="prefs.general.historyInfoSize"
                  aria-label="历史记录数"
                  @change="updateGeneral('historyInfoSize', $event)"
                />
              </label>
            </div>
          </div>
        </section>

        <!-- ═══ 画廊 ══════════════════════════════════════════════════ -->
        <section class="pref-group">
          <h2 class="pref-group__title">画廊</h2>
          <div class="pref-card">
            <div class="pref">
              <AppIcon name="book-open-primary" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">显示日文标题</span>
                <span class="pref__summary">优先显示画廊的日文标题</span>
              </div>
              <button
                type="button"
                class="switch"
                role="switch"
                :aria-checked="prefs.general.showJpnTitle"
                aria-label="显示日文标题"
                @click="toggleGeneral('showJpnTitle')"
              >
                <span class="switch__thumb" />
              </button>
            </div>
            <div class="pref-divider" />
            <div class="pref">
              <AppIcon name="reorder" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">显示画廊页数</span>
                <span class="pref__summary">在画廊信息中显示总页数</span>
              </div>
              <button
                type="button"
                class="switch"
                role="switch"
                :aria-checked="prefs.general.showGalleryPages"
                aria-label="显示画廊页数"
                @click="toggleGeneral('showGalleryPages')"
              >
                <span class="switch__thumb" />
              </button>
            </div>
            <div class="pref-divider" />
            <div class="pref">
              <AppIcon name="similar-primary" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">显示标签翻译</span>
                <span class="pref__summary">将标签翻译为本地语言</span>
              </div>
              <button
                type="button"
                class="switch"
                role="switch"
                :aria-checked="prefs.general.showTagTranslations"
                aria-label="显示标签翻译"
                @click="toggleGeneral('showTagTranslations')"
              >
                <span class="switch__thumb" />
              </button>
            </div>
            <div class="pref-divider" />
            <div class="pref">
              <AppIcon name="reply-dark" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">显示评论</span>
                <span class="pref__summary">在画廊页面显示评论</span>
              </div>
              <button
                type="button"
                class="switch"
                role="switch"
                :aria-checked="prefs.general.showGalleryComment"
                aria-label="显示评论"
                @click="toggleGeneral('showGalleryComment')"
              >
                <span class="switch__thumb" />
              </button>
            </div>
            <div class="pref-divider" />
            <div class="pref">
              <AppIcon name="star" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">显示评分</span>
                <span class="pref__summary">在画廊信息中显示评分</span>
              </div>
              <button
                type="button"
                class="switch"
                role="switch"
                :aria-checked="prefs.general.showGalleryRating"
                aria-label="显示评分"
                @click="toggleGeneral('showGalleryRating')"
              >
                <span class="switch__thumb" />
              </button>
            </div>
            <div class="pref-divider" />
            <div class="pref">
              <AppIcon name="fire-black" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">显示 EH 事件</span>
                <span class="pref__summary">显示 E-Hentai 站点事件横幅</span>
              </div>
              <button
                type="button"
                class="switch"
                role="switch"
                :aria-checked="prefs.general.showEhEvents"
                aria-label="显示 EH 事件"
                @click="toggleGeneral('showEhEvents')"
              >
                <span class="switch__thumb" />
              </button>
            </div>
            <div class="pref-divider" />
            <div class="pref">
              <AppIcon name="chart-accent" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">显示 EH 限额</span>
                <span class="pref__summary">在顶部显示 E-Hentai 配额信息</span>
              </div>
              <button
                type="button"
                class="switch"
                role="switch"
                :aria-checked="prefs.general.showEhLimits"
                aria-label="显示 EH 限额"
                @click="toggleGeneral('showEhLimits')"
              >
                <span class="switch__thumb" />
              </button>
            </div>
          </div>
        </section>
      </template>
    </div>

    <!-- Snackbar. -->
    <Transition name="snack">
      <div v-if="snack" class="general-settings__snackbar" role="status">{{ snack }}</div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { GeneralPreferences } from '@/api/preferences'
import { usePreferencesStore } from '@/stores/preferences'
import { useThemeStore, type Theme } from '@/stores/theme'
import AppIcon from '@/components/atoms/AppIcon.vue'

const preferencesStore = usePreferencesStore()
const themeStore = useThemeStore()

const prefs = computed(() => preferencesStore.prefs)

/* ------------------------------ option lists ----------------------------- */

const THEME_OPTIONS: ReadonlyArray<{ value: Theme; label: string }> = [
  { value: 'light', label: '亮色' },
  { value: 'dark', label: '暗色' },
  { value: 'black', label: '纯黑' },
]

const LAUNCH_PAGE_OPTIONS = [
  { value: 'home', label: '首页' },
  { value: 'subscription', label: '订阅' },
  { value: 'hot', label: '热门' },
]

const LIST_MODE_OPTIONS = [
  { value: 'list', label: '列表' },
  { value: 'grid', label: '网格' },
]

const DETAIL_SIZE_OPTIONS = [
  { value: 'long', label: '长' },
  { value: 'short', label: '短' },
]

const THUMB_SIZE_OPTIONS = [
  { value: 'large', label: '大' },
  { value: 'middle', label: '中' },
  { value: 'small', label: '小' },
]

const themeLabel = computed<string>(
  () => THEME_OPTIONS.find((option) => option.value === themeStore.currentTheme)?.label ?? '亮色',
)

/* -------------------------------- handlers ------------------------------- */

function setTheme(theme: Theme): void {
  themeStore.setTheme(theme)
  preferencesStore.updateGeneral({ theme })
}

function updateGeneral(
  key: keyof GeneralPreferences,
  event: Event,
): void {
  const target = event.target as HTMLInputElement | HTMLSelectElement
  const raw = target.value
  const value = key === 'historyInfoSize' ? clampHistory(raw) : raw
  target.value = String(value)
  preferencesStore.updateGeneral({ [key]: value })
}

function toggleGeneral(key: keyof GeneralPreferences): void {
  if (!prefs.value) return
  preferencesStore.updateGeneral({ [key]: !prefs.value.general[key] })
}

function clampHistory(raw: string): number {
  const parsed = Number.parseInt(raw, 10)
  if (Number.isNaN(parsed)) return 10
  return Math.min(100, Math.max(1, parsed))
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
</script>

<style scoped>
.general-settings {
  min-height: 100%;
  background: var(--color-bg);
}

.general-settings__column {
  max-width: 760px;
  margin: 0 auto;
  padding: 4px var(--keyline-margin) calc(56px + var(--safe-area-bottom));
}

.general-settings__loading {
  padding: 32px 0;
  text-align: center;
  font-size: var(--text-small);
  color: var(--text-color-secondary);
}

/* ---------------------------------- header --------------------------------- */

.general-settings__header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 4px 4px;
}

.general-settings__title {
  margin: 0;
  font-size: clamp(17px, 20px, 24px);
  font-weight: 600;
  letter-spacing: 0.01em;
  color: var(--text-color-primary);
}

.general-settings__saved {
  margin-left: auto;
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
  min-width: 96px;
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

.general-settings__snackbar {
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
