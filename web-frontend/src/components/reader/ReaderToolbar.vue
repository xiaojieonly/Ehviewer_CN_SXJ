<template>
  <div class="reader-toolbar" :class="{ hidden: hidden }">
    <div class="toolbar-top">
      <button class="btn-back" @click="$emit('back')">← Back</button>
      <span class="page-info">{{ currentIndex + 1 }} / {{ totalPages }}</span>
    </div>
    <div class="toolbar-bottom">
      <button @click="$emit('prevPage')">◀</button>
      <button @click="$emit('toggleSettings')">⚙</button>
      <button @click="$emit('nextPage')">▶</button>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  hidden: boolean
  currentIndex: number
  totalPages: number
}>()

defineEmits<{
  back: []
  prevPage: []
  nextPage: []
  toggleSettings: []
}>()
</script>

<style scoped>
.reader-toolbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
  z-index: 50;
  transition: opacity 0.2s;
}
.reader-toolbar.hidden {
  opacity: 0;
}
.reader-toolbar.hidden * {
  pointer-events: none;
}
.toolbar-top {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem 1rem;
  background: linear-gradient(rgba(0, 0, 0, 0.7), transparent);
  pointer-events: auto;
}
.btn-back {
  padding: 0.3rem 0.8rem;
  border: none;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.2);
  color: white;
  cursor: pointer;
  font-size: 0.9rem;
}
.page-info {
  color: white;
  font-size: 0.9rem;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.5);
}
.toolbar-bottom {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 2rem;
  padding: 1rem;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.7));
  pointer-events: auto;
}
.toolbar-bottom button {
  width: 48px;
  height: 48px;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  color: white;
  font-size: 1.2rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}
.toolbar-bottom button:hover {
  background: rgba(255, 255, 255, 0.3);
}
</style>
