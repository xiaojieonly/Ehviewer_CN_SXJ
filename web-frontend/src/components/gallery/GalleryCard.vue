<template>
  <div class="gallery-card">
    <div class="thumb-wrapper">
      <img
        v-if="item.thumb"
        :src="item.thumb"
        :alt="item.title"
        loading="lazy"
        class="thumb"
      />
      <div v-else class="thumb-placeholder"></div>
      <span class="category-badge" :style="{ background: categoryColor }">{{ categoryName }}</span>
    </div>
    <div class="card-info">
      <h3 class="title" :title="item.title">{{ item.title }}</h3>
      <div class="meta">
        <span class="rating">★ {{ item.rating?.toFixed(1) }}</span>
        <span v-if="item.simpleLanguage" class="lang">{{ item.simpleLanguage }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { GalleryInfo } from '@/types'
import { CATEGORY_MAP, CATEGORY_COLORS } from '@/types'

const props = defineProps<{
  item: GalleryInfo
}>()

const categoryName = computed(() => CATEGORY_MAP[props.item.category] || 'Unknown')
const categoryColor = computed(() => CATEGORY_COLORS[props.item.category] || '#95a5a6')
</script>

<style scoped>
.gallery-card {
  background: white;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
  cursor: pointer;
  transition: box-shadow 0.2s;
}
.gallery-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}
.thumb-wrapper {
  position: relative;
  aspect-ratio: 1;
  background: #fafafa;
}
.thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.thumb-placeholder {
  width: 100%;
  height: 100%;
  background: #e0e0e0;
}
.category-badge {
  position: absolute;
  top: 4px;
  right: 4px;
  padding: 2px 6px;
  border-radius: 3px;
  color: white;
  font-size: 0.7rem;
  font-weight: 500;
}
.card-info {
  padding: 0.5rem;
}
.title {
  font-size: 0.85rem;
  margin: 0 0 0.3rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #333;
}
.meta {
  display: flex;
  justify-content: space-between;
  font-size: 0.75rem;
  color: #666;
}
.rating {
  color: #f39c12;
}
.lang {
  color: #4a90d9;
}
</style>
