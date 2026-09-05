<template>
  <a
    class="app-card"
    :class="`app-card--${mode}`"
    :href="detailUrl"
    role="button"
    tabindex="0"
    @click="onRootClick"
    @keydown.enter.prevent="activate"
    @keydown.space.prevent="activate"
  >
    <slot />
  </a>
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
 * B2 link semantics: the root is a real `<a>`; passing `detailUrl` makes
 * middle-click / Ctrl+click / the new-tab context menu work for free (the
 * href carries gid semantics only). Plain left clicks are intercepted —
 * they `preventDefault` the href navigation and emit `click` so the SPA
 * router stays in charge; modified clicks (Ctrl/Cmd/Shift/Alt) and non-left
 * buttons never emit and keep the browser's open-in-new-tab behavior.
 *
 * Implements the frozen `AppCardProps` / `AppCardSlots` / `AppCardEmits`
 * contracts from `@/types/components` (components.ts §Atoms), additively
 * extended with the optional `detailUrl` (the contract file stays
 * untouched).
 */
import {
  type AppCardEmits,
  type AppCardProps,
  type AppCardSlots,
} from '@/types/components'

defineProps<AppCardProps & { /** Detail route for the `<a>` href (B2). Optional — no href when absent. */ detailUrl?: string }>()
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

/**
 * Root click on the `<a>`: plain left clicks feed the app's `click` emit
 * (and suppress the href navigation — the SPA router owns them); modified
 * clicks and auxiliary buttons fall through to the browser's native
 * open-in-new-tab semantics and never reach the app.
 */
function onRootClick(event: MouseEvent): void {
  if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) {
    return
  }
  event.preventDefault()
  emit('click', event)
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
  /* The root is an `<a>` now (B2) — keep the global link color out of the
     card surface. */
  color: inherit;
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

/* B4 触屏豁免：(hover: none) 下没有 hover 通道，点按留下的粘滞 hover 会把
   抬升阴影永久卡在被点的卡片上（长按菜单 B1 / 详情页承载操作）。 */
@media (hover: none) {
  .app-card:hover {
    box-shadow: 0 var(--card-elevation) var(--card-max-elevation) var(--shadow-color);
  }
}
</style>
