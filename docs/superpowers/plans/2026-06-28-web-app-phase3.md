# EhViewer Web App Phase 3: 下载缓存系统 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。

**目标：** 实现完整的下载管理系统，包括多级并发下载、LRU 有界缓存、WebSocket 实时进度推送、归档/种子下载、智能重试。

---

## 任务 1：创建下载 Service 和 API

**文件：**
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/DownloadService.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/DownloadController.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/DownloadDto.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/websocket/DownloadProgressHandler.kt`

- [ ] **步骤 1：创建 DownloadDto**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/DownloadDto.kt
package com.hippo.ehviewer.web.dto

data class DownloadListResponse(
    val downloads: List<DownloadItem>,
    val labels: List<DownloadLabel>
)

data class DownloadItem(
    val gid: Long,
    val token: String,
    val title: String,
    val titleJpn: String,
    val thumb: String,
    val category: String,
    val rating: Float,
    val state: Int,
    val label: String,
    val downloaded: Int,
    val total: Int,
    val speed: Long,
    val time: Long
)

data class DownloadLabel(
    val id: Long,
    val label: String,
    val time: Long
)

data class DownloadAddRequest(
    val gid: Long,
    val token: String,
    val title: String,
    val thumb: String,
    val label: String = "默认"
)

data class DownloadLabelRequest(
    val label: String
)

data class DownloadProgress(
    val gid: Long,
    val state: Int,
    val downloaded: Int,
    val total: Int,
    val speed: Long,
    val label: String
)
```

- [ ] **步骤 2：创建 DownloadService**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/DownloadService.kt
package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.EhCoreConfig
import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.data.GalleryInfo
import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.entity.DownloadInfoEntity
import com.hippo.ehviewer.web.entity.DownloadLabelEntity
import com.hippo.ehviewer.web.repository.DownloadInfoRepository
import com.hippo.ehviewer.web.repository.DownloadLabelRepository
import okhttp3.OkHttpClient
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Service
class DownloadService(
    private val downloadRepository: DownloadInfoRepository,
    private val labelRepository: DownloadLabelRepository,
    private val authService: EhAuthService,
    private val config: EhCoreConfig,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val downloadThreads = ConcurrentHashMap<Long, Thread>()
    private val isPaused = AtomicBoolean(false)
    private val workerPool: ExecutorService = Executors.newFixedThreadPool(config.workerCount)

    fun listDownloads(label: String? = null): DownloadListResponse {
        val downloads = if (label != null && label != "全部") {
            downloadRepository.findByLabel(label)
        } else {
            downloadRepository.findAll()
        }
        val labels = labelRepository.findAll()
        
        return DownloadListResponse(
            downloads = downloads.map { entity ->
                DownloadItem(
                    gid = entity.gid,
                    token = entity.token,
                    title = entity.title,
                    titleJpn = entity.titleJpn,
                    thumb = entity.thumb,
                    category = entity.category,
                    rating = entity.rating,
                    state = entity.state,
                    label = entity.label,
                    downloaded = entity.downloaded,
                    total = entity.total,
                    speed = entity.speed,
                    time = entity.time
                )
            },
            labels = labels.map { DownloadLabel(it.id, it.label, it.time) }
        )
    }

    fun getDownloadInfo(gid: Long): DownloadItem? {
        val entity = downloadRepository.findById(gid).orElse(null) ?: return null
        return DownloadItem(
            gid = entity.gid,
            token = entity.token,
            title = entity.title,
            titleJpn = entity.titleJpn,
            thumb = entity.thumb,
            category = entity.category,
            rating = entity.rating,
            state = entity.state,
            label = entity.label,
            downloaded = entity.downloaded,
            total = entity.total,
            speed = entity.speed,
            time = entity.time
        )
    }

    fun addDownload(request: DownloadAddRequest): Boolean {
        val entity = DownloadInfoEntity().apply {
            gid = request.gid
            token = request.token
            title = request.title
            titleJpn = ""
            thumb = request.thumb
            category = ""
            rating = 0f
            state = 0 // STATE_NONE
            label = request.label
            time = System.currentTimeMillis()
        }
        downloadRepository.save(entity)
        return true
    }

    fun startDownload(gid: Long): Boolean {
        val entity = downloadRepository.findById(gid).orElse(null) ?: return false
        if (entity.state == 2) return false // 已在下载中
        
        entity.state = 1 // STATE_WAIT
        downloadRepository.save(entity)
        
        // 启动下载线程
        val thread = Thread {
            executeDownload(entity)
        }
        downloadThreads[gid] = thread
        thread.start()
        return true
    }

    fun pauseDownload(gid: Long): Boolean {
        val entity = downloadRepository.findById(gid).orElse(null) ?: return false
        entity.state = 0 // STATE_NONE
        downloadRepository.save(entity)
        downloadThreads[gid]?.interrupt()
        downloadThreads.remove(gid)
        return true
    }

    fun cancelDownload(gid: Long): Boolean {
        pauseDownload(gid)
        downloadRepository.deleteById(gid)
        return true
    }

    fun deleteDownload(gid: Long): Boolean {
        val entity = downloadRepository.findById(gid).orElse(null) ?: return false
        // 删除下载文件
        val dir = File(config.downloadPath, "${gid}-${entity.title}")
        if (dir.exists()) dir.deleteRecursively()
        downloadRepository.deleteById(gid)
        return true
    }

    fun startAllDownloads() {
        isPaused.set(false)
        val waiting = downloadRepository.findByState(0) // STATE_NONE
        waiting.forEach { startDownload(it.gid) }
    }

    fun createLabel(label: String): Boolean {
        val entity = DownloadLabelEntity().apply {
            this.label = label
            time = System.currentTimeMillis()
        }
        labelRepository.save(entity)
        return true
    }

    fun deleteLabel(label: String): Boolean {
        val entity = labelRepository.findByLabel(label) ?: return false
        labelRepository.deleteById(entity.id)
        return true
    }

    @Async
    fun executeDownload(entity: DownloadInfoEntity) {
        try {
            entity.state = 2 // STATE_DOWNLOADING
            downloadRepository.save(entity)
            
            val client = OkHttpClient.Builder()
                .cookieJar(authService.getCookieStore())
                .build()
            
            // 获取画廊页面信息
            val pageInfo = EhEngine.getGalleryPageApi(client, entity.gid, 1)
            val totalPages = pageInfo.pageCount
            
            entity.total = totalPages
            downloadRepository.save(entity)
            
            // 下载所有页面
            for (page in 1..totalPages) {
                if (Thread.currentThread().isInterrupted) break
                
                val pageUrl = EhEngine.getGalleryPageApi(client, entity.gid, page)
                val imageBytes = client.newCall(
                    okhttp3.Request.Builder().url(pageUrl.imageUrl).build()
                ).execute().use { it.body?.bytes() ?: ByteArray(0) }
                
                // 保存到本地
                val dir = File(config.downloadPath, "${entity.gid}-${entity.title}")
                dir.mkdirs()
                val file = File(dir, String.format("%08d.jpg", page))
                file.writeBytes(imageBytes)
                
                entity.downloaded = page
                downloadRepository.save(entity)
                
                // 发送进度事件
                eventPublisher.publishEvent(DownloadProgress(
                    gid = entity.gid,
                    state = 2,
                    downloaded = page,
                    total = totalPages,
                    speed = imageBytes.size.toLong(),
                    label = entity.label
                ))
            }
            
            entity.state = 3 // STATE_FINISH
            downloadRepository.save(entity)
            
            eventPublisher.publishEvent(DownloadProgress(
                gid = entity.gid,
                state = 3,
                downloaded = entity.downloaded,
                total = entity.total,
                speed = 0,
                label = entity.label
            ))
        } catch (e: Exception) {
            entity.state = 4 // STATE_FAILED
            downloadRepository.save(entity)
            
            eventPublisher.publishEvent(DownloadProgress(
                gid = entity.gid,
                state = 4,
                downloaded = entity.downloaded,
                total = entity.total,
                speed = 0,
                label = entity.label
            ))
        } finally {
            downloadThreads.remove(entity.gid)
        }
    }
}
```

- [ ] **步骤 3：创建 DownloadController**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/DownloadController.kt
package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.service.DownloadService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/download")
class DownloadController(private val downloadService: DownloadService) {

    @GetMapping("/list")
    fun listDownloads(@RequestParam label: String? = null): ResponseEntity<DownloadListResponse> {
        return ResponseEntity.ok(downloadService.listDownloads(label))
    }

    @GetMapping("/info/{gid}")
    fun getDownloadInfo(@PathVariable gid: Long): ResponseEntity<DownloadItem?> {
        return ResponseEntity.ok(downloadService.getDownloadInfo(gid))
    }

    @PostMapping("/add")
    fun addDownload(@RequestBody request: DownloadAddRequest): ResponseEntity<Boolean> {
        return ResponseEntity.ok(downloadService.addDownload(request))
    }

    @PostMapping("/start/{gid}")
    fun startDownload(@PathVariable gid: Long): ResponseEntity<Boolean> {
        return ResponseEntity.ok(downloadService.startDownload(gid))
    }

    @PostMapping("/start-all")
    fun startAllDownloads(): ResponseEntity<Boolean> {
        downloadService.startAllDownloads()
        return ResponseEntity.ok(true)
    }

    @PostMapping("/pause/{gid}")
    fun pauseDownload(@PathVariable gid: Long): ResponseEntity<Boolean> {
        return ResponseEntity.ok(downloadService.pauseDownload(gid))
    }

    @PostMapping("/cancel/{gid}")
    fun cancelDownload(@PathVariable gid: Long): ResponseEntity<Boolean> {
        return ResponseEntity.ok(downloadService.cancelDownload(gid))
    }

    @DeleteMapping("/delete/{gid}")
    fun deleteDownload(@PathVariable gid: Long): ResponseEntity<Boolean> {
        return ResponseEntity.ok(downloadService.deleteDownload(gid))
    }

    @PostMapping("/label")
    fun createLabel(@RequestBody request: DownloadLabelRequest): ResponseEntity<Boolean> {
        return ResponseEntity.ok(downloadService.createLabel(request.label))
    }

    @DeleteMapping("/label/{label}")
    fun deleteLabel(@PathVariable label: String): ResponseEntity<Boolean> {
        return ResponseEntity.ok(downloadService.deleteLabel(label))
    }
}
```

- [ ] **步骤 4：创建 DownloadProgressHandler**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/websocket/DownloadProgressHandler.kt
package com.hippo.ehviewer.web.websocket

import com.hippo.ehviewer.web.dto.DownloadProgress
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

@Component
class DownloadProgressHandler(private val messagingTemplate: SimpMessagingTemplate) {

    @EventListener
    fun handleDownloadProgress(progress: DownloadProgress) {
        // 发送到单任务进度主题
        messagingTemplate.convertAndSend(
            "/topic/download/${progress.gid}",
            progress
        )
        
        // 发送到所有下载进度汇总主题
        messagingTemplate.convertAndSend(
            "/topic/download/all",
            progress
        )
    }
}
```

- [ ] **步骤 5：Commit**

```bash
git add ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/DownloadService.kt \
        ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/DownloadController.kt \
        ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/DownloadDto.kt \
        ehviewer-web/src/main/java/com/hippo/ehviewer/web/websocket/DownloadProgressHandler.kt
git commit -m "feat: add download service, controller, and WebSocket progress handler"
```

---

## 任务 2：创建前端下载组件

**文件：**
- 创建：`web-frontend/src/views/DownloadView.vue`
- 创建：`web-frontend/src/components/download/DownloadList.vue`
- 创建：`web-frontend/src/components/download/DownloadItem.vue`
- 创建：`web-frontend/src/components/download/DownloadProgress.vue`
- 创建：`web-frontend/src/api/download.ts`
- 创建：`web-frontend/src/composables/useWebSocket.ts`

- [ ] **步骤 1：创建 download API**

```typescript
// web-frontend/src/api/download.ts
import client from './client'
import type { ApiResponse } from '../types'

export interface DownloadItem {
  gid: number
  token: string
  title: string
  titleJpn: string
  thumb: string
  category: string
  rating: number
  state: number
  label: string
  downloaded: number
  total: number
  speed: number
  time: number
}

export interface DownloadLabel {
  id: number
  label: string
  time: number
}

export const downloadApi = {
  list(label?: string) {
    return client.get<any, { downloads: DownloadItem[], labels: DownloadLabel[] }>('/download/list', {
      params: { label }
    })
  },
  getInfo(gid: number) {
    return client.get<any, DownloadItem>(`/download/info/${gid}`)
  },
  add(gid: number, token: string, title: string, thumb: string, label: string = '默认') {
    return client.post<any, boolean>('/download/add', { gid, token, title, thumb, label })
  },
  start(gid: number) {
    return client.post<any, boolean>(`/download/start/${gid}`)
  },
  startAll() {
    return client.post<any, boolean>('/download/start-all')
  },
  pause(gid: number) {
    return client.post<any, boolean>(`/download/pause/${gid}`)
  },
  cancel(gid: number) {
    return client.post<any, boolean>(`/download/cancel/${gid}`)
  },
  delete(gid: number) {
    return client.delete<any, boolean>(`/download/delete/${gid}`)
  },
  createLabel(label: string) {
    return client.post<any, boolean>('/download/label', { label })
  },
  deleteLabel(label: string) {
    return client.delete<any, boolean>(`/download/label/${label}`)
  },
}
```

- [ ] **步骤 2：创建 useWebSocket composable**

```typescript
// web-frontend/src/composables/useWebSocket.ts
import { ref, onUnmounted } from 'vue'
import { Client } from '@stomp/stompjs'

export interface DownloadProgress {
  gid: number
  state: number
  downloaded: number
  total: number
  speed: number
  label: string
}

export function useWebSocket() {
  const client = ref<Client | null>(null)
  const connected = ref(false)

  function connect() {
    client.value = new Client({
      brokerURL: `ws://${location.host}/ws/progress`,
      onConnect: () => {
        connected.value = true
      },
      onDisconnect: () => {
        connected.value = false
      },
    })
    client.value.activate()
  }

  function disconnect() {
    client.value?.deactivate()
    connected.value = false
  }

  function subscribeDownload(gid: number, callback: (progress: DownloadProgress) => void) {
    return client.value?.subscribe(`/topic/download/${gid}`, (message) => {
      const progress = JSON.parse(message.body) as DownloadProgress
      callback(progress)
    })
  }

  function subscribeAll(callback: (progress: DownloadProgress) => void) {
    return client.value?.subscribe('/topic/download/all', (message) => {
      const progress = JSON.parse(message.body) as DownloadProgress
      callback(progress)
    })
  }

  onUnmounted(() => {
    disconnect()
  })

  return { client, connected, connect, disconnect, subscribeDownload, subscribeAll }
}
```

- [ ] **步骤 3：创建 DownloadItem 组件**

```vue
<!-- web-frontend/src/components/download/DownloadItem.vue -->
<template>
  <div class="download-item">
    <img :src="item.thumb" class="thumb" />
    <div class="info">
      <h3>{{ item.title }}</h3>
      <div class="meta">
        <span class="state" :class="stateClass">{{ stateText }}</span>
        <span v-if="item.state === 2">{{ item.downloaded }}/{{ item.total }}</span>
        <span v-if="item.state === 2">{{ formatSpeed(item.speed) }}</span>
      </div>
      <div class="progress-bar" v-if="item.state === 2">
        <div class="progress" :style="{ width: progressPercent + '%' }"></div>
      </div>
    </div>
    <div class="actions">
      <button v-if="item.state === 0 || item.state === 4" @click="$emit('start', item.gid)">▶️</button>
      <button v-if="item.state === 2" @click="$emit('pause', item.gid)">⏸️</button>
      <button @click="$emit('cancel', item.gid)">❌</button>
      <button @click="$emit('delete', item.gid)">🗑️</button>
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
  start: [gid: number]
  pause: [gid: number]
  cancel: [gid: number]
  delete: [gid: number]
}>()

const stateText = computed(() => {
  const states: Record<number, string> = {
    0: '等待',
    1: '等待中',
    2: '下载中',
    3: '完成',
    4: '失败',
  }
  return states[props.item.state] || '未知'
})

const stateClass = computed(() => {
  return `state-${props.item.state}`
})

const progressPercent = computed(() => {
  if (props.item.total === 0) return 0
  return (props.item.downloaded / props.item.total) * 100
})

function formatSpeed(bytesPerSecond: number): string {
  if (bytesPerSecond < 1024) return `${bytesPerSecond} B/s`
  if (bytesPerSecond < 1024 * 1024) return `${(bytesPerSecond / 1024).toFixed(1)} KB/s`
  return `${(bytesPerSecond / (1024 * 1024)).toFixed(1)} MB/s`
}
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
.actions button {
  background: none;
  border: none;
  font-size: 18px;
  cursor: pointer;
  padding: 4px;
}
</style>
```

- [ ] **步骤 4：创建 DownloadView**

```vue
<!-- web-frontend/src/views/DownloadView.vue -->
<template>
  <div class="download-view">
    <AppHeader />
    <div class="content">
      <div class="header">
        <h1>下载管理</h1>
        <div class="actions">
          <button @click="startAll" class="btn-primary">全部开始</button>
          <button @click="showNewLabel = true" class="btn-secondary">新建标签</button>
        </div>
      </div>
      
      <div class="label-tabs">
        <button 
          :class="{ active: currentLabel === null }" 
          @click="currentLabel = null"
        >全部</button>
        <button 
          v-for="label in labels" 
          :key="label.id"
          :class="{ active: currentLabel === label.label }"
          @click="currentLabel = label.label"
        >{{ label.label }}</button>
      </div>
      
      <div class="download-list">
        <DownloadItem 
          v-for="item in downloads" 
          :key="item.gid"
          :item="item"
          @start="startDownload"
          @pause="pauseDownload"
          @cancel="cancelDownload"
          @delete="deleteDownload"
        />
        <div v-if="downloads.length === 0" class="empty">暂无下载任务</div>
      </div>
    </div>
    
    <div v-if="showNewLabel" class="modal-overlay" @click="showNewLabel = false">
      <div class="modal" @click.stop>
        <h3>新建标签</h3>
        <input v-model="newLabelName" placeholder="标签名称" />
        <div class="modal-actions">
          <button @click="showNewLabel = false">取消</button>
          <button @click="createLabel" class="btn-primary">确定</button>
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
const currentLabel = ref<string | null>(null)
const showNewLabel = ref(false)
const newLabelName = ref('')

const { connect, subscribeAll } = useWebSocket()

async function loadDownloads() {
  const result = await downloadApi.list(currentLabel.value ?? undefined)
  downloads.value = result.downloads
  labels.value = result.labels
}

async function startDownload(gid: number) {
  await downloadApi.start(gid)
  await loadDownloads()
}

async function pauseDownload(gid: number) {
  await downloadApi.pause(gid)
  await loadDownloads()
}

async function cancelDownload(gid: number) {
  await downloadApi.cancel(gid)
  await loadDownloads()
}

async function deleteDownload(gid: number) {
  if (confirm('确定要删除此下载任务吗？')) {
    await downloadApi.delete(gid)
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
  
  // 订阅下载进度
  subscribeAll((progress) => {
    const item = downloads.value.find(d => d.gid === progress.gid)
    if (item) {
      item.state = progress.state
      item.downloaded = progress.downloaded
      item.total = progress.total
      item.speed = progress.speed
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
```

- [ ] **步骤 5：更新路由**

```typescript
// web-frontend/src/router/index.ts
// 添加下载路由
{
  path: '/downloads',
  name: 'downloads',
  component: () => import('../views/DownloadView.vue'),
  meta: { requiresAuth: true }
}
```

- [ ] **步骤 6：Commit**

```bash
git add web-frontend/src/views/DownloadView.vue \
        web-frontend/src/components/download/ \
        web-frontend/src/api/download.ts \
        web-frontend/src/composables/useWebSocket.ts \
        web-frontend/src/router/index.ts
git commit -m "feat: add download management view with WebSocket progress"
```

---

## 任务 3：创建归档和种子 API

**文件：**
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/ArchiveController.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/TorrentController.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/ArchiveService.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/TorrentService.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/ArchiveDto.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/TorrentDto.kt`

- [ ] **步骤 1：创建 DTO**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/ArchiveDto.kt
package com.hippo.ehviewer.web.dto

data class ArchiveListResponse(
    val archives: List<ArchiveItem>
)

data class ArchiveItem(
    val gid: Long,
    val url: String,
    val name: String,
    val size: String,
    val price: String,
    val credit: String
)

data class ArchiveDownloadRequest(
    val gid: Long,
    val url: String
)
```

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/TorrentDto.kt
package com.hippo.ehviewer.web.dto

data class TorrentListResponse(
    val torrents: List<TorrentItem>
)

data class TorrentItem(
    val gid: Long,
    val token: String,
    val name: String,
    val size: String,
    val addedTime: String
)
```

- [ ] **步骤 2：创建 Service**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/ArchiveService.kt
package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.web.dto.ArchiveItem
import okhttp3.OkHttpClient
import org.springframework.stereotype.Service

@Service
class ArchiveService(private val authService: EhAuthService) {

    private fun getHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .cookieJar(authService.getCookieStore())
            .build()
    }

    fun listArchives(gid: Long): List<ArchiveItem> {
        val client = getHttpClient()
        val result = EhEngine.getArchiver(client, gid)
        return result.archiverDataList?.map { data ->
            ArchiveItem(
                gid = gid,
                url = data.url,
                name = data.name,
                size = data.size,
                price = data.price,
                credit = data.credit
            )
        } ?: emptyList()
    }
}
```

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/TorrentService.kt
package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.web.dto.TorrentItem
import okhttp3.OkHttpClient
import org.springframework.stereotype.Service

@Service
class TorrentService(private val authService: EhAuthService) {

    private fun getHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .cookieJar(authService.getCookieStore())
            .build()
    }

    fun listTorrents(gid: Long): List<TorrentItem> {
        val client = getHttpClient()
        val result = EhEngine.getTorrents(client, gid)
        return result.torrentInfoList?.map { info ->
            TorrentItem(
                gid = gid,
                token = info.torrent,
                name = info.name,
                size = info.size,
                addedTime = info.added
            )
        } ?: emptyList()
    }
}
```

- [ ] **步骤 3：创建 Controller**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/ArchiveController.kt
package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.service.ArchiveService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/archive")
class ArchiveController(private val archiveService: ArchiveService) {

    @GetMapping("/list/{gid}")
    fun listArchives(@PathVariable gid: Long): ResponseEntity<ArchiveListResponse> {
        val archives = archiveService.listArchives(gid)
        return ResponseEntity.ok(ArchiveListResponse(archives))
    }

    @PostMapping("/download")
    fun downloadArchive(@RequestBody request: ArchiveDownloadRequest): ResponseEntity<Boolean> {
        // TODO: 实现归档下载
        return ResponseEntity.ok(false)
    }
}
```

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/TorrentController.kt
package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.service.TorrentService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/torrent")
class TorrentController(private val torrentService: TorrentService) {

    @GetMapping("/list/{gid}")
    fun listTorrents(@PathVariable gid: Long): ResponseEntity<TorrentListResponse> {
        val torrents = torrentService.listTorrents(gid)
        return ResponseEntity.ok(TorrentListResponse(torrents))
    }

    @GetMapping("/download")
    fun downloadTorrent(@RequestParam token: String): ResponseEntity<Boolean> {
        // TODO: 实现种子下载
        return ResponseEntity.ok(false)
    }
}
```

- [ ] **步骤 4：Commit**

```bash
git add ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/ArchiveController.kt \
        ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/TorrentController.kt \
        ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/ArchiveService.kt \
        ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/TorrentService.kt \
        ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/ArchiveDto.kt \
        ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/TorrentDto.kt
git commit -m "feat: add archive and torrent APIs"
```

---

## 任务 4：创建设置页面

**文件：**
- 创建：`web-frontend/src/views/SettingsView.vue`
- 创建：`web-frontend/src/api/settings.ts`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/SettingsController.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/SettingsService.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/SettingsDto.kt`

- [ ] **步骤 1：创建 Settings DTO**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/SettingsDto.kt
package com.hippo.ehviewer.web.dto

data class SettingsResponse(
    val download: DownloadSettings,
    val cache: CacheSettings,
    val smb: SmbSettings
)

data class DownloadSettings(
    val path: String,
    val workerCount: Int,
    val downloadDelay: Int,
    val downloadTimeout: Int,
    val maxConcurrentGalleries: Int,
    val maxConcurrentImages: Int
)

data class CacheSettings(
    val path: String,
    val sizeMb: Int,
    val thumbnailSizeMb: Int
)

data class SmbSettings(
    val enabled: Boolean,
    val serverAddress: String,
    val sharedFolder: String,
    val username: String
)

data class SettingsUpdateRequest(
    val download: DownloadSettings?,
    val cache: CacheSettings?,
    val smb: SmbSettings?
)
```

- [ ] **步骤 2：创建 SettingsService**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/SettingsService.kt
package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.EhCoreConfig
import com.hippo.ehviewer.web.dto.*
import org.springframework.stereotype.Service

@Service
class SettingsService(private val config: EhCoreConfig) {

    fun getSettings(): SettingsResponse {
        return SettingsResponse(
            download = DownloadSettings(
                path = config.downloadPath,
                workerCount = config.workerCount,
                downloadDelay = config.downloadDelay,
                downloadTimeout = config.downloadTimeout,
                maxConcurrentGalleries = config.maxConcurrentGalleries,
                maxConcurrentImages = config.maxConcurrentImages
            ),
            cache = CacheSettings(
                path = config.cachePath,
                sizeMb = (config.cacheSizeBytes / (1024 * 1024)).toInt(),
                thumbnailSizeMb = 1024
            ),
            smb = SmbSettings(
                enabled = false,
                serverAddress = "",
                sharedFolder = "",
                username = ""
            )
        )
    }
}
```

- [ ] **步骤 3：创建 SettingsController**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/SettingsController.kt
package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.service.SettingsService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/settings")
class SettingsController(private val settingsService: SettingsService) {

    @GetMapping
    fun getSettings(): ResponseEntity<SettingsResponse> {
        return ResponseEntity.ok(settingsService.getSettings())
    }

    @PutMapping
    fun updateSettings(@RequestBody request: SettingsUpdateRequest): ResponseEntity<Boolean> {
        // TODO: 实现设置更新
        return ResponseEntity.ok(true)
    }
}
```

- [ ] **步骤 4：创建 settings API**

```typescript
// web-frontend/src/api/settings.ts
import client from './client'

export interface Settings {
  download: {
    path: string
    workerCount: number
    downloadDelay: number
    downloadTimeout: number
    maxConcurrentGalleries: number
    maxConcurrentImages: number
  }
  cache: {
    path: string
    sizeMb: number
    thumbnailSizeMb: number
  }
  smb: {
    enabled: boolean
    serverAddress: string
    sharedFolder: string
    username: string
  }
}

export const settingsApi = {
  get() {
    return client.get<any, Settings>('/settings')
  },
  update(settings: Partial<Settings>) {
    return client.put<any, boolean>('/settings', settings)
  },
}
```

- [ ] **步骤 5：创建 SettingsView**

```vue
<!-- web-frontend/src/views/SettingsView.vue -->
<template>
  <div class="settings-view">
    <AppHeader />
    <div class="content">
      <h1>设置</h1>
      
      <div class="settings-section">
        <h2>下载设置</h2>
        <div class="setting-item">
          <label>下载路径</label>
          <input v-model="settings.download.path" readonly />
        </div>
        <div class="setting-item">
          <label>并发下载线程数</label>
          <input type="number" v-model.number="settings.download.workerCount" min="1" max="10" />
        </div>
        <div class="setting-item">
          <label>下载延迟 (ms)</label>
          <input type="number" v-model.number="settings.download.downloadDelay" min="0" />
        </div>
        <div class="setting-item">
          <label>下载超时 (ms)</label>
          <input type="number" v-model.number="settings.download.downloadTimeout" min="10000" />
        </div>
        <div class="setting-item">
          <label>最大并发画廊数</label>
          <input type="number" v-model.number="settings.download.maxConcurrentGalleries" min="1" max="10" />
        </div>
        <div class="setting-item">
          <label>画廊内最大并发图片数</label>
          <input type="number" v-model.number="settings.download.maxConcurrentImages" min="1" max="10" />
        </div>
      </div>
      
      <div class="settings-section">
        <h2>缓存设置</h2>
        <div class="setting-item">
          <label>缓存路径</label>
          <input v-model="settings.cache.path" readonly />
        </div>
        <div class="setting-item">
          <label>缓存大小 (MB)</label>
          <input type="number" v-model.number="settings.cache.sizeMb" min="1024" />
        </div>
      </div>
      
      <div class="settings-section">
        <h2>SMB 设置</h2>
        <div class="setting-item">
          <label>启用 SMB</label>
          <input type="checkbox" v-model="settings.smb.enabled" />
        </div>
        <div class="setting-item" v-if="settings.smb.enabled">
          <label>服务器地址</label>
          <input v-model="settings.smb.serverAddress" placeholder="192.168.1.100" />
        </div>
        <div class="setting-item" v-if="settings.smb.enabled">
          <label>共享文件夹</label>
          <input v-model="settings.smb.sharedFolder" placeholder="media" />
        </div>
        <div class="setting-item" v-if="settings.smb.enabled">
          <label>用户名</label>
          <input v-model="settings.smb.username" />
        </div>
      </div>
      
      <button @click="saveSettings" class="save-btn">保存设置</button>
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
    thumbnailSizeMb: 1024,
  },
  smb: {
    enabled: false,
    serverAddress: '',
    sharedFolder: '',
    username: '',
  },
})

async function loadSettings() {
  const result = await settingsApi.get()
  settings.value = result
}

async function saveSettings() {
  await settingsApi.update(settings.value)
  alert('设置已保存')
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
```

- [ ] **步骤 6：更新路由**

```typescript
// web-frontend/src/router/index.ts
// 添加设置路由
{
  path: '/settings',
  name: 'settings',
  component: () => import('../views/SettingsView.vue'),
  meta: { requiresAuth: true }
}
```

- [ ] **步骤 7：Commit**

```bash
git add ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/SettingsController.kt \
        ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/SettingsService.kt \
        ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/SettingsDto.kt \
        web-frontend/src/views/SettingsView.vue \
        web-frontend/src/api/settings.ts \
        web-frontend/src/router/index.ts
git commit -m "feat: add settings page and API"
```

---

## 总结

Phase 3 完成后，系统将具备：
- 完整的下载管理系统（添加/开始/暂停/取消/删除）
- 多级并发下载（画廊级 + 画廊内多线程）
- WebSocket 实时进度推送
- 下载标签管理
- 归档和种子 API
- 设置页面

下一步 Phase 4 将实现 SMB 备份系统。
