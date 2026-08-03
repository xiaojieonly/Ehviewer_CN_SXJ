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
        <section>
          <SectionHeader title="通用" />
          <PrefCard>
            <PrefRow icon="settings-dark" title="外观" :summary="`当前：${themeLabel}`">
              <AppSegmented
                :model-value="themeStore.currentTheme"
                :options="THEME_OPTIONS"
                aria-label="外观"
                @update:model-value="(v) => setTheme(v as Theme)"
              />
            </PrefRow>
            <PrefRow
              icon="refresh-dark"
              title="跟随系统主题"
              summary="根据系统深浅色自动切换主题"
            >
              <AppSwitch
                :model-value="prefs.general.themeAutoSwitch"
                aria-label="跟随系统主题"
                @update:model-value="() => toggleGeneral('themeAutoSwitch')"
              />
            </PrefRow>
            <PrefRow icon="homepage-black" title="启动页" summary="打开应用时显示的页面">
              <AppSelect
                :model-value="prefs.general.launchPage"
                :options="LAUNCH_PAGE_OPTIONS"
                @update:model-value="(v) => updateGeneralValue('launchPage', v)"
              />
            </PrefRow>
            <PrefRow icon="check-all-dark" title="显示阅读进度" summary="在画廊卡片上显示阅读进度">
              <AppSwitch
                :model-value="prefs.general.showReadProgress"
                aria-label="显示阅读进度"
                @update:model-value="() => toggleGeneral('showReadProgress')"
              />
            </PrefRow>
          </PrefCard>
        </section>

        <!-- ═══ 浏览（Wave-1 B 组） ═══════════════════════════════════ -->
        <section>
          <SectionHeader title="浏览" />
          <PrefCard>
            <PrefRow icon="reorder" title="列表模式" summary="画廊列表的默认布局">
              <AppSelect
                :model-value="prefs.general.listMode"
                :options="LIST_MODE_OPTIONS"
                @update:model-value="(v) => updateGeneralValue('listMode', v)"
              />
            </PrefRow>
            <PrefRow icon="share-primary" title="显示上传者" summary="在画廊卡片上显示上传者">
              <AppSwitch
                :model-value="prefs.general.showUploader"
                aria-label="显示上传者"
                @update:model-value="() => toggleGeneral('showUploader')"
              />
            </PrefRow>
            <PrefRow icon="history-black" title="显示发布时间" summary="在画廊卡片上显示发布时间">
              <AppSwitch
                :model-value="prefs.general.showPostedTime"
                aria-label="显示发布时间"
                @update:model-value="() => toggleGeneral('showPostedTime')"
              />
            </PrefRow>
            <PrefRow icon="heart-primary" title="默认收藏槽" summary="收藏时默认使用的槽位，-2 到 9">
              <label class="num-field">
                <input
                  type="number"
                  min="-2"
                  max="9"
                  :value="prefs.general.defaultFavoriteSlot"
                  aria-label="默认收藏槽"
                  @change="updateGeneral('defaultFavoriteSlot', $event)"
                />
              </label>
            </PrefRow>
            <PrefRow icon="magnify-dark" title="最近搜索条数" summary="保留的最近搜索记录条数，0 表示关闭">
              <label class="num-field">
                <input
                  type="number"
                  min="0"
                  max="100"
                  :value="prefs.general.recentSearchMax"
                  aria-label="最近搜索条数"
                  @change="updateGeneral('recentSearchMax', $event)"
                />
              </label>
            </PrefRow>
            <PrefRow icon="pencil-dark" title="收藏槽名称" summary="以 | 分隔 10 个槽位名，空项回退默认名称">
              <template #below>
                <AppTextField
                  class="general-settings__slot-names"
                  :model-value="prefs.general.favoriteSlotNames"
                  aria-label="收藏槽名称"
                  placeholder="例如：主用|备用"
                  :maxlength="255"
                  @update:model-value="(v) => updateGeneralValue('favoriteSlotNames', v)"
                />
              </template>
            </PrefRow>
          </PrefCard>
        </section>

        <!-- ═══ 布局 ═══════════════════════════════════════════════════ -->
        <section>
          <SectionHeader title="布局" />
          <PrefCard>
            <PrefRow icon="go-to-dark" title="详情栏宽度" summary="画廊详情栏的显示宽度">
              <AppSelect
                :model-value="prefs.general.detailSize"
                :options="DETAIL_SIZE_OPTIONS"
                @update:model-value="(v) => updateGeneralValue('detailSize', v)"
              />
            </PrefRow>
            <PrefRow icon="magnify-dark" title="缩略图大小" summary="画廊缩略图展示尺寸">
              <AppSelect
                :model-value="prefs.general.thumbSize"
                :options="THUMB_SIZE_OPTIONS"
                @update:model-value="(v) => updateGeneralValue('thumbSize', v)"
              />
            </PrefRow>
            <PrefRow icon="history-black" title="历史记录数" summary="保留的历史浏览记录条数">
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
            </PrefRow>
          </PrefCard>
        </section>

        <!-- ═══ 画廊 ══════════════════════════════════════════════════ -->
        <section>
          <SectionHeader title="画廊" />
          <PrefCard>
            <PrefRow icon="book-open-primary" title="显示日文标题" summary="优先显示画廊的日文标题">
              <AppSwitch
                :model-value="prefs.general.showJpnTitle"
                aria-label="显示日文标题"
                @update:model-value="() => toggleGeneral('showJpnTitle')"
              />
            </PrefRow>
            <PrefRow icon="reorder" title="显示画廊页数" summary="在画廊信息中显示总页数">
              <AppSwitch
                :model-value="prefs.general.showGalleryPages"
                aria-label="显示画廊页数"
                @update:model-value="() => toggleGeneral('showGalleryPages')"
              />
            </PrefRow>
            <PrefRow icon="similar-primary" title="显示标签翻译" summary="将标签翻译为本地语言">
              <AppSwitch
                :model-value="prefs.general.showTagTranslations"
                aria-label="显示标签翻译"
                @update:model-value="() => toggleGeneral('showTagTranslations')"
              />
            </PrefRow>
            <PrefRow icon="reply-dark" title="显示评论" summary="在画廊页面显示评论">
              <AppSwitch
                :model-value="prefs.general.showGalleryComment"
                aria-label="显示评论"
                @update:model-value="() => toggleGeneral('showGalleryComment')"
              />
            </PrefRow>
            <PrefRow icon="star" title="显示评分" summary="在画廊信息中显示评分">
              <AppSwitch
                :model-value="prefs.general.showGalleryRating"
                aria-label="显示评分"
                @update:model-value="() => toggleGeneral('showGalleryRating')"
              />
            </PrefRow>
            <PrefRow icon="fire-black" title="显示 EH 事件" summary="显示站点事件横幅">
              <AppSwitch
                :model-value="prefs.general.showSiteEvents"
                aria-label="显示 EH 事件"
                @update:model-value="() => toggleGeneral('showSiteEvents')"
              />
            </PrefRow>
            <PrefRow icon="chart-accent" title="显示 EH 限额" summary="在顶部显示站点配额信息">
              <AppSwitch
                :model-value="prefs.general.showSiteLimits"
                aria-label="显示 EH 限额"
                @update:model-value="() => toggleGeneral('showSiteLimits')"
              />
            </PrefRow>
          </PrefCard>
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
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { GeneralPreferences } from '@/api/preferences'
import { usePreferencesStore } from '@/stores/preferences'
import { useThemeStore, type Theme } from '@/stores/theme'
import { AppSelect, AppSegmented, AppSwitch, AppTextField, PrefCard, PrefRow, SectionHeader } from '@/components/form'

const preferencesStore = usePreferencesStore()
const themeStore = useThemeStore()

const prefs = computed(() => preferencesStore.prefs)

/* ------------------------------ option lists ----------------------------- */

const THEME_OPTIONS: Array<{ value: Theme; label: string }> = [
  { value: 'light', label: '亮色' },
  { value: 'dark', label: '暗色' },
  { value: 'black', label: '纯黑' },
]

// UX-03: every storable launchPage value must resolve to a visible label —
// web main routes (home/search/favorites/history/downloads) + legacy values
// (subscription/hot) + backend defaults (homepage/whats_hot).
const LAUNCH_PAGE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: 'home', label: '首页' },
  { value: 'homepage', label: '首页' },
  { value: 'search', label: '搜索' },
  { value: 'favorites', label: '收藏' },
  { value: 'history', label: '历史' },
  { value: 'downloads', label: '下载' },
  { value: 'subscription', label: '订阅' },
  { value: 'hot', label: '热门' },
  { value: 'whats_hot', label: '热门' },
]

const LIST_MODE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: 'grid', label: '网格' },
  { value: 'list', label: '列表' },
]

const DETAIL_SIZE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: 'long', label: '长' },
  { value: 'short', label: '短' },
]

const THUMB_SIZE_OPTIONS: Array<{ value: string; label: string }> = [
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
  const value = normalizeValue(key, target.value)
  target.value = String(value)
  preferencesStore.updateGeneral({ [key]: value })
}

/** 裸值版本（AppSelect / AppTextField 的 @update:model-value），clamp 逻辑与 updateGeneral 一致。 */
function updateGeneralValue(key: keyof GeneralPreferences, raw: string | number): void {
  const value = normalizeValue(key, String(raw))
  preferencesStore.updateGeneral({ [key]: value })
}

function toggleGeneral(key: keyof GeneralPreferences): void {
  if (!prefs.value) return
  preferencesStore.updateGeneral({ [key]: !prefs.value.general[key] })
}

/** 数字键按各自范围 clamp，其余键原样透传。 */
function normalizeValue(key: keyof GeneralPreferences, raw: string): string | number {
  switch (key) {
    case 'historyInfoSize':
      return clampInt(raw, 1, 100, 10)
    case 'defaultFavoriteSlot':
      return clampInt(raw, -2, 9, 0)
    case 'recentSearchMax':
      return clampInt(raw, 0, 100, 10)
    default:
      return raw
  }
}

function clampInt(raw: string, min: number, max: number, fallback: number): number {
  const parsed = Number.parseInt(raw, 10)
  if (Number.isNaN(parsed)) return fallback
  return Math.min(max, Math.max(min, parsed))
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

/* 收藏槽名称输入占满 PrefRow 的 below 槽 */
.general-settings__slot-names {
  margin-top: 4px;
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
