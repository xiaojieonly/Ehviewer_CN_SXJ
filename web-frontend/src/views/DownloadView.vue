<template>
  <div class="download-view">
    <AppHeader />
    <div class="content">
      <div class="header">
        <h1>Downloads</h1>
        <div class="actions">
          <button @click="startAll" class="btn-primary">Start All</button>
          <button @click="showNewLabel = true" class="btn-secondary">New Label</button>
        </div>
      </div>

      <div class="label-tabs">
        <button
          :class="{ active: currentLabel === 0 }"
          @click="currentLabel = 0"
        >All</button>
        <button
          v-for="lbl in labels"
          :key="lbl.id"
          :class="{ active: currentLabel === lbl.id }"
          @click="currentLabel = lbl.id"
        >{{ lbl.label }}</button>
      </div>

      <div class="download-list">
        <DownloadItemComponent
          v-for="item in downloads"
          :key="item.id"
          :item="item"
          @start="startDownload"
          @pause="pauseDownload"
          @cancel="cancelDownload"
          @delete="deleteDownload"
        />
        <div v-if="downloads.length === 0" class="empty">No downloads</div>
      </div>
    </div>

    <div v-if="showNewLabel" class="modal-overlay" @click="showNewLabel = false">
      <div class="modal" @click.stop>
        <h3>New Label</h3>
        <input v-model="newLabelName" placeholder="Label name" />
        <div class="modal-actions">
          <button @click="showNewLabel = false">Cancel</button>
          <button @click="createLabel" class="btn-primary">Create</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { downloadApi } from '../api/download'
import { useWebSocket } from '../composables/useWebSocket'
import type { DownloadItem, DownloadLabel } from '../api/download'
import AppHeader from '../components/layout/AppHeader.vue'
import DownloadItemComponent from '../components/download/DownloadItem.vue'

const downloads = ref<DownloadItem[]>([])
const labels = ref<DownloadLabel[]>([])
const currentLabel = ref(0)
const showNewLabel = ref(false)
const newLabelName = ref('')

const { connect, subscribeAll } = useWebSocket()

async function loadDownloads() {
  const labelParam = currentLabel.value !== 0 ? currentLabel.value : undefined
  const result = await downloadApi.list(labelParam)
  downloads.value = result.downloads
  labels.value = result.labels
}

async function startDownload(id: number) {
  await downloadApi.start(id)
  await loadDownloads()
}

async function pauseDownload(id: number) {
  await downloadApi.pause(id)
  await loadDownloads()
}

async function cancelDownload(id: number) {
  await downloadApi.cancel(id)
  await loadDownloads()
}

async function deleteDownload(id: number) {
  if (confirm('Delete this download?')) {
    await downloadApi.delete(id)
    await loadDownloads()
  }
}

async function startAll() {
  await downloadApi.startAll()
  await loadDownloads()
}

async function createLabel() {
  if (newLabelName.value.trim()) {
    await downloadApi.createLabel(newLabelName.value.trim())
    newLabelName.value = ''
    showNewLabel.value = false
    await loadDownloads()
  }
}

watch(currentLabel, () => {
  loadDownloads()
})

onMounted(() => {
  connect()
  loadDownloads()

  subscribeAll((progress) => {
    const item = downloads.value.find(d => d.gid === progress.gid)
    if (item) {
      item.state = progress.state
      item.done = progress.downloaded
      item.total = progress.total
    }
  })
})
</script>

<style scoped>
.download-view {
  min-height: 100vh;
  background: #f5f5f5;
}
.content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 1rem;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}
.actions {
  display: flex;
  gap: 8px;
}
.btn-primary {
  padding: 8px 16px;
  background: #4a90d9;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}
.btn-secondary {
  padding: 8px 16px;
  background: white;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
}
.label-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 1rem;
  flex-wrap: wrap;
}
.label-tabs button {
  padding: 6px 12px;
  border: 1px solid #ddd;
  background: white;
  border-radius: 4px;
  cursor: pointer;
}
.label-tabs button.active {
  background: #4a90d9;
  color: white;
  border-color: #4a90d9;
}
.empty {
  text-align: center;
  padding: 2rem;
  color: #999;
}
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.modal {
  background: white;
  padding: 1.5rem;
  border-radius: 8px;
  width: 100%;
  max-width: 400px;
}
.modal h3 {
  margin: 0 0 1rem;
}
.modal input {
  width: 100%;
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
  margin-bottom: 1rem;
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
