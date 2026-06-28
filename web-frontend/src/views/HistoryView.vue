<template>
  <div class="history-view">
    <AppHeader />
    <div class="content">
      <div class="history-header">
        <h2>History</h2>
        <button v-if="history.length > 0" @click="handleClear" class="btn-clear">Clear All</button>
      </div>
      <div v-if="loading" class="loading">Loading...</div>
      <div v-else-if="history.length === 0" class="empty">No history yet</div>
      <div v-else class="history-list">
        <div
          v-for="item in history"
          :key="item.gid"
          class="history-item"
          @click="$router.push(`/gallery/${item.gid}`)"
        >
          <img v-if="item.thumb" :src="item.thumb" :alt="item.title" class="thumb" />
          <div class="info">
            <h3 class="title">{{ item.title }}</h3>
            <div class="meta">
              <span class="rating">★ {{ item.rating?.toFixed(1) }}</span>
              <span class="time">{{ formatTime(item.time) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { historyApi, type HistoryItem } from '@/api/history'
import AppHeader from '@/components/layout/AppHeader.vue'

const history = ref<HistoryItem[]>([])
const loading = ref(false)

function formatTime(timestamp: number): string {
  const date = new Date(timestamp)
  return date.toLocaleDateString() + ' ' + date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

async function loadHistory() {
  loading.value = true
  try {
    const res = await historyApi.listHistory()
    history.value = res.history
  } catch (e) {
    console.error('Failed to load history', e)
  } finally {
    loading.value = false
  }
}

async function handleClear() {
  if (!confirm('Clear all history?')) return
  await historyApi.clearHistory()
  history.value = []
}

onMounted(loadHistory)
</script>

<style scoped>
.history-view {
  min-height: 100vh;
  background: #f5f5f5;
}
.content {
  max-width: 900px;
  margin: 0 auto;
  padding: 1rem;
}
.history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}
.history-header h2 {
  color: #333;
  margin: 0;
}
.btn-clear {
  padding: 0.3rem 0.8rem;
  border: 1px solid #e74c3c;
  border-radius: 4px;
  background: white;
  color: #e74c3c;
  cursor: pointer;
}
.btn-clear:hover {
  background: #fdf0f0;
}
.history-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.history-item {
  display: flex;
  gap: 1rem;
  background: white;
  border-radius: 8px;
  padding: 0.75rem;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  cursor: pointer;
  transition: box-shadow 0.2s;
}
.history-item:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}
.thumb {
  width: 60px;
  height: 60px;
  border-radius: 4px;
  object-fit: cover;
  flex-shrink: 0;
}
.info {
  flex: 1;
  min-width: 0;
}
.title {
  font-size: 0.9rem;
  margin: 0 0 0.3rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #333;
}
.meta {
  display: flex;
  gap: 1rem;
  font-size: 0.8rem;
  color: #666;
}
.rating {
  color: #f39c12;
}
.loading, .empty {
  text-align: center;
  padding: 2rem;
  color: #666;
}
</style>
