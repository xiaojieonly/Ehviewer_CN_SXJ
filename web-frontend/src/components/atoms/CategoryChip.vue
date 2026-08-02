<template>
  <button
    v-if="clickable"
    type="button"
    class="category-chip category-chip--clickable"
    :style="chipStyle"
    @click="emit('click', category)"
  >
    {{ displayLabel }}
  </button>
  <span v-else class="category-chip" :style="chipStyle">
    {{ displayLabel }}
  </span>
</template>

<script setup lang="ts">
/**
 * CategoryChip — the colored category label tag shown on list-mode gallery
 * cards and the gallery detail header.
 *
 * Background comes from the category color tokens (`--color-cat-*`, exact
 * Material values from `colors.xml` / roadmap §色彩系统); label defaults to
 * `CATEGORY_LABELS` (Android `SiteUtils.getCategory` strings). White 12px text,
 * 2px corner radius — the restrained-M2 tag look, not a pill.
 */
import { computed } from 'vue'
import {
  CATEGORY_LABELS,
  type CategoryChipProps,
  type CategoryChipEmits,
} from '@/types/components'

const props = withDefaults(defineProps<CategoryChipProps>(), {
  label: undefined,
  clickable: false,
})

const emit = defineEmits<CategoryChipEmits>()

/**
 * Map a `GalleryCategory` key to its color token name
 * (`artist_cg` → `--color-cat-artist-cg`). The token set in `tokens.css`
 * mirrors `CATEGORY_COLOR_MAP` exactly, so deriving the name keeps a single
 * source of truth and stays theme-aware.
 */
const categoryToken = computed(() => `--color-cat-${props.category.replace(/_/g, '-')}`)

const displayLabel = computed(() => props.label ?? CATEGORY_LABELS[props.category])

const chipStyle = computed(() => ({
  backgroundColor: `var(${categoryToken.value})`,
}))
</script>

<style scoped>
.category-chip {
  display: inline-block;
  padding: 2px 6px;
  border: none;
  border-radius: var(--card-radius); /* 2px — matches card corner spec */
  background-color: var(--grey-500); /* fallback; overridden inline */
  color: var(--color-white);
  font-size: var(--text-super-small); /* 12px */
  line-height: 1.4;
  font-family: inherit;
  white-space: nowrap;
  vertical-align: middle;
}

.category-chip--clickable {
  cursor: pointer;
  transition: filter 120ms var(--ease-decelerate-quart);
}

.category-chip--clickable:hover {
  filter: brightness(1.1);
}

.category-chip--clickable:active {
  filter: brightness(0.9);
}
</style>
