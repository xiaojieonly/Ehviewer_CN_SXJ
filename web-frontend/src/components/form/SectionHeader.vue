<template>
  <h2 class="section-header">
    <AppIcon v-if="icon" :name="icon" class="section-header__icon" :size="size" />
    <span>{{ title }}</span>
  </h2>
</template>

<script setup lang="ts">
/**
 * SectionHeader — uppercase primary-colored group heading with an optional
 * leading icon (icon and title share one line — UX-11).
 */
import AppIcon from '@/components/atoms/AppIcon.vue'

withDefaults(
  defineProps<{
    title: string
    /** AppIcon name shown before the title. */
    icon?: string
    /** Icon size in px (default 18). */
    size?: string
  }>(),
  {
    icon: undefined,
    size: '18px',
  },
)
</script>

<style scoped>
.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 22px 4px 8px;
  padding: 16px var(--keyline-margin, 16px) 4px;
  font-size: clamp(12px, 14px, 16px);
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-primary);
}

.section-header__icon {
  flex-shrink: 0;
}
</style>
