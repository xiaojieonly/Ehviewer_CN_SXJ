<template>
  <div class="pref-card">
    <slot />
  </div>
</template>

<script setup lang="ts">
/**
 * PrefCard — the card container grouping preference rows. Dividers between
 * direct children are drawn automatically (`:not(:last-child)::after`), so
 * migrations do not need explicit divider elements.
 */
</script>

<style scoped>
.pref-card {
  background: var(--color-background-floating);
  border-radius: var(--card-radius);
  box-shadow:
    0 var(--card-elevation) 4px var(--shadow-color),
    0 0 1px var(--shadow-color);
  overflow: hidden;
}

.pref-card > :not(:last-child)::after {
  content: '';
  position: absolute;
  left: var(--keyline-margin, 16px);
  right: 0;
  bottom: 0;
  height: 1px;
  background: var(--color-divider);
}
</style>
