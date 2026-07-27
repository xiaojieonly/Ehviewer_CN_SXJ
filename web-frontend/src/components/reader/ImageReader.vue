<template>
  <div
    class="image-reader"
    @click="toggleToolbar"
    ref="readerRef"
  >
    <ReaderToolbar
      :hidden="toolbarHidden"
      :currentIndex="currentIndex"
      :totalPages="totalPages"
      @back="$emit('back')"
      @prevPage="$emit('prevPage')"
      @nextPage="$emit('nextPage')"
      @toggleSettings="showSettings = true"
    />

    <PageMode
      v-if="readMode === 'page'"
      ref="pageModeRef"
      :imageSrc="currentImageSrc"
      :currentIndex="currentIndex"
      :zoom="zoom"
    />

    <ScrollMode
      v-else
      ref="scrollModeRef"
      :images="imageUrls"
    />

    <ReaderSettings
      :visible="showSettings"
      :readMode="readMode"
      :zoom="zoom"
      @close="showSettings = false"
      @update:readMode="$emit('update:readMode', $event)"
      @zoomIn="$emit('zoomIn')"
      @zoomOut="$emit('zoomOut')"
      @resetZoom="$emit('resetZoom')"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import ReaderToolbar from './ReaderToolbar.vue'
import ReaderSettings from './ReaderSettings.vue'
import PageMode from './PageMode.vue'
import ScrollMode from './ScrollMode.vue'

const props = defineProps<{
  imageUrls: string[]
  currentIndex: number
  totalPages: number
  readMode: string
  zoom: number
}>()

defineEmits<{
  back: []
  prevPage: []
  nextPage: []
  'update:readMode': [mode: string]
  zoomIn: []
  zoomOut: []
  resetZoom: []
}>()

const readerRef = ref<HTMLElement | null>(null)
const pageModeRef = ref<InstanceType<typeof PageMode> | null>(null)
const scrollModeRef = ref<InstanceType<typeof ScrollMode> | null>(null)
const toolbarHidden = ref(false)
const showSettings = ref(false)

const currentImageSrc = computed(() => props.imageUrls[props.currentIndex] || '')

function toggleToolbar() {
  toolbarHidden.value = !toolbarHidden.value
}
</script>

<style scoped>
.image-reader {
  width: 100%;
  height: 100vh;
  overflow: hidden;
  background: #1a1a1a;
}
</style>
