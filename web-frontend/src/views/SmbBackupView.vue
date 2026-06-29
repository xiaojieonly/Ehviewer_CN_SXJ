<template>
  <div class="smb-backup-view">
    <AppHeader />
    <div class="content">
      <h1>SMB 备份</h1>

      <SmbConfigForm
        :config="config"
        :test-result="testResult"
        @save="saveConfig"
        @test="testConnection"
      />

      <div class="sync-section">
        <h2>同步</h2>
        <div class="sync-actions">
          <button @click="startSync" :disabled="progress.state === 'syncing'" class="btn-primary">
            开始同步
          </button>
          <button @click="startSyncAggressive" :disabled="progress.state === 'syncing'" class="btn-secondary">
            高速同步
          </button>
        </div>

        <SyncProgress
          :progress="progress"
          @cancel="cancelSync"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { smbApi } from '../api/smb'
import type { SmbConfig, SmbTestResult, SmbSyncProgress } from '../api/smb'
import AppHeader from '../components/layout/AppHeader.vue'
import SmbConfigForm from '../components/smb/SmbConfigForm.vue'
import SyncProgress from '../components/smb/SyncProgress.vue'

const config = ref<SmbConfig & { password?: string }>({
  id: 0,
  host: '',
  port: 445,
  share: '',
  path: null,
  loginMode: 'GUEST',
  username: null,
  password: '',
  enabled: false,
})

const testResult = ref<SmbTestResult | null>(null)
const progress = ref<SmbSyncProgress>({
  state: 'idle',
  totalFiles: 0,
  syncedFiles: 0,
  currentFile: '',
  speed: 0,
})

let progressTimer: number | null = null

async function loadConfig() {
  const result = await smbApi.getConfig()
  if (result) {
    config.value = { ...result, password: '' }
  }
}

async function saveConfig() {
  await smbApi.updateConfig(config.value)
  alert('配置已保存')
}

async function testConnection() {
  testResult.value = await smbApi.testConnection(config.value)
}

async function startSync() {
  await smbApi.sync(false)
  startProgressPolling()
}

async function startSyncAggressive() {
  await smbApi.sync(true)
  startProgressPolling()
}

async function cancelSync() {
  await smbApi.cancel()
  stopProgressPolling()
}

async function pollProgress() {
  progress.value = await smbApi.getProgress()
  if (progress.value.state !== 'syncing') {
    stopProgressPolling()
  }
}

function startProgressPolling() {
  progressTimer = window.setInterval(pollProgress, 1000)
}

function stopProgressPolling() {
  if (progressTimer) {
    clearInterval(progressTimer)
    progressTimer = null
  }
}

onMounted(() => {
  loadConfig()
})

onUnmounted(() => {
  stopProgressPolling()
})
</script>

<style scoped>
.smb-backup-view {
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
.sync-section {
  margin-top: 2rem;
}
h2 {
  font-size: 18px;
  margin-bottom: 1rem;
}
.sync-actions {
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
.btn-primary:disabled {
  background: #ccc;
}
.btn-secondary {
  padding: 8px 16px;
  background: white;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
}
.btn-secondary:disabled {
  background: #f5f5f5;
}
</style>
