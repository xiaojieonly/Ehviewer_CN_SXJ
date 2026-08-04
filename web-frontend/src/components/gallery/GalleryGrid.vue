<template>
  <div class="gallery-grid">
    <div v-for="(gallery, index) in items" :key="gallery.gid" class="gallery-grid__cell">
      <GalleryCard
        :gallery="gallery"
        mode="grid"
        :style="enterStyle(index)"
        @click="emit('select', gallery)"
      >
        <!-- F-UX1: optional in-card meta sub line (History's last-viewed
             stamp), flowing below the title. Threaded only when the list
             consumer supplies `cell-sub`, so cards without a sub line keep
             the classic single-line meta. -->
        <template v-if="$slots['cell-sub']" #grid-sub>
          <slot name="cell-sub" :gallery="gallery" :index="index" />
        </template>
      </GalleryCard>
      <!-- Per-cell addon (e.g. a status badge) positioned over the tile;
           GalleryList threads its `item-extra` slot through here. -->
      <slot name="cell-extra" :gallery="gallery" :index="index" />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * GalleryGrid — the grid-form renderer of the gallery list (S1), consumed by
 * the shared `GalleryList` (B-1). List-form rows live in `GalleryList`
 * itself; this component only renders the tile grid.
 *
 * Masonry-style auto-column layout, the web equivalent of
 * `AutoStaggeredGridLayoutManager` (STRATEGY_MIN_SIZE): the column count is
 * NEVER hardcoded — CSS Grid `auto-fill` derives it as
 * `floor(availableWidth / columnWidth)` with the column width taken from
 * `--column-width-grid-middle` (120px, `gallery_grid_column_width_middle`),
 * per `contracts/responsive-strategy.md` §4.
 *
 * Spacing comes straight from the Android dimens tokens
 * (`--gallery-grid-*`, stepped at the sw600dp breakpoint). The bottom padding
 * clears the FAB cluster (`--gallery-padding-bottom-fab`). The floating-bar
 * top clearance (HomeView's SearchBar) is carried by GalleryList's sticky
 * toggle toolbar (`--gallery-list-clear-top`), so the grid itself starts with
 * only the standard `--gallery-grid-margin-v`.
 *
 * Each card receives a staggered `--enter-delay` custom property for the
 * reveal animation (capped so late pages don't wait). Every card sits in a
 * `position: relative` cell so the optional `cell-extra` slot can badge it.
 */
import type { CSSProperties } from 'vue'
import type { GalleryInfo } from '@/types/components'
import GalleryCard from './GalleryCard.vue'

defineProps<{
  /** Galleries to render. */
  items: GalleryInfo[]
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
/* AutoStaggeredGridLayoutManager equivalent:
   columns = floor(availableWidth / 120px). `min(…, 100%)` keeps a single
   column from overflowing containers narrower than the column width. */
.gallery-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(var(--column-width-grid-middle), 100%), 1fr));
  gap: var(--gallery-grid-interval);
  align-items: start;
  padding:
    var(--gallery-grid-margin-v)
    var(--gallery-grid-margin-h)
    var(--gallery-padding-bottom-fab);
}

/* Badge anchor for the `cell-extra` slot. */
.gallery-grid__cell {
  position: relative;
}
</style>
