<template>
  <div class="gallery-list-shell">
    <!-- B-1 view-mode toggle — the single control shared by Home / History /
         Favorites. Switching writes `general.listMode` through the
         preferences store (debounced PUT /preferences); localStorage is no
         longer involved. Sticky so it stays reachable while the list
         scrolls; the tall top padding doubles as the floating-SearchBar
         clearance on HomeView (`--gallery-list-clear-top`). -->
    <div class="gallery-list__toolbar">
      <div class="gallery-list__mode-toggle" role="group" aria-label="列表布局模式">
        <button
          v-for="option in MODE_OPTIONS"
          :key="option.mode"
          type="button"
          class="gallery-list__mode-btn"
          :class="{ 'gallery-list__mode-btn--active': mode === option.mode }"
          :aria-pressed="mode === option.mode"
          :aria-label="option.label"
          :title="option.label"
          @click="setMode(option.mode)"
        >
          <AppIcon :name="option.icon" size="16px" />
        </button>
      </div>
    </div>

    <!-- Grid form: tile grid (GalleryGrid, auto-column per the responsive
         strategy contract). -->
    <GalleryGrid v-if="mode === 'grid'" :items="items" @select="(g) => emit('select', g)">
      <template #cell-extra="{ gallery, index }">
        <slot name="item-extra" :gallery="gallery" :index="index" />
      </template>
      <!-- F-UX1: optional in-card meta sub line for the grid form (History's
           last-viewed stamp flows below the title). Threaded only when the
           consumer supplies it, so Home / Favorites keep their single-line
           grid meta. -->
      <template v-if="$slots['item-sub']" #cell-sub="{ gallery, index }">
        <slot name="item-sub" :gallery="gallery" :index="index" />
      </template>
    </GalleryGrid>

    <!-- List form: rows of horizontal cards. Auto-column at the 480dp
         `column-width-list-long` (AutoStaggeredGridLayoutManager, List/Long),
         so wide screens keep the multi-column rows History/Favorites have
         always used. Entrance keeps the T-1 WebKit-safe style: fill
         `backwards` + natural-state opacity 1, staggered inline delay. -->
    <ul v-else class="gallery-list__rows">
      <li
        v-for="(gallery, index) in items"
        :key="gallery.gid"
        class="gallery-list__row"
        :style="{ animationDelay: `${Math.min(index * 24, 240)}ms` }"
      >
        <GalleryCard :gallery="gallery" mode="list" @click="(g) => emit('select', g)" />
        <slot name="item-extra" :gallery="gallery" :index="index" />
      </li>
    </ul>
  </div>
</template>

<script lang="ts">
/** Layout modes of the gallery list (Android `Settings` list mode). */
export type ListMode = 'list' | 'grid'

/**
 * Resolves a raw `general.listMode` value to a renderable mode. Unknown,
 * missing or stale values fall back to `grid` — the safe default while the
 * preferences schema is extended by a parallel work stream (defensive read,
 * no hard dependency on the key existing).
 */
export function resolveListMode(raw: unknown): ListMode {
  return raw === 'list' || raw === 'grid' ? raw : 'grid'
}
</script>

<script setup lang="ts">
/**
 * GalleryList — the unified gallery list (B-1, resolves R4-1): Home,
 * Favorites and History all render through this component instead of each
 * carrying its own hardcoded layout.
 *
 * - Consumes `prefs.general.listMode` (`grid` | `list`) reactively; a
 *   missing/unloaded preference falls back to the `grid` form.
 * - Provides the view-mode toggle control. Switching writes the preference
 *   back through the preferences store (debounced `PUT /preferences`) — the
 *   legacy HomeView localStorage persistence is gone.
 * - Grid form delegates to `GalleryGrid` (auto-column tiles); list form
 *   renders rows of horizontal `GalleryCard`s with the T-1 WebKit-safe
 *   entrance (fill `backwards`, natural-state `opacity: 1`).
 * - The `item-extra` scoped slot threads per-item addons (History's
 *   last-viewed badge, Favorites' slot badge) into both forms.
 * - The `item-sub` scoped slot (F-UX1) threads an in-card meta sub line
 *   into the grid form only (History's last-viewed stamp flowing below the
 *   title instead of overlapping it).
 *
 * Preference access is defensive (optional chaining + defaults) because the
 * schema keys are being added by a parallel work stream.
 */
import { computed, onMounted } from 'vue'
import { usePreferencesStore } from '@/stores/preferences'
import AppIcon from '@/components/atoms/AppIcon.vue'
import GalleryGrid from './GalleryGrid.vue'
import GalleryCard from './GalleryCard.vue'
import type { GalleryInfo } from '@/types/components'

defineProps<{
  /** Galleries to render (HomeView hands over its virtualized window). */
  items: GalleryInfo[]
}>()

const emit = defineEmits<{
  /** Card tapped — open the gallery detail. */
  (e: 'select', gallery: GalleryInfo): void
}>()

const preferencesStore = usePreferencesStore()

/** Kick a one-shot preferences load (no-op when loaded/loading already). */
onMounted(() => {
  if (!preferencesStore.prefs && !preferencesStore.loading) {
    void preferencesStore.load()
  }
})

/** Active layout — `grid` while the preference is absent (see resolveListMode). */
const mode = computed<ListMode>(() =>
  resolveListMode(preferencesStore.prefs?.general?.listMode),
)

/** Switch the layout and persist it via the preferences API (no localStorage). */
function setMode(next: ListMode): void {
  if (next === mode.value) return
  preferencesStore.updateGeneral({ listMode: next })
}

function toggleMode(): void {
  setMode(mode.value === 'grid' ? 'list' : 'grid')
}

defineExpose({ mode, setMode, toggleMode })

/**
 * Toggle buttons. Icons follow the established HomeView FAB semantics:
 * `top-lists` = grid, `reorder` = list.
 */
const MODE_OPTIONS: Array<{ mode: ListMode; icon: string; label: string }> = [
  { mode: 'grid', icon: 'top-lists', label: '网格视图' },
  { mode: 'list', icon: 'reorder', label: '列表视图' },
]
</script>

<style scoped>
.gallery-list-shell {
  position: relative;
}

/* --------------------------------------------------------------------------
   View-mode toggle toolbar — sticky so it stays reachable mid-scroll.
   `--gallery-list-clear-top` (set by HomeView to the floating-SearchBar
   clearance) pads the bar, which both clears the SearchBar at rest and keeps
   the buttons parked just below it while pinned; the solid background masks
   rows scrolling underneath. History/Favorites leave the variable unset
   (no floating bar → compact bar).
   -------------------------------------------------------------------------- */
.gallery-list__toolbar {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  justify-content: flex-end;
  padding:
    calc(var(--gallery-list-clear-top, 0px) + 6px)
    max(var(--gallery-list-margin-h), 4px)
    2px;
  background: var(--color-bg);
}

.gallery-list__mode-toggle {
  display: inline-flex;
  border: 1px solid var(--color-divider);
  border-radius: var(--card-radius); /* 2dp — CheckTextView corner, not a pill */
  background: var(--color-background-floating);
  overflow: hidden;
}

.gallery-list__mode-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 28px;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--text-color-secondary);
  cursor: pointer;
  transition:
    background-color 160ms var(--ease-decelerate-quart),
    color 160ms var(--ease-decelerate-quart);
}

.gallery-list__mode-btn + .gallery-list__mode-btn {
  border-left: 1px solid var(--color-divider);
}

.gallery-list__mode-btn:hover {
  color: var(--text-color-primary);
}

.gallery-list__mode-btn:active {
  transform: scale(0.94);
}

.gallery-list__mode-btn--active {
  background: var(--color-primary);
  color: var(--color-white);
}

.gallery-list__mode-btn--active:hover {
  color: var(--color-white);
}

.gallery-list__mode-btn:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

/* --------------------------------------------------------------------------
   List form — rows of horizontal cards, AutoStaggeredGridLayoutManager
   (List/Long 480dp) auto-columns: `floor(availableWidth / 480px)`, min(…,
   100%) so narrow containers keep a single column without overflow.
   Bottom padding clears the FAB cluster.
   -------------------------------------------------------------------------- */
.gallery-list__rows {
  list-style: none;
  margin: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, var(--column-width-list-long)), 1fr));
  padding:
    var(--gallery-list-margin-v)
    var(--gallery-list-margin-h)
    var(--gallery-padding-bottom-fab);
}

.gallery-list__row {
  position: relative;
  /* `backwards` (not `both`): natural state opacity 1, never stuck invisible
     when WebKit fails to run the staggered entrance (T-1 regression). */
  opacity: 1;
  animation: item-in 240ms var(--ease-decelerate-quart) backwards;
}

@keyframes item-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .gallery-list__row {
    animation: none;
  }
}
</style>
