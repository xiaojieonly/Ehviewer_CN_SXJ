<template>
  <div class="reader-view" :style="readerRootStyle">
    <div v-if="loadState === 'loading'" class="reader-view__center">
      <ProgressSpinner size="large" />
      <p class="reader-view__hint">正在加载画廊…</p>
    </div>

    <div v-else-if="loadState === 'error'" class="reader-view__center" role="alert">
      <p class="reader-view__hint">{{ errorMessage }}</p>
      <button type="button" class="reader-view__retry" @click="load">重试</button>
    </div>

    <ImageReader
      v-else
      ref="readerRef"
      :gid="gid"
      :title="displayTitle"
      :total-pages="totalPages"
      :current-page="currentPage"
      :direction="direction"
      :page-mode="pageModePref"
      :mode="resolvedMode"
      :zoom="zoom"
      :brightness="brightness"
      :auto-play="autoPlay"
      :auto-play-progress="autoPlayProgress"
      :enhanced-urls="enhancedUrls"
      @update:current-page="onPageChange"
      @update:direction="direction = $event"
      @update:page-mode="pageModePref = $event"
      @update:zoom="zoom = $event"
      @update:brightness="brightness = $event"
      @update:auto-play="autoPlay = $event"
      @prev="prevPage"
      @next="nextPage"
      @back="goBack"
    />

    <div
      v-if="degraded && loadState === 'ready'"
      class="reader-view__degraded"
      role="status"
      data-testid="reader-degraded-banner"
    >
      画廊详情不可用（站点不可达或本地无元数据）——无法加载的页面会在页内提示
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * ReaderView.vue (S3) — orchestrates the gallery reader, the web counterpart
 * of `GalleryActivity`:
 *
 * - Loads the gallery (`GET /api/v1/gallery/{gid}`) and resolves the starting
 *   page from the `/reader/:gid/:page?` route (0-based, deep-linkable).
 * - Owns the reader state the chrome components bind to: current page,
 *   reading direction (LTR/RTL/vertical), page-mode preference, zoom,
 *   brightness and auto-play. Direction / page mode / brightness persist to
 *   localStorage (the web equivalent of `Settings.PutReadingDirection`…).
 * - Resolves the effective mode: vertical direction forces scroll; `auto`
 *   page mode follows the viewport — dual at `min-aspect-ratio: 1/1`
 *   (responsive-strategy §6 rules 1–3), single otherwise.
 * - Navigation is spread-aware in dual mode (cover alone, then (2,3), (4,5)…
 *   in 1-based terms), matching `SpreadLayoutManager` paging.
 * - Keyboard: ←/→ (or A/D) turn pages — mirrored under RTL — Home/End jump,
 *   Space/Esc toggle the chrome, +/− zoom.
 * - Auto-play advances on a 100 ms-ticked timer (progress drives the countdown
 *   chip) and stops at the last page; it pauses while the tab is hidden.
 * - WebSocket: subscribes to `/topic/gallery/{gid}/enhanced` and hot-swaps
 *   `image.enhanced.ready` pages per websocket-protocol §3.3 (preload the
 *   enhanced file, swap atomically on load, silently keep the original on
 *   failure).
 * - The current page is synced back to the route (debounced) so refresh /
 *   back-navigation restore the reading position.
 * - F1: the visit is written to server history on entry, throttled mid-reading
 *   (≥10 pages or ≥30 s since the last write), and flushed once on
 *   leave/pagehide — so web reading lands in History and syncs back to the
 *   app. Failures degrade to console.warn. W4 (plan-2026-09-02): the payload
 *   carries `page: currentPage`; the initial page restores 深链 >
 *   `detail.readProgress` > localStorage `readProgress:{gid}` > 0 (the
 *   localStorage layer only backs the degraded / tokenless state, where the
 *   server write path is unavailable).
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isEhUnavailableError } from '@/api/client'
import { markDown } from '@/stores/availability'
import { galleryApi } from '@/api/gallery'
import type { GalleryDetail } from '@/types'

/** 画廊存在但未知页数（导入 .db 的 total=0 行且上游无法解析）：进入
 *  unknownPageCounts 模式，翻页由 PageMode 逐页请求驱动，不降级卡 1 页。 */
class UnknownPageCounts extends Error {
  readonly title: string
  constructor(title: string) {
    super('Gallery has no pages')
    this.name = 'UnknownPageCounts'
    this.title = title
  }
}
import { useKeyboardNav } from '@/composables/useKeyboardNav'
import { useEnhancedImage } from '@/composables/useEnhancedImage'
import { usePreferencesStore } from '@/stores/preferences'
import { DEFAULT_READER_PREFERENCES } from '@/api/preferences'
import { maskedTitle } from '@/utils/privacyMask'
import ProgressSpinner from '@/components/atoms/ProgressSpinner.vue'
import ImageReader from '@/components/reader/ImageReader.vue'
import {
  AUTO_PLAY_INTERVALS_MS,
  PAGE_MODE_PREFS,
  READING_DIRECTIONS,
  firstPageOfSpread,
  spreadIndexOf,
} from '@/components/reader/PageMode.vue'
import type {
  AutoPlayState,
  PageModePref,
  ReadingDirection,
  ResolvedReaderMode,
} from '@/components/reader/PageMode.vue'

const route = useRoute()
const router = useRouter()
const preferencesStore = usePreferencesStore()

const gid = computed(() => Number(route.params.gid))

const loadState = ref<'loading' | 'error' | 'ready'>('loading')
const errorMessage = ref('Failed to load gallery')
/** 统一阅读器：detail 拉取失败时仍打开阅读器壳（单页错误由 PageMode 呈现）。 */
const degraded = ref(false)
/** 未知页数模式（import .db total=0）：totalPages=0，翻页由 PageMode 逐页驱动。 */
const unknownPageCounts = ref(false)
const title = ref('')
/** 隐私打码：工具栏/标签页标题以内容序列号显示（title 本体保持真实值，
 *  历史回写 payload 仍用原文，见 pushHistory）。 */
const displayTitle = computed(() => maskedTitle(title.value, gid.value))
/** Gallery site token from the detail response — required by history writeback. */
const galleryToken = ref('')
const totalPages = ref(0)
/** 0-based reading position. */
const currentPage = ref(0)
/** Zoom factor (page mode only); intentionally NOT persisted. */
const zoom = ref(1)

/* Wave-1 1c: reader preference consumption (defaults when prefs unloaded). */
const readerPrefs = computed(() => preferencesStore.prefs?.reader ?? DEFAULT_READER_PREFERENCES)

const READER_BACKGROUNDS: Record<string, string> = {
  black: '#080808',
  gray: '#1c1c1e',
  white: '#f7f7f5',
}

const readerRootStyle = computed(() => ({
  background: READER_BACKGROUNDS[readerPrefs.value.backgroundColor] ?? READER_BACKGROUNDS.black,
  '--reader-dual-gap': `${readerPrefs.value.dualPageGap}px`,
  '--reader-page-transition':
    readerPrefs.value.pageTransition === 'none'
      ? 'none'
      : readerPrefs.value.pageTransition === 'fade'
        ? 'opacity 200ms var(--ease-decelerate-quart)'
        : 'opacity 200ms var(--ease-decelerate-quart), transform 200ms var(--ease-decelerate-quart)',
}))
/** Monotonic guard so stale loads (superseded navigation) are dropped. */
let loadSeq = 0

const readerRef = ref<InstanceType<typeof ImageReader> | null>(null)

/* ------------------------------------------------------------------ */
/* Persisted reader preferences                                        */
/* ------------------------------------------------------------------ */

const SETTINGS_STORAGE_KEY = 'anotherviewer-web.reader-settings'

/**
 * 载荷版本。v1（无 v 字段）是「服务器偏好」与本地快捷设置统一之前的产物：
 * 它固化了当时的默认 pageMode:'dual'，对竖屏手机是错误的持久值，因此迁移
 * 时丢弃其 pageMode，让服务器偏好（'auto'）重新生效；direction/brightness
 * 默认未变，照常继承。
 */
const SETTINGS_VERSION = 2

interface PersistedReaderSettings {
  v?: number
  direction: ReadingDirection
  pageMode: PageModePref
  brightness: number
}

const direction = ref<ReadingDirection>('ltr')
const pageModePref = ref<PageModePref>('auto')
const brightness = ref(0)

/** Clamp helper for the persisted brightness (0–100). */
function clampBrightness(value: number): number {
  return Math.min(100, Math.max(0, Math.round(value)))
}

/**
 * Resolve effective settings — precedence:
 *   localStorage (per-device in-reader choice, v≥2) > server prefs > defaults.
 * Server prefs are the base so the main settings page actually drives the
 * reader; the per-device layer only overrides what the reader sheet changed.
 */
function applyStoredSettings(): void {
  const r = preferencesStore.prefs?.reader
  if (r) {
    if (READING_DIRECTIONS.includes(r.readingDirection as ReadingDirection)) {
      direction.value = r.readingDirection as ReadingDirection
    }
    if (PAGE_MODE_PREFS.includes(r.pageMode as PageModePref)) {
      pageModePref.value = r.pageMode as PageModePref
    }
    if (typeof r.brightness === 'number' && Number.isFinite(r.brightness)) {
      brightness.value = clampBrightness(r.brightness)
    }
  }

  try {
    const raw = localStorage.getItem(SETTINGS_STORAGE_KEY)
    if (!raw) return
    const stored = JSON.parse(raw) as Partial<PersistedReaderSettings>
    if (stored.direction && READING_DIRECTIONS.includes(stored.direction)) {
      direction.value = stored.direction
    }
    // v1 payload 的 pageMode 是过期默认（见 SETTINGS_VERSION），不继承。
    if (
      stored.v === SETTINGS_VERSION &&
      stored.pageMode &&
      PAGE_MODE_PREFS.includes(stored.pageMode)
    ) {
      pageModePref.value = stored.pageMode
    }
    if (typeof stored.brightness === 'number' && Number.isFinite(stored.brightness)) {
      brightness.value = clampBrightness(stored.brightness)
    }
  } catch {
    // Corrupted storage — fall back to server prefs / defaults.
  }
  appliedSnapshot = settingsSnapshot()

  // 主动升级旧版本载荷：v1 的过期 pageMode（'dual' 默认）已在上面的解析中
  // 被忽略，这里把当前生效值立即重写为 v2，不让过期值滞留到下次会话。
  try {
    const raw = localStorage.getItem(SETTINGS_STORAGE_KEY)
    if (raw && JSON.parse(raw)?.v !== SETTINGS_VERSION) writeSettings()
  } catch {
    /* 解析失败时无需升级——下次写入自然带版本号。 */
  }
}

/** 用户在本会话内改过任一项后置位——此后异步到达的服务器偏好不再回冲。 */
let settingsTouched = false

/**
 * 「最近一次程序化应用」的快照：applyStoredSettings 的批量赋值同样会触发
 * 下面的 watcher（Vue 预刷新队列），用快照比对把它与真实用户改动区分开，
 * 避免打开页面就误写 localStorage / 服务器偏好。
 */
let appliedSnapshot = ''

function settingsSnapshot(): string {
  return JSON.stringify([direction.value, pageModePref.value, brightness.value])
}

/** 把当前生效值写入 localStorage（v2）并镜像到服务器偏好。 */
function writeSettings(): void {
  try {
    const payload: PersistedReaderSettings = {
      v: SETTINGS_VERSION,
      direction: direction.value,
      pageMode: pageModePref.value,
      brightness: brightness.value,
    }
    localStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(payload))
  } catch {
    // Storage unavailable / full — reading continues unimpaired.
  }
  // 回写服务器偏好：主设置页与阅读器快捷设置自此保持同一份状态
  // （updateReader 内部有防抖合并保存）。
  preferencesStore.updateReader({
    readingDirection: direction.value,
    pageMode: pageModePref.value,
    brightness: brightness.value,
  })
}

watch(settingsSnapshot, (next) => {
  if (next === appliedSnapshot) return
  settingsTouched = true
  writeSettings()
})

/* ------------------------------------------------------------------ */
/* Mode resolution — dual at min-aspect-ratio 1/1 (responsive §6)      */
/* ------------------------------------------------------------------ */

const landscape = ref(false)
let landscapeQuery: MediaQueryList | null = null

function onLandscapeChange(event: { matches: boolean }) {
  landscape.value = event.matches
}

const resolvedMode = computed<ResolvedReaderMode>(() => {
  // 未知页数（import .db total=0）：scroll/dual 依赖 totalPages 枚举渲染
  // （v-for in total / spread 计算），强制 page 单页模式——翻页由 PageMode
  // 逐页请求驱动，失败页内提示。
  if (unknownPageCounts.value) return 'page'
  if (direction.value === 'vertical') return 'scroll'
  switch (pageModePref.value) {
    case 'scroll':
      return 'scroll'
    case 'dual':
      return 'dual'
    case 'single':
      return 'page'
    default:
      return landscape.value ? 'dual' : 'page'
  }
})

/* ------------------------------------------------------------------ */
/* Navigation — spread-aware in dual mode                              */
/* ------------------------------------------------------------------ */

function onPageChange(page: number) {
  currentPage.value =
    totalPages.value > 0
      ? Math.min(Math.max(page, 0), Math.max(0, totalPages.value - 1))
      : Math.max(page, 0)
}

/** Advance one page — or one whole spread in dual mode. Returns false at the end. */
function nextPage(): boolean {
  const max = totalPages.value - 1
  if (resolvedMode.value === 'dual') {
    const target = firstPageOfSpread(spreadIndexOf(currentPage.value) + 1)
    if (totalPages.value > 0 && (target > max || target === currentPage.value)) return false
    currentPage.value = target
    return true
  }
  if (totalPages.value > 0 && currentPage.value >= max) return false
  currentPage.value += 1
  return true
}

/** Step back one page — to the start of the previous spread in dual mode. */
function prevPage(): boolean {
  if (resolvedMode.value === 'dual') {
    const target = firstPageOfSpread(spreadIndexOf(currentPage.value) - 1)
    if (target >= currentPage.value) return false
    currentPage.value = target
    return true
  }
  if (currentPage.value <= 0) return false
  currentPage.value -= 1
  return true
}

function goBack() {
  router.push(`/gallery/${gid.value}`)
}

/* ------------------------------------------------------------------ */
/* Keyboard — ←/→ mirror under RTL, Space/Esc toggle chrome            */
/* ------------------------------------------------------------------ */

useKeyboardNav({
  onPrev: () => {
    if (direction.value === 'rtl') nextPage()
    else prevPage()
  },
  onNext: () => {
    if (direction.value === 'rtl') prevPage()
    else nextPage()
  },
  onFirst: () => {
    currentPage.value = 0
  },
  onLast: () => {
    currentPage.value = Math.max(0, totalPages.value - 1)
  },
  onToggleToolbar: () => {
    readerRef.value?.toggleChrome()
  },
  onZoomIn: () => {
    zoom.value = Math.min(readerPrefs.value.maxZoom, Math.round((zoom.value + readerPrefs.value.zoomStep) * 100) / 100)
  },
  onZoomOut: () => {
    zoom.value = Math.max(0.5, Math.round((zoom.value - readerPrefs.value.zoomStep) * 100) / 100)
  },
  pagingEnabled: () => readerPrefs.value.keyboardPaging,
})

/* ------------------------------------------------------------------ */
/* Auto-play — 100 ms tick; pauses when the tab is hidden              */
/* ------------------------------------------------------------------ */

const AUTO_PLAY_TICK_MS = 100

const autoPlay = ref<AutoPlayState>({
  enabled: false,
  intervalMs: AUTO_PLAY_INTERVALS_MS[1] ?? 3000,
})
const autoPlayProgress = ref(0)
let autoPlayTimer: ReturnType<typeof setInterval> | null = null

watch(
  [() => autoPlay.value.enabled, () => autoPlay.value.intervalMs],
  () => {
    if (autoPlayTimer !== null) {
      clearInterval(autoPlayTimer)
      autoPlayTimer = null
    }
    autoPlayProgress.value = 0
    if (!autoPlay.value.enabled) return

    let elapsed = 0
    autoPlayTimer = setInterval(() => {
      if (typeof document !== 'undefined' && document.hidden) return
      elapsed += AUTO_PLAY_TICK_MS
      autoPlayProgress.value = Math.min(1, elapsed / autoPlay.value.intervalMs)
      if (elapsed >= autoPlay.value.intervalMs) {
        elapsed = 0
        if (!nextPage()) {
          // Reached the last page / spread — stop.
          autoPlay.value = { ...autoPlay.value, enabled: false }
        }
      }
    }, AUTO_PLAY_TICK_MS)
  },
)

/* ------------------------------------------------------------------ */
/* Route sync — debounced so scroll-mode tracking doesn't spam history */
/* ------------------------------------------------------------------ */

let routeSyncTimer: ReturnType<typeof setTimeout> | null = null

watch(currentPage, () => {
  if (loadState.value !== 'ready') return
  if (routeSyncTimer !== null) clearTimeout(routeSyncTimer)
  routeSyncTimer = setTimeout(() => {
    router
      .replace({
        name: 'Reader',
        params: { gid: String(gid.value), page: String(currentPage.value) },
      })
      .catch(() => {
        // Navigation superseded (e.g. leaving the reader) — harmless.
      })
  }, 250)
})

/* ------------------------------------------------------------------ */
/* F1 — history writeback                                              */
/* ------------------------------------------------------------------ */

/**
 * Mid-reading writeback strategy (at most one request per window):
 *
 * - Entering the reader records the visit once (`load()` success).
 * - Afterwards a page change triggers a writeback only when EITHER
 *   ≥ {@link HISTORY_WRITE_PAGE_STRIDE} pages flipped since the last write,
 *   OR ≥ {@link HISTORY_WRITE_MIN_INTERVAL_MS} elapsed since it.
 * - `pagehide` / unmount flush the tail once (best effort, fire-and-forget).
 *
 * Rationale: fast flippers keep the synced position within ~10 pages of
 * reality without per-flip spam; slow readers still checkpoint every ~30 s.
 * The server upserts one history row (timestamp refresh), so extra writes
 * are idempotent but not free — hence the throttle. Failures degrade to
 * console.warn; reading is never disturbed by a history outage.
 */
/* ------------------------------------------------------------------ */
/* W4 — reading progress (plan-2026-09-02 §5.3 W4)                     */
/* ------------------------------------------------------------------ */

/** 降级态进度 localStorage 键前缀（W4④）：完整键 `readProgress:{gid}`，
 *  值为十进制页码字符串（0 起页索引）。 */
const READ_PROGRESS_KEY_PREFIX = 'readProgress:'

function readProgressKey(id: number): string {
  return `${READ_PROGRESS_KEY_PREFIX}${id}`
}

/** 读取本机降级进度；无记录 / 损坏 / 负数返回 -1（调用方以 <0 判缺失）。 */
function loadLocalProgress(id: number): number {
  try {
    const raw = localStorage.getItem(readProgressKey(id))
    if (raw === null) return -1
    const page = Number(raw)
    return Number.isFinite(page) && page >= 0 ? Math.floor(page) : -1
  } catch {
    // Storage unavailable — degrade to "no local progress".
    return -1
  }
}

/** 写入本机降级进度（degraded || 无 token 时替代 REST 回写，W4④）。 */
function saveLocalProgress(id: number, page: number): void {
  try {
    localStorage.setItem(readProgressKey(id), String(page))
  } catch {
    // Storage unavailable / full — reading continues unimpaired.
  }
}

/**
 * W4① 恢复优先级：深链路由参数 > detail.readProgress > 0。
 *
 * detail 来源缺失时（旧服务器不下发字段 / detail 拉取失败 / 无 token 无法
 * 同步）以 localStorage 键 `readProgress:{gid}` 兜底（W4④，D2 降级态语义）。
 * 服务器已带进度（含显式 REST 写 0 的「重读」）时不读本地——
 * detail.readProgress 是同步链路的唯一权威来源（D2）。
 */
function resolveStartPage(
  rawDeepLink: unknown,
  serverProgress: number | null | undefined,
): number {
  const linked = Number(rawDeepLink)
  if (Number.isFinite(linked)) return Math.max(0, Math.floor(linked))
  if (
    typeof serverProgress === 'number' &&
    Number.isFinite(serverProgress) &&
    serverProgress > 0
  ) {
    return Math.floor(serverProgress)
  }
  const local = loadLocalProgress(gid.value)
  if (
    local >= 0 &&
    (!galleryToken.value || serverProgress === undefined || serverProgress === null)
  ) {
    return local
  }
  return 0
}

/**
 * W4①/② 已知页数钳 [0, pages-1]；页数未知（totalPages=0：降级壳 /
 * unknownPageCounts 模式）只钳下界——`Math.max(0, pages-1)` 会把恢复出的
 * 进度压平成 0（plan §10.7），故不做上界钳制。
 */
function clampStartPage(page: number, knownPages: number): number {
  return knownPages > 0 ? Math.min(Math.max(page, 0), knownPages - 1) : Math.max(page, 0)
}

const HISTORY_WRITE_PAGE_STRIDE = 10
const HISTORY_WRITE_MIN_INTERVAL_MS = 30_000

/** Page already persisted by the last writeback (-1 = nothing yet). */
let lastHistoryPage = -1
/** Wall clock of the last writeback attempt — anti-spam bound, not delivery tracking. */
let lastHistoryAt = 0

function pushHistory(): void {
  // 统一阅读器：降级态（无 detail token）不回写服务器——改写本机
  // localStorage 键 readProgress:{gid} 兜底（W4④；load 入口 / 翻页节流 /
  // leave-pagehide flush 均经此处落盘），同步脏标记防 leave-flush 误发。
  if (degraded.value || !galleryToken.value) {
    lastHistoryPage = currentPage.value
    lastHistoryAt = Date.now()
    saveLocalProgress(gid.value, currentPage.value)
    return
  }
  lastHistoryPage = currentPage.value
  lastHistoryAt = Date.now()
  galleryApi
    .addHistory(gid.value, {
      token: galleryToken.value,
      title: title.value,
      // W4③: 翻页位置随回写落库（含 0——显式重读语义，D5）。
      page: currentPage.value,
    })
    .catch((error) => {
      console.warn(`[reader] history writeback failed (gid=${gid.value})`, error)
    })
}

/** Throttle gate for mid-reading page changes (see strategy above). */
function syncReadingProgress(): void {
  if (loadState.value !== 'ready') return
  const flipped = Math.abs(currentPage.value - lastHistoryPage)
  if (flipped === 0) return
  if (
    flipped < HISTORY_WRITE_PAGE_STRIDE &&
    Date.now() - lastHistoryAt < HISTORY_WRITE_MIN_INTERVAL_MS
  ) {
    return
  }
  pushHistory()
}

/** Last-chance flush on leave/unload: bypasses the throttle, skips no-op writes. */
function flushHistoryOnLeave(): void {
  if (loadState.value !== 'ready') return
  if (currentPage.value === lastHistoryPage) return
  pushHistory()
}

watch(currentPage, () => {
  syncReadingProgress()
})

/* ------------------------------------------------------------------ */
/* WebSocket — AI enhancement hot-swap (websocket-protocol §3.3/§6.3)  */
/* ------------------------------------------------------------------ */

// I3 composable — exclusively owns `/topic/gallery/{gid}/enhanced`
// (websocket-protocol §3.3/§6.3): preloads each `image.enhanced.ready` file
// and exposes it in the reactive map only after a successful load, so the
// reader hot-swaps atomically and silently keeps the original on failure.
// Page keys are 0-based; cleanup happens on unmount inside the composable.
const { enhancedUrls, connect: connectEnhanced } = useEnhancedImage(gid)

function subscribeEnhanced() {
  try {
    connectEnhanced()
  } catch {
    // WebSocket is an optional enhancement channel; reading works without it.
  }
}

/* ------------------------------------------------------------------ */
/* Gallery load                                                        */
/* ------------------------------------------------------------------ */

async function load() {
  const seq = ++loadSeq
  loadState.value = 'loading'
  // P-B：入口 token 透传——token 只从 route.query 一次取（页面自翻页替换路由
  // 参数时保持原 token 不变；route.params.page 的 watch 不重置它）。
  const token = typeof route.query.token === 'string' ? route.query.token : undefined
  // W4①/②/④：hoist 出 catch 路径——unknownPageCounts / 降级态恢复同样要读
  // detail.readProgress（UPC 时 detail 已返回；降级时为 undefined → 本地兜底）。
  let detail: GalleryDetail | undefined
  try {
    detail = await galleryApi.getDetail(gid.value, token)
    if (seq !== loadSeq) return
    const pages = Number(detail?.pages)
    if (!Number.isFinite(pages) || pages <= 0) {
      // 2026-08-30：导入 .db 的元数据行 total=0（无上游/token 失效）——不再
      // 降级 totalPages=1 卡死，改为「未知页数」：允许翻页、页内失败由
      // PageMode 的错误覆盖层提示（本站可达时图片流会逐页解析）。
      throw new UnknownPageCounts(detail?.title ?? `Gallery ${gid.value}`)
    }
    title.value = detail.title || `Gallery ${gid.value}`
    galleryToken.value = detail.token || ''
    totalPages.value = pages

    // W4①: 初始页 = 深链路由参数 > detail.readProgress > 0，钳 [0, pages-1]。
    currentPage.value = clampStartPage(
      resolveStartPage(route.params.page, detail.readProgress),
      pages,
    )

    loadState.value = 'ready'
    document.title = displayTitle.value
    subscribeEnhanced()
    // F1: entering the reader records the visit immediately (server upserts
    // the history row and refreshes its timestamp).
    pushHistory()
  } catch (error) {
    if (seq !== loadSeq) return
    console.error('Failed to load gallery', error)
    // EH 熔断（§0）：落 DOWN 标记——PageMode 图片失败直接终态（不重试风暴）。
    if (isEhUnavailableError(error)) markDown()
    if (error instanceof UnknownPageCounts) {
      // 画廊存在（title 已知）但页数未定：进入「未知页数」阅读模式——
      // 翻页由 PageMode 逐页请求驱动，失败给出页内提示而非卡死。
      title.value = error.title
      totalPages.value = 0 // 0 = 未知（CodeBlock: PageMode 据此允许任意翻页）
      // W4②: 页数未知——恢复已存进度（深链 > readProgress > 本地 > 0），
      // 不做上界钳制（totalPages=0，§10.7）。
      currentPage.value = clampStartPage(
        resolveStartPage(route.params.page, detail?.readProgress),
        0,
      )
      unknownPageCounts.value = true
      loadState.value = 'ready'
      document.title = displayTitle.value
      return
    }
    // 统一阅读器（2026-08-24）：detail 拉不到（EH 不可达 / 无本地元数据）也必须
    // 打开阅读器壳——单页失败由 PageMode 的错误覆盖层呈现（含重试）。
    // total 置 1 让阅读器停在可交互状态；历史回写跳过（无 token），改写
    // localStorage 兜底（W4④）。
    title.value = `Gallery ${gid.value}`
    totalPages.value = 1
    // W4④: 降级态恢复——detail 缺失，localStorage 同 ① 优先级兜底；
    // totalPages=1 是假值，不做上界钳制（否则恢复恒为 0）。
    currentPage.value = clampStartPage(resolveStartPage(route.params.page, undefined), 0)
    degraded.value = true
    loadState.value = 'ready'
    document.title = displayTitle.value
  }
}

/* ------------------------------------------------------------------ */
/* Route param changes — the view is reused across navigations         */
/* ------------------------------------------------------------------ */

/** Reset all per-gallery state before (re)loading a different gid. */
function resetReaderState() {
  loadState.value = 'loading'
  errorMessage.value = 'Failed to load gallery'
  degraded.value = false
  unknownPageCounts.value = false
  title.value = ''
  galleryToken.value = ''
  lastHistoryPage = -1
  lastHistoryAt = 0
  totalPages.value = 0
  currentPage.value = 0
  zoom.value = 1
  autoPlay.value = { enabled: false, intervalMs: AUTO_PLAY_INTERVALS_MS[1] ?? 3000 }
  autoPlayProgress.value = 0
  if (autoPlayTimer !== null) clearInterval(autoPlayTimer)
  autoPlayTimer = null
  if (routeSyncTimer !== null) clearTimeout(routeSyncTimer)
  routeSyncTimer = null
}

// gid change → full reset + reload. The `useEnhancedImage` composable
// unsubscribes / re-subscribes the WebSocket topic on gid change itself.
watch(
  gid,
  () => {
    resetReaderState()
    void load()
  },
  { immediate: true },
)

// Same gallery, a different page param (deep-link / back-nav): jump to the
// page without reloading the gallery. Echoes of our own route-sync replace
// are skipped via the target === currentPage guard; page changes that arrive
// mid-load are picked up by `load()` reading `route.params.page`.
watch(
  () => route.params.page,
  (page) => {
    if (loadState.value !== 'ready') return
    const target = Number(page)
    if (!Number.isFinite(target) || target === currentPage.value) return
    currentPage.value = Math.min(Math.max(target, 0), Math.max(0, totalPages.value - 1))
  },
)

/* ------------------------------------------------------------------ */
/* Lifecycle                                                           */
/* ------------------------------------------------------------------ */

onMounted(() => {
  if (typeof window.matchMedia === 'function') {
    landscapeQuery = window.matchMedia('(min-aspect-ratio: 1/1)')
    landscape.value = landscapeQuery.matches
    landscapeQuery.addEventListener('change', onLandscapeChange)
  }
  applyStoredSettings()
  // 服务器偏好异步到达后重新解析基底——但用户已在本会话做过选择时跳过，
  // 防止在途的旧值覆盖刚改的设置。
  const applyIfUntouched = () => {
    if (!settingsTouched) applyStoredSettings()
  }
  if (!preferencesStore.prefs && !preferencesStore.loading) {
    void preferencesStore.load().then(applyIfUntouched)
  } else {
    void applyIfUntouched()
  }
  // F1: tab close / refresh — one best-effort writeback of the tail position.
  window.addEventListener('pagehide', flushHistoryOnLeave)
})

onBeforeUnmount(() => {
  landscapeQuery?.removeEventListener('change', onLandscapeChange)
  window.removeEventListener('pagehide', flushHistoryOnLeave)
  // Leaving the reader (router navigation) ends the session — flush the tail.
  flushHistoryOnLeave()
  if (autoPlayTimer !== null) clearInterval(autoPlayTimer)
  if (routeSyncTimer !== null) clearTimeout(routeSyncTimer)
  // The WebSocket subscription + connection ref are released by the
  // composable's own unmount hook.
})
</script>

<style scoped>
.reader-view {
  position: fixed;
  inset: 0;
  overflow: hidden;
  background: var(--grey-975); /* #080808 */
}

.reader-view__center {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--keyline-margin);
  height: 100%;
  padding: var(--keyline-margin);
  text-align: center;
}

.reader-view__hint {
  margin: 0;
  color: var(--grey-500);
  font-size: var(--text-little-small); /* 16sp */
}

.reader-view__degraded {
  position: absolute;
  top: 8px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 10;
  padding: 4px 12px;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.65);
  color: var(--grey-300, #aaa);
  font-size: var(--text-little-small);
  pointer-events: none;
}

.reader-view__retry {
  padding: 10px 32px;
  border: none;
  border-radius: var(--card-radius); /* 2dp */
  background: var(--color-primary);
  color: var(--color-white);
  font-size: var(--text-small); /* 14sp */
  font-weight: 500;
  cursor: pointer;
  transition:
    background 150ms var(--ease-decelerate-quart),
    transform 150ms var(--ease-decelerate-quart);
}

.reader-view__retry:hover {
  background: var(--color-primary-dark);
}

.reader-view__retry:active {
  transform: scale(0.97);
}

.reader-view__retry:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 2px;
}
</style>
