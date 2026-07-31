<!--
  SettingsView.vue — Android PreferenceScreen-style settings (S6 rework).

  Structure mirrors the Android settings screen:
    - section headers — 14sp uppercase, `var(--color-primary)`;
    - groups rendered on the app card surface (2dp radius / 2dp elevation,
      `--color-background-floating`); NOTE: the frozen `AppCard.vue` atom is
      implemented as a *gallery* card (requires a `gallery` prop), so the
      group surface replicates the same card spec directly instead;
    - rows — icon + title + summary/widget, 48px minimum height;
    - switches — `var(--color-accent)` when checked;
    - inputs — `var(--color-divider)` border, `var(--color-primary)` on focus.

  Persistence is split by ownership:
    - server-backed (PUT /settings, debounced): download path, worker count,
      cache size — the fields `settingsApi` actually exposes;
    - client-backed (localStorage `ehviewer-ui-settings`): theme (via the
      theme store), gallery-list/reader preferences, download auto-start —
      UI concerns the REST API does not model.
-->
<template>
  <div class="settings-scene">
    <NavigationDrawer
      v-model:open="drawerOpen"
      :items="DEFAULT_NAV_ITEMS"
      active-item-id="settings"
      :username="authStore.username ?? undefined"
      :theme="themeStore.currentTheme"
      @select="onNavSelect"
      @toggle-theme="themeStore.toggleTheme()"
    />

    <div class="settings-scene__main">
      <header class="toolbar">
        <button
          type="button"
          class="toolbar__nav"
          aria-label="Open menu"
          @click="drawerOpen = true"
        >
          <AppIcon name="reorder" />
        </button>
        <h1 class="toolbar__title">Settings</h1>
        <Transition name="saved">
          <span v-if="savedFlash" class="toolbar__saved" role="status">Saved</span>
        </Transition>
      </header>

      <main class="settings-body">
        <div class="settings-column">
          <!-- ═══ Account ═══════════════════════════════════════════════ -->
          <section class="pref-group">
            <h2 class="pref-group__title">Account</h2>
            <div class="pref-card">
              <div class="pref">
                <span class="pref__avatar" aria-hidden="true">{{ userInitial }}</span>
                <div class="pref__text">
                  <span class="pref__title">{{ authStore.username ?? 'Guest' }}</span>
                  <span class="pref__summary">
                    {{ authStore.isAuthenticated ? 'Signed in to this server' : 'Not signed in' }}
                  </span>
                </div>
              </div>
              <div class="pref-divider" />
              <button type="button" class="pref pref--action" @click="confirmLogout">
                <AppIcon name="delete-red" class="pref__icon" />
                <div class="pref__text">
                  <span class="pref__title pref__title--danger">Log out</span>
                  <span class="pref__summary">End the session on this device</span>
                </div>
              </button>
            </div>
          </section>

          <!-- ═══ Theme ═════════════════════════════════════════════════ -->
          <section class="pref-group">
            <h2 class="pref-group__title">Theme</h2>
            <div class="pref-card">
              <div class="pref">
                <AppIcon name="chart-accent" class="pref__icon" />
                <div class="pref__text">
                  <span class="pref__title">Appearance</span>
                  <span class="pref__summary">Current: {{ themeLabel }}</span>
                </div>
                <div class="segment" role="radiogroup" aria-label="Theme">
                  <button
                    v-for="option in THEME_OPTIONS"
                    :key="option.value"
                    type="button"
                    class="segment__btn"
                    role="radio"
                    :aria-checked="themeStore.currentTheme === option.value"
                    @click="themeStore.setTheme(option.value)"
                  >
                    {{ option.label }}
                  </button>
                </div>
              </div>
            </div>
          </section>

          <!-- ═══ Gallery list ══════════════════════════════════════════ -->
          <section class="pref-group">
            <h2 class="pref-group__title">Gallery list</h2>
            <div class="pref-card">
              <div class="pref">
                <AppIcon name="reorder" class="pref__icon" />
                <div class="pref__text">
                  <span class="pref__title">List size</span>
                  <span class="pref__summary">Card height in list layout</span>
                </div>
                <div class="segment" role="radiogroup" aria-label="List size">
                  <button
                    v-for="option in LIST_SIZE_OPTIONS"
                    :key="option.value"
                    type="button"
                    class="segment__btn"
                    role="radio"
                    :aria-checked="ui.listSize === option.value"
                    @click="ui.listSize = option.value"
                  >
                    {{ option.label }}
                  </button>
                </div>
              </div>
              <div class="pref-divider" />
              <div class="pref">
                <AppIcon name="book-open" class="pref__icon" />
                <div class="pref__text">
                  <span class="pref__title">Grid size</span>
                  <span class="pref__summary">Thumbnail column width in grid layout</span>
                </div>
                <div class="segment" role="radiogroup" aria-label="Grid size">
                  <button
                    v-for="option in GRID_SIZE_OPTIONS"
                    :key="option.value"
                    type="button"
                    class="segment__btn"
                    role="radio"
                    :aria-checked="ui.gridSize === option.value"
                    @click="ui.gridSize = option.value"
                  >
                    {{ option.label }}
                  </button>
                </div>
              </div>
              <div class="pref-divider" />
              <div class="pref">
                <AppIcon name="info-outline-dark" class="pref__icon" />
                <div class="pref__text">
                  <span class="pref__title">Show tags</span>
                  <span class="pref__summary">Display tag chips on gallery cards</span>
                </div>
                <button
                  type="button"
                  class="switch"
                  role="switch"
                  :aria-checked="ui.showTags"
                  aria-label="Show tags"
                  @click="ui.showTags = !ui.showTags"
                >
                  <span class="switch__thumb" />
                </button>
              </div>
            </div>
          </section>

          <!-- ═══ Reader ════════════════════════════════════════════════ -->
          <section class="pref-group">
            <h2 class="pref-group__title">Reader</h2>
            <div class="pref-card">
              <div class="pref">
                <AppIcon name="mobile-hand-left" class="pref__icon" />
                <div class="pref__text">
                  <span class="pref__title">Reading direction</span>
                  <span class="pref__summary">{{ directionLabel }}</span>
                </div>
                <div class="segment" role="radiogroup" aria-label="Reading direction">
                  <button
                    v-for="option in DIRECTION_OPTIONS"
                    :key="option.value"
                    type="button"
                    class="segment__btn"
                    role="radio"
                    :aria-checked="ui.readingDirection === option.value"
                    @click="ui.readingDirection = option.value"
                  >
                    {{ option.label }}
                  </button>
                </div>
              </div>
              <div class="pref-divider" />
              <div class="pref">
                <AppIcon name="book-open-primary" class="pref__icon" />
                <div class="pref__text">
                  <span class="pref__title">Default page mode</span>
                  <span class="pref__summary">{{ pageModeLabel }}</span>
                </div>
                <div class="segment" role="radiogroup" aria-label="Default page mode">
                  <button
                    v-for="option in PAGE_MODE_OPTIONS"
                    :key="option.value"
                    type="button"
                    class="segment__btn"
                    role="radio"
                    :aria-checked="ui.pageMode === option.value"
                    @click="ui.pageMode = option.value"
                  >
                    {{ option.label }}
                  </button>
                </div>
              </div>
              <div class="pref-divider" />
              <div class="pref">
                <AppIcon name="play-dark" class="pref__icon" />
                <div class="pref__text">
                  <span class="pref__title">Auto-play interval</span>
                  <span class="pref__summary">Seconds per page in auto-play</span>
                </div>
                <div class="stepper">
                  <button
                    type="button"
                    class="stepper__btn"
                    aria-label="Decrease interval"
                    :disabled="ui.autoPlayInterval <= 1"
                    @click="ui.autoPlayInterval = Math.max(1, ui.autoPlayInterval - 1)"
                  >
                    −
                  </button>
                  <span class="stepper__value">{{ ui.autoPlayInterval }}s</span>
                  <button
                    type="button"
                    class="stepper__btn"
                    aria-label="Increase interval"
                    :disabled="ui.autoPlayInterval >= 30"
                    @click="ui.autoPlayInterval = Math.min(30, ui.autoPlayInterval + 1)"
                  >
                    +
                  </button>
                </div>
              </div>
            </div>
          </section>

          <!-- ═══ Download ══════════════════════════════════════════════ -->
          <section class="pref-group">
            <h2 class="pref-group__title">Download</h2>
            <div class="pref-card">
              <button type="button" class="pref pref--action" @click="openPathDialog">
                <AppIcon name="folder-share-dark" class="pref__icon" />
                <div class="pref__text">
                  <span class="pref__title">Download path</span>
                  <span class="pref__summary">{{ server?.download.path || 'Not set' }}</span>
                </div>
                <AppIcon name="pencil-dark" class="pref__chevron" size="20px" />
              </button>
              <div class="pref-divider" />
              <div class="pref">
                <AppIcon name="download-dark" class="pref__icon" />
                <div class="pref__text">
                  <span class="pref__title">Concurrent downloads</span>
                  <span class="pref__summary">Worker threads fetching images</span>
                </div>
                <div class="stepper">
                  <button
                    type="button"
                    class="stepper__btn"
                    aria-label="Fewer concurrent downloads"
                    :disabled="!server || server.download.workerCount <= 1"
                    @click="bumpWorkers(-1)"
                  >
                    −
                  </button>
                  <span class="stepper__value">{{ server?.download.workerCount ?? '–' }}</span>
                  <button
                    type="button"
                    class="stepper__btn"
                    aria-label="More concurrent downloads"
                    :disabled="!server || server.download.workerCount >= 10"
                    @click="bumpWorkers(1)"
                  >
                    +
                  </button>
                </div>
              </div>
              <div class="pref-divider" />
              <div class="pref">
                <AppIcon name="go-to-dark" class="pref__icon" />
                <div class="pref__text">
                  <span class="pref__title">Auto-start downloads</span>
                  <span class="pref__summary">Resume queued downloads immediately</span>
                </div>
                <button
                  type="button"
                  class="switch"
                  role="switch"
                  :aria-checked="ui.autoStartDownload"
                  aria-label="Auto-start downloads"
                  @click="ui.autoStartDownload = !ui.autoStartDownload"
                >
                  <span class="switch__thumb" />
                </button>
              </div>
            </div>
          </section>

          <!-- ═══ Advanced ══════════════════════════════════════════════ -->
          <section class="pref-group">
            <h2 class="pref-group__title">Advanced</h2>
            <div class="pref-card">
              <button type="button" class="pref pref--action" @click="confirmClearCache">
                <AppIcon name="clear-all-dark" class="pref__icon" />
                <div class="pref__text">
                  <span class="pref__title">Clear cache</span>
                  <span class="pref__summary">
                    Cached thumbnails and local data
                    <template v-if="server"> · {{ server.cache.sizeMb }} MB reserved</template>
                  </span>
                </div>
              </button>
              <div class="pref-divider" />
              <button type="button" class="pref pref--action" @click="confirmClearHistory">
                <AppIcon name="history-black" class="pref__icon" />
                <div class="pref__text">
                  <span class="pref__title">Clear search history</span>
                  <span class="pref__summary">Remove all recent search keywords</span>
                </div>
              </button>
              <div class="pref-divider" />
              <button type="button" class="pref pref--action" @click="aboutOpen = true">
                <AppIcon name="info-dark" class="pref__icon" />
                <div class="pref__text">
                  <span class="pref__title">About</span>
                  <span class="pref__summary">Version and credits</span>
                </div>
              </button>
            </div>
          </section>
        </div>
      </main>
    </div>

    <!-- Edit download path dialog. -->
    <Transition name="dialog">
      <div v-if="pathDialogOpen" class="dialog-scrim" @click.self="pathDialogOpen = false">
        <div class="dialog" role="dialog" aria-modal="true" aria-label="Download path">
          <h2 class="dialog__title">Download path</h2>
          <label class="field">
            <input
              v-model="pathDraft"
              type="text"
              placeholder=" "
              @keydown.enter.prevent="savePath"
            />
            <span class="field__label">Server-side path</span>
          </label>
          <div class="dialog__actions">
            <button type="button" class="btn-text" @click="pathDialogOpen = false">Cancel</button>
            <button type="button" class="btn-primary" @click="savePath">Save</button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Generic confirm dialog. -->
    <Transition name="dialog">
      <div v-if="confirmState" class="dialog-scrim" @click.self="confirmState = null">
        <div class="dialog" role="dialog" aria-modal="true" :aria-label="confirmState.title">
          <h2 class="dialog__title">{{ confirmState.title }}</h2>
          <p class="dialog__message">{{ confirmState.message }}</p>
          <div class="dialog__actions">
            <button type="button" class="btn-text" @click="confirmState = null">Cancel</button>
            <button type="button" class="btn-primary btn-primary--danger" @click="runConfirm">
              {{ confirmState.confirmLabel }}
            </button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- About dialog. -->
    <Transition name="dialog">
      <div v-if="aboutOpen" class="dialog-scrim" @click.self="aboutOpen = false">
        <div class="dialog" role="dialog" aria-modal="true" aria-label="About">
          <div class="about">
            <AppIcon name="sad-panda-primary" size="56px" />
            <h2 class="about__name">AnotherViewer <span>WebUI</span></h2>
            <p class="about__version">Version 1.0.0 · companion client</p>
            <p class="about__note">
              Pixel-faithful web replica of the CN SXJ Android app —
              same tokens, same layouts, three themes.
            </p>
          </div>
          <div class="dialog__actions">
            <button type="button" class="btn-text" @click="aboutOpen = false">Close</button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Snackbar. -->
    <Transition name="snack">
      <div v-if="snack" class="snackbar" role="status">{{ snack }}</div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { NavItem } from '@/types/components'
import type { Settings } from '@/api/settings'
import { settingsApi } from '@/api/settings'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore, type Theme } from '@/stores/theme'
import NavigationDrawer, { DEFAULT_NAV_ITEMS } from '@/components/layout/NavigationDrawer.vue'
import AppIcon from '@/components/atoms/AppIcon.vue'

const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()

/* ------------------------------ option lists ----------------------------- */

const THEME_OPTIONS: ReadonlyArray<{ value: Theme; label: string }> = [
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
  { value: 'black', label: 'Black' },
]

type ListSize = 'long' | 'short'
type GridSize = 'large' | 'middle' | 'small'
type ReadingDirection = 'ltr' | 'rtl' | 'vertical'
type PageMode = 'pager' | 'scroll'

const LIST_SIZE_OPTIONS: ReadonlyArray<{ value: ListSize; label: string }> = [
  { value: 'long', label: 'Long' },
  { value: 'short', label: 'Short' },
]

const GRID_SIZE_OPTIONS: ReadonlyArray<{ value: GridSize; label: string }> = [
  { value: 'large', label: 'Large' },
  { value: 'middle', label: 'Middle' },
  { value: 'small', label: 'Small' },
]

const DIRECTION_OPTIONS: ReadonlyArray<{ value: ReadingDirection; label: string }> = [
  { value: 'ltr', label: 'LTR' },
  { value: 'rtl', label: 'RTL' },
  { value: 'vertical', label: 'Vertical' },
]

const PAGE_MODE_OPTIONS: ReadonlyArray<{ value: PageMode; label: string }> = [
  { value: 'pager', label: 'Pager' },
  { value: 'scroll', label: 'Scroll' },
]

const DIRECTION_LABELS: Readonly<Record<ReadingDirection, string>> = {
  ltr: 'Left to right',
  rtl: 'Right to left',
  vertical: 'Vertical (webtoon style)',
}

const NAV_ROUTES: Readonly<Record<string, string>> = {
  homepage: '/',
  favourite: '/favorites',
  history: '/history',
  downloads: '/downloads',
  settings: '/settings',
}

/* ----------------------------- client settings ---------------------------- */

interface UiSettings {
  listSize: ListSize
  gridSize: GridSize
  showTags: boolean
  readingDirection: ReadingDirection
  pageMode: PageMode
  autoPlayInterval: number
  autoStartDownload: boolean
}

const UI_SETTINGS_KEY = 'ehviewer-ui-settings'

const DEFAULT_UI_SETTINGS: UiSettings = {
  listSize: 'short',
  gridSize: 'middle',
  showTags: true,
  readingDirection: 'ltr',
  pageMode: 'pager',
  autoPlayInterval: 5,
  autoStartDownload: true,
}

function loadUiSettings(): UiSettings {
  try {
    const raw = localStorage.getItem(UI_SETTINGS_KEY)
    if (raw) {
      return { ...DEFAULT_UI_SETTINGS, ...(JSON.parse(raw) as Partial<UiSettings>) }
    }
  } catch {
    // Corrupt/unavailable storage — fall back to defaults.
  }
  return { ...DEFAULT_UI_SETTINGS }
}

const ui = reactive<UiSettings>(loadUiSettings())

watch(
  ui,
  (next) => {
    try {
      localStorage.setItem(UI_SETTINGS_KEY, JSON.stringify(next))
    } catch {
      // ignore write failures
    }
  },
  { deep: true },
)

/* ----------------------------- server settings ---------------------------- */

const server = ref<Settings | null>(null)
const savedFlash = ref(false)
let savedTimer: number | undefined
let saveTimer: number | undefined

/** Debounced PUT /settings — mirrors Android committing prefs on change. */
function scheduleServerSave(): void {
  if (saveTimer) window.clearTimeout(saveTimer)
  saveTimer = window.setTimeout(async () => {
    if (!server.value) return
    try {
      await settingsApi.update(server.value)
      flashSaved()
    } catch (error) {
      console.error('[SettingsView] failed to persist settings', error)
      showSnack('Could not save settings on the server')
    }
  }, 600)
}

function flashSaved(): void {
  savedFlash.value = true
  if (savedTimer) window.clearTimeout(savedTimer)
  savedTimer = window.setTimeout(() => {
    savedFlash.value = false
  }, 1600)
}

function bumpWorkers(delta: number): void {
  if (!server.value) return
  server.value.download.workerCount = Math.min(10, Math.max(1, server.value.download.workerCount + delta))
  scheduleServerSave()
}

/* ------------------------------- dialogs --------------------------------- */

const pathDialogOpen = ref(false)
const pathDraft = ref('')
const aboutOpen = ref(false)

interface ConfirmState {
  title: string
  message: string
  confirmLabel: string
  action: () => void
}
const confirmState = ref<ConfirmState | null>(null)

function openPathDialog(): void {
  pathDraft.value = server.value?.download.path ?? ''
  pathDialogOpen.value = true
}

function savePath(): void {
  if (!server.value) return
  server.value.download.path = pathDraft.value.trim()
  pathDialogOpen.value = false
  scheduleServerSave()
}

function runConfirm(): void {
  confirmState.value?.action()
  confirmState.value = null
}

function confirmLogout(): void {
  confirmState.value = {
    title: 'Log out',
    message: 'End the current session? You will return to the login screen.',
    confirmLabel: 'Log out',
    action: () => {
      void authStore.logout().then(() => router.push('/login'))
    },
  }
}

function confirmClearCache(): void {
  confirmState.value = {
    title: 'Clear cache',
    message: 'Remove locally cached thumbnails and transient data from this browser?',
    confirmLabel: 'Clear',
    action: () => {
      let cleared = 0
      for (let i = localStorage.length - 1; i >= 0; i--) {
        const key = localStorage.key(i)
        if (key?.startsWith('ehviewer-cache-')) {
          localStorage.removeItem(key)
          cleared++
        }
      }
      showSnack(cleared > 0 ? `Cleared ${cleared} cached item${cleared === 1 ? '' : 's'}` : 'Cache is already empty')
    },
  }
}

function confirmClearHistory(): void {
  confirmState.value = {
    title: 'Clear search history',
    message: 'Delete every recent search keyword stored on this device?',
    confirmLabel: 'Delete',
    action: () => {
      try {
        localStorage.removeItem('ehviewer-search-history')
      } catch {
        // ignore
      }
      showSnack('Search history cleared')
    },
  }
}

/* --------------------------------- chrome --------------------------------- */

const drawerOpen = ref(false)
const snack = ref('')
let snackTimer: number | undefined

const userInitial = computed<string>(
  () => (authStore.username ?? 'G').charAt(0).toUpperCase(),
)

const themeLabel = computed<string>(
  () => THEME_OPTIONS.find((option) => option.value === themeStore.currentTheme)?.label ?? 'Light',
)

const directionLabel = computed<string>(() => DIRECTION_LABELS[ui.readingDirection])

const pageModeLabel = computed<string>(() =>
  ui.pageMode === 'pager' ? 'Paged (swipe between pages)' : 'Continuous vertical scroll',
)

function onNavSelect(item: NavItem): void {
  router.push(NAV_ROUTES[item.id] ?? '/')
}

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
    server.value = await settingsApi.get()
  } catch (error) {
    console.error('[SettingsView] failed to load settings', error)
    showSnack('Could not load server settings')
  }
})
</script>

<style scoped>
/* Scene shell — horizontal flex; the drawer panel becomes a static sibling
   column at ≥720px (see NavigationDrawer responsive behavior). */
.settings-scene {
  display: flex;
  height: 100dvh;
  background: var(--color-bg);
  overflow: hidden;
}

.settings-scene__main {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

/* --------------------------------- toolbar -------------------------------- */

.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  /* Extend the bar up into the status-bar / cutout zone (same pattern as
     ReaderToolbar): the toolbar background fills the inset area while the
     controls sit below it, exactly like Android's tinted status bar. */
  flex: 0 0 calc(var(--toolbar-height) + var(--safe-area-top));
  padding: var(--safe-area-top) 8px 0 4px;
  background: var(--color-toolbar);
  color: var(--color-white);
  box-shadow: 0 2px 4px var(--shadow-color);
  z-index: 10;
}

.toolbar__nav {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--color-white);
  cursor: pointer;
  transition: background-color 150ms var(--ease-decelerate-quart);
}

.toolbar__nav:hover {
  background: color-mix(in srgb, var(--color-white) 12%, transparent);
}

.toolbar__title {
  margin: 0;
  font-size: clamp(17px, 20px, 24px);
  font-weight: 600;
  letter-spacing: 0.01em;
}

.toolbar__saved {
  margin-left: auto;
  margin-right: 12px;
  padding: 4px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-white) 16%, transparent);
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

/* ---------------------------------- body ---------------------------------- */

.settings-body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.settings-column {
  max-width: 760px;
  margin: 0 auto;
  /* Bottom grows by the home-indicator inset so the last preference card
     clears it when the body is scrolled to the end. */
  padding: 4px var(--keyline-margin) calc(56px + var(--safe-area-bottom));
}

/* ----------------------------- preference group --------------------------- */

.pref-group__title {
  margin: 22px 4px 8px;
  font-size: clamp(12px, 14px, 16px); /* 14sp ideal — Android preference category */
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-primary);
}

/* Card surface per roadmap §卡片规范 (AppCard atom is gallery-specific, so
   the group replicates the same 2dp radius / elevation spec). */
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
  min-height: 48px; /* Android preference item height */
  padding: 10px var(--keyline-margin);
}

button.pref {
  width: 100%;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
  font: inherit;
  transition: background-color 120ms var(--ease-decelerate-quart);
}

button.pref:hover {
  background: var(--color-surface);
}

button.pref:active {
  background: var(--color-surface-activated);
}

.pref__icon {
  flex: 0 0 24px;
  color: var(--drawable-color-primary);
}

.pref__avatar {
  flex: 0 0 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--color-primary);
  color: var(--color-white);
  font-size: clamp(16px, 18px, 22px);
  font-weight: 700;
}

.pref__text {
  flex: 1 1 160px;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.pref__title {
  font-size: clamp(14px, 16px, 18px); /* 16sp — Android preference title */
  color: var(--text-color-primary);
}

.pref__title--danger {
  color: var(--color-red-500);
}

.pref__summary {
  font-size: clamp(11px, 12px, 14px); /* 12sp — Android preference summary */
  color: var(--text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pref__chevron {
  flex: 0 0 20px;
  color: var(--drawable-color-secondary);
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

/* Checked track + thumb use the accent color (task requirement). */
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

/* ---------------------------------- stepper -------------------------------- */

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

/* Outlined material field (divider border → primary on focus). */
.field {
  position: relative;
  display: block;
}

.field input {
  width: 100%;
  padding: 14px 12px 10px;
  border: 1px solid var(--color-divider);
  border-radius: var(--card-radius);
  background: transparent;
  font-size: clamp(14px, 16px, 18px);
  color: var(--text-color-primary);
  outline: none;
  transition: border-color 150ms var(--ease-decelerate-quart);
}

.field input:focus {
  border-color: var(--color-primary);
}

.field__label {
  position: absolute;
  left: 10px;
  top: 50%;
  translate: 0 -50%;
  padding: 0 4px;
  font-size: clamp(14px, 16px, 18px);
  color: var(--text-color-secondary);
  pointer-events: none;
  transition:
    top 150ms var(--ease-decelerate-quart),
    font-size 150ms var(--ease-decelerate-quart),
    color 150ms var(--ease-decelerate-quart);
}

.field input:focus + .field__label,
.field input:not(:placeholder-shown) + .field__label {
  top: 0;
  font-size: clamp(10px, 12px, 13px);
  color: var(--color-primary);
  background: var(--color-background-floating);
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

/* ---------------------------------- about ---------------------------------- */

.about {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 8px 0 4px;
}

.about__name {
  margin: 12px 0 2px;
  font-size: clamp(20px, 24px, 28px);
  font-weight: 800;
  letter-spacing: -0.01em;
  color: var(--text-color-primary);
}

.about__name span {
  color: var(--color-primary);
}

.about__version {
  margin: 0 0 10px;
  font-size: clamp(11px, 12px, 14px);
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-color-secondary);
}

.about__note {
  margin: 0;
  max-width: 32ch;
  font-size: clamp(13px, 14px, 16px);
  line-height: 1.55;
  color: var(--text-color-secondary);
}

/* --------------------------------- snackbar -------------------------------- */

.snackbar {
  position: fixed;
  left: 50%;
  /* Clear the home indicator in standalone PWA mode (0 where absent). */
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
