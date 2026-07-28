<template>
  <span
    class="rating-stars"
    role="img"
    :aria-label="`评分 ${rating} / 5`"
    :style="{ gap: `${gap}px` }"
  >
    <svg
      v-for="(star, i) in stars"
      :key="i"
      class="rating-star"
      :class="`rating-star--${star}`"
      :width="size"
      :height="size"
      viewBox="0 0 24 24"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
    >
      <path :d="STAR_PATHS[star]" />
    </svg>
  </span>
</template>

<script setup lang="ts">
/**
 * RatingStars — web replica of `SimpleRatingView.java`.
 *
 * 5 stars, `--rating-size` (16px) each, `--rating-interval` (1px) gap.
 *
 * SCALE NOTE: `gallery.rating` is a **0–5 float** (parsed by
 * `GalleryListParser.parseRating` → "4.5" / "5" style strings, and the EH API
 * `rating` field). This matches Android, where `SimpleRatingView.setRating`
 * receives a 0–5 value and quantises it with
 * `clamp(Math.ceil(rating * 2), 0, 10)` — i.e. into a 0–10 *half-star* count.
 * The "0–10" figure in the roadmap refers to that internal half-star count,
 * NOT the input scale. `floor(q / 2)` gives full stars, `q % 2` the half star.
 *
 * Star path data is taken verbatim from the Android vector drawables
 * `v_star_x16` / `v_star_half_x16` / `v_star_outline_x16` (viewport 24×24).
 * Filled/half stars use `--color-rating-star` (yellow 800 `#f9a825`); empty
 * stars render as a grey outline via the theme-aware secondary drawable color.
 */
import { computed } from 'vue'
import type { RatingStarsProps } from '@/types/components'

const props = withDefaults(defineProps<RatingStarsProps>(), {
  size: 16,
  gap: 1,
})

/** Path data copied from the Android vector drawables (24×24 viewport). */
const STAR_PATHS = {
  full: 'M12,17.27L18.18,21L16.54,13.97L22,9.24L14.81,8.62L12,2L9.19,8.62L2,9.24L7.45,13.97L5.82,21L12,17.27Z',
  half: 'M12,15.89V6.59L13.71,10.63L18.09,11L14.77,13.88L15.76,18.16M22,9.74L14.81,9.13L12,2.5L9.19,9.13L2,9.74L7.45,14.47L5.82,21.5L12,17.77L18.18,21.5L16.54,14.47L22,9.74Z',
  empty:
    'M12,15.39L8.24,17.66L9.23,13.38L5.91,10.5L10.29,10.13L12,6.09L13.71,10.13L18.09,10.5L14.77,13.38L15.76,17.66M22,9.24L14.81,8.63L12,2L9.19,8.63L2,9.24L7.45,13.97L5.82,21L12,17.27L18.18,21L16.54,13.97L22,9.24Z',
} as const

type StarState = keyof typeof STAR_PATHS

/**
 * The 5 star states for the current rating, replicating
 * `SimpleRatingView.setRating` + `onDraw` exactly.
 */
const stars = computed<StarState[]>(() => {
  // clamp(Math.ceil(rating * 2), 0, 10) → number of filled half-stars.
  const q = Math.min(10, Math.max(0, Math.ceil(props.rating * 2)))
  const full = Math.floor(q / 2)
  const half = q % 2

  const result: StarState[] = []
  for (let i = 0; i < full; i++) result.push('full')
  if (half === 1) result.push('half')
  while (result.length < 5) result.push('empty')
  return result
})
</script>

<style scoped>
.rating-stars {
  display: inline-flex;
  align-items: center;
  line-height: 0;
  flex-shrink: 0;
}

.rating-star {
  display: inline-block;
  flex-shrink: 0;
}

/* Filled + half stars use the rating yellow; the half-star path already
   encodes the half-filled glyph, so a single fill color suffices. */
.rating-star--full,
.rating-star--half {
  fill: var(--color-rating-star);
}

/* Empty stars render as a grey outline (theme-aware secondary drawable). */
.rating-star--empty {
  fill: var(--drawable-color-secondary);
}
</style>
