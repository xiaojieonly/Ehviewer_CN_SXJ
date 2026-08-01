/**
 * Shared form primitives — the single import point for all settings/admin
 * form components (see docs/PLAN-uiux-implementation.md §4).
 *
 * These components are a READ-ONLY contract for migration agents: import
 * them, never modify them. If an API is missing, report the gap to the
 * coordinator instead of editing files in this directory.
 */
export { default as AppSegmented } from './AppSegmented.vue'
export { default as AppSelect } from './AppSelect.vue'
export { default as AppSwitch } from './AppSwitch.vue'
export { default as AppTextField } from './AppTextField.vue'
export { default as PrefCard } from './PrefCard.vue'
export { default as PrefRow } from './PrefRow.vue'
export { default as SectionHeader } from './SectionHeader.vue'
