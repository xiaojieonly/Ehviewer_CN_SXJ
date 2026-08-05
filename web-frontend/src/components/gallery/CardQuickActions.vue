<template>
  <div
    class="card-quick-actions"
    :class="`card-quick-actions--${variant}`"
    @keydown.enter.stop
    @keydown.space.stop
  >
    <button
      type="button"
      class="card-quick-actions__btn"
      :title="favoriteLabel"
      :aria-label="favoriteLabel"
      :aria-pressed="isFavorited"
      :disabled="favoritePending"
      @click.stop="emit('favorite')"
    >
      <AppIcon :name="isFavorited ? 'heart' : 'heart-outline-primary'" size="18px" />
    </button>
    <button
      type="button"
      class="card-quick-actions__btn"
      :title="downloadLabel"
      :aria-label="downloadLabel"
      :disabled="downloadState !== 'idle'"
      @click.stop="emit('download')"
    >
      <AppIcon :name="downloadState === 'done' ? 'check-dark' : 'download'" size="18px" />
    </button>
    <button
      type="button"
      class="card-quick-actions__btn"
      title="Details"
      aria-label="Details"
      @click.stop="emit('detail')"
    >
      <AppIcon name="info-outline-dark" size="18px" />
    </button>
  </div>
</template>

<script setup lang="ts">
/**
 * CardQuickActions — the hover/focus-within quick-action bar of a gallery
 * card (F-UX6, PC form only; GalleryCard only renders it when the
 * `pointer: fine` + ≥720px gate passes). Favorite / download / details, icon
 * semantics per the established atoms (heart/heart-outline, download,
 * info-outline).
 *
 * Presentation only — every action is emitted to the parent card, which
 * owns the API wiring (favorite slot follows `general.defaultFavoriteSlot`,
 * download via `downloadApi.add`, detail via the router).
 *
 * Variants: `grid` = strip across the tile bottom (over a scrim gradient);
 * `list` = floating pill at the card's right edge. The bar reveals on card
 * hover or focus-within (keyboard reachable — the buttons are tabbable), so
 * `keydown.enter/space` propagation is stopped to keep an activated button
 * from also activating the card surface underneath.
 */
import { computed } from 'vue'
import AppIcon from '@/components/atoms/AppIcon.vue'

const props = defineProps<{
  /** Placement: grid tile strip vs list card pill. */
  variant: 'grid' | 'list'
  /** Current favorite state (drives heart fill + toggle label). */
  isFavorited: boolean
  /** Favorite request in flight. */
  favoritePending: boolean
  /** Download queue state (idle → busy → done). */
  downloadState: 'idle' | 'busy' | 'done'
}>()

const emit = defineEmits<{
  (e: 'favorite'): void
  (e: 'download'): void
  (e: 'detail'): void
}>()

const favoriteLabel = computed(() => (props.isFavorited ? 'Remove from favorites' : 'Favorite'))

const downloadLabel = computed(() => {
  switch (props.downloadState) {
    case 'busy':
      return 'Adding to downloads…'
    case 'done':
      return 'Added to downloads'
    default:
      return 'Download'
  }
})
</script>

<style scoped>
.card-quick-actions {
  position: absolute;
  z-index: 2;
  display: flex;
  gap: 2px;
  opacity: 0;
  pointer-events: none;
  transition: opacity 140ms var(--ease-decelerate-quart);
}

/* Grid variant: strip across the tile bottom, over a scrim gradient. */
.card-quick-actions--grid {
  left: 0;
  right: 0;
  bottom: 0;
  justify-content: center;
  padding: 14px 6px 6px;
  background: linear-gradient(180deg, transparent 0%, var(--black-overlay) 100%);
}

/* List variant: floating pill on the card's right edge. */
.card-quick-actions--list {
  top: 50%;
  right: 8px;
  transform: translateY(-50%);
  padding: 3px;
  border-radius: var(--card-radius);
  background: var(--color-background-floating);
  box-shadow: 0 2px 8px var(--shadow-color);
}

.card-quick-actions__btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  cursor: pointer;
  transition: background-color 140ms var(--ease-decelerate-quart);
}

.card-quick-actions--grid .card-quick-actions__btn {
  color: var(--color-white);
}

.card-quick-actions--grid .card-quick-actions__btn:hover {
  background: rgba(255, 255, 255, 0.18);
}

.card-quick-actions--list .card-quick-actions__btn {
  color: var(--text-color-primary);
}

.card-quick-actions--list .card-quick-actions__btn:hover {
  background: var(--color-surface-activated);
}

.card-quick-actions__btn:disabled {
  opacity: 0.5;
  cursor: default;
}

.card-quick-actions__btn:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}

@media (prefers-reduced-motion: reduce) {
  .card-quick-actions {
    transition: none;
  }
}
</style>
