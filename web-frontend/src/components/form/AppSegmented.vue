<template>
  <div
    ref="rootRef"
    class="app-segmented"
    role="radiogroup"
    :aria-label="ariaLabel"
    @keydown="onKeydown"
  >
    <button
      v-for="(option, index) in options"
      :key="option.value"
      type="button"
      class="app-segmented__btn"
      :class="{ 'app-segmented__btn--active': option.value === modelValue }"
      role="radio"
      :aria-checked="option.value === modelValue"
      :tabindex="index === activeIndex ? 0 : -1"
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
 *
 * Keyboard: roving tabindex radiogroup convention — Tab reaches the checked
 * (fallback: first) segment only, Arrow keys move the selection in the
 * direction they point and keep focus riding along (plan-2026-09-05 C7).
 */
import { computed, nextTick, ref } from 'vue'
import AppIcon from '@/components/atoms/AppIcon.vue'

interface AppSegmentedOption {
  value: string
  label: string
  icon?: string
}

const props = withDefaults(
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

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const rootRef = ref<HTMLDivElement | null>(null)

/** Checked segment, falling back to the first one when nothing matches. */
const activeIndex = computed(() => {
  const index = props.options.findIndex((option) => option.value === props.modelValue)
  return index === -1 ? 0 : index
})

/** Arrow Left/Up = previous, Arrow Right/Down = next (wraps around). */
function onKeydown(event: KeyboardEvent): void {
  const count = props.options.length
  if (count === 0) return
  const delta =
    event.key === 'ArrowRight' || event.key === 'ArrowDown'
      ? 1
      : event.key === 'ArrowLeft' || event.key === 'ArrowUp'
        ? -1
        : 0
  if (delta === 0) return
  event.preventDefault()
  const next = (activeIndex.value + delta + count) % count
  emit('update:modelValue', props.options[next].value)
  void nextTick(() => {
    rootRef.value
      ?.querySelectorAll<HTMLButtonElement>('.app-segmented__btn')
      [next]?.focus()
  })
}
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
