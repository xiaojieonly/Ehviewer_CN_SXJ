<template>
  <div class="reader-settings" v-if="visible">
    <div class="settings-overlay" @click="$emit('close')"></div>
    <div class="settings-panel">
      <h3>Settings</h3>
      <div class="setting-item">
        <label>Read Mode</label>
        <div class="setting-options">
          <button :class="{ active: readMode === 'page' }" @click="$emit('update:readMode', 'page')">Page</button>
          <button :class="{ active: readMode === 'scroll' }" @click="$emit('update:readMode', 'scroll')">Scroll</button>
        </div>
      </div>
      <div class="setting-item">
        <label>Zoom</label>
        <div class="setting-options">
          <button @click="$emit('zoomOut')">−</button>
          <span class="zoom-value">{{ Math.round(zoom * 100) }}%</span>
          <button @click="$emit('zoomIn')">+</button>
          <button @click="$emit('resetZoom')">Reset</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  visible: boolean
  readMode: string
  zoom: number
}>()

defineEmits<{
  close: []
  'update:readMode': [mode: string]
  zoomIn: []
  zoomOut: []
  resetZoom: []
}>()
</script>

<style scoped>
.settings-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 99;
}
.settings-panel {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: white;
  border-radius: 12px 12px 0 0;
  padding: 1.5rem;
  z-index: 100;
  box-shadow: 0 -4px 12px rgba(0, 0, 0, 0.15);
}
.settings-panel h3 {
  margin: 0 0 1rem;
  color: #333;
}
.setting-item {
  margin-bottom: 1rem;
}
.setting-item label {
  display: block;
  font-weight: 500;
  margin-bottom: 0.5rem;
  color: #555;
}
.setting-options {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}
.setting-options button {
  padding: 0.4rem 0.8rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  background: white;
  cursor: pointer;
  font-size: 0.9rem;
}
.setting-options button.active {
  background: #4a90d9;
  color: white;
  border-color: #4a90d9;
}
.zoom-value {
  min-width: 3rem;
  text-align: center;
  font-size: 0.9rem;
}
</style>
