<template>
  <div class="gallery-grid" :class="`gallery-grid--${mode}`">
    <GalleryCard
      v-for="(gallery, index) in items"
      :key="gallery.gid"
      :gallery="gallery"
      :mode="mode"
      :style="enterStyle(index)"
      @click="emit('select', gallery)"
    />
  </div>
</template>

<script setup lang="ts">
/**
 * GalleryGrid — the gallery list content in both layout modes (S1).
 *
 * - `grid`: masonry-style auto-column layout, the web equivalent of
 *   `AutoStaggeredGridLayoutManager` (STRATEGY_MIN_SIZE): the column count is
 *   NEVER hardcoded — CSS Grid `auto-fill` derives it as
 *   `floor(availableWidth / columnWidth)` with the column width taken from
 *   `--column-width-grid-middle` (120px, `gallery_grid_column_width_middle`),
 *   per `contracts/responsive-strategy.md` §4.
 * - `list`: single column of horizontal cards.
 *
 * Spacing comes straight from the Android dimens tokens
 * (`--gallery-grid-*` / `--gallery-list-*`, stepped at the sw600dp
 * breakpoint); the top padding clears the floating SearchBar
 * (`--gallery-padding-top-search-bar`) and the bottom padding clears the FAB
 * cluster (`--gallery-padding-bottom-fab`).
 *
 * Each card receives a staggered `--enter-delay` custom property for the
 * reveal animation (capped so late pages don't wait).
 */
import type { CSSProperties } from 'vue'
import type { GalleryInfo } from '@/types/components'
import GalleryCard from './GalleryCard.vue'

defineProps<{
  /** Galleries to render. */
  items: GalleryInfo[]
  /** Layout mode, kept in sync with the host screen's toggle. */
  mode: 'list' | 'grid'
}>()

const emit = defineEmits<{
  /** Card tapped — open the gallery detail. */
  (e: 'select', gallery: GalleryInfo): void
}>()

/** Per-card stagger for the entrance animation, capped at 200ms. */
function enterStyle(index: number): CSSProperties {
  return { '--enter-delay': `${Math.min(index * 20, 200)}ms` }
}
</script>

<style scoped>
/* Grid mode — AutoStaggeredGridLayoutManager equivalent:
   columns = floor(availableWidth / 120px). `min(…, 100%)` keeps a single
   column from overflowing containers narrower than the column width. */
.gallery-grid--grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(var(--column-width-grid-middle), 100%), 1fr));
  gap: var(--gallery-grid-interval);
  align-items: start;
  padding:
    calc(var(--gallery-padding-top-search-bar) + var(--gallery-grid-margin-v))
    var(--gallery-grid-margin-h)
    var(--gallery-padding-bottom-fab);
}

/* List mode — single column of horizontal cards. */
.gallery-grid--list {
  display: flex;
  flex-direction: column;
  gap: var(--gallery-list-interval);
  padding:
    calc(var(--gallery-padding-top-search-bar) + var(--gallery-list-margin-v))
    var(--gallery-list-margin-h)
    var(--gallery-padding-bottom-fab);
}
</style>
