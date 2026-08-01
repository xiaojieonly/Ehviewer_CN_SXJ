<template>
  <div
    class="app-card"
    :class="`app-card--${mode}`"
    role="button"
    tabindex="0"
    @click="emit('click', $event)"
    @keydown.enter.prevent="activate"
    @keydown.space.prevent="activate"
  >
    <slot />
  </div>
</template>

<script setup lang="ts">
/**
 * AppCard — the app's restrained-Material card surface (roadmap §卡片规范,
 * `CardView.Normal` replica): radius 2dp, elevation/shadow 2dp, outer margin
 * 2dp, background = `--color-surface` (`contentColorPrimary`, theme-aware).
 *
 * Purely presentational: all layout and content come from the default slot;
 * `mode` only adjusts the surface's flex direction as a sizing hint
 * (`list` = row, `grid` = column). The card is keyboard-activatable and
 * emits `click` with the raw event — consumers re-emit with their own
 * payload (e.g. GalleryCard re-emits the gallery).
 *
 * Implements the frozen `AppCardProps` / `AppCardSlots` / `AppCardEmits`
 * contracts from `@/types/components` (components.ts §Atoms).
 */
import {
  type AppCardEmits,
  type AppCardProps,
  type AppCardSlots,
} from '@/types/components'

defineProps<AppCardProps>()
defineSlots<AppCardSlots>()
const emit = defineEmits<AppCardEmits>()

/**
 * Keyboard activation (enter/space) — emits `click` per the frozen
 * `AppCardEmits` signature; the actual `KeyboardEvent` is carried as the
 * (typed) `MouseEvent` payload, mirroring how a real button synthesizes
 * click on keypress.
 */
function activate(event: KeyboardEvent): void {
  emit('click', event as unknown as MouseEvent)
}
</script>

<style scoped>
.app-card {
  display: flex;
  background: var(--color-surface);
  border-radius: var(--card-radius); /* 2px */
  box-shadow: 0 var(--card-elevation) var(--card-max-elevation) var(--shadow-color);
  margin: 2px;
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow 160ms var(--ease-decelerate-quart);
}

.app-card:hover {
  box-shadow: 0 var(--card-elevation) calc(var(--card-max-elevation) * 3) var(--shadow-color);
}

.app-card:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}

/* `mode` is a sizing hint only: `list` = horizontal row card,
   `grid` = vertical tile. */
.app-card--list {
  flex-direction: row;
}

.app-card--grid {
  flex-direction: column;
}
</style>
