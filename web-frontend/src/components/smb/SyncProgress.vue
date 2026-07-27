<template>
  <div class="sync-progress" v-if="progress.state !== 'idle'">
    <div class="progress-header">
      <span class="state" :class="progress.state">{{ stateText }}</span>
      <button v-if="progress.state === 'syncing'" @click="$emit('cancel')" class="cancel-btn">取消</button>
    </div>
    <div v-if="progress.state === 'syncing'" class="progress-details">
      <div class="progress-bar">
        <div class="progress" :style="{ width: progressPercent + '%' }"></div>
      </div>
      <div class="progress-info">
        <span>{{ progress.syncedFiles }} / {{ progress.totalFiles }}</span>
        <span>{{ progress.currentFile }}</span>
      </div>
    </div>
    <div v-if="progress.state === 'completed'" class="completed">同步完成</div>
    <div v-if="progress.state === 'failed'" class="failed">同步失败: {{ progress.currentFile }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { SmbSyncProgress } from '../../api/smb'

const props = defineProps<{
  progress: SmbSyncProgress
}>()

defineEmits<{
  cancel: []
}>()

const stateText = computed(() => {
  const states: Record<string, string> = {
    idle: '空闲',
    syncing: '同步中',
    completed: '已完成',
    failed: '失败',
  }
  return states[props.progress.state] || '未知'
})

const progressPercent = computed(() => {
  if (props.progress.totalFiles === 0) return 0
  return (props.progress.syncedFiles / props.progress.totalFiles) * 100
})
</script>

<style scoped>
.sync-progress {
  background: white;
  border-radius: 8px;
  padding: 1rem;
  margin-top: 1rem;
}
.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.state {
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 14px;
}
.state-idle { background: #e0e0e0; }
.state-syncing { background: #d4edda; color: #155724; }
.state-completed { background: #cce5ff; color: #004085; }
.state-failed { background: #f8d7da; color: #721c24; }
.cancel-btn {
  padding: 4px 12px;
  background: #e74c3c;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}
.progress-bar {
  height: 8px;
  background: #e0e0e0;
  border-radius: 4px;
  margin: 1rem 0;
  overflow: hidden;
}
.progress {
  height: 100%;
  background: #4a90d9;
  transition: width 0.3s;
}
.progress-info {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #666;
}
.completed {
  text-align: center;
  padding: 1rem;
  color: #155724;
}
.failed {
  text-align: center;
  padding: 1rem;
  color: #721c24;
}
</style>
