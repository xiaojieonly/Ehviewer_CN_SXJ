<template>
  <div class="recycle-view">
    <!-- 平板对齐（T3 同构）：宽屏双栏骨架——回收站条目无可预览详情（key 类型
         契约未定、实体异构），右栏恒为占位符说明；列表恒驻左栏。 -->
    <TwoPaneLayout
      :has-selection="false"
      placeholder="回收站条目不支持预览，恢复后请到对应列表查看"
    >
      <template #list>
        <div class="recycle-view__heading">
          <h1 class="recycle-view__title">回收站</h1>
          <span v-if="state === 'content'" class="recycle-view__count">
            {{ countLabel }}
          </span>
        </div>

        <!-- 客户端过滤（WP8 契约：/recycle/list 无 q 参数）——按 title 过滤
             当前已加载页，不动分页状态。 -->
        <div class="search-bar">
          <AppIcon name="magnify-dark" size="18px" />
          <input
            v-model="searchQuery"
            class="search-bar__input"
            type="search"
            placeholder="按标题过滤当前页…"
            aria-label="过滤回收站"
          />
          <button
            v-if="searchQuery"
            type="button"
            class="search-bar__clear"
            aria-label="清除过滤"
            @click="searchQuery = ''"
          >
            <AppIcon name="close-dark" size="16px" />
          </button>
        </div>

        <!-- 分页条（与历史/下载页同构）：页码指示 + 每页条数切换（50/100/200，
             默认 50）+ 跳页。page 1 起【直传】/recycle/list（无历史的 -1 换算）。 -->
        <nav
          v-if="paginationVisible"
          class="pagination-bar"
          data-testid="recycle-pagination"
          aria-label="回收站分页"
        >
          <span class="pagination-bar__info">
            第 {{ currentPage }} / {{ totalPages }} 页 · {{ total }} 条
          </span>
          <span class="pagination-bar__pages" role="group" aria-label="页码">
            <button
              type="button"
              class="pagination-bar__page"
              :disabled="currentPage <= 1"
              aria-label="上一页"
              @click="jumpToPage(currentPage - 1)"
            >
              ‹
            </button>
            <template v-for="(item, i) in pageWindow" :key="`${item}-${i}`">
              <button
                v-if="item !== '…'"
                type="button"
                class="pagination-bar__page"
                :class="{ 'pagination-bar__page--active': item === currentPage }"
                :aria-current="item === currentPage ? 'page' : undefined"
                :aria-label="`第 ${item} 页`"
                @click="jumpToPage(item)"
              >
                {{ item }}
              </button>
              <span v-else class="pagination-bar__ellipsis" aria-hidden="true">…</span>
            </template>
            <button
              type="button"
              class="pagination-bar__page"
              :disabled="currentPage >= totalPages"
              aria-label="下一页"
              @click="jumpToPage(currentPage + 1)"
            >
              ›
            </button>
          </span>
          <label class="pagination-bar__size">
            条/页
            <select
              v-model.number="pageSize"
              class="pagination-bar__select"
              aria-label="每页条数"
            >
              <option v-for="size in RECYCLE_PAGE_SIZES" :key="size" :value="size">
                {{ size }}
              </option>
            </select>
          </label>
        </nav>

        <!-- 多选工具条（Android choice mode 同构，照 DownloadView select-bar）：
             全选 / 恢复 / 立即清除（danger）/ 退出；长按或右键条目进入。 -->
        <div
          v-if="selectMode"
          class="select-bar"
          role="toolbar"
          aria-label="批量操作"
          @keyup.esc="exitSelectMode"
        >
          <span class="select-bar__count" role="status">
            共 {{ total }} 条 · 已选 {{ selectedIds.size }} 条
          </span>
          <div class="select-bar__actions">
            <button type="button" class="select-bar__btn" @click="selectAll">全选</button>
            <button
              type="button"
              class="select-bar__btn"
              :disabled="selectedIds.size === 0 || restoring"
              @click="openRestoreDialog"
            >
              恢复
            </button>
            <button
              type="button"
              class="select-bar__btn select-bar__btn--danger"
              :disabled="selectedIds.size === 0 || purging"
              @click="openPurgeDialog('selected')"
            >
              立即清除
            </button>
            <button
              type="button"
              class="select-bar__close"
              aria-label="退出多选"
              @click="exitSelectMode"
            >
              <AppIcon name="close-dark" size="18px" />
            </button>
          </div>
        </div>

        <ContentLayout
          ref="contentRef"
          class="recycle-view__content"
          :state="state"
          v-model:refreshing="refreshing"
          empty-text="回收站为空。删除的收藏、历史、下载等记录会在这里保留 7 天。"
          :error-text="errorText"
          @refresh="onRefresh"
          @retry="onRetry"
        >
          <!-- 全宽单列密信息行（AppListRow 同构）：#badge 剩余天数（≤1 天红色
               强调）、#meta 实体 chip、subtitle 删除时刻。id/gid 用行索引——
               key 的类型契约未定，展示层不耦合（选择/多选也全按索引）。 -->
          <div class="recycle-list">
            <AppListRow
              v-for="row in visibleRows"
              :key="row.index"
              :id="row.index"
              :gid="row.index"
              :title="row.item.title"
              :subtitle="`删除于 ${formatDeletedAt(row.item.deletedAt)}`"
              :selectable="selectMode"
              :selected="selectedIds.has(row.index)"
              @select="onItemSelect"
              @menu="onItemMenu"
            >
              <template #badge>
                <span
                  class="recycle-remaining"
                  :class="{ 'recycle-remaining--danger': row.item.remainingDays <= 1 }"
                  :title="`剩 ${row.item.remainingDays} 天后自动清除`"
                >
                  <AppIcon name="delete-dark" size="14px" />
                  剩 {{ row.item.remainingDays }} 天
                </span>
              </template>

              <template #meta>
                <span class="recycle-entity-chip" data-testid="recycle-entity-chip">
                  {{ entityLabel(row.item.entity) }}
                </span>
              </template>
            </AppListRow>
          </div>
        </ContentLayout>

        <!-- FabLayout：清除到期（purge() 无 items）+ 回顶部；多选中隐藏
             （choice mode 替换 FAB 集群，对齐下载页）。 -->
        <FabLayout
          v-if="!selectMode"
          v-model:expanded="fabExpanded"
          primary-icon="reorder"
          :actions="fabActions"
          @click-secondary="onFabAction"
        />
      </template>
    </TwoPaneLayout>

    <Teleport to="body">
      <!-- 恢复确认（普通 AlertDialog 形态，照 HistoryView 确认框）。 -->
      <div
        v-if="showRestoreDialog"
        class="dialog-scrim"
        @click.self="closeRestoreDialog"
      >
        <div
          class="dialog"
          role="alertdialog"
          aria-modal="true"
          aria-labelledby="recycle-restore-title"
          aria-describedby="recycle-restore-message"
        >
          <h3 id="recycle-restore-title" class="dialog__title">恢复条目</h3>
          <p id="recycle-restore-message" class="dialog__message">
            将选中的 {{ selectedIds.size }} 项恢复到原列表？
          </p>
          <div class="dialog__actions">
            <button type="button" class="dialog__btn" @click="closeRestoreDialog">取消</button>
            <button
              type="button"
              class="dialog__btn dialog__btn--primary"
              :disabled="restoring"
              @click="confirmRestore"
            >
              {{ restoring ? '恢复中…' : '恢复' }}
            </button>
          </div>
        </div>
      </div>

      <!-- 立即清除确认（danger，语义同「清空历史」）。 -->
      <div
        v-if="showPurgeDialog"
        class="dialog-scrim"
        @click.self="closePurgeDialog"
      >
        <div
          class="dialog"
          role="alertdialog"
          aria-modal="true"
          aria-labelledby="recycle-purge-title"
          aria-describedby="recycle-purge-message"
        >
          <h3 id="recycle-purge-title" class="dialog__title">立即清除</h3>
          <p id="recycle-purge-message" class="dialog__message">
            {{ purgeMessage }}
          </p>
          <div class="dialog__actions">
            <button type="button" class="dialog__btn" @click="closePurgeDialog">取消</button>
            <button
              type="button"
              class="dialog__btn dialog__btn--danger"
              :disabled="purging"
              @click="confirmPurge"
            >
              {{ purging ? '清除中…' : '清除' }}
            </button>
          </div>
        </div>
      </div>

      <!-- Toast (Android Toast equivalent) -->
      <div v-if="toastMessage" class="toast" role="status">{{ toastMessage }}</div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
/**
 * RecycleView — 回收站（WP8 任务 1）：已删除条目（收藏/历史/下载/书签/
 * 过滤/快速搜索/下载标签 7 类）的统一暂存与恢复入口，7 天后到期自动清除。
 *
 * 骨架照 HistoryView：TwoPaneLayout（右栏恒占位——回收站条目不支持预览）
 * + ContentLayout 四态机 + AppListRow 单列密信息行 + 分页条 + FabLayout。
 * 服务端分页（usePagedList）：`/recycle/list` page 1 起【直传】（无历史的
 * -1 换算），条数档位 50/100/200（服务端钳制 1..200，默认 50）。
 *
 * 行语义：AppListRow id/gid 用行索引（key 的类型契约未定，展示层不耦合）；
 * `#badge` 挂剩余天数（≤1 天红色强调），`#meta` 挂实体 chip，subtitle 放
 * 删除时刻。搜索为客户端按 title 过滤当前已加载页（服务端无 q 参数）。
 *
 * 多选（Android choice mode，照 DownloadView select-bar）：长按/右键进入，
 * 全选 / 恢复 / 立即清除（danger，二次确认含「不可恢复」文案）/ 退出。
 * 操作成功走乐观移除（splice + total 递减）+ toast + 当前页静默重拉对账
 * （照 DownloadView 的 loadPage(currentPage, { silent: true })）。
 *
 * KeepAlive（App.vue 缓存本视图）：对话框 Esc 守卫随 activated/deactivated
 * 摘挂（audit P2 同款，照 HistoryView:596-631）。
 */
import { computed, onActivated, onDeactivated, onMounted, onUnmounted, ref, watch } from 'vue'
import { recycleApi } from '@/api/recycle'
import type { RecycleEntity, RecycleItem, RecycleTarget } from '@/api/recycle'
import { usePagedList } from '@/composables/usePagedList'
import type { FabAction } from '@/types/components'
import ContentLayout from '@/components/layout/ContentLayout.vue'
import TwoPaneLayout from '@/components/layout/TwoPaneLayout.vue'
import FabLayout from '@/components/atoms/FabLayout.vue'
import AppListRow from '@/components/gallery/AppListRow.vue'
import AppIcon from '@/components/atoms/AppIcon.vue'

/** View states matching ContentLayout's internal ViewTransition. */
type ViewState = 'loading' | 'content' | 'empty' | 'error'

/* --------------------------------------------------------------- data --- */

/**
 * 每页条数档位（对齐历史/下载页：50/100/200，默认 50）。服务端把 pageSize
 * 钳制在 1..200——三档全部有效。
 */
const RECYCLE_PAGE_SIZES = [50, 100, 200] as const
const DEFAULT_RECYCLE_PAGE_SIZE = 50

const state = ref<ViewState>('loading')
const refreshing = ref(false)
const contentRef = ref<InstanceType<typeof ContentLayout> | null>(null)
const errorText = ref('加载回收站失败')

/** 标题计数：服务端全集条数。 */
const countLabel = computed(() => `${total.value} 条`)

/**
 * 分页状态机（W3-C1 usePagedList）：fetchPage 把 1 起页码与 pageSize 直传
 * `/recycle/list`；视图四态机留在本视图经钩子接线。
 */
const {
  items: entries,
  total,
  pageSize,
  currentPage,
  totalPages,
  paginationVisible,
  pageWindow,
  load: loadPage,
  jumpToPage,
} = usePagedList<RecycleItem>({
  fetchPage: async (page, size) => {
    const data = await recycleApi.list(page, size)
    return { items: data.items, total: data.total }
  },
  pageSizes: RECYCLE_PAGE_SIZES,
  initialPageSize: DEFAULT_RECYCLE_PAGE_SIZE,
  fallbackPageSize: DEFAULT_RECYCLE_PAGE_SIZE,
  onLoadStart: () => {
    state.value = 'loading'
  },
  onSuccess: (result) => {
    state.value = result.items.length === 0 ? 'empty' : 'content'
    contentRef.value?.scrollToTop()
  },
  onError: (error) => {
    console.error('Failed to load recycle bin', error)
    errorText.value = '加载回收站失败'
    if (entries.value.length === 0) {
      state.value = 'error'
    } else {
      showToast('刷新回收站失败')
    }
  },
  keyboardPaging: true,
})

/** 视图层加载入口（刷新/重试都回第 1 页）。 */
function load(opts?: { silent?: boolean }): Promise<void> {
  return loadPage(1, opts)
}

async function onRefresh(): Promise<void> {
  await load({ silent: true })
  refreshing.value = false
}

function onRetry(): void {
  void load()
}

/* ------------------------------------------- 客户端 title 过滤当前页 --- */

const searchQuery = ref('')

/** 行视图模型：原始条目 + 页内索引（过滤后保留原索引，选择映射不漂移）。 */
const visibleRows = computed(() => {
  const needle = searchQuery.value.trim().toLowerCase()
  return entries.value
    .map((item, index) => ({ item, index }))
    .filter(({ item }) => item.title.toLowerCase().includes(needle))
})

/* --------------------------------------------------- row presentation --- */

/** 实体 → 中文 chip 文案（WP8 契约 7 类）。 */
const ENTITY_LABELS: Record<RecycleEntity, string> = {
  favorites: '收藏',
  history: '历史',
  downloads: '下载',
  bookmarks: '书签',
  filters: '过滤',
  quickSearches: '快速搜索',
  downloadLabels: '下载标签',
}

function entityLabel(entity: RecycleEntity): string {
  return ENTITY_LABELS[entity] ?? entity
}

/** deletedAt 兼容 epoch 毫秒 / ISO 字符串（契约两种都可能）；不可解析原样回显。 */
function formatDeletedAt(value: number | string): string {
  const ts = typeof value === 'number' ? value : Date.parse(value)
  if (!Number.isFinite(ts)) return String(value)
  const date = new Date(ts)
  return `${date.toLocaleDateString()} ${date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`
}

/* ----------------------------------------------------- multi-select ----- */

/** 多选模式：长按/右键条目进入（Android onItemLongClick → choice mode）。 */
const selectMode = ref(false)
/** 选中集合：页内行索引（AppListRow id/gid 同源——key 类型契约未定）。 */
const selectedIds = ref(new Set<number>())

const restoring = ref(false)
const purging = ref(false)

function onItemMenu(index: number): void {
  selectMode.value = true
  toggleSelect(index)
}

function onItemSelect(index: number): void {
  if (selectMode.value) toggleSelect(index)
}

function toggleSelect(index: number): void {
  const next = new Set(selectedIds.value)
  if (next.has(index)) {
    next.delete(index)
  } else {
    next.add(index)
  }
  selectedIds.value = next
}

function selectAll(): void {
  // 全选当前已加载页（回收站按页操作，服务端无 all 模式契约）。
  selectedIds.value = new Set(entries.value.map((_, index) => index))
}

function exitSelectMode(): void {
  selectMode.value = false
  selectedIds.value = new Set()
}

/** 已选行 → restore/purge 定位单元（entity+key 原样回传）。 */
function selectedTargets(): RecycleTarget[] {
  const targets: RecycleTarget[] = []
  for (const index of selectedIds.value) {
    const item = entries.value[index]
    if (item) targets.push({ entity: item.entity, key: item.key })
  }
  return targets
}

/** 乐观移除已选行（索引降序 splice，避免前移错位）+ total 递减。 */
function removeSelectedRows(): void {
  const indexes = [...selectedIds.value].sort((a, b) => b - a)
  let removed = 0
  for (const index of indexes) {
    if (index < entries.value.length) {
      entries.value.splice(index, 1)
      removed++
    }
  }
  if (removed > 0) {
    total.value = Math.max(0, total.value - removed)
    if (entries.value.length === 0) state.value = 'empty'
  }
  exitSelectMode()
}

/* -------------------------------------------------------------- FAB ---- */

const fabExpanded = ref(false)

const fabActions: FabAction[] = [
  { id: 'purge-expired', icon: 'delete-dark', label: '清除到期条目' },
  { id: 'scroll-top', icon: 'go-to-dark', label: '回顶部' },
]

function onFabAction(action: FabAction): void {
  fabExpanded.value = false
  if (action.id === 'purge-expired') {
    openPurgeDialog('expired')
  } else if (action.id === 'scroll-top') {
    contentRef.value?.scrollToTop()
  }
}

/* ------------------------------------------------- confirm dialogs ----- */

const showRestoreDialog = ref(false)
const showPurgeDialog = ref(false)
/** 清除范围：selected = 多选选中项；expired = 全部到期（purge() 无 items）。 */
const purgeScope = ref<'selected' | 'expired'>('selected')

const purgeMessage = computed(() =>
  purgeScope.value === 'selected'
    ? `立即清除选中的 ${selectedIds.value.size} 项？清除后不可恢复。`
    : '立即清除全部到期条目？清除后不可恢复。',
)

function openRestoreDialog(): void {
  if (selectedIds.value.size === 0) return
  showRestoreDialog.value = true
}

function openPurgeDialog(scope: 'selected' | 'expired'): void {
  if (scope === 'selected' && selectedIds.value.size === 0) return
  purgeScope.value = scope
  showPurgeDialog.value = true
}

function closeRestoreDialog(): void {
  if (!restoring.value) showRestoreDialog.value = false
}

function closePurgeDialog(): void {
  if (!purging.value) showPurgeDialog.value = false
}

/** Esc 统一挂 window（C7）：打开时注册，焦点不在面板内也能关闭。 */
function onDialogKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Escape' || event.isComposing) return
  if (showRestoreDialog.value) closeRestoreDialog()
  else if (showPurgeDialog.value) closePurgeDialog()
}

watch([showRestoreDialog, showPurgeDialog], ([restoreOpen, purgeOpen]) => {
  if (restoreOpen || purgeOpen) window.addEventListener('keydown', onDialogKeydown)
  else window.removeEventListener('keydown', onDialogKeydown)
})

/* KeepAlive 停用守卫（audit P2，HistoryView 同款）：App.vue 缓存本视图——
   对话框开着离开时，window Escape 监听会随缓存实例残留并在后台误关对话框。
   停用即摘除；重新激活且对话框仍开着时摘后重挂（remove-before-add）。本
   视图搜索为客户端 computed 过滤，无防抖时钟可作废。 */
onActivated(() => {
  if (showRestoreDialog.value || showPurgeDialog.value) {
    window.removeEventListener('keydown', onDialogKeydown)
    window.addEventListener('keydown', onDialogKeydown)
  }
})

onDeactivated(() => {
  window.removeEventListener('keydown', onDialogKeydown)
})

onUnmounted(() => {
  clearTimeout(toastTimer)
  window.removeEventListener('keydown', onDialogKeydown)
})

async function confirmRestore(): Promise<void> {
  if (restoring.value) return
  const targets = selectedTargets()
  if (targets.length === 0) {
    showRestoreDialog.value = false
    return
  }
  restoring.value = true
  try {
    const { restored, failed } = await recycleApi.restore(targets)
    removeSelectedRows()
    showRestoreDialog.value = false
    showToast(failed > 0 ? `已恢复 ${restored} 项，${failed} 项恢复失败` : `已恢复 ${restored} 项`)
    // 以服务端为准静默重拉当前页（乐观移除后对账剩余条目的真实位置）。
    await loadPage(currentPage.value, { silent: true })
  } catch (error) {
    console.error('Failed to restore recycle items', error)
    showRestoreDialog.value = false
    showToast('恢复失败，请稍后重试')
  } finally {
    restoring.value = false
  }
}

async function confirmPurge(): Promise<void> {
  if (purging.value) return
  const targets: RecycleTarget[] | undefined =
    purgeScope.value === 'selected' ? selectedTargets() : undefined
  if (targets !== undefined && targets.length === 0) {
    showPurgeDialog.value = false
    return
  }
  purging.value = true
  try {
    const { purged } = await recycleApi.purge(targets)
    if (targets !== undefined) removeSelectedRows()
    showPurgeDialog.value = false
    showToast(`已清除 ${purged} 项`)
    await loadPage(currentPage.value, { silent: true })
  } catch (error) {
    console.error('Failed to purge recycle items', error)
    showPurgeDialog.value = false
    showToast('清除失败，请稍后重试')
  } finally {
    purging.value = false
  }
}

/* -------------------------------------------------------------- toast --- */

const toastMessage = ref('')
let toastTimer: ReturnType<typeof setTimeout> | undefined

function showToast(message: string): void {
  toastMessage.value = message
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toastMessage.value = ''
  }, 2400)
}

/* 首屏加载：usePagedList 不自动加载——宿主在挂载时触发（本视图无路由参数
   /偏好前置）。 */
onMounted(() => {
  void load()
})
</script>

<style scoped>
.recycle-view {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  /* Standalone PWA: push the header row + heading below the status bar /
     cutout. border-box keeps the column at 100dvh — the flex:1
     ContentLayout shrinks instead of overflowing. */
  padding-top: var(--safe-area-top);
  background: var(--color-bg);
}

.recycle-view__content {
  flex: 1;
  min-height: 0;
}

/* ------------------------------------------------------------ heading --- */
.recycle-view__heading {
  display: flex;
  align-items: baseline;
  gap: var(--spacing);
  flex-shrink: 0;
  padding: 14px max(var(--gallery-list-margin-h), 4px);
  border-bottom: 1px solid var(--color-divider);
}

.recycle-view__title {
  font-size: var(--text-super-large); /* 24sp */
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--text-color-primary);
}

.recycle-view__count {
  font-size: var(--text-super-small); /* 12sp */
  color: var(--text-color-secondary);
  font-variant-numeric: tabular-nums;
}

/* 汉堡可见视口（<720px，或横屏矮视口）：顶部标题行避让浮动汉堡。 */
@media (max-width: 719px), (min-width: 720px) and (max-height: 479.98px) {
  .recycle-view__heading {
    padding-left: var(--hamburger-clearance);
  }
}

/* ------------------------------------------------- client-side filter --- */
.search-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  margin: var(--spacing) var(--keyline-margin) 8px;
  padding: 0 10px;
  border: 1px solid var(--color-divider);
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--text-color-secondary);
}

.search-bar__input {
  flex: 1 1 auto;
  min-width: 0;
  padding: 8px 0;
  border: none;
  background: transparent;
  color: var(--text-color-primary);
  font-family: inherit;
  font-size: var(--text-small);
  outline: none;
}

.search-bar__input::placeholder {
  color: var(--text-color-secondary);
}

/* 键盘焦点可见（C7）：pill 容器内的输入框用内嵌 outline。 */
.search-bar__input:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

/* 触控目标加大（B5/C 附加项）：24px 图标钮 → 32px 命中区 + padding。 */
.search-bar__clear {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 4px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--text-color-secondary);
  cursor: pointer;
}

.search-bar__clear:hover {
  background: var(--color-surface-activated);
}

/* ----------------------------------------------------- pagination bar --- */
/* 与 HistoryView 分页条同构复刻（A4）：页码 / 每页条数。 */
.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing);
  flex-shrink: 0;
  padding: 6px max(var(--gallery-list-margin-h), 4px);
  background: var(--color-bg);
  border-bottom: 1px solid var(--color-divider);
  font-size: var(--text-super-small); /* 12sp */
  color: var(--text-color-secondary);
}

.pagination-bar__info {
  flex: 0 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

/* PC 页码窗口：直点页码 + 省略号折叠 + 前后页。 */
.pagination-bar__pages {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex: 0 1 auto;
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: none;
}

.pagination-bar__pages::-webkit-scrollbar {
  display: none;
}

.pagination-bar__page {
  min-width: 26px;
  padding: 2px 5px;
  border: 1px solid transparent;
  border-radius: var(--card-radius);
  background: transparent;
  color: var(--color-primary);
  font-family: inherit;
  font-size: var(--text-super-small);
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  transition: background-color 140ms var(--ease-decelerate-quart);
}

.pagination-bar__page:hover:not(:disabled) {
  background: var(--color-surface-activated);
}

.pagination-bar__page:disabled {
  color: var(--text-color-disabled, #9e9e9e);
  cursor: default;
}

.pagination-bar__page--active {
  background: var(--color-primary);
  color: var(--color-primary-inverse, #fff);
  border-color: var(--color-primary);
}

.pagination-bar__page--active:hover {
  background: var(--color-primary);
}

.pagination-bar__ellipsis {
  min-width: 18px;
  text-align: center;
  color: var(--text-color-secondary);
  user-select: none;
}

.pagination-bar__size {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: 0 0 auto;
  white-space: nowrap;
}

.pagination-bar__select {
  padding: 2px 6px;
  border: 1px solid var(--color-divider);
  border-radius: var(--card-radius);
  background: var(--color-surface);
  color: var(--text-color-primary);
  font-family: inherit;
  font-size: var(--text-super-small);
}

.pagination-bar__select:focus {
  outline: none;
  border-color: var(--color-primary);
}

/* -------------------------------------------------- multi-select bar ---- */
.select-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-shrink: 0;
  padding: 8px var(--keyline-margin);
  background: var(--color-bg);
  border-bottom: 1px solid var(--color-divider);
  animation: select-in 160ms var(--ease-decelerate-quart);
}

@keyframes select-in {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
}

.select-bar__count {
  flex: 0 0 auto;
  font-size: var(--text-super-small);
  font-weight: 700;
  color: var(--color-primary);
}

.select-bar__actions {
  display: flex;
  align-items: center;
  gap: 4px;
  overflow-x: auto;
  scrollbar-width: none;
}

.select-bar__actions::-webkit-scrollbar {
  display: none;
}

.select-bar__btn {
  flex: 0 0 auto;
  padding: 6px 12px;
  border: 1px solid var(--color-divider);
  border-radius: 999px;
  background: transparent;
  color: var(--text-color-primary);
  font-family: inherit;
  font-size: var(--text-super-small);
  font-weight: 600;
  cursor: pointer;
  transition: background-color 140ms var(--ease-decelerate-quart);
}

.select-bar__btn:hover:not(:disabled) {
  background: var(--color-surface-activated);
}

.select-bar__btn:disabled {
  opacity: 0.45;
  cursor: default;
}

.select-bar__btn--danger {
  border-color: color-mix(in srgb, var(--color-danger, #e5484d) 55%, transparent);
  color: var(--color-danger, #e5484d);
}

.select-bar__close {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--text-color-secondary);
  cursor: pointer;
}

.select-bar__close:hover {
  background: var(--color-surface-activated);
}

/* -------------------------------------------------------------- list ---- */
.recycle-list {
  padding: var(--gallery-list-margin-v) var(--gallery-list-margin-h)
    var(--gallery-padding-bottom-fab);
}

/* 剩余天数角标 — 绝对定位角标挂在 AppListRow 行（badge slot），次级墨色；
   ≤1 天红色强调（即将清除）。 */
.recycle-remaining {
  position: absolute;
  right: 10px;
  bottom: 10px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--text-color-secondary);
  font-size: var(--text-super-small); /* 12sp */
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  pointer-events: none;
}

.recycle-remaining--danger {
  color: var(--color-red-500);
  font-weight: 700;
}

/* 实体 chip — 元信息行的类别标签（CategoryChip 的中性变体）。 */
.recycle-entity-chip {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-primary) 10%, transparent);
  color: var(--color-primary-text, var(--color-primary-dark));
  font-size: var(--text-super-small); /* 12sp */
  white-space: nowrap;
}

/* ------------------------------------------------------------- toast --- */
.toast {
  position: fixed;
  left: 50%;
  /* The FAB cluster is offset by --safe-area-bottom (FabLayout) — carry the
     same inset so the toast keeps its distance above the FABs / home
     indicator on cutout devices. */
  bottom: calc(96px + var(--safe-area-bottom));
  transform: translateX(-50%);
  z-index: 300;
  padding: 10px 20px;
  background: var(--grey-850);
  color: var(--grey-100);
  border-radius: var(--card-radius);
  font-size: var(--text-small);
  box-shadow: 0 3px 10px var(--shadow-color);
  opacity: 1;
  animation: toast-in 220ms var(--ease-decelerate-quint);
}

@keyframes toast-in {
  from {
    opacity: 0;
    transform: translateX(-50%) translateY(10px);
  }
}

/* ------------------------------------------------------------- dialog --- */
.dialog-scrim {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--keyline-margin);
  background: var(--black-overlay);
  opacity: 1;
  animation: scrim-in 160ms linear;
}

@keyframes scrim-in {
  from {
    opacity: 0;
  }
}

.dialog {
  width: 100%;
  max-width: 360px;
  padding: 20px var(--keyline-margin) var(--spacing);
  background: var(--color-background-floating);
  border-radius: var(--card-radius);
  box-shadow: 0 6px 24px var(--shadow-color);
  opacity: 1;
  animation: dialog-in 200ms var(--ease-decelerate-quart);
}

@keyframes dialog-in {
  from {
    opacity: 0;
    transform: scale(0.96) translateY(6px);
  }
}

.dialog__title {
  margin: 0 0 var(--spacing);
  font-size: var(--text-medium); /* 18sp */
  font-weight: 600;
  color: var(--text-color-primary);
}

.dialog__message {
  margin: 0;
  font-size: var(--text-small); /* 14sp */
  line-height: 1.5;
  color: var(--text-color-secondary);
}

.dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing);
  margin-top: var(--keyline-margin);
}

.dialog__btn {
  padding: 8px 12px;
  border: none;
  border-radius: var(--card-radius);
  background: transparent;
  color: var(--button-text-color);
  font-family: inherit;
  font-size: var(--text-small);
  font-weight: 500;
  cursor: pointer;
  transition: background-color 140ms var(--ease-decelerate-quart);
}

.dialog__btn:hover {
  background: var(--color-surface-activated);
}

.dialog__btn--danger {
  color: var(--color-red-500);
}

.dialog__btn:disabled {
  color: var(--text-color-secondary);
  opacity: 0.5;
  cursor: default;
}

.dialog__btn:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}

@media (prefers-reduced-motion: reduce) {
  .dialog-scrim,
  .dialog,
  .toast {
    animation: none;
  }
}
</style>
