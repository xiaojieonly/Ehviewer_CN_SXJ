<!--
  CategoryTable.vue — web replica of `CategoryTable.java`
  (`app/src/main/res/layout/widget_category_table.xml`).

  10 `CheckTextView` blocks in 2 columns × 5 rows
  (`- -category-table-item-height` 40px, `- -category-table-item-margin` 4px),
  each colored by the tokens.css `- -color-cat-*` variables (same palette as
  `CATEGORY_COLOR_MAP` in the frozen contract, `@/types/components.ts`).

  SEMANTICS (frozen contract, task CA4): POSITIVE — `selected` lists the
  categories INCLUDED in the search. This deliberately inverts the Android
  widget, where a CHECKED block meant the category was EXCLUDED
  (`getCategory()` sets the EhConfig exclusion bit when NOT checked).
  Scenes convert to the exclusion bitmask via `CATEGORY_BIT_VALUES`.

  Interaction:
  - tap toggles one block (`update:selected`);
  - long-press (contextmenu on the web) applies the Android inversion rule —
    long-pressing an included category selects ONLY it, long-pressing an
    excluded one selects everything BUT it;
  - the header "All" chip (F6 additive, per search-component task spec)
    selects / deselects every category in one tap.
-->
<template>
  <div class="category-table" role="group" aria-label="Gallery categories">
    <div class="category-table__header">
      <span class="category-table__title">Category</span>
      <button
        type="button"
        class="category-table__all"
        :class="{ 'is-on': allSelected }"
        :aria-pressed="allSelected"
        @click="toggleAll"
      >
        <svg
          v-if="allSelected"
          class="category-table__all-check"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <path d="M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z" />
        </svg>
        All
      </button>
    </div>

    <div class="category-table__grid">
      <button
        v-for="category in CATEGORY_ORDER"
        :key="category"
        type="button"
        class="category-table__block"
        :class="{ 'is-selected': isSelected(category) }"
        :style="{ background: `var(${colorVar(category)})` }"
        :aria-pressed="isSelected(category)"
        :aria-label="`${CATEGORY_LABELS[category]} — ${isSelected(category) ? 'included' : 'excluded'}`"
        @click="toggle(category)"
        @contextmenu.prevent="longPress(category)"
      >
        <span class="category-table__label">{{ CATEGORY_LABELS[category] }}</span>
        <svg
          v-if="isSelected(category)"
          class="category-table__check"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <path d="M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z" />
        </svg>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { CategoryTableEmits, CategoryTableProps, GalleryCategory } from '@/types/components'
import { CATEGORY_LABELS, CATEGORY_ORDER } from '@/types/components'

const props = defineProps<CategoryTableProps>()

const emit = defineEmits<CategoryTableEmits>()

/** tokens.css variable for a category — e.g. `artist_cg` → `- -color-cat-artist-cg`. */
function colorVar(category: GalleryCategory): string {
  return `--color-cat-${category.replace(/_/g, '-')}`
}

function isSelected(category: GalleryCategory): boolean {
  return props.selected.includes(category)
}

const allSelected = computed<boolean>(() =>
  CATEGORY_ORDER.every((category) => props.selected.includes(category)),
)

/** Tap: toggle a single block (positive semantics). */
function toggle(category: GalleryCategory): void {
  const next = isSelected(category)
    ? props.selected.filter((c) => c !== category)
    : [...props.selected, category]
  emit('update:selected', next)
}

/**
 * Long-press: Android `onLongClick` inversion rule translated to positive
 * semantics — an included block becomes the ONLY selection; an excluded one
 * selects everything but itself.
 */
function longPress(category: GalleryCategory): void {
  const next = isSelected(category)
    ? [category]
    : CATEGORY_ORDER.filter((c) => c !== category)
  emit('update:selected', [...next])
  emit('long-press', category)
}

/** Header chip: select all / clear all. */
function toggleAll(): void {
  emit('update:selected', allSelected.value ? [] : [...CATEGORY_ORDER])
}
</script>

<style scoped>
.category-table {
  display: flex;
  flex-direction: column;
}

.category-table__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 32px;
  margin-bottom: 4px;
}

.category-table__title {
  font-size: var(--text-super-small);
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-color-secondary);
}

.category-table__all {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: var(--text-color-theme-primary);
  font-size: var(--text-super-small);
  font-weight: 700;
  letter-spacing: 0.04em;
  cursor: pointer;
  transition: background-color 150ms var(--ease-decelerate-quart);
}

.category-table__all:hover {
  background: var(--color-surface);
}

.category-table__all.is-on {
  background: var(--color-surface-activated);
}

.category-table__all-check {
  width: 12px;
  height: 12px;
  fill: currentColor;
}

.category-table__grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: calc(var(--category-table-item-margin) * 2);
  padding: var(--category-table-item-margin);
}

.category-table__block {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: var(--category-table-item-height);
  border: none;
  border-radius: var(--card-radius);
  color: #ffffff;
  cursor: pointer;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
  /* Excluded blocks read as dimmed; included blocks are full-color. */
  opacity: 0.35;
  filter: saturate(0.7);
  transition:
    opacity 200ms var(--ease-decelerate-quart),
    filter 200ms var(--ease-decelerate-quart),
    transform 120ms var(--ease-decelerate-quart),
    box-shadow 200ms var(--ease-decelerate-quart);
}

.category-table__block:hover {
  opacity: 0.75;
  transform: translateY(-1px);
}

.category-table__block:active {
  transform: scale(0.97);
}

.category-table__block.is-selected {
  opacity: 1;
  filter: none;
  box-shadow: 0 1px 3px var(--shadow-color);
}

.category-table__label {
  /* Android `CategoryText`: Subhead (~16sp) bold, centered, white. */
  font-size: var(--text-little-small);
  font-weight: 700;
  line-height: 1;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.25);
}

.category-table__check {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 14px;
  height: 14px;
  fill: #ffffff;
  filter: drop-shadow(0 1px 1px rgba(0, 0, 0, 0.3));
}
</style>
