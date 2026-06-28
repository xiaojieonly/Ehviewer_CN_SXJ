<template>
  <span class="tag-chip">{{ tag }}</span>
</template>

<script setup lang="ts">
defineProps<{
  tag: string
}>()
</script>

<style scoped>
.tag-chip {
  display: inline-block;
  padding: 0.2rem 0.5rem;
  background: #e8f4fd;
  color: #2980b9;
  border-radius: 12px;
  font-size: 0.75rem;
  white-space: nowrap;
}
</style>
