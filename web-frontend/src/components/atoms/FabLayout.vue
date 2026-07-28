<template>
  <div class="fab-layout" :class="{ 'fab-layout--expanded': expanded }">
    <!-- Dim backdrop shown while expanded; tap collapses (autoCancel) -->
    <div
      class="fab-layout__backdrop"
      :class="{ 'fab-layout__backdrop--visible': expanded && autoCancel }"
      @click="onBackdropClick"
    />

    <div class="fab-layout__cluster">
      <!-- Secondary 40dp mini FABs stacked above the primary -->
      <button
        v-for="(action, index) in visibleActions"
        :key="action.id"
        type="button"
        class="fab fab--mini"
        :class="{ 'fab--collapsed': !expanded }"
        :style="{ transitionDelay: expanded ? `${index * 30}ms` : '0ms' }"
        :aria-label="action.label"
        :title="action.label"
        :tabindex="expanded ? 0 : -1"
        @click.stop="onSecondaryClick(action, index)"
      >
        <AppIcon :name="action.icon" size="24px" color="#ffffff" />
      </button>

      <!-- Primary 56dp FAB -->
      <button
        v-show="!hidePrimaryFab || expanded"
        type="button"
        class="fab fab--primary"
        :aria-label="primaryLabel"
        :aria-expanded="expanded"
        @click.stop="onPrimaryClick"
      >
        <AppIcon :name="primaryIcon" size="24px" color="#ffffff" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * FabLayout — web replica of `FabLayout.java`: a bottom-right cluster with ONE
 * primary FAB (56dp, `--fab-size`) and N secondary mini FABs (40dp,
 * `--fab-size-mini`) stacked above it, expand/collapse animated with the
 * decelerate curves from `tokens.css`.
 *
 * Android defaults preserved: `expanded = true`, `autoCancel = true` (tap on
 * the backdrop collapses), `hidePrimaryFab = false`.
 *
 * Expansion is v-model'd (`update:expanded`); the primary FAB toggles it and
 * the backdrop collapses it. Secondary taps only report `click-secondary`
 * (with the action + its rendered index) — mirroring Android, where the host
 * scene decides whether to collapse after an action.
 */
import { computed } from 'vue'
import type { FabLayoutProps, FabLayoutEmits, FabAction } from '@/types/components'
import AppIcon from './AppIcon.vue'

const props = withDefaults(defineProps<FabLayoutProps>(), {
  primaryIcon: 'plus-dark',
  expanded: true,
  autoCancel: true,
  hidePrimaryFab: false,
})

const emit = defineEmits<FabLayoutEmits>()

/** Accessible label for the primary FAB. */
const primaryLabel = '展开操作'

/** Secondary actions with `visible !== false` (Android per-action visibility). */
const visibleActions = computed<FabAction[]>(() =>
  props.actions.filter((action) => action.visible !== false),
)

function setExpanded(next: boolean) {
  emit('update:expanded', next)
  emit('expand', next)
}

function onPrimaryClick() {
  setExpanded(!props.expanded)
  emit('click-primary')
}

function onSecondaryClick(action: FabAction, index: number) {
  emit('click-secondary', action, index)
}

function onBackdropClick() {
  if (props.autoCancel && props.expanded) {
    setExpanded(false)
  }
}
</script>

<style scoped>
.fab-layout {
  /* Container is layout-transparent; children are fixed-positioned. */
  pointer-events: none;
}

/* Backdrop -------------------------------------------------------------- */
.fab-layout__backdrop {
  position: fixed;
  inset: 0;
  z-index: 90;
  background: var(--black-overlay);
  opacity: 0;
  pointer-events: none;
  transition: opacity var(--duration-scene-opacity) var(--ease-decelerate-quart);
}

.fab-layout__backdrop--visible {
  opacity: 1;
  pointer-events: auto;
}

/* Cluster --------------------------------------------------------------- */
.fab-layout__cluster {
  position: fixed;
  /* 16px corner margin + safe-area insets, so the FABs clear the home
     indicator (bottom) and any rounded-corner / cutout edge (right). */
  right: calc(var(--corner-fab-margin) + var(--safe-area-right));
  bottom: calc(var(--corner-fab-margin) + var(--safe-area-bottom));
  z-index: 100;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--fab-layout-secondary-margin); /* 16px */
  pointer-events: auto;
}

/* FAB buttons ----------------------------------------------------------- */
.fab {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: var(--color-accent); /* theme-aware accent */
  color: var(--color-white);
  cursor: pointer;
  box-shadow: 0 2px 5px var(--shadow-color);
  transition:
    transform 300ms var(--ease-decelerate-quint),
    opacity 300ms var(--ease-decelerate-quart),
    box-shadow 160ms var(--ease-decelerate-quart);
}

.fab:hover {
  box-shadow: 0 4px 9px var(--shadow-color);
}

.fab:active {
  transform: scale(0.92);
}

.fab--primary {
  width: var(--fab-size); /* 56px */
  height: var(--fab-size);
}

.fab--mini {
  width: var(--fab-size-mini); /* 40px */
  height: var(--fab-size-mini);
}

/* Collapsed secondary FABs shrink + fade out (scale from the bottom). */
.fab--mini.fab--collapsed {
  transform: scale(0);
  opacity: 0;
  pointer-events: none;
}
</style>
