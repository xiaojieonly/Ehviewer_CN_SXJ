<template>
  <div class="reader-view">
    <ImageReader
      v-if="imageUrls.length > 0"
      :imageUrls="imageUrls"
      :currentIndex="currentIndex"
      :totalPages="imageUrls.length"
      :readMode="readMode"
      :zoom="zoom"
      @back="goBack"
      @prevPage="prevPage"
      @nextPage="nextPage"
      @update:readMode="readMode = $event as 'page' | 'scroll'"
      @zoomIn="zoomIn"
      @zoomOut="zoomOut"
      @resetZoom="resetZoom"
    />
    <div v-else class="loading">Loading pages...</div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { galleryApi } from '@/api/gallery'
import ImageReader from '@/components/reader/ImageReader.vue'
import { useKeyboardNav } from '@/composables/useKeyboardNav'

const route = useRoute()
const router = useRouter()

const gid = computed(() => Number(route.params.gid))
const pageIndex = computed(() => Number(route.params.page) || 0)
const imageUrls = ref<string[]>([])
const currentIndex = ref(0)
const readMode = ref<'page' | 'scroll'>('page')
const zoom = ref(1)

function goBack() {
  router.push(`/gallery/${gid.value}`)
}

function prevPage() {
  if (currentIndex.value > 0) {
    currentIndex.value--
    updateUrl()
  }
}

function nextPage() {
  if (currentIndex.value < imageUrls.value.length - 1) {
    currentIndex.value++
    updateUrl()
  }
}

function updateUrl() {
  router.replace({
    name: 'Reader',
    params: { gid: gid.value, page: currentIndex.value },
  })
}

function zoomIn() {
  zoom.value = Math.min(zoom.value + 0.25, 3)
}

function zoomOut() {
  zoom.value = Math.max(zoom.value - 0.25, 0.5)
}

function resetZoom() {
  zoom.value = 1
}

useKeyboardNav({
  onPrev: prevPage,
  onNext: nextPage,
  onFirst: () => { currentIndex.value = 0; updateUrl() },
  onLast: () => { currentIndex.value = imageUrls.value.length - 1; updateUrl() },
  onZoomIn: zoomIn,
  onZoomOut: zoomOut,
})

onMounted(async () => {
  currentIndex.value = pageIndex.value
  try {
    const detail = await galleryApi.getDetail(gid.value)
    if (detail.imageUrl) {
      const urls: string[] = []
      for (let i = 1; i <= detail.pages; i++) {
        urls.push(`/api/v1/image/proxy?url=${encodeURIComponent(detail.imageUrl.replace('{page}', String(i)))}`)
      }
      imageUrls.value = urls
    }
  } catch (e) {
    console.error('Failed to load gallery images', e)
  }
})
</script>

<style scoped>
.reader-view {
  width: 100%;
  height: 100vh;
  background: #1a1a1a;
  overflow: hidden;
}
.loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  color: #666;
  font-size: 1.2rem;
}
</style>
