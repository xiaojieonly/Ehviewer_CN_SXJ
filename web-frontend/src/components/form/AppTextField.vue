<template>
  <label
    class="app-text-field"
    :class="{
      'app-text-field--error': errorText,
      'app-text-field--disabled': disabled,
      'app-text-field--focused': focused,
    }"
  >
    <input
      ref="inputRef"
      class="app-text-field__input"
      :type="type"
      :value="modelValue"
      :placeholder="placeholder || ' '"
      :disabled="disabled"
      :maxlength="maxlength"
      :aria-label="ariaLabel ?? label"
      @input="onInput"
      @focus="focused = true"
      @blur="focused = false"
    />
    <span v-if="label" class="app-text-field__label">{{ label }}</span>
    <span v-if="errorText || helperText" class="app-text-field__helper">
      {{ errorText || helperText }}
    </span>
  </label>
</template>

<script setup lang="ts">
/**
 * AppTextField — MD outlined text field with a floating label, helper text
 * and an error state (red border/label/helper when `errorText` is set).
 */
import { ref } from 'vue'

withDefaults(
  defineProps<{
    modelValue: string
    label?: string
    type?: 'text' | 'password' | 'number' | 'url'
    placeholder?: string
    /** Shown under the field; overridden visually by `errorText`. */
    helperText?: string
    /** Non-empty → error state (red border, label and helper text). */
    errorText?: string
    disabled?: boolean
    maxlength?: number
    /** Accessible name; falls back to `label`. */
    ariaLabel?: string
  }>(),
  {
    label: undefined,
    type: 'text',
    placeholder: undefined,
    helperText: undefined,
    errorText: undefined,
    disabled: false,
    maxlength: undefined,
    ariaLabel: undefined,
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const focused = ref(false)

function onInput(event: Event): void {
  emit('update:modelValue', (event.target as HTMLInputElement).value)
}
</script>

<style scoped>
.app-text-field {
  position: relative;
  display: block;
  width: 100%;
  min-height: var(--field-height, 48px);
}

.app-text-field__input {
  width: 100%;
  height: var(--field-height, 48px);
  padding: 16px var(--field-padding-h, 16px) 12px;
  border: 1px solid var(--color-outline, var(--color-divider));
  border-radius: var(--field-radius, 4px);
  background: transparent;
  font-size: clamp(14px, 16px, 18px);
  color: var(--text-color-primary);
  caret-color: var(--color-accent);
  outline: none;
  transition:
    border-color 150ms var(--ease-decelerate-quart),
    box-shadow 150ms var(--ease-decelerate-quart);
}

.app-text-field__input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 1px var(--color-primary);
}

.app-text-field--error .app-text-field__input {
  border-color: var(--color-error, #b00020);
  box-shadow: 0 0 0 1px var(--color-error, #b00020);
}

.app-text-field--disabled {
  opacity: 0.6;
}

.app-text-field__label {
  position: absolute;
  left: var(--field-padding-h, 16px);
  top: 50%;
  translate: 0 -50%;
  padding: 0 4px;
  font-size: clamp(14px, 16px, 18px);
  color: var(--text-color-secondary);
  background: var(--color-background-floating);
  pointer-events: none;
  transition:
    top 150ms var(--ease-decelerate-quart),
    font-size 150ms var(--ease-decelerate-quart),
    color 150ms var(--ease-decelerate-quart);
}

.app-text-field__input:focus + .app-text-field__label,
.app-text-field__input:not(:placeholder-shown) + .app-text-field__label {
  top: 0;
  font-size: var(--field-label-size, 12px);
  color: var(--color-primary);
}

.app-text-field--error .app-text-field__label {
  color: var(--color-error, #b00020);
}

.app-text-field__helper {
  display: block;
  padding: 4px var(--field-padding-h, 16px) 0;
  font-size: clamp(11px, 12px, 14px);
  color: var(--text-color-secondary);
}

.app-text-field--error .app-text-field__helper {
  color: var(--color-error, #b00020);
}
</style>
