<template>
  <button
    type="button"
    class="app-switch"
    :class="{
      'app-switch--on': modelValue,
      'app-switch--disabled': disabled,
    }"
    role="switch"
    :aria-checked="modelValue"
    :aria-label="ariaLabel"
    :disabled="disabled"
    @click="$emit('update:modelValue', !modelValue)"
  >
    <span class="app-switch__track">
      <span class="app-switch__thumb" />
    </span>
  </button>
</template>

<script setup lang="ts">
/**
 * AppSwitch — MD3-style toggle switch. Track 52×32, 24px thumb; on state
 * translateX(20px). `ariaLabel` is required in practice whenever no visible
 * text labels the switch.
 */
defineProps<{
  modelValue: boolean
  disabled?: boolean
  /** Accessible name when there is no visible text nearby. */
  ariaLabel?: string
}>()

defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()
</script>

<style scoped>
.app-switch {
  position: relative;
  flex: 0 0 var(--switch-width, 52px);
  width: var(--switch-width, 52px);
  height: var(--switch-height, 32px);
  padding: 0;
  border: none;
  border-radius: 999px;
  background: var(--color-surface-variant, var(--widget-color));
  cursor: pointer;
  transition: background-color 200ms var(--ease-decelerate-quart);
}

.app-switch--on {
  background: var(--color-primary);
}

.app-switch--disabled {
  opacity: 0.38;
  pointer-events: none;
}

.app-switch__track {
  position: relative;
  display: block;
  width: 100%;
  height: 100%;
}

.app-switch__thumb {
  position: absolute;
  top: 4px;
  left: 4px;
  width: var(--switch-thumb-size, 24px);
  height: var(--switch-thumb-size, 24px);
  border-radius: 50%;
  background: var(--color-background-floating);
  box-shadow: 0 1px 2px var(--shadow-color);
  transition:
    transform 200ms var(--ease-decelerate-quart),
    background-color 200ms var(--ease-decelerate-quart);
}

.app-switch--on .app-switch__thumb {
  transform: translateX(20px);
}
</style>
