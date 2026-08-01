<template>
  <div
    ref="rootRef"
    class="app-select"
    :class="{ 'app-select--open': open, 'app-select--disabled': disabled }"
  >
    <button
      ref="triggerRef"
      type="button"
      class="app-select__trigger"
      role="combobox"
      :aria-haspopup="'listbox'"
      :aria-expanded="open"
      :aria-controls="menuId"
      :aria-activedescendant="activeDescendant"
      :disabled="disabled"
      @click="toggle"
      @keydown="onTriggerKeydown"
    >
      <span class="app-select__value">{{ selectedLabel }}</span>
      <span class="app-select__arrow" aria-hidden="true" />
    </button>
    <Teleport to="body">
      <ul
        v-if="open"
        :id="menuId"
        ref="menuRef"
        class="app-select__menu"
        role="listbox"
        :style="menuStyle"
      >
        <li
          v-for="(option, index) in options"
          :key="option.value"
          :id="`${menuId}-option-${index}`"
          class="app-select__option"
          :class="{
            'app-select__option--selected': option.value === modelValue,
            'app-select__option--highlighted': index === highlightIndex,
            'app-select__option--disabled': option.disabled,
          }"
          role="option"
          :aria-selected="option.value === modelValue"
          :aria-disabled="option.disabled"
          @click="select(option)"
        >
          {{ option.label }}
        </li>
      </ul>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
/**
 * AppSelect — MD dropdown replacing native `<select>` everywhere.
 *
 * The trigger is a `role="combobox"` button that always renders the selected
 * option's label (UX-03: no more invisible selection). The listbox is
 * teleported to `<body>` and positioned with `position: fixed` via
 * `computeDropdownPosition`, so parent `overflow` never clips it.
 *
 * Keyboard: ↑/↓ move the highlight, Enter/Space selects, Escape closes.
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  computeDropdownPosition,
  isClickOutside,
  type DropdownPlacement,
} from '@/composables/useDropdownPosition'

interface AppSelectOption {
  value: string | number
  label: string
  disabled?: boolean
}

const props = withDefaults(
  defineProps<{
    modelValue: string | number
    options: AppSelectOption[]
    /** Floating label (usually unnecessary inside a PrefRow). */
    label?: string
    /** Shown when `modelValue` matches no option. */
    placeholder?: string
    disabled?: boolean
  }>(),
  {
    label: undefined,
    placeholder: undefined,
    disabled: false,
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | number): void
}>()

const menuId = `app-select-menu-${Math.random().toString(36).slice(2, 8)}`

const rootRef = ref<HTMLElement | null>(null)
const triggerRef = ref<HTMLButtonElement | null>(null)
const menuRef = ref<HTMLUListElement | null>(null)

const open = ref(false)
const highlightIndex = ref(-1)
const placement = ref<DropdownPlacement>({ top: 0, left: 0, minWidth: 0 })

const selectedLabel = computed<string>(() => {
  const match = props.options.find((option) => option.value === props.modelValue)
  if (match) return match.label
  return props.placeholder ?? ''
})

const activeDescendant = computed<string | undefined>(() =>
  open.value && highlightIndex.value >= 0
    ? `${menuId}-option-${highlightIndex.value}`
    : undefined,
)

const menuStyle = computed(() => ({
  top: `${placement.value.top}px`,
  left: `${placement.value.left}px`,
  minWidth: `${placement.value.minWidth}px`,
}))

function updatePlacement(): void {
  const trigger = triggerRef.value
  if (!trigger) return
  const rect = trigger.getBoundingClientRect()
  const menu = menuRef.value?.getBoundingClientRect()
  placement.value = computeDropdownPosition(
    { x: rect.x, y: rect.y, width: rect.width, height: rect.height },
    { width: menu?.width ?? rect.width, height: menu?.height ?? 0 },
    { width: window.innerWidth, height: window.innerHeight },
  )
}

function openMenu(): void {
  if (props.disabled) return
  open.value = true
  highlightIndex.value = props.options.findIndex((option) => option.value === props.modelValue)
  nextTick(updatePlacement)
}

function closeMenu(): void {
  open.value = false
  highlightIndex.value = -1
  triggerRef.value?.focus()
}

function toggle(): void {
  if (props.disabled) return
  if (open.value) closeMenu()
  else openMenu()
}

function select(option: AppSelectOption): void {
  if (option.disabled) return
  emit('update:modelValue', option.value)
  closeMenu()
}

function moveHighlight(delta: number): void {
  const enabled = props.options
    .map((option, index) => (option.disabled ? -1 : index))
    .filter((index) => index >= 0)
  if (enabled.length === 0) return
  const position = enabled.indexOf(highlightIndex.value)
  let next: number
  if (position === -1) {
    next = delta > 0 ? 0 : enabled.length - 1
  } else {
    next = (position + delta + enabled.length) % enabled.length
  }
  highlightIndex.value = enabled[next]
}

function selectHighlighted(): void {
  const option = props.options[highlightIndex.value]
  if (option && !option.disabled) select(option)
}

function onTriggerKeydown(event: KeyboardEvent): void {
  if (props.disabled) return
  switch (event.key) {
    case 'ArrowDown':
      event.preventDefault()
      if (open.value) moveHighlight(1)
      else openMenu()
      break
    case 'ArrowUp':
      event.preventDefault()
      if (open.value) moveHighlight(-1)
      break
    case 'Enter':
    case ' ':
      event.preventDefault()
      if (open.value) selectHighlighted()
      else openMenu()
      break
    case 'Escape':
      event.preventDefault()
      if (open.value) closeMenu()
      break
  }
}

function onDocMousedown(event: MouseEvent): void {
  if (!open.value) return
  // The listbox is teleported to <body>, so a click on an option must NOT be
  // treated as outside — closing here would detach the <li> before the click
  // event fires and the selection would be lost (real-browser behavior:
  // clicks never dispatch on removed elements).
  const menuEl = menuRef.value
  if (menuEl?.contains(event.target as Node)) return
  if (isClickOutside(rootRef.value, event.target)) closeMenu()
}

function onViewportChange(): void {
  if (open.value) {
    updatePlacement()
  }
}

onMounted(() => {
  document.addEventListener('mousedown', onDocMousedown)
  window.addEventListener('scroll', onViewportChange, true)
  window.addEventListener('resize', onViewportChange)
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocMousedown)
  window.removeEventListener('scroll', onViewportChange, true)
  window.removeEventListener('resize', onViewportChange)
})

watch(
  () => props.modelValue,
  () => {
    if (open.value) {
      highlightIndex.value = props.options.findIndex(
        (option) => option.value === props.modelValue,
      )
    }
  },
)
</script>

<style scoped>
.app-select {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.app-select__trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 96px;
  padding: 8px 12px;
  border: 1px solid var(--color-outline, var(--color-divider));
  border-radius: var(--field-radius, 4px);
  background: var(--color-background-floating);
  color: var(--text-color-primary);
  font-size: clamp(13px, 14px, 16px);
  cursor: pointer;
  outline: none;
  transition: border-color 150ms var(--ease-decelerate-quart);
}

.app-select__trigger:hover {
  border-color: var(--color-primary);
}

.app-select__trigger:focus-visible {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 1px var(--color-primary);
}

.app-select--disabled {
  opacity: 0.38;
  pointer-events: none;
}

.app-select__value {
  flex: 1;
  text-align: left;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-select__arrow {
  flex: 0 0 8px;
  width: 8px;
  height: 8px;
  border-right: 2px solid var(--drawable-color-secondary);
  border-bottom: 2px solid var(--drawable-color-secondary);
  transform: rotate(45deg);
  translate: 0 -1px;
  pointer-events: none;
}

.app-select__menu {
  position: fixed;
  z-index: var(--z-popup, 1000);
  margin: 0;
  padding: 4px 0;
  border-radius: var(--field-radius, 4px);
  background: var(--popup-window-background);
  box-shadow: 0 4px 16px var(--shadow-color);
  list-style: none;
}

.app-select__option {
  padding: 10px 16px;
  font-size: clamp(13px, 14px, 16px);
  color: var(--text-color-primary);
  cursor: pointer;
  white-space: nowrap;
}

.app-select__option--highlighted {
  background: var(--color-surface-activated);
}

.app-select__option--selected {
  color: var(--color-primary-text);
  font-weight: 700;
}

.app-select__option--disabled {
  opacity: 0.38;
  pointer-events: none;
}
</style>
