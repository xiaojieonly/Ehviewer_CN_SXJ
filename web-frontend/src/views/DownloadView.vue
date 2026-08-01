<template>
  <div class="download-view">
    <!-- Download label tabs (Android DownloadsScene label spinner, reimagined
         as a scrollable tab strip; active tab gets the primary underline) -->
    <nav class="label-tabs" aria-label="Download labels">
      <button
        v-for="tab in tabs"
        :key="tab.id ?? 'all'"
        type="button"
        class="label-tabs__tab"
        :class="{ 'label-tabs__tab--active': activeLabel === tab.id }"
        :aria-current="activeLabel === tab.id ? 'true' : undefined"
        @click="selectTab(tab.id, $event)"
      >
        {{ tab.name }}
      </button>
    </nav>

    <ContentLayout
      ref="contentRef"
      class="download-view__content"
      :state="state"
      v-model:refreshing="refreshing"
      empty-text="No downloads"
      error-text="Failed to load downloads"
      @refresh="onRefresh"
      @retry="onRetry"
    >
      <ul class="download-list">
        <li
          v-for="(item, index) in downloads"
          :key="item.id"
          class="download-list__item"
          :style="{ animationDelay: `${Math.min(index * 24, 240)}ms` }"
        >
          <DownloadItemCard
            :item="item"
            :speed="liveSpeeds[item.gid] ?? 0"
            @start="onStart"
            @pause="onPause"
            @cancel="onCancel"
            @delete="onDelete"
          />
        </li>
      </ul>
    </ContentLayout>

    <!-- FabLayout replica: primary 56dp FAB + mini FABs
         (scene_download.xml: play / pause / … cluster) -->
    <FabLayout
      v-model:expanded="fabExpanded"
      primary-icon="plus-dark"
      :actions="fabActions"
      @click-secondary="onFabAction"
    />

    <Teleport to="body">
      <!-- New label dialog (Android EditTextDialog replica) -->
      <div
        v-if="showLabelDialog"
        class="dialog-scrim"
        @click.self="closeLabelDialog"
      >
        <div
          class="dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="new-label-title"
          @keyup.esc="closeLabelDialog"
        >
          <h3 id="new-label-title" class="dialog__title">New label</h3>
          <input
            ref="labelInputRef"
            v-model="labelName"
            class="dialog__input"
            type="text"
            maxlength="20"
            placeholder="Label name"
            autocomplete="off"
            @keyup.enter="createLabel"
          />
          <p v-if="labelError" class="dialog__error" role="alert">{{ labelError }}</p>
          <div class="dialog__actions">
            <button type="button" class="dialog__btn" @click="closeLabelDialog">Cancel</button>
            <button
              type="button"
              class="dialog__btn dialog__btn--primary"
              :disabled="!labelName.trim() || labelSaving"
              @click="createLabel"
            >
              {{ labelSaving ? 'Creating…' : 'Create' }}
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
 * DownloadView — web replica of Android `DownloadsScene`:
 * ContentLayout (pull-to-refresh + empty tip + fast scroller) filled with
 * `item_download.xml` rows, a label filter strip (All + user labels), and the
 * scene's FabLayout cluster (start all / pause all / new label).
 *
 * Real-time updates: subscribes to `/topic/download/all` over the STOMP
 * WebSocket. The backend currently always publishes `speed = 0`
 * (DownloadService.publishProgress), so the transfer rate shown on each row
 * is derived client-side from progress deltas (pages/second) and used for
 * the ETA estimate.
 *
 * State model = `DownloadInfo.STATE_*`: 0 idle · 1 wait · 2 download ·
 * 3 finish · 4 failed (ehviewer-web mirrors the Android constants).
 */
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import type { StompSubscription } from '@stomp/stompjs'
import { downloadApi } from '@/api/download'
import type { DownloadItem, DownloadLabel } from '@/api/download'
import { useWebSocket } from '@/composables/useWebSocket'
import type { DownloadProgress } from '@/composables/useWebSocket'
import type { FabAction } from '@/types/components'
import ContentLayout from '@/components/layout/ContentLayout.vue'
import FabLayout from '@/components/atoms/FabLayout.vue'
import DownloadItemCard from '@/components/download/DownloadItem.vue'

/** View states matching ContentLayout's internal ViewTransition. */
type ViewState = 'loading' | 'content' | 'empty' | 'error'

/** Android `DownloadInfo.STATE_*` used for optimistic UI updates. */
const STATE_NONE = 0
const STATE_WAIT = 1
const STATE_DOWNLOAD = 2
const STATE_FINISH = 3
const STATE_FAILED = 4

/* ------------------------------------------------------------- list ----- */

const downloads = ref<DownloadItem[]>([])
const labels = ref<DownloadLabel[]>([])
const activeLabel = ref<number | null>(null)
const state = ref<ViewState>('loading')
const refreshing = ref(false)
const contentRef = ref<InstanceType<typeof ContentLayout> | null>(null)

interface LabelTab {
  id: number | null
  name: string
}

const tabs = computed<LabelTab[]>(() => [
  { id: null, name: 'All' },
  ...labels.value.map((label) => ({ id: label.id, name: label.label })),
])

/** Monotonic request guard — stale responses (fast label switches) drop. */
let requestSeq = 0

async function load(): Promise<void> {
  const seq = ++requestSeq
  try {
    const result = await downloadApi.list(activeLabel.value ?? undefined)
    if (seq !== requestSeq) return
    downloads.value = result.downloads
    labels.value = result.labels
    state.value = result.downloads.length === 0 ? 'empty' : 'content'
  } catch (error) {
    if (seq !== requestSeq) return
    console.error('Failed to load downloads', error)
    if (downloads.value.length === 0) {
      state.value = 'error'
    } else {
      showToast('Failed to refresh downloads')
    }
  }
}

function selectTab(id: number | null, event: MouseEvent): void {
  if (id === activeLabel.value) return
  activeLabel.value = id
  const el = event.currentTarget
  if (el instanceof HTMLElement) {
    el.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' })
  }
  state.value = 'loading'
  void load()
}

async function onRefresh(): Promise<void> {
  await load()
  refreshing.value = false
}

function onRetry(): void {
  state.value = 'loading'
  void load()
}

/* -------------------------------------------------------- row actions --- */

/**
 * Runs a per-item API action with an optimistic state flip; on failure the
 * previous state is restored and a toast explains what happened.
 */
async function runItemAction(
  id: number,
  action: (id: number) => Promise<boolean>,
  nextState: number,
): Promise<void> {
  const item = downloads.value.find((entry) => entry.id === id)
  const previousState = item?.state
  if (item) item.state = nextState
  try {
    await action(id)
  } catch (error) {
    console.error('Download action failed', error)
    if (item && previousState !== undefined) item.state = previousState
    showToast('Operation failed')
  }
}

function onStart(id: number): void {
  void runItemAction(id, downloadApi.start, STATE_WAIT)
}

function onPause(id: number): void {
  void runItemAction(id, downloadApi.pause, STATE_NONE)
}

function onCancel(id: number): void {
  void runItemAction(id, downloadApi.cancel, STATE_NONE)
}

async function onDelete(id: number): Promise<void> {
  const index = downloads.value.findIndex((entry) => entry.id === id)
  if (index === -1) return
  const [removed] = downloads.value.splice(index, 1)
  if (downloads.value.length === 0) state.value = 'empty'
  try {
    await downloadApi.delete(id)
  } catch (error) {
    console.error('Failed to delete download', error)
    downloads.value.splice(index, 0, removed)
    state.value = 'content'
    showToast('Delete failed')
  }
}

/* -------------------------------------------------------------- FAB ----- */

const fabExpanded = ref(false)

const fabActions: FabAction[] = [
  { id: 'start-all', icon: 'play-dark', label: 'Start all' },
  { id: 'pause-all', icon: 'pause-dark', label: 'Pause all' },
  { id: 'new-label', icon: 'folder-add-dark', label: 'New label' },
]

function onFabAction(action: FabAction): void {
  fabExpanded.value = false
  if (action.id === 'start-all') void startAll()
  else if (action.id === 'pause-all') void pauseAll()
  else if (action.id === 'new-label') openLabelDialog()
}

async function startAll(): Promise<void> {
  try {
    await downloadApi.startAll()
    for (const item of downloads.value) {
      if (item.state === STATE_NONE || item.state === STATE_FAILED) {
        item.state = STATE_WAIT
      }
    }
    showToast('All downloads started')
  } catch (error) {
    console.error('Failed to start all downloads', error)
    showToast('Failed to start downloads')
  }
}

/** The API exposes no pause-all endpoint — fan out over the active rows. */
async function pauseAll(): Promise<void> {
  const active = downloads.value.filter(
    (item) => item.state === STATE_WAIT || item.state === STATE_DOWNLOAD,
  )
  if (active.length === 0) {
    showToast('No active downloads')
    return
  }
  try {
    await Promise.all(active.map((item) => downloadApi.pause(item.id)))
    for (const item of active) item.state = STATE_NONE
    showToast('Downloads paused')
  } catch (error) {
    console.error('Failed to pause downloads', error)
    showToast('Failed to pause downloads')
    await load()
  }
}

/* ---------------------------------------------------- new label dialog -- */

const showLabelDialog = ref(false)
const labelName = ref('')
const labelSaving = ref(false)
const labelError = ref('')
const labelInputRef = ref<HTMLInputElement | null>(null)

function openLabelDialog(): void {
  labelName.value = ''
  labelError.value = ''
  showLabelDialog.value = true
}

function closeLabelDialog(): void {
  showLabelDialog.value = false
}

watch(showLabelDialog, async (open) => {
  if (open) {
    await nextTick()
    labelInputRef.value?.focus()
  }
})

async function createLabel(): Promise<void> {
  const name = labelName.value.trim()
  if (!name || labelSaving.value) return
  labelSaving.value = true
  labelError.value = ''
  try {
    await downloadApi.createLabel(name)
    showLabelDialog.value = false
    showToast(`Label “${name}” created`)
    await load()
  } catch (error) {
    console.error('Failed to create label', error)
    labelError.value = 'Failed to create label'
  } finally {
    labelSaving.value = false
  }
}

/* ------------------------------------------------------------- toast ---- */

const toastMessage = ref('')
let toastTimer: ReturnType<typeof setTimeout> | undefined

function showToast(message: string): void {
  toastMessage.value = message
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toastMessage.value = ''
  }, 2400)
}

/* ------------------------------------------------- WebSocket progress --- */

const { connected, connect, subscribeAll } = useWebSocket()

/** gid → live transfer rate in pages/second, fed to each row. */
const liveSpeeds = ref<Record<number, number>>({})

/** gid → last progress sample, used to derive speed from deltas. */
const speedSamples = new Map<number, { done: number; time: number }>()

let progressSubscription: StompSubscription | null = null

function handleProgress(progress: DownloadProgress): void {
  const item = downloads.value.find((entry) => entry.gid === progress.gid)
  if (item) {
    item.state = progress.state
    item.done = progress.downloaded
    if (progress.total > 0) item.total = progress.total
    item.label = progress.label
  }

  const now = Date.now()
  const previous = speedSamples.get(progress.gid)
  let speed = progress.speed
  if (speed <= 0 && previous && now > previous.time && progress.downloaded > previous.done) {
    speed = ((progress.downloaded - previous.done) * 1000) / (now - previous.time)
  }

  if (progress.state === STATE_FINISH || progress.state === STATE_FAILED) {
    speedSamples.delete(progress.gid)
    liveSpeeds.value[progress.gid] = 0
  } else {
    speedSamples.set(progress.gid, { done: progress.downloaded, time: now })
    liveSpeeds.value[progress.gid] = speed
  }
}

/* Subscribe once the STOMP session is up (subscribeAll is a no-op before). */
watch(
  connected,
  (up) => {
    if (up && !progressSubscription) {
      progressSubscription = subscribeAll(handleProgress) ?? null
    }
  },
  { immediate: true },
)

onMounted(() => {
  connect()
  void load()
})

onUnmounted(() => {
  clearTimeout(toastTimer)
})
</script>

<style scoped>
.download-view {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  /* Standalone PWA: push the header row + label tabs below the status bar /
     cutout. border-box keeps the column at 100dvh — the flex:1
     ContentLayout shrinks instead of overflowing. The list bottom already
     clears the home indicator via --gallery-padding-bottom-fab. */
  padding-top: var(--safe-area-top);
  background: var(--color-bg);
}

.download-view__content {
  flex: 1;
  min-height: 0;
}

/* -------------------------------------------------------- label tabs --- */
.label-tabs {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
  overflow-x: auto;
  padding: 0 max(var(--gallery-list-margin-h), 4px);
  background: var(--color-bg);
  border-bottom: 1px solid var(--color-divider);
  scrollbar-width: none;
}

.label-tabs::-webkit-scrollbar {
  display: none;
}

.label-tabs__tab {
  position: relative;
  flex: 0 0 auto;
  padding: 12px 16px;
  border: none;
  background: transparent;
  color: var(--text-color-secondary);
  font-family: inherit;
  font-size: var(--text-small); /* 14sp */
  font-weight: 500;
  letter-spacing: 0.02em;
  cursor: pointer;
  transition: color 160ms var(--ease-decelerate-quart);
}

.label-tabs__tab::after {
  content: '';
  position: absolute;
  left: 8px;
  right: 8px;
  bottom: 0;
  height: 2px;
  border-radius: 1px;
  background: var(--color-primary);
  transform: scaleX(0);
  transform-origin: center;
  transition: transform 200ms var(--ease-decelerate-quart);
}

.label-tabs__tab:hover {
  color: var(--text-color-primary);
}

.label-tabs__tab--active {
  color: var(--text-color-primary);
}

.label-tabs__tab--active::after {
  transform: scaleX(1);
}

.label-tabs__tab:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

/* -------------------------------------------------------------- list ---- */
.download-list {
  list-style: none;
  margin: 0;
  padding: var(--gallery-list-margin-v) var(--gallery-list-margin-h)
    var(--gallery-padding-bottom-fab);
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, var(--column-width-list-long)), 1fr));
}

.download-list__item {
  animation: item-in 240ms var(--ease-decelerate-quart) both;
}

@keyframes item-in {
  from {
    opacity: 0;
    transform: translateY(6px);
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
  animation: scrim-in 160ms linear both;
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
  animation: dialog-in 200ms var(--ease-decelerate-quart) both;
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

.dialog__input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--color-divider);
  border-radius: var(--card-radius);
  background: var(--color-bg);
  color: var(--text-color-primary);
  font-family: inherit;
  font-size: var(--text-little-small); /* 16sp */
  transition: border-color 140ms var(--ease-decelerate-quart);
}

.dialog__input::placeholder {
  color: var(--text-color-secondary);
}

.dialog__input:focus {
  outline: none;
  border-color: var(--color-primary);
}

.dialog__error {
  margin: 6px 0 0;
  font-size: var(--text-super-small);
  color: var(--color-red-500);
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

.dialog__btn:disabled {
  color: var(--text-color-secondary);
  opacity: 0.5;
  cursor: default;
}

.dialog__btn:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}

/* -------------------------------------------------------------- toast --- */
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
  animation: toast-in 220ms var(--ease-decelerate-quint) both;
}

@keyframes toast-in {
  from {
    opacity: 0;
    transform: translateX(-50%) translateY(10px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .download-list__item,
  .dialog-scrim,
  .dialog,
  .toast {
    animation: none;
  }
}
</style>
