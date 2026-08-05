<template>
  <Teleport to="body">
    <div
      ref="rootRef"
      class="card-context-menu"
      role="menu"
      aria-label="Gallery actions"
      :style="positionStyle"
    >
      <button
        v-for="item in items"
        :key="item.id"
        ref="itemRefs"
        type="button"
        role="menuitem"
        class="card-context-menu__item"
        @click="emit('action', item.id)"
      >
        <AppIcon :name="item.icon" size="16px" />
        <span class="card-context-menu__label">{{ item.label }}</span>
      </button>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
/**
 * CardContextMenu — the right-click menu of a gallery card (F-UX6, PC form
 * only; rendered by GalleryCard). Teleported to `body` with fixed
 * positioning so the grid/list scroll containers never clip it, clamped into
 * the viewport. Focus moves to the first item on open; Escape (document
 * level), outside mousedown, window resize/scroll dismiss it — the parent
 * owns the actual state and receives `action` with the invoked item id.
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import AppIcon from '@/components/atoms/AppIcon.vue'

export interface CardContextMenuItem {
  /** Stable action id the parent switches on. */
  id: 'detail' | 'favorite' | 'download' | 'copy-link'
  /** AppIcon registry name. */
  icon: string
  label: string
}

const props = defineProps<{
  /** Viewport coordinates of the invoking `contextmenu` event. */
  x: number
  y: number
  items: CardContextMenuItem[]
}>()

const emit = defineEmits<{
  (e: 'action', id: CardContextMenuItem['id']): void
  (e: 'close'): void
}>()

const rootRef = ref<HTMLElement | null>(null)
const itemRefs = ref<HTMLButtonElement[]>([])

/**
 * Estimated footprint for viewport clamping (constants — no layout reads,
 * keeps the math deterministic in tests and avoids a second render pass).
 */
const MENU_WIDTH = 190
const MENU_ITEM_HEIGHT = 34
const MENU_PADDING = 12
const VIEWPORT_MARGIN = 8

const positionStyle = computed(() => {
  const height = props.items.length * MENU_ITEM_HEIGHT + MENU_PADDING
  const maxX = Math.max(VIEWPORT_MARGIN, window.innerWidth - MENU_WIDTH - VIEWPORT_MARGIN)
  const maxY = Math.max(VIEWPORT_MARGIN, window.innerHeight - height - VIEWPORT_MARGIN)
  return {
    left: `${Math.min(props.x, maxX)}px`,
    top: `${Math.min(props.y, maxY)}px`,
  }
})

function onDocumentMousedown(event: MouseEvent): void {
  const target = event.target
  if (rootRef.value && target instanceof Node && !rootRef.value.contains(target)) {
    emit('close')
  }
}

function onDocumentKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') emit('close')
}

function close(): void {
  emit('close')
}

onMounted(() => {
  document.addEventListener('mousedown', onDocumentMousedown)
  document.addEventListener('keydown', onDocumentKeydown)
  window.addEventListener('resize', close)
  // Capture: ANY scroll container (ContentLayout, page) moves the card away
  // from the pinned menu.
  window.addEventListener('scroll', close, true)
  void nextTick(() => {
    itemRefs.value[0]?.focus()
  })
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocumentMousedown)
  document.removeEventListener('keydown', onDocumentKeydown)
  window.removeEventListener('resize', close)
  window.removeEventListener('scroll', close, true)
})
</script>

<style scoped>
.card-context-menu {
  position: fixed;
  z-index: 300;
  min-width: 176px;
  padding: 6px 0;
  background: var(--color-background-floating);
  border-radius: var(--card-radius);
  box-shadow:
    0 6px 24px var(--shadow-color),
    0 0 1px var(--shadow-color);
  animation: card-context-menu-in 120ms var(--ease-decelerate-quart);
}

@keyframes card-context-menu-in {
  from {
    opacity: 0;
    transform: scale(0.96);
  }
}

.card-context-menu__item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 8px 14px;
  border: none;
  background: transparent;
  color: var(--text-color-primary);
  font-family: inherit;
  font-size: var(--text-small);
  line-height: 1.3;
  text-align: left;
  cursor: pointer;
  transition: background-color 120ms var(--ease-decelerate-quart);
}

.card-context-menu__item:hover {
  background: var(--color-surface-activated);
}

.card-context-menu__item:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

.card-context-menu__label {
  white-space: nowrap;
}

@media (prefers-reduced-motion: reduce) {
  .card-context-menu {
    animation: none;
  }

  .card-context-menu__item {
    transition: none;
  }
}
</style>
