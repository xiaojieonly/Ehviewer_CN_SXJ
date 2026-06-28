<template>
  <div class="settings-view">
    <AppHeader />
    <div class="content">
      <h1>Settings</h1>

      <div class="settings-section">
        <h2>Download Settings</h2>
        <div class="setting-item">
          <label>Download Path</label>
          <input v-model="settings.download.path" readonly />
        </div>
        <div class="setting-item">
          <label>Worker Threads</label>
          <input type="number" v-model.number="settings.download.workerCount" min="1" max="10" />
        </div>
        <div class="setting-item">
          <label>Download Delay (ms)</label>
          <input type="number" v-model.number="settings.download.downloadDelay" min="0" />
        </div>
        <div class="setting-item">
          <label>Download Timeout (ms)</label>
          <input type="number" v-model.number="settings.download.downloadTimeout" min="10000" />
        </div>
        <div class="setting-item">
          <label>Max Concurrent Galleries</label>
          <input type="number" v-model.number="settings.download.maxConcurrentGalleries" min="1" max="10" />
        </div>
        <div class="setting-item">
          <label>Max Concurrent Images</label>
          <input type="number" v-model.number="settings.download.maxConcurrentImages" min="1" max="10" />
        </div>
      </div>

      <div class="settings-section">
        <h2>Cache Settings</h2>
        <div class="setting-item">
          <label>Cache Path</label>
          <input v-model="settings.cache.path" readonly />
        </div>
        <div class="setting-item">
          <label>Cache Size (MB)</label>
          <input type="number" v-model.number="settings.cache.sizeMb" min="1024" />
        </div>
      </div>

      <div class="settings-section">
        <h2>SMB Settings</h2>
        <div class="setting-item">
          <label>Enable SMB</label>
          <input type="checkbox" v-model="settings.smb.enabled" />
        </div>
      </div>

      <button @click="saveSettings" class="save-btn">Save Settings</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { settingsApi } from '../api/settings'
import type { Settings } from '../api/settings'
import AppHeader from '../components/layout/AppHeader.vue'

const settings = ref<Settings>({
  download: {
    path: '',
    workerCount: 3,
    downloadDelay: 0,
    downloadTimeout: 60000,
    maxConcurrentGalleries: 3,
    maxConcurrentImages: 3,
  },
  cache: {
    path: '',
    sizeMb: 10240,
  },
  smb: {
    enabled: false,
  },
})

async function loadSettings() {
  const result = await settingsApi.get()
  settings.value = result
}

async function saveSettings() {
  await settingsApi.update(settings.value)
  alert('Settings saved')
}

onMounted(() => {
  loadSettings()
})
</script>

<style scoped>
.settings-view {
  min-height: 100vh;
  background: #f5f5f5;
}
.content {
  max-width: 800px;
  margin: 0 auto;
  padding: 1rem;
}
h1 {
  margin-bottom: 1.5rem;
}
.settings-section {
  background: white;
  border-radius: 8px;
  padding: 1rem;
  margin-bottom: 1rem;
}
h2 {
  font-size: 16px;
  margin: 0 0 1rem;
  color: #333;
}
.setting-item {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}
.setting-item label {
  width: 200px;
  font-size: 14px;
}
.setting-item input {
  flex: 1;
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
}
.setting-item input[type="checkbox"] {
  width: auto;
}
.save-btn {
  width: 100%;
  padding: 12px;
  background: #4a90d9;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 16px;
  cursor: pointer;
}
.save-btn:hover {
  background: #357abd;
}
</style>
