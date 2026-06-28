<template>
  <div class="scroll-mode" ref="containerRef">
    <img
      v-for="(src, index) in images"
      :key="index"
      :src="src"
      :alt="`Page ${index + 1}`"
      class="scroll-image"
      loading="lazy"
      draggable="false"
    />
    <div v-if="images.length === 0" class="empty">No images loaded</div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

defineProps<{
  images: string[]
}>()

const containerRef = ref<HTMLElement | null>(null)

defineExpose({ containerRef })
</script>

<style scoped>
.scroll-mode {
  width: 100%;
  background: #1a1a1a;
  padding: 0;
}
.scroll-image {
  display: block;
  width: 100%;
  user-select: none;
}
.empty {
  text-align: center;
  padding: 3rem;
  color: #666;
}
</style>
