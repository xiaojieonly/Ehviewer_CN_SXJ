<template>
  <div class="gallery-grid">
    <div v-if="loading" class="loading">Loading...</div>
    <div v-else-if="items.length === 0" class="empty">No galleries found</div>
    <div v-else class="grid">
      <GalleryCard
        v-for="item in items"
        :key="item.gid"
        :item="item"
        @click="$emit('select', item.gid)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import type { GalleryInfo } from '@/types'
import GalleryCard from './GalleryCard.vue'

defineProps<{
  items: GalleryInfo[]
  loading: boolean
}>()

defineEmits<{
  select: [gid: number]
}>()
</script>

<style scoped>
.gallery-grid {
  min-height: 200px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 1rem;
}
.loading, .empty {
  text-align: center;
  padding: 2rem;
  color: #666;
}
</style>
