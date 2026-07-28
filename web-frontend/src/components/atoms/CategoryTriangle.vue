<template>
  <span
    class="category-triangle"
    :style="triangleStyle"
    role="img"
    :aria-label="CATEGORY_LABELS[category]"
  />
</template>

<script setup lang="ts">
/**
 * CategoryTriangle — the 32×24dp category-colored right-top corner triangle
 * on grid-mode tiles (roadmap §卡片规范).
 *
 * Drawn with the CSS border trick: a zero-size box whose colored
 * `border-top` (height = `--thumb-triangle-height`) meets a transparent
 * `border-left` (width = `--thumb-triangle-width`), producing a right
 * triangle whose right angle sits at the TOP-RIGHT corner. Position it with
 * `position:absolute; top:0; right:0` inside a tile (see `AppCard` grid mode).
 */
import { computed } from 'vue'
import { CATEGORY_LABELS, type CategoryTriangleProps } from '@/types/components'

const props = defineProps<CategoryTriangleProps>()

/** `artist_cg` → `--color-cat-artist-cg` (see CategoryChip for rationale). */
const categoryToken = computed(() => `--color-cat-${props.category.replace(/_/g, '-')}`)

const triangleStyle = computed(() => ({
  borderTopColor: `var(${categoryToken.value})`,
}))
</script>

<style scoped>
.category-triangle {
  display: block;
  width: 0;
  height: 0;
  /* Colored top edge forms the triangle; left edge stays transparent. */
  border-top: var(--thumb-triangle-height) solid var(--grey-500); /* color overridden inline */
  border-left: var(--thumb-triangle-width) solid transparent;
}
</style>
