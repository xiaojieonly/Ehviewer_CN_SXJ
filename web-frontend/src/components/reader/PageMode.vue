<template>
  <div class="page-mode" ref="containerRef">
    <div
      class="page-image"
      :style="{ transform: `scale(${zoom}) translateX(${panX}px)`, transformOrigin: 'center center' }"
    >
      <img
        v-if="imageSrc"
        :src="imageSrc"
        :alt="`Page ${currentIndex + 1}`"
        @load="onImageLoad"
        draggable="false"
      />
      <div v-else class="placeholder">Loading...</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

defineProps<{
  imageSrc: string
  currentIndex: number
  zoom: number
}>()

const containerRef = ref<HTMLElement | null>(null)
const panX = ref(0)

function onImageLoad() {
  panX.value = 0
}

defineExpose({ containerRef })
</script>

<style scoped>
.page-mode {
  width: 100%;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: #1a1a1a;
}
.page-image {
  transition: transform 0.2s ease;
  max-width: 100%;
  max-height: 100vh;
}
.page-image img {
  max-width: 100vw;
  max-height: 100vh;
  object-fit: contain;
  user-select: none;
}
.placeholder {
  color: #666;
  font-size: 1.2rem;
}
</style>
