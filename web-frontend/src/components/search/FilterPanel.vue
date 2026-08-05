<!--
  FilterPanel.vue — Wave-1 1a (task A5): the PC-oriented search filter
  surface for `GET /gallery/search`'s extended params (contracts/openapi.yaml
  searchGallery). Renders as an anchored popover card below the SearchBar
  (parent provides a `position: relative` anchor), toggled by the SearchBar
  filter button and the `f` shortcut.

  Sections:
    Category   — 10 toggle chips over the SiteConfig exclusion bitmask
                 (chip lit = category INCLUDED; toggling flips its bit);
    Sort       — 0 default / 1 posted desc / 2 rating desc / 3 title asc;
    Pages      — pageMin / pageMax numeric bounds;
    Min rating — 0 = disabled, 2–5;
    Scope      — searchName / searchTags / searchDesc / searchTorrents.

  The component is fully controlled (`v-model:filters` / `v-model:open`);
  the scene owns the canonical state and derives query params from it.
  `sort` has no QuickSearchDto representation — the save action therefore
  persists every field except sort (see searchFilters.ts).
-->
<template>
  <Transition name="filter-panel">
    <section
      v-if="open"
      ref="panelRef"
      class="filter-panel"
      role="dialog"
      aria-label="Search filters"
      @keydown.esc="emit('update:open', false)"
    >
      <header class="filter-panel__header">
        <span class="filter-panel__heading">Filters</span>
        <button
          type="button"
          class="filter-panel__close"
          aria-label="Close filters"
          @click="emit('update:open', false)"
        >
          <AppIcon name="close-dark" size="18px" />
        </button>
      </header>

      <div ref="bodyRef" class="filter-panel__body" @scroll="updateScrollCue">
        <!-- Keyword mode — absorbed from the legacy search surface
             (W3 R4-10 single filter surface). -->
        <div class="filter-panel__section">
          <span class="filter-panel__label">Keyword mode</span>
          <div class="filter-panel__modes" role="radiogroup" aria-label="Keyword search mode">
            <button
              v-for="option in MODE_OPTIONS"
              :key="option.value"
              type="button"
              class="filter-panel__mode"
              role="radio"
              :aria-checked="keywordMode === option.value"
              @click="emit('update:keywordMode', option.value)"
            >
              {{ option.label }}
            </button>
          </div>
        </div>

        <!-- Category chips — bitmask with SiteConfig exclusion semantics. -->
        <div class="filter-panel__section">
          <span class="filter-panel__label">Categories</span>
          <div class="filter-panel__chips" role="group" aria-label="Included categories">
            <button
              v-for="category in CATEGORY_ORDER"
              :key="category"
              type="button"
              class="filter-panel__chip"
              :class="{ 'is-included': isIncluded(category) }"
              :style="chipStyle(category)"
              :aria-pressed="isIncluded(category)"
              @click="toggleCategory(category)"
            >
              {{ CATEGORY_LABELS[category] }}
            </button>
          </div>
        </div>

        <!-- Sort order. -->
        <div class="filter-panel__row">
          <span class="filter-panel__label">Sort by</span>
          <AppSelect
            class="filter-panel__select"
            :model-value="filters.sort ?? 0"
            :options="sortOptions"
            aria-label="Sort order"
            @update:model-value="setSort"
          />
        </div>

        <!-- Page-count bounds. -->
        <div class="filter-panel__row">
          <span class="filter-panel__label">Pages</span>
          <div class="filter-panel__pages">
            <input
              type="number"
              class="filter-panel__page-input"
              min="0"
              inputmode="numeric"
              placeholder="Min"
              aria-label="Minimum page count"
              :value="filters.pageMin ?? ''"
              @input="setPageBound('pageMin', $event)"
            />
            <span class="filter-panel__page-sep">to</span>
            <input
              type="number"
              class="filter-panel__page-input"
              min="0"
              inputmode="numeric"
              placeholder="Max"
              aria-label="Maximum page count"
              :value="filters.pageMax ?? ''"
              @input="setPageBound('pageMax', $event)"
            />
          </div>
        </div>

        <!-- Minimum rating (0 = disabled). -->
        <div class="filter-panel__row">
          <span class="filter-panel__label">Minimum rating</span>
          <AppSelect
            class="filter-panel__select"
            :model-value="filters.minRating ?? 0"
            :options="ratingOptions"
            aria-label="Minimum rating"
            @update:model-value="setMinRating"
          />
        </div>

        <!-- Search scope switches. -->
        <div class="filter-panel__section">
          <span class="filter-panel__label">Search scope</span>
          <div
            v-for="scope in SCOPE_ITEMS"
            :key="scope.key"
            class="filter-panel__switch-row"
          >
            <span class="filter-panel__switch-label">{{ scope.label }}</span>
            <AppSwitch
              :model-value="filters[scope.key] ?? false"
              :aria-label="`Search ${scope.label}`"
              @update:model-value="setSwitch(scope.key, $event)"
            />
          </div>
        </div>

        <!-- Advanced options — the higher AdvanceSearchTable bits, backendized
             in W3 R4-10 (f_sto/f_sdt1/f_sdt2/f_sh/f_sfl/f_sfu/f_sft). -->
        <div class="filter-panel__section">
          <span class="filter-panel__label">Advanced options</span>
          <div
            v-for="advanced in ADVANCED_ITEMS"
            :key="advanced.key"
            class="filter-panel__switch-row"
          >
            <span class="filter-panel__switch-label">{{ advanced.label }}</span>
            <AppSwitch
              :model-value="filters[advanced.key] ?? false"
              :aria-label="advanced.label"
              @update:model-value="setSwitch(advanced.key, $event)"
            />
          </div>
        </div>
      </div>

      <footer
        class="filter-panel__footer"
        :class="{ 'filter-panel__footer--raised': scrollableBelow }"
      >
        <button type="button" class="filter-panel__btn-text" @click="reset">
          Reset
        </button>
        <button type="button" class="filter-panel__btn-text" @click="emit('save-quick-search')">
          Save as quick search
        </button>
        <button type="button" class="filter-panel__btn-primary" @click="emit('search')">
          Search
        </button>
      </footer>
    </section>
  </Transition>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import type { SearchFilters, SearchSortOrder } from '@/api/gallery'
import type { GalleryCategory, NormalSearchMode } from '@/types/components'
import {
  CATEGORY_BIT_VALUES,
  CATEGORY_COLOR_MAP,
  CATEGORY_LABELS,
  CATEGORY_ORDER,
} from '@/types/components'
import { isClickOutside } from '@/composables/useDropdownPosition'
import AppIcon from '@/components/atoms/AppIcon.vue'
import AppSelect from '@/components/form/AppSelect.vue'
import AppSwitch from '@/components/form/AppSwitch.vue'
import type { AdvanceSwitchKey } from './searchFilters'
import { ADVANCED_ITEMS, MIN_RATING_OPTIONS, SCOPE_ITEMS, SORT_OPTIONS } from './searchFilters'

const props = withDefaults(
  defineProps<{
    /** Current filter state. v-model:filters. */
    filters: SearchFilters
    /** Panel visibility. v-model:open. @default false */
    open?: boolean
    /**
     * Keyword search mode — W3 R4-10: absorbed from the legacy search
     * surface so the FilterPanel is the single filter surface.
     * v-model:keywordMode. @default 'normal'
     */
    keywordMode?: NormalSearchMode
  }>(),
  { open: false, keywordMode: 'normal' },
)

const emit = defineEmits<{
  (e: 'update:filters', filters: SearchFilters): void
  (e: 'update:open', open: boolean): void
  /** v-model:keywordMode — keyword mode radio changed (W3 R4-10). */
  (e: 'update:keywordMode', mode: NormalSearchMode): void
  /** Primary Search action — scene re-runs the query with current state. */
  (e: 'search'): void
  /** Scene should open its save-preset dialog (QuickSearchDto schema). */
  (e: 'save-quick-search'): void
}>()

const panelRef = ref<HTMLElement | null>(null)
const bodyRef = ref<HTMLElement | null>(null)

/* F-UX3 scroll cue ---------------------------------------------------------
   The body is the panel's only scroll container; the sticky-feeling footer
   sits flush below it. Without a hint, content ending right above the
   action bar reads as "panel overflows the last section" and gives no clue
   there is more to scroll. While content remains below the fold the footer
   therefore raises a top shadow (present from the moment the body
   overflows, i.e. also at scrollTop > 0; gone once the bottom is reached). */

/** True while the body has scrollable content below the current viewport. */
const scrollableBelow = ref(false)

function updateScrollCue(): void {
  const el = bodyRef.value
  if (!el) {
    scrollableBelow.value = false
    return
  }
  const overflowing = el.scrollHeight > el.clientHeight
  const atBottom = el.scrollTop + el.clientHeight >= el.scrollHeight - 1
  scrollableBelow.value = overflowing && !atBottom
}

/** Re-measures when the panel/body size changes (72dvh cap, content). */
let bodyResizeObserver: ResizeObserver | null = null

function stopBodyObserver(): void {
  bodyResizeObserver?.disconnect()
  bodyResizeObserver = null
}

const sortOptions = SORT_OPTIONS.map((option) => ({ value: option.value, label: option.label }))
const ratingOptions = MIN_RATING_OPTIONS.map((option) => ({ value: option.value, label: option.label }))

/** Keyword modes (Android `search_normal.xml` RadioGridGroup labels). */
const MODE_OPTIONS: ReadonlyArray<{ value: NormalSearchMode; label: string }> = [
  { value: 'normal', label: 'Search' },
  { value: 'subscription', label: 'Subscription' },
  { value: 'uploader', label: 'Uploader' },
  { value: 'tag', label: 'Tag' },
]

const mask = computed<number>(() => props.filters.category ?? 0)

function isIncluded(category: GalleryCategory): boolean {
  return (mask.value & CATEGORY_BIT_VALUES[category]) === 0
}

/** Included chips carry their category color; excluded chips read dimmed. */
function chipStyle(category: GalleryCategory): Record<string, string> {
  const color = CATEGORY_COLOR_MAP[category]
  return isIncluded(category)
    ? { background: color, borderColor: color, color: '#ffffff' }
    : {}
}

function toggleCategory(category: GalleryCategory): void {
  const bit = CATEGORY_BIT_VALUES[category]
  const nextMask = mask.value ^ bit
  emit('update:filters', { ...props.filters, category: nextMask === 0 ? undefined : nextMask })
}

function setSort(value: string | number): void {
  const sort = (Number(value) || 0) as SearchSortOrder
  emit('update:filters', { ...props.filters, sort: sort === 0 ? undefined : sort })
}

function setMinRating(value: string | number): void {
  const rating = Number(value) || 0
  emit('update:filters', { ...props.filters, minRating: rating > 0 ? rating : undefined })
}

function setPageBound(field: 'pageMin' | 'pageMax', event: Event): void {
  const parsed = parseInt((event.target as HTMLInputElement).value, 10)
  const next: SearchFilters = { ...props.filters }
  next[field] = Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
  emit('update:filters', next)
}

/** Scope + advanced (W3 R4-10) switches share one setter. */
function setSwitch(key: AdvanceSwitchKey, value: boolean): void {
  emit('update:filters', { ...props.filters, [key]: value || undefined })
}

function reset(): void {
  emit('update:filters', {})
}

/* Click outside the anchored popover closes it (PC affordance). */
function onDocumentMousedown(event: MouseEvent): void {
  if (props.open && isClickOutside(panelRef.value, event.target)) {
    emit('update:open', false)
  }
}

watch(
  () => props.open,
  async (open) => {
    if (open) {
      // Defer so the opening click itself is not treated as "outside".
      requestAnimationFrame(() => document.addEventListener('mousedown', onDocumentMousedown))
      // F-UX3: measure once laid out, then keep the cue honest when the
      // panel/body size changes (72dvh cap, window resize, filter edits).
      await nextTick()
      updateScrollCue()
      if (typeof ResizeObserver !== 'undefined' && bodyRef.value) {
        stopBodyObserver()
        bodyResizeObserver = new ResizeObserver(updateScrollCue)
        bodyResizeObserver.observe(bodyRef.value)
      }
    } else {
      document.removeEventListener('mousedown', onDocumentMousedown)
      scrollableBelow.value = false
      stopBodyObserver()
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocumentMousedown)
  stopBodyObserver()
})
</script>

<style scoped>
.filter-panel {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 120;
  width: min(400px, calc(100vw - 2 * var(--gallery-search-bar-margin-h)));
  max-height: min(72dvh, 640px);
  display: flex;
  flex-direction: column;
  border-radius: var(--card-radius);
  background: var(--color-background-floating);
  box-shadow:
    0 calc(var(--card-elevation) * 3) 12px var(--shadow-color),
    0 0 1px var(--shadow-color);
  overflow: hidden;
}

.filter-panel__header {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px 6px 16px;
}

.filter-panel__heading {
  font-size: var(--text-super-small);
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-color-secondary);
}

.filter-panel__close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--drawable-color-secondary);
  cursor: pointer;
  transition: background-color 150ms var(--ease-decelerate-quart);
}

.filter-panel__close:hover {
  background: var(--color-surface);
}

.filter-panel__body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  /* F-UX3: bottom clearance ≥ footer height (~54px) so the last section
     (Advanced options) can scroll fully clear of the action bar instead of
     ending crushed against it. */
  padding: 4px 16px 56px;
}

.filter-panel__section {
  margin-top: 10px;
}

.filter-panel__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}

.filter-panel__label {
  display: block;
  margin-bottom: 6px;
  font-size: var(--text-super-small);
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-color-secondary);
}

.filter-panel__row .filter-panel__label {
  flex: 0 0 auto;
  margin: 0;
}

/* --- Category chips ------------------------------------------------------- */

.filter-panel__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.filter-panel__chip {
  padding: 5px 12px;
  border: 1px solid var(--color-divider);
  border-radius: 999px;
  background: transparent;
  color: var(--text-color-secondary);
  font-size: var(--text-small);
  cursor: pointer;
  transition:
    background-color 150ms var(--ease-decelerate-quart),
    border-color 150ms var(--ease-decelerate-quart),
    color 150ms var(--ease-decelerate-quart),
    opacity 150ms var(--ease-decelerate-quart);
}

.filter-panel__chip:hover {
  opacity: 0.9;
}

.filter-panel__chip.is-included {
  font-weight: 700;
  box-shadow: 0 1px 2px var(--shadow-color);
}

/* --- Keyword mode radio group (legacy surface absorbed, W3 R4-10) ------- */

.filter-panel__modes {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.filter-panel__mode {
  padding: 5px 14px;
  border: 1px solid var(--color-divider);
  border-radius: 999px;
  background: transparent;
  color: var(--text-color-secondary);
  font-size: var(--text-small);
  cursor: pointer;
  transition:
    background-color 150ms var(--ease-decelerate-quart),
    border-color 150ms var(--ease-decelerate-quart),
    color 150ms var(--ease-decelerate-quart);
}

.filter-panel__mode:hover {
  background: var(--color-surface);
}

.filter-panel__mode[aria-checked='true'] {
  background: var(--content-color-theme-primary);
  border-color: var(--content-color-theme-primary);
  color: #ffffff;
  font-weight: 700;
}

/* --- Sort / rating selects + page inputs ---------------------------------- */

.filter-panel__select {
  flex: 1 1 auto;
  max-width: 240px;
}

.filter-panel__pages {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-panel__page-input {
  width: 72px;
  padding: 6px 8px;
  border: 1px solid var(--color-divider);
  border-radius: var(--card-radius);
  background: transparent;
  color: var(--text-color-primary);
  font-size: var(--text-small);
  outline: none;
  transition: border-color 150ms var(--ease-decelerate-quart);
}

.filter-panel__page-input:focus {
  border-color: var(--color-primary);
}

.filter-panel__page-sep {
  font-size: var(--text-small);
  color: var(--text-color-secondary);
}

/* --- Scope switches -------------------------------------------------------- */

.filter-panel__switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 7px 0;
}

.filter-panel__switch-row + .filter-panel__switch-row {
  border-top: 1px solid var(--color-divider);
}

.filter-panel__switch-label {
  font-size: var(--text-small);
  color: var(--text-color-primary);
}

/* Compact switch sizing via the atom's CSS variables (40×24 track, 16px
   thumb keeps the 20px on-state travel inside the track). */
.filter-panel__switch-row :deep(.app-switch) {
  --switch-width: 40px;
  --switch-height: 24px;
  --switch-thumb-size: 16px;
}

/* --- Footer ---------------------------------------------------------------- */

.filter-panel__footer {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 12px 10px;
  border-top: 1px solid var(--color-divider);
  transition: box-shadow 160ms var(--ease-decelerate-quart);
}

/* F-UX3 scroll cue: raised top shadow while body content remains below the
   fold (see `scrollableBelow` in the script). */
.filter-panel__footer--raised {
  box-shadow: 0 -6px 10px -6px var(--shadow-color);
}

.filter-panel__btn-text {
  padding: 8px 12px;
  border: none;
  border-radius: var(--card-radius);
  background: transparent;
  color: var(--text-color-theme-primary);
  font-size: var(--text-small);
  font-weight: 700;
  cursor: pointer;
  transition: background-color 150ms var(--ease-decelerate-quart);
}

.filter-panel__btn-text:hover {
  background: var(--color-surface);
}

.filter-panel__btn-primary {
  margin-left: auto;
  padding: 8px 24px;
  border: none;
  border-radius: var(--card-radius);
  background: var(--content-color-theme-primary);
  color: #ffffff;
  font-size: var(--text-small);
  font-weight: 700;
  letter-spacing: 0.02em;
  cursor: pointer;
  box-shadow: 0 1px 3px var(--shadow-color);
  transition:
    filter 150ms var(--ease-decelerate-quart),
    transform 120ms var(--ease-decelerate-quart);
}

.filter-panel__btn-primary:hover {
  filter: brightness(1.08);
}

.filter-panel__btn-primary:active {
  transform: scale(0.97);
}

/* --- Enter/leave ------------------------------------------------------------ */

.filter-panel-enter-active,
.filter-panel-leave-active {
  transition:
    opacity 200ms var(--ease-decelerate-quart),
    transform 200ms var(--ease-decelerate-quart);
  transform-origin: top right;
}

.filter-panel-enter-from,
.filter-panel-leave-to {
  opacity: 0;
  transform: translateY(-6px) scale(0.98);
}

@media (prefers-reduced-motion: reduce) {
  .filter-panel-enter-active,
  .filter-panel-leave-active,
  .filter-panel__footer {
    transition: none;
  }
}
</style>
