<template>
  <div class="download-item">
    <img :src="item.thumb || ''" class="thumb" />
    <div class="info">
      <h3>{{ item.title || item.titleJpn || 'Unknown' }}</h3>
      <div class="meta">
        <span class="state" :class="stateClass">{{ stateText }}</span>
        <span v-if="item.state === 2">{{ item.done }}/{{ item.total }}</span>
        <span v-if="item.total > 0" class="progress-text">{{ progressPercent }}%</span>
      </div>
      <div class="progress-bar" v-if="item.state === 2">
        <div class="progress" :style="{ width: progressPercent + '%' }"></div>
      </div>
    </div>
    <div class="actions">
      <button v-if="item.state === 0 || item.state === 4" @click="$emit('start', item.id)" title="Start">&#9654;</button>
      <button v-if="item.state === 2" @click="$emit('pause', item.id)" title="Pause">&#9208;</button>
      <button @click="$emit('cancel', item.id)" title="Cancel">&#10005;</button>
      <button @click="$emit('delete', item.id)" title="Delete">&#128465;</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { DownloadItem } from '../../api/download'

const props = defineProps<{
  item: DownloadItem
}>()

defineEmits<{
  start: [id: number]
  pause: [id: number]
  cancel: [id: number]
  delete: [id: number]
}>()

const stateText = computed(() => {
  const states: Record<number, string> = {
    0: 'Waiting',
    1: 'Queued',
    2: 'Downloading',
    3: 'Done',
    4: 'Failed',
  }
  return states[props.item.state] || 'Unknown'
})

const stateClass = computed(() => {
  return `state-${props.item.state}`
})

const progressPercent = computed(() => {
  if (props.item.total === 0) return 0
  return Math.round((props.item.done / props.item.total) * 100)
})
</script>

<style scoped>
.download-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: white;
  border-radius: 8px;
  margin-bottom: 8px;
}
.thumb {
  width: 60px;
  height: 80px;
  object-fit: cover;
  border-radius: 4px;
}
.info {
  flex: 1;
}
h3 {
  margin: 0 0 4px;
  font-size: 14px;
}
.meta {
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: #666;
}
.progress-text {
  font-weight: bold;
}
.state {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
}
.state-0 { background: #e0e0e0; }
.state-1 { background: #fff3cd; }
.state-2 { background: #d4edda; color: #155724; }
.state-3 { background: #cce5ff; color: #004085; }
.state-4 { background: #f8d7da; color: #721c24; }
.progress-bar {
  height: 4px;
  background: #e0e0e0;
  border-radius: 2px;
  margin-top: 8px;
  overflow: hidden;
}
.progress {
  height: 100%;
  background: #4a90d9;
  transition: width 0.3s;
}
.actions {
  display: flex;
  gap: 4px;
}
.actions button {
  background: none;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 16px;
  cursor: pointer;
  padding: 4px 8px;
}
.actions button:hover {
  background: #f0f0f0;
}
</style>
