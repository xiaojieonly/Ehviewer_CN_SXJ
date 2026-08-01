<template>
  <div class="pref-row" :class="{ 'pref-row--disabled': disabled }">
    <AppIcon v-if="icon" :name="icon" class="pref-row__icon" />
    <div class="pref-row__text">
      <span class="pref-row__title">{{ title }}</span>
      <span v-if="summary" class="pref-row__summary">{{ summary }}</span>
    </div>
    <div class="pref-row__control">
      <slot />
    </div>
    <div v-if="$slots.below" class="pref-row__below">
      <slot name="below" />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * PrefRow — a settings row: leading icon, stacked title/summary, and a
 * right-aligned control area (default slot) plus an optional full-width
 * "below" slot for expanded content.
 */
import AppIcon from '@/components/atoms/AppIcon.vue'

withDefaults(
  defineProps<{
    /** AppIcon name rendered at the leading edge (optional). */
    icon?: string
    /** Primary row title. */
    title: string
    /** Secondary line under the title. */
    summary?: string
    /** Disabled state — dims the row and blocks interaction. */
    disabled?: boolean
  }>(),
  {
    icon: undefined,
    summary: undefined,
    disabled: false,
  },
)
</script>

<style scoped>
.pref-row {
  position: relative;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 16px;
  min-height: var(--field-height, 48px);
  padding: 10px var(--keyline-margin, 16px);
}

.pref-row__icon {
  flex: 0 0 24px;
  color: var(--drawable-color-primary);
}

.pref-row__text {
  flex: 1 1 160px;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.pref-row__title {
  font-size: clamp(14px, 16px, 18px);
  color: var(--text-color-primary);
}

.pref-row__summary {
  font-size: clamp(11px, 12px, 14px);
  color: var(--text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pref-row__control {
  margin-left: auto;
}

.pref-row__below {
  flex: 1 1 100%;
  min-width: 0;
}

.pref-row--disabled {
  opacity: 0.38;
  pointer-events: none;
}
</style>
