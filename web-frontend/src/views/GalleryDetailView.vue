<template>
  <div class="detail">
    <AppHeader />
    <div class="content" v-if="gallery">
      <button class="back-btn" @click="$router.back()">← Back</button>
      <div class="detail-card">
        <div class="thumb-section">
          <img v-if="gallery.thumb" :src="gallery.thumb" :alt="gallery.title" class="thumb" />
        </div>
        <div class="info-section">
          <h1>{{ gallery.title }}</h1>
          <h2 v-if="gallery.titleJpn" class="title-jpn">{{ gallery.titleJpn }}</h2>
          <div class="meta">
            <span class="rating">★ {{ gallery.rating?.toFixed(1) }}</span>
            <span class="pages">{{ gallery.pages }} pages</span>
            <span v-if="gallery.uploader" class="uploader">by {{ gallery.uploader }}</span>
          </div>
          <div v-if="gallery.simpleTags?.length" class="tags">
            <TagChip v-for="tag in gallery.simpleTags" :key="tag" :tag="tag" />
          </div>
        </div>
      </div>
    </div>
    <div v-else class="loading">Loading...</div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { galleryApi } from '@/api/gallery'
import AppHeader from '@/components/layout/AppHeader.vue'
import TagChip from '@/components/common/TagChip.vue'
import type { GalleryDetail } from '@/types'

const route = useRoute()
const gallery = ref<GalleryDetail | null>(null)

onMounted(async () => {
  const gid = Number(route.params.gid)
  try {
    gallery.value = await galleryApi.getDetail(gid)
  } catch (e) {
    console.error('Failed to load gallery detail', e)
  }
})
</script>

<style scoped>
.detail {
  min-height: 100vh;
  background: #f5f5f5;
}
.content {
  max-width: 900px;
  margin: 0 auto;
  padding: 1rem;
}
.back-btn {
  margin-bottom: 1rem;
  padding: 0.4rem 0.8rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  background: white;
  cursor: pointer;
}
.detail-card {
  background: white;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}
.thumb-section {
  text-align: center;
  padding: 1rem;
  background: #fafafa;
}
.thumb {
  max-width: 100%;
  max-height: 500px;
  border-radius: 4px;
}
.info-section {
  padding: 1.5rem;
}
.info-section h1 {
  margin: 0 0 0.5rem;
  color: #333;
}
.title-jpn {
  color: #666;
  font-size: 1rem;
  font-weight: normal;
  margin-bottom: 1rem;
}
.meta {
  display: flex;
  gap: 1rem;
  margin-bottom: 1rem;
  color: #666;
  font-size: 0.9rem;
}
.rating {
  color: #f39c12;
  font-weight: bold;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}
.loading {
  text-align: center;
  padding: 3rem;
  color: #666;
}
</style>
