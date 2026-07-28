<!--
  SearchLayout.vue — web replica of `SearchLayout.java` and its section
  layouts (`search_normal.xml`, `search_advance.xml`, `search_image.xml`,
  `search_action.xml`, `widget_advance_search_table.xml`).

  The expandable filter panel shown under `SearchBar`, composed of titled
  sections:
    Normal  — CategoryTable + keyword search-mode radio group
              (normal / subscription / uploader / tag) + "enable advanced"
              switch;
    Advanced — `AdvanceSearchTable` replica (11 bitmask checkboxes, minimum
              rating stars, page range), inserted/removed by the switch with
              the same 300ms decelerate clip used across the search UI;
    Image   — image-search entrance (`select-image`);
    Actions — image-search toggle (`change-mode`), tag-selector entrance
              (`open-tag-selector`) and the primary Search button.

  All state is v-model'd so the search screen can compose the final query
  (web equivalent of `formatListUrlBuilder`).

  F6 additives (per search-component task spec, additive to the frozen
  contract in `@/types/components.ts`):
    - `expanded` / `update:expanded` — panel collapse with animation,
      toggled from the header chevron;
    - `keyword` + `search(query, options)` — the action-row Search button
      submits the scene's current keyword together with the advanced
      options (category / mode state flows through their own v-models).
-->
<template>
  <section class="search-layout" :class="{ 'is-collapsed': !expanded }">
    <header class="search-layout__header">
      <span class="search-layout__heading">Search options</span>
      <button
        type="button"
        class="search-layout__toggle"
        :aria-expanded="expanded"
        aria-label="Toggle search options"
        @click="emit('update:expanded', !expanded)"
      >
        <span class="search-layout__chevron" aria-hidden="true" />
      </button>
    </header>

    <div class="search-layout__clip">
      <div class="search-layout__body">
        <template v-if="mode === 'normal'">
          <!-- Category section (search_category.xml). -->
          <div class="search-layout__section">
            <CategoryTable
              :selected="selectedCategories"
              @update:selected="(value) => emit('update:selectedCategories', value)"
            />
          </div>

          <!-- Keyword search mode (search_normal.xml RadioGridGroup). -->
          <div class="search-layout__section">
            <span class="search-layout__section-title">Search mode</span>
            <div class="search-layout__modes" role="radiogroup" aria-label="Keyword search mode">
              <button
                v-for="option in MODE_OPTIONS"
                :key="option.value"
                type="button"
                class="search-layout__mode"
                role="radio"
                :aria-checked="normalSearchMode === option.value"
                @click="emit('update:normalSearchMode', option.value)"
              >
                {{ option.label }}
              </button>
            </div>
          </div>

          <!-- Enable advanced search (search_enable_advance switch). -->
          <div class="search-layout__switch-row">
            <span class="search-layout__switch-label">Enable advanced search</span>
            <button
              type="button"
              class="search-layout__switch"
              role="switch"
              :aria-checked="enableAdvance"
              @click="emit('update:enableAdvance', !enableAdvance)"
            >
              <span class="search-layout__switch-thumb" />
            </button>
          </div>

          <!-- AdvanceSearchTable — inserted/removed by the switch. -->
          <div class="search-layout__advance-clip" :class="{ 'is-open': enableAdvance }">
            <div class="search-layout__advance">
              <div class="search-layout__checks">
                <label
                  v-for="item in ADVANCE_ITEMS"
                  :key="item.bit"
                  class="search-layout__check"
                  :class="{ 'is-on': hasBit(item.bit) }"
                >
                  <input
                    type="checkbox"
                    :checked="hasBit(item.bit)"
                    @change="toggleBit(item.bit)"
                  />
                  <span class="search-layout__check-box" aria-hidden="true">
                    <svg viewBox="0 0 24 24">
                      <path d="M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z" />
                    </svg>
                  </span>
                  {{ item.label }}
                </label>
              </div>

              <span class="search-layout__section-title search-layout__section-title--sub">
                Disable default filters
              </span>
              <div class="search-layout__checks">
                <label
                  v-for="item in FILTER_ITEMS"
                  :key="item.bit"
                  class="search-layout__check"
                  :class="{ 'is-on': hasBit(item.bit) }"
                >
                  <input
                    type="checkbox"
                    :checked="hasBit(item.bit)"
                    @change="toggleBit(item.bit)"
                  />
                  <span class="search-layout__check-box" aria-hidden="true">
                    <svg viewBox="0 0 24 24">
                      <path d="M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z" />
                    </svg>
                  </span>
                  {{ item.label }}
                </label>
              </div>

              <!-- Minimum rating (search_sr + search_min_rating spinner). -->
              <div class="search-layout__field-row">
                <span class="search-layout__field-label">Minimum rating</span>
                <div class="search-layout__stars" role="group" aria-label="Minimum rating">
                  <button
                    v-for="star in 5"
                    :key="star"
                    type="button"
                    class="search-layout__star"
                    :class="{ 'is-filled': star <= opts.minRating }"
                    :aria-label="`${star} star${star > 1 ? 's' : ''}`"
                    :aria-pressed="star <= opts.minRating"
                    @click="setMinRating(star)"
                  >
                    <AppIcon :name="star <= opts.minRating ? 'star' : 'star-outline'" size="20px" />
                  </button>
                </div>
              </div>

              <!-- Page range (search_sp: From [spf] to [spt]). -->
              <div class="search-layout__field-row">
                <span class="search-layout__field-label">Pages</span>
                <div class="search-layout__pages">
                  <label class="search-layout__page">
                    <span>From</span>
                    <input
                      type="number"
                      min="0"
                      inputmode="numeric"
                      :value="opts.pageFrom || ''"
                      placeholder="—"
                      @input="setPage('pageFrom', $event)"
                    />
                  </label>
                  <span class="search-layout__page-to">to</span>
                  <label class="search-layout__page">
                    <span>To</span>
                    <input
                      type="number"
                      min="0"
                      inputmode="numeric"
                      :value="opts.pageTo || ''"
                      placeholder="—"
                      @input="setPage('pageTo', $event)"
                    />
                  </label>
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- Image search section (search_image.xml). -->
        <div v-else class="search-layout__section">
          <span class="search-layout__section-title">Image search</span>
          <div class="search-layout__image-box">
            <p class="search-layout__image-hint">Select an image to search similar galleries</p>
            <button type="button" class="search-layout__image-btn" @click="emit('select-image')">
              Select image
            </button>
          </div>
        </div>

        <!-- Action row (search_action.xml). -->
        <div class="search-layout__actions">
          <button type="button" class="search-layout__action" @click="emit('change-mode')">
            {{ mode === 'normal' ? 'Image search' : 'Keyword search' }}
          </button>
          <button
            v-if="mode === 'normal'"
            type="button"
            class="search-layout__action"
            @click="emit('open-tag-selector')"
          >
            Tag selector
          </button>
          <button type="button" class="search-layout__search" @click="submitSearch">
            Search
          </button>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type {
  AdvanceSearchOptions,
  NormalSearchMode,
  SearchLayoutEmits,
  SearchLayoutProps,
} from '@/types/components'
import { ADVANCE_SEARCH_BITS, CATEGORY_ORDER } from '@/types/components'
import CategoryTable from './CategoryTable.vue'
import AppIcon from '@/components/atoms/AppIcon.vue'

/**
 * Contract props plus two F6 additives — see the component header comment.
 */
const props = withDefaults(
  defineProps<
    SearchLayoutProps & {
      /** Panel expanded (v-model:expanded). @default true */
      expanded?: boolean
      /** Current keyword held by the scene's SearchBar; echoed by `search`. @default '' */
      keyword?: string
    }
  >(),
  {
    mode: 'normal',
    enableAdvance: false,
    selectedCategories: () => [...CATEGORY_ORDER],
    normalSearchMode: 'normal',
    advanceOptions: undefined,
    expanded: true,
    keyword: '',
  },
)

const emit = defineEmits<
  SearchLayoutEmits & {
    /** v-model:expanded — header chevron toggled. */
    (e: 'update:expanded', expanded: boolean): void
    /** Action-row Search tapped — scene composes the final query. */
    (e: 'search', query: string, options: AdvanceSearchOptions): void
  }
>()

const DEFAULT_ADVANCE_OPTIONS: AdvanceSearchOptions = {
  advanceSearch: 0,
  minRating: 0,
  pageFrom: 0,
  pageTo: 0,
}

const opts = computed<AdvanceSearchOptions>(
  () => props.advanceOptions ?? DEFAULT_ADVANCE_OPTIONS,
)

/** `search_normal.xml` RadioGridGroup entries. */
const MODE_OPTIONS: ReadonlyArray<{ value: NormalSearchMode; label: string }> = [
  { value: 'normal', label: 'Search' },
  { value: 'subscription', label: 'Subscription' },
  { value: 'uploader', label: 'Uploader' },
  { value: 'tag', label: 'Tag' },
]

/** `AdvanceSearchTable` checkboxes (widget_advance_search_table.xml). */
const ADVANCE_ITEMS: ReadonlyArray<{ bit: number; label: string }> = [
  { bit: ADVANCE_SEARCH_BITS.SNAME, label: 'Search gallery name' },
  { bit: ADVANCE_SEARCH_BITS.STAGS, label: 'Search gallery tags' },
  { bit: ADVANCE_SEARCH_BITS.SDESC, label: 'Search gallery description' },
  { bit: ADVANCE_SEARCH_BITS.STORR, label: 'Search torrent filenames' },
  { bit: ADVANCE_SEARCH_BITS.STO, label: 'Only show galleries with torrents' },
  { bit: ADVANCE_SEARCH_BITS.SDT1, label: 'Search low-power tags' },
  { bit: ADVANCE_SEARCH_BITS.SDT2, label: 'Search downvoted tags' },
  { bit: ADVANCE_SEARCH_BITS.SH, label: 'Search expunged galleries' },
]

/** "Disable default filters" group (search_sf). */
const FILTER_ITEMS: ReadonlyArray<{ bit: number; label: string }> = [
  { bit: ADVANCE_SEARCH_BITS.SFL, label: 'Language' },
  { bit: ADVANCE_SEARCH_BITS.SFU, label: 'Uploader' },
  { bit: ADVANCE_SEARCH_BITS.SFT, label: 'Tags' },
]

function hasBit(bit: number): boolean {
  return (opts.value.advanceSearch & bit) !== 0
}

function toggleBit(bit: number): void {
  emit('update:advanceOptions', {
    ...opts.value,
    advanceSearch: opts.value.advanceSearch ^ bit,
  })
}

/** Star n sets the minimum rating; tapping the current maximum steps down. */
function setMinRating(star: number): void {
  emit('update:advanceOptions', {
    ...opts.value,
    minRating: opts.value.minRating === star ? star - 1 : star,
  })
}

function setPage(field: 'pageFrom' | 'pageTo', event: Event): void {
  const parsed = parseInt((event.target as HTMLInputElement).value, 10)
  emit('update:advanceOptions', {
    ...opts.value,
    [field]: Number.isFinite(parsed) && parsed > 0 ? parsed : 0,
  })
}

function submitSearch(): void {
  emit('search', props.keyword ?? '', { ...opts.value })
}
</script>

<style scoped>
.search-layout {
  margin: 0 var(--gallery-search-bar-margin-h) var(--gallery-search-bar-margin-v);
  background: var(--color-background-floating);
  border-radius: var(--card-radius);
  box-shadow:
    0 var(--card-elevation) 4px var(--shadow-color),
    0 0 1px var(--shadow-color);
  overflow: hidden;
}

/* --- Header (F6 additive — collapse affordance) -------------------------- */

.search-layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px 6px;
}

.search-layout__heading {
  font-size: var(--text-super-small);
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-color-secondary);
}

.search-layout__toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: transparent;
  cursor: pointer;
  transition: background-color 150ms var(--ease-decelerate-quart);
}

.search-layout__toggle:hover {
  background: var(--color-surface);
}

.search-layout__chevron {
  width: 8px;
  height: 8px;
  border-right: 2px solid var(--drawable-color-primary);
  border-bottom: 2px solid var(--drawable-color-primary);
  transform: rotate(45deg) translate(-1px, -1px);
  transition: transform 300ms var(--ease-decelerate-quart);
}

.is-collapsed .search-layout__chevron {
  transform: rotate(-45deg) translate(-1px, 1px);
}

/* --- Collapse clip (300ms decelerate, matches the search UI family) ------ */

.search-layout__clip {
  display: grid;
  grid-template-rows: 1fr;
  transition: grid-template-rows 300ms var(--ease-decelerate-quart);
}

.is-collapsed .search-layout__clip {
  grid-template-rows: 0fr;
}

.search-layout__body {
  min-height: 0;
  overflow: hidden;
  padding: 0 12px 12px;
}

.search-layout__section {
  margin-top: 8px;
}

.search-layout__section-title {
  display: block;
  margin: 8px 4px 6px;
  font-size: var(--text-super-small);
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-color-secondary);
}

.search-layout__section-title--sub {
  margin-top: 14px;
}

/* --- Keyword search mode radio group ------------------------------------- */

.search-layout__modes {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 0 4px;
}

.search-layout__mode {
  padding: 6px 14px;
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

.search-layout__mode:hover {
  background: var(--color-surface);
}

.search-layout__mode[aria-checked='true'] {
  background: var(--content-color-theme-primary);
  border-color: var(--content-color-theme-primary);
  color: #ffffff;
  font-weight: 700;
}

/* --- Enable-advance switch ------------------------------------------------ */

.search-layout__switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
  padding: 0 4px;
}

.search-layout__switch-label {
  font-size: var(--text-small);
  color: var(--text-color-primary);
}

.search-layout__switch {
  position: relative;
  width: 36px;
  height: 20px;
  border: none;
  border-radius: 999px;
  background: var(--widget-color);
  cursor: pointer;
  transition: background-color 200ms var(--ease-decelerate-quart);
}

.search-layout__switch[aria-checked='true'] {
  background: var(--widget-color-theme-primary);
}

.search-layout__switch-thumb {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--color-background-floating);
  box-shadow: 0 1px 2px var(--shadow-color);
  transition: transform 200ms var(--ease-decelerate-quart);
}

.search-layout__switch[aria-checked='true'] .search-layout__switch-thumb {
  transform: translateX(16px);
}

/* --- AdvanceSearchTable --------------------------------------------------- */

.search-layout__advance-clip {
  display: grid;
  grid-template-rows: 0fr;
  transition: grid-template-rows 300ms var(--ease-decelerate-quart);
}

.search-layout__advance-clip.is-open {
  grid-template-rows: 1fr;
}

.search-layout__advance {
  min-height: 0;
  overflow: hidden;
}

.search-layout__checks {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 2px 12px;
  padding: 0 4px;
}

.search-layout__check {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 4px;
  border-radius: var(--card-radius);
  font-size: var(--text-small);
  color: var(--text-color-primary);
  cursor: pointer;
  transition: background-color 120ms var(--ease-decelerate-quart);
}

.search-layout__check:hover {
  background: var(--color-surface);
}

.search-layout__check input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

.search-layout__check-box {
  flex: 0 0 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border: 2px solid var(--drawable-color-secondary);
  border-radius: 2px;
  transition:
    background-color 150ms var(--ease-decelerate-quart),
    border-color 150ms var(--ease-decelerate-quart);
}

.search-layout__check-box svg {
  width: 12px;
  height: 12px;
  fill: #ffffff;
  opacity: 0;
  transform: scale(0.5);
  transition:
    opacity 150ms var(--ease-decelerate-quart),
    transform 150ms var(--ease-decelerate-quart);
}

.search-layout__check.is-on .search-layout__check-box {
  background: var(--widget-color-theme-primary);
  border-color: var(--widget-color-theme-primary);
}

.search-layout__check.is-on .search-layout__check-box svg {
  opacity: 1;
  transform: scale(1);
}

/* --- Minimum rating stars -------------------------------------------------- */

.search-layout__field-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
  padding: 0 4px;
}

.search-layout__field-label {
  font-size: var(--text-small);
  color: var(--text-color-primary);
}

.search-layout__stars {
  display: flex;
  gap: 2px;
}

.search-layout__star {
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
  transition:
    color 150ms var(--ease-decelerate-quart),
    background-color 150ms var(--ease-decelerate-quart),
    transform 120ms var(--ease-decelerate-quart);
}

.search-layout__star:hover {
  background: var(--color-surface);
}

.search-layout__star:active {
  transform: scale(0.85);
}

.search-layout__star.is-filled {
  color: var(--color-rating-star);
}

/* --- Page range ------------------------------------------------------------ */

.search-layout__pages {
  display: flex;
  align-items: center;
  gap: 8px;
}

.search-layout__page {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--text-small);
  color: var(--text-color-secondary);
}

.search-layout__page input {
  width: 64px;
  padding: 5px 8px;
  border: 1px solid var(--color-divider);
  border-radius: var(--card-radius);
  background: transparent;
  color: var(--text-color-primary);
  font-size: var(--text-small);
  outline: none;
  transition: border-color 150ms var(--ease-decelerate-quart);
}

.search-layout__page input:focus {
  border-color: var(--color-primary);
}

.search-layout__page-to {
  font-size: var(--text-small);
  color: var(--text-color-secondary);
}

/* --- Image search ----------------------------------------------------------- */

.search-layout__image-box {
  margin: 0 4px;
  padding: 20px 16px;
  border: 1px dashed var(--color-divider);
  border-radius: var(--card-radius);
  text-align: center;
}

.search-layout__image-hint {
  margin: 0 0 12px;
  font-size: var(--text-small);
  color: var(--text-color-secondary);
}

.search-layout__image-btn {
  padding: 8px 20px;
  border: none;
  border-radius: var(--card-radius);
  background: var(--content-color-theme-primary);
  color: #ffffff;
  font-size: var(--text-small);
  font-weight: 700;
  cursor: pointer;
  transition:
    filter 150ms var(--ease-decelerate-quart),
    transform 120ms var(--ease-decelerate-quart);
}

.search-layout__image-btn:hover {
  filter: brightness(1.08);
}

.search-layout__image-btn:active {
  transform: scale(0.97);
}

/* --- Action row (search_action.xml) ------------------------------------------ */

.search-layout__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--color-divider);
}

.search-layout__action {
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

.search-layout__action:hover {
  background: var(--color-surface);
}

.search-layout__search {
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
    transform 120ms var(--ease-decelerate-quart),
    box-shadow 150ms var(--ease-decelerate-quart);
}

.search-layout__search:hover {
  filter: brightness(1.08);
  box-shadow: 0 2px 6px var(--shadow-color);
}

.search-layout__search:active {
  transform: scale(0.97);
}
</style>
