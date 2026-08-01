<template>
  <div class="app-segmented" role="radiogroup" :aria-label="ariaLabel">
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      class="app-segmented__btn"
      :class="{ 'app-segmented__btn--active': option.value === modelValue }"
      role="radio"
      :aria-checked="option.value === modelValue"
      @click="$emit('update:modelValue', option.value)"
    >
      <AppIcon v-if="option.icon" :name="option.icon" :size="size" />
      <span>{{ option.label }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
/**
 * AppSegmented — segmented control for mutually exclusive options
 * (`role="radiogroup"` with `role="radio"` buttons).
 */
import AppIcon from '@/components/atoms/AppIcon.vue'

interface AppSegmentedOption {
  value: string
  label: string
  icon?: string
}

withDefaults(
  defineProps<{
    modelValue: string
    options: AppSegmentedOption[]
    ariaLabel?: string
    /** Icon size in px (default 18). */
    size?: string
  }>(),
  {
    ariaLabel: undefined,
    size: '18px',
  },
)

defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()
</script>

<style scoped>
.app-segmented {
  display: inline-flex;
  gap: 2px;
  padding: 2px;
  border-radius: var(--segment-radius, 8px);
  background: var(--color-surface);
}

.app-segmented__btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: none;
  border-radius: calc(var(--segment-radius, 8px) - 2px);
  background: transparent;
  color: var(--text-color-secondary);
  font-size: clamp(11px, 12px, 14px);
  white-space: nowrap;
  cursor: pointer;
  transition:
    background-color 150ms var(--ease-decelerate-quart),
    color 150ms var(--ease-decelerate-quart);
}

.app-segmented__btn:hover {
  color: var(--text-color-primary);
}

.app-segmented__btn--active {
  background: var(--content-color-theme-primary);
  color: var(--color-white);
  font-weight: 700;
  box-shadow: 0 1px 2px var(--shadow-color);
}
</style>
