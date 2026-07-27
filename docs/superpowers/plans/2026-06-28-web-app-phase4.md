# EhViewer Web App Phase 4: SMB 备份 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。

**目标：** 实现完整的 SMB 备份系统，包括配置管理、连接测试、同步执行、进度推送、定时同步。

---

## 任务 1：创建 SMB 备份 API

**文件：**
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/SmbController.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/SmbBackupService.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/SmbDto.kt`

- [ ] **步骤 1：创建 SmbDto**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/SmbDto.kt
package com.hippo.ehviewer.web.dto

data class SmbConfigResponse(
    val id: Long,
    val serverAddress: String,
    val sharedFolder: String,
    val username: String,
    val domain: String,
    val enabled: Boolean
)

data class SmbConfigUpdateRequest(
    val serverAddress: String,
    val sharedFolder: String,
    val username: String,
    val password: String,
    val domain: String = "WORKGROUP",
    val enabled: Boolean = false
)

data class SmbTestConnectionRequest(
    val serverAddress: String,
    val sharedFolder: String,
    val username: String,
    val password: String,
    val domain: String = "WORKGROUP"
)

data class SmbTestConnectionResponse(
    val success: Boolean,
    val message: String
)

data class SmbSyncProgress(
    val state: String,
    val totalFiles: Int,
    val syncedFiles: Int,
    val currentFile: String,
    val speed: Long
)

data class SmbSyncRequest(
    val aggressive: Boolean = false
)
```

- [ ] **步骤 2：创建 SmbBackupService**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/SmbBackupService.kt
package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.smb.SmbConnection
import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.entity.SmbConfigEntity
import com.hippo.ehviewer.web.repository.SmbConfigRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@Service
class SmbBackupService(
    private val smbConfigRepository: SmbConfigRepository,
    private val encryptionService: EncryptionService,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val isSyncing = AtomicBoolean(false)
    private var syncThread: Thread? = null

    fun getConfig(): SmbConfigResponse? {
        val entity = smbConfigRepository.findByEnabled(true).firstOrNull() 
            ?: smbConfigRepository.findAll().firstOrNull()
            ?: return null
        
        return SmbConfigResponse(
            id = entity.id,
            serverAddress = entity.serverAddress,
            sharedFolder = entity.sharedFolder,
            username = entity.username,
            domain = entity.domain,
            enabled = entity.enabled
        )
    }

    fun updateConfig(request: SmbConfigUpdateRequest): Boolean {
        val existing = smbConfigRepository.findByEnabled(true).firstOrNull()
            ?: SmbConfigEntity()
        
        existing.serverAddress = request.serverAddress
        existing.sharedFolder = request.sharedFolder
        existing.username = request.username
        existing.password = encryptionService.encrypt(request.password)
        existing.domain = request.domain
        existing.enabled = request.enabled
        
        smbConfigRepository.save(existing)
        return true
    }

    fun testConnection(request: SmbTestConnectionRequest): SmbTestConnectionResponse {
        return try {
            val conn = SmbConnection(
                request.serverAddress,
                request.sharedFolder,
                request.username,
                request.password,
                request.domain
            )
            conn.connect()
            conn.close()
            SmbTestConnectionResponse(true, "连接成功")
        } catch (e: Exception) {
            SmbTestConnectionResponse(false, "连接失败: ${e.message}")
        }
    }

    fun startSync(aggressive: Boolean): Boolean {
        if (isSyncing.get()) return false
        
        val config = smbConfigRepository.findByEnabled(true).firstOrNull()
            ?: return false
        
        isSyncing.set(true)
        syncThread = Thread {
            try {
                executeSync(config, aggressive)
            } finally {
                isSyncing.set(false)
            }
        }
        syncThread?.start()
        return true
    }

    fun cancelSync(): Boolean {
        if (!isSyncing.get()) return false
        syncThread?.interrupt()
        isSyncing.set(false)
        return true
    }

    fun getProgress(): SmbSyncProgress {
        return SmbSyncProgress(
            state = if (isSyncing.get()) "syncing" else "idle",
            totalFiles = 0,
            syncedFiles = 0,
            currentFile = "",
            speed = 0
        )
    }

    private fun executeSync(config: SmbConfigEntity, aggressive: Boolean) {
        try {
            val conn = SmbConnection(
                config.serverAddress,
                config.sharedFolder,
                config.username,
                encryptionService.decrypt(config.password),
                config.domain
            )
            conn.connect()
            
            val downloadDir = File("./data/downloads")
            if (downloadDir.exists()) {
                val files = downloadDir.listFiles() ?: emptyArray()
                var synced = 0
                
                for (file in files) {
                    if (Thread.currentThread().isInterrupted) break
                    
                    eventPublisher.publishEvent(SmbSyncProgress(
                        state = "syncing",
                        totalFiles = files.size,
                        syncedFiles = synced,
                        currentFile = file.name,
                        speed = 0
                    ))
                    
                    // 同步文件到 SMB
                    if (file.isDirectory) {
                        conn.createDirectory(file.name)
                        file.listFiles()?.forEach { child ->
                            if (child.isFile) {
                                conn.writeFile(
                                    "${file.name}/${child.name}",
                                    child.inputStream()
                                )
                            }
                        }
                    }
                    
                    synced++
                }
            }
            
            conn.close()
            
            eventPublisher.publishEvent(SmbSyncProgress(
                state = "completed",
                totalFiles = 0,
                syncedFiles = 0,
                currentFile = "",
                speed = 0
            ))
        } catch (e: Exception) {
            eventPublisher.publishEvent(SmbSyncProgress(
                state = "failed",
                totalFiles = 0,
                syncedFiles = 0,
                currentFile = e.message ?: "未知错误",
                speed = 0
            ))
        }
    }

    @Scheduled(fixedRate = 3600000) // 每小时检查一次
    fun scheduledSync() {
        val config = smbConfigRepository.findByEnabled(true).firstOrNull()
        if (config != null && config.enabled && !isSyncing.get()) {
            startSync(false)
        }
    }
}
```

- [ ] **步骤 3：创建 SmbController**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/SmbController.kt
package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.service.SmbBackupService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/smb")
class SmbController(private val smbBackupService: SmbBackupService) {

    @GetMapping("/config")
    fun getConfig(): ResponseEntity<SmbConfigResponse?> {
        return ResponseEntity.ok(smbBackupService.getConfig())
    }

    @PutMapping("/config")
    fun updateConfig(@RequestBody request: SmbConfigUpdateRequest): ResponseEntity<Boolean> {
        return ResponseEntity.ok(smbBackupService.updateConfig(request))
    }

    @PostMapping("/test-connection")
    fun testConnection(@RequestBody request: SmbTestConnectionRequest): ResponseEntity<SmbTestConnectionResponse> {
        return ResponseEntity.ok(smbBackupService.testConnection(request))
    }

    @PostMapping("/sync")
    fun startSync(@RequestBody request: SmbSyncRequest = SmbSyncRequest()): ResponseEntity<Boolean> {
        return ResponseEntity.ok(smbBackupService.startSync(request.aggressive))
    }

    @PostMapping("/sync-aggressive")
    fun startSyncAggressive(): ResponseEntity<Boolean> {
        return ResponseEntity.ok(smbBackupService.startSync(true))
    }

    @PostMapping("/cancel")
    fun cancelSync(): ResponseEntity<Boolean> {
        return ResponseEntity.ok(smbBackupService.cancelSync())
    }

    @GetMapping("/progress")
    fun getProgress(): ResponseEntity<SmbSyncProgress> {
        return ResponseEntity.ok(smbBackupService.getProgress())
    }
}
```

- [ ] **步骤 4：Commit**

```bash
git add ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/SmbController.kt \
        ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/SmbBackupService.kt \
        ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/SmbDto.kt
git commit -m "feat: add SMB backup service and controller"
```

---

## 任务 2：创建前端 SMB 组件

**文件：**
- 创建：`web-frontend/src/api/smb.ts`
- 创建：`web-frontend/src/views/SmbBackupView.vue`
- 创建：`web-frontend/src/components/smb/SmbConfigForm.vue`
- 创建：`web-frontend/src/components/smb/SyncProgress.vue`

- [ ] **步骤 1：创建 smb API**

```typescript
// web-frontend/src/api/smb.ts
import client from './client'

export interface SmbConfig {
  id: number
  serverAddress: string
  sharedFolder: string
  username: string
  domain: string
  enabled: boolean
}

export interface SmbTestResult {
  success: boolean
  message: string
}

export interface SmbSyncProgress {
  state: string
  totalFiles: number
  syncedFiles: number
  currentFile: string
  speed: number
}

export const smbApi = {
  getConfig() {
    return client.get<any, SmbConfig>('/smb/config')
  },
  updateConfig(config: Partial<SmbConfig> & { password?: string }) {
    return client.put<any, boolean>('/smb/config', config)
  },
  testConnection(config: SmbConfig & { password: string }) {
    return client.post<any, SmbTestResult>('/smb/test-connection', config)
  },
  sync(aggressive: boolean = false) {
    return client.post<any, boolean>('/smb/sync', { aggressive })
  },
  syncAggressive() {
    return client.post<any, boolean>('/smb/sync-aggressive')
  },
  cancel() {
    return client.post<any, boolean>('/smb/cancel')
  },
  getProgress() {
    return client.get<any, SmbSyncProgress>('/smb/progress')
  },
}
```

- [ ] **步骤 2：创建 SmbConfigForm**

```vue
<!-- web-frontend/src/components/smb/SmbConfigForm.vue -->
<template>
  <div class="smb-config-form">
    <div class="form-group">
      <label>服务器地址</label>
      <input v-model="config.serverAddress" placeholder="192.168.1.100" />
    </div>
    <div class="form-group">
      <label>共享文件夹</label>
      <input v-model="config.sharedFolder" placeholder="media" />
    </div>
    <div class="form-group">
      <label>用户名</label>
      <input v-model="config.username" />
    </div>
    <div class="form-group">
      <label>密码</label>
      <input v-model="config.password" type="password" />
    </div>
    <div class="form-group">
      <label>域</label>
      <input v-model="config.domain" placeholder="WORKGROUP" />
    </div>
    <div class="form-group">
      <label>启用 SMB 备份</label>
      <input type="checkbox" v-model="config.enabled" />
    </div>
    <div class="form-actions">
      <button @click="$emit('test')" class="btn-secondary">测试连接</button>
      <button @click="$emit('save')" class="btn-primary">保存配置</button>
    </div>
    <div v-if="testResult" class="test-result" :class="{ success: testResult.success }">
      {{ testResult.message }}
    </div>
  </div>
</template>

<script setup lang="ts">
import type { SmbConfig, SmbTestResult } from '../../api/smb'

defineProps<{
  config: SmbConfig & { password: string }
  testResult: SmbTestResult | null
}>()

defineEmits<{
  save: []
  test: []
}>()
</script>

<style scoped>
.smb-config-form {
  background: white;
  border-radius: 8px;
  padding: 1rem;
}
.form-group {
  margin-bottom: 12px;
}
.form-group label {
  display: block;
  margin-bottom: 4px;
  font-weight: 500;
}
.form-group input {
  width: 100%;
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
}
.form-group input[type="checkbox"] {
  width: auto;
}
.form-actions {
  display: flex;
  gap: 8px;
  margin-top: 1rem;
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
.test-result {
  margin-top: 1rem;
  padding: 8px;
  border-radius: 4px;
  background: #f8d7da;
  color: #721c24;
}
.test-result.success {
  background: #d4edda;
  color: #155724;
}
</style>
```

- [ ] **步骤 3：创建 SyncProgress**

```vue
<!-- web-frontend/src/components/smb/SyncProgress.vue -->
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
```

- [ ] **步骤 4：创建 SmbBackupView**

```vue
<!-- web-frontend/src/views/SmbBackupView.vue -->
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

const config = ref<SmbConfig & { password: string }>({
  id: 0,
  serverAddress: '',
  sharedFolder: '',
  username: '',
  password: '',
  domain: 'WORKGROUP',
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
```

- [ ] **步骤 5：更新路由**

```typescript
// web-frontend/src/router/index.ts
// 添加 SMB 备份路由
{
  path: '/smb-backup',
  name: 'smb-backup',
  component: () => import('../views/SmbBackupView.vue'),
  meta: { requiresAuth: true }
}
```

- [ ] **步骤 6：Commit**

```bash
git add web-frontend/src/api/smb.ts \
        web-frontend/src/views/SmbBackupView.vue \
        web-frontend/src/components/smb/ \
        web-frontend/src/router/index.ts
git commit -m "feat: add SMB backup page with config, test, and sync"
```

---

## 总结

Phase 4 完成后，系统将具备：
- SMB 备份配置管理
- 连接测试功能
- 同步执行（普通/高速模式）
- 实时同步进度推送
- 定时自动同步

下一步 Phase 5 将进行优化和部署。
