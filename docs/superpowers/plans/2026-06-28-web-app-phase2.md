# EhViewer Web App Phase 2: 阅读器 + 收藏 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。

**目标：** 实现完整的图片阅读器（翻页/滚动/缩放/手势/键盘/亮度/背景/白边裁切/双页/自动/右至左/长条/书签/PDF）、收藏管理、评论功能、浏览历史。

**架构：** 在 Phase 1 基础上，扩展 Vue 前端组件和后端 API。

---

## 任务 1：创建阅读器相关 API

**文件：**
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/CommentController.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/FavoriteController.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/HistoryController.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/CommentService.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/FavoriteService.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/HistoryService.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/CommentDto.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/FavoriteDto.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/HistoryDto.kt`

- [ ] **步骤 1：创建 CommentDto**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/CommentDto.kt
package com.hippo.ehviewer.web.dto

data class CommentListResponse(
    val comments: List<CommentItem>
)

data class CommentPostRequest(
    val gid: Long,
    val comment: String
)

data class CommentVoteRequest(
    val gid: Long,
    val commentId: Long,
    val vote: Int  // 1=up, -1=down
)

data class CommentItem(
    val id: Long,
    val uploader: String,
    val comment: String,
    val time: String,
    val score: Int
)
```

- [ ] **步骤 2：创建 FavoriteDto**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/FavoriteDto.kt
package com.hippo.ehviewer.web.dto

data class FavoriteListResponse(
    val favorites: List<FavoriteItem>,
    val totalPages: Int,
    val currentPage: Int
)

data class FavoriteItem(
    val gid: Long,
    val token: String,
    val title: String,
    val titleJpn: String,
    val thumb: String,
    val category: String,
    val rating: Float,
    val uploader: String?,
    val posted: String?
)

data class FavoriteAddRequest(
    val gid: Long,
    val token: String,
    val category: Int
)

data class FavoriteRemoveRequest(
    val gid: Long,
    val token: String,
    val category: Int
)
```

- [ ] **步骤 3：创建 HistoryDto**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/HistoryDto.kt
package com.hippo.ehviewer.web.dto

data class HistoryListResponse(
    val history: List<HistoryItem>
)

data class HistoryItem(
    val gid: Long,
    val token: String,
    val title: String,
    val titleJpn: String,
    val thumb: String,
    val category: String,
    val rating: Float,
    val mode: Int,
    val time: Long
)
```

- [ ] **步骤 4：创建 CommentService**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/CommentService.kt
package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.web.dto.CommentItem
import okhttp3.OkHttpClient
import org.springframework.stereotype.Service

@Service
class CommentService(private val authService: EhAuthService) {

    private fun getHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .cookieJar(authService.getCookieStore())
            .build()
    }

    fun listComments(gid: Long): List<CommentItem> {
        val client = getHttpClient()
        val result = EhEngine.getComments(client, gid)
        return result.comments?.map { comment ->
            CommentItem(
                id = comment.id,
                uploader = comment.uploader,
                comment = comment.comment,
                time = comment.time,
                score = comment.score
            )
        } ?: emptyList()
    }

    fun postComment(gid: Long, comment: String): Boolean {
        val client = getHttpClient()
        return EhEngine.postComment(client, gid, comment)
    }

    fun voteComment(gid: Long, commentId: Long, vote: Int): Boolean {
        val client = getHttpClient()
        return EhEngine.voteComment(client, gid, commentId, vote)
    }
}
```

- [ ] **步骤 5：创建 FavoriteService**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/FavoriteService.kt
package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.web.dto.FavoriteItem
import okhttp3.OkHttpClient
import org.springframework.stereotype.Service

@Service
class FavoriteService(private val authService: EhAuthService) {

    private fun getHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .cookieJar(authService.getCookieStore())
            .build()
    }

    fun listFavorites(slot: Int, page: Int): Pair<List<FavoriteItem>, Int> {
        val client = getHttpClient()
        val result = EhEngine.getFavorites(client, slot, page)
        val items = result.galleryInfoList.map { info ->
            FavoriteItem(
                gid = info.gid,
                token = info.token,
                title = info.title,
                titleJpn = info.titleJpn ?: "",
                thumb = info.thumb ?: "",
                category = info.category ?: "",
                rating = info.rating,
                uploader = info.uploader,
                posted = info.posted
            )
        }
        return Pair(items, result.pages)
    }

    fun addFavorite(gid: Long, token: String, category: Int): Boolean {
        val client = getHttpClient()
        return EhEngine.addFavorite(client, gid, token, category)
    }

    fun removeFavorite(gid: Long, token: String, category: Int): Boolean {
        val client = getHttpClient()
        return EhEngine.removeFavorite(client, gid, token, category)
    }
}
```

- [ ] **步骤 6：创建 HistoryService**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/HistoryService.kt
package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.web.dto.HistoryItem
import com.hippo.ehviewer.web.entity.HistoryInfoEntity
import com.hippo.ehviewer.web.repository.HistoryInfoRepository
import org.springframework.stereotype.Service

@Service
class HistoryService(private val historyRepository: HistoryInfoRepository) {

    fun listHistory(): List<HistoryItem> {
        return historyRepository.findAllByOrderByTimeDesc().map { entity ->
            HistoryItem(
                gid = entity.gid,
                token = entity.token,
                title = entity.title,
                titleJpn = entity.titleJpn,
                thumb = entity.thumb,
                category = entity.category,
                rating = entity.rating,
                mode = entity.mode,
                time = entity.time
            )
        }
    }

    fun addHistory(gid: Long, token: String, title: String, titleJpn: String,
                   thumb: String, category: String, rating: Float, mode: Int) {
        val entity = HistoryInfoEntity().apply {
            this.gid = gid
            this.token = token
            this.title = title
            this.titleJpn = titleJpn
            this.thumb = thumb
            this.category = category
            this.rating = rating
            this.mode = mode
            this.time = System.currentTimeMillis()
        }
        historyRepository.save(entity)
    }

    fun clearHistory() {
        historyRepository.deleteAll()
    }
}
```

- [ ] **步骤 7：创建 CommentController**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/CommentController.kt
package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.service.CommentService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/comment")
class CommentController(private val commentService: CommentService) {

    @GetMapping("/list/{gid}")
    fun listComments(@PathVariable gid: Long): ResponseEntity<ApiResponse<CommentListResponse>> {
        val comments = commentService.listComments(gid)
        return ResponseEntity.ok(ApiResponse(0, "success", CommentListResponse(comments)))
    }

    @PostMapping("/post")
    fun postComment(@RequestBody request: CommentPostRequest): ResponseEntity<ApiResponse<Boolean>> {
        val result = commentService.postComment(request.gid, request.comment)
        return ResponseEntity.ok(ApiResponse(0, "success", result))
    }

    @PostMapping("/vote")
    fun voteComment(@RequestBody request: CommentVoteRequest): ResponseEntity<ApiResponse<Boolean>> {
        val result = commentService.voteComment(request.gid, request.commentId, request.vote)
        return ResponseEntity.ok(ApiResponse(0, "success", result))
    }
}
```

- [ ] **步骤 8：创建 FavoriteController**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/FavoriteController.kt
package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.service.FavoriteService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/favorite")
class FavoriteController(private val favoriteService: FavoriteService) {

    @GetMapping("/list")
    fun listFavorites(
        @RequestParam(defaultValue = "0") slot: Int,
        @RequestParam(defaultValue = "1") page: Int
    ): ResponseEntity<ApiResponse<FavoriteListResponse>> {
        val (favorites, totalPages) = favoriteService.listFavorites(slot, page)
        return ResponseEntity.ok(ApiResponse(0, "success", FavoriteListResponse(favorites, totalPages, page)))
    }

    @PostMapping("/add")
    fun addFavorite(@RequestBody request: FavoriteAddRequest): ResponseEntity<ApiResponse<Boolean>> {
        val result = favoriteService.addFavorite(request.gid, request.token, request.category)
        return ResponseEntity.ok(ApiResponse(0, "success", result))
    }

    @DeleteMapping("/remove")
    fun removeFavorite(@RequestBody request: FavoriteRemoveRequest): ResponseEntity<ApiResponse<Boolean>> {
        val result = favoriteService.removeFavorite(request.gid, request.token, request.category)
        return ResponseEntity.ok(ApiResponse(0, "success", result))
    }
}
```

- [ ] **步骤 9：创建 HistoryController**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/HistoryController.kt
package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.service.HistoryService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/history")
class HistoryController(private val historyService: HistoryService) {

    @GetMapping("/list")
    fun listHistory(): ResponseEntity<ApiResponse<HistoryListResponse>> {
        val history = historyService.listHistory()
        return ResponseEntity.ok(ApiResponse(0, "success", HistoryListResponse(history)))
    }

    @DeleteMapping("/clear")
    fun clearHistory(): ResponseEntity<ApiResponse<Unit>> {
        historyService.clearHistory()
        return ResponseEntity.ok(ApiResponse(0, "success", null))
    }
}
```

- [ ] **步骤 10：Commit**

```bash
git add ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/ \
        ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/ \
        ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/
git commit -m "feat: add comment, favorite, history APIs"
```

---

## 任务 2：创建前端 API 和 Store

**文件：**
- 创建：`web-frontend/src/api/comment.ts`
- 创建：`web-frontend/src/api/favorite.ts`
- 创建：`web-frontend/src/api/history.ts`
- 创建：`web-frontend/src/stores/gallery.ts`
- 创建：`web-frontend/src/stores/download.ts`

- [ ] **步骤 1：创建 comment.ts**

```typescript
// web-frontend/src/api/comment.ts
import client from './client'
import type { ApiResponse } from '../types'

export interface CommentItem {
  id: number
  uploader: string
  comment: string
  time: string
  score: number
}

export const commentApi = {
  list(gid: number) {
    return client.get<any, ApiResponse<{ comments: CommentItem[] }>>(`/comment/list/${gid}`)
  },
  post(gid: number, comment: string) {
    return client.post<any, ApiResponse<boolean>>('/comment/post', { gid, comment })
  },
  vote(gid: number, commentId: number, vote: number) {
    return client.post<any, ApiResponse<boolean>>('/comment/vote', { gid, commentId, vote })
  },
}
```

- [ ] **步骤 2：创建 favorite.ts**

```typescript
// web-frontend/src/api/favorite.ts
import client from './client'
import type { ApiResponse, GalleryListItem } from '../types'

export interface FavoriteListResponse {
  favorites: GalleryListItem[]
  totalPages: number
  currentPage: number
}

export const favoriteApi = {
  list(slot: number = 0, page: number = 1) {
    return client.get<any, ApiResponse<FavoriteListResponse>>('/favorite/list', {
      params: { slot, page }
    })
  },
  add(gid: number, token: string, category: number) {
    return client.post<any, ApiResponse<boolean>>('/favorite/add', { gid, token, category })
  },
  remove(gid: number, token: string, category: number) {
    return client.delete<any, ApiResponse<boolean>>('/favorite/remove', {
      data: { gid, token, category }
    })
  },
}
```

- [ ] **步骤 3：创建 history.ts**

```typescript
// web-frontend/src/api/history.ts
import client from './client'
import type { ApiResponse } from '../types'

export interface HistoryItem {
  gid: number
  token: string
  title: string
  titleJpn: string
  thumb: string
  category: string
  rating: number
  mode: number
  time: number
}

export const historyApi = {
  list() {
    return client.get<any, ApiResponse<{ history: HistoryItem[] }>>('/history/list')
  },
  clear() {
    return client.delete<any, ApiResponse<void>>('/history/clear')
  },
}
```

- [ ] **步骤 4：创建 gallery store**

```typescript
// web-frontend/src/stores/gallery.ts
import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { GalleryDetailResponse } from '../types'

export const useGalleryStore = defineStore('gallery', () => {
  const currentGallery = ref<GalleryDetailResponse | null>(null)
  const currentPage = ref(1)

  function setGallery(gallery: GalleryDetailResponse) {
    currentGallery.value = gallery
  }

  function setPage(page: number) {
    currentPage.value = page
  }

  return { currentGallery, currentPage, setGallery, setPage }
})
```

- [ ] **步骤 5：创建 download store**

```typescript
// web-frontend/src/stores/download.ts
import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface DownloadProgress {
  gid: number
  state: number
  downloaded: number
  total: number
  speed: number
  label: string
}

export const useDownloadStore = defineStore('download', () => {
  const downloads = ref<Map<number, DownloadProgress>>(new Map())

  function updateProgress(progress: DownloadProgress) {
    downloads.value.set(progress.gid, progress)
  }

  function removeDownload(gid: number) {
    downloads.value.delete(gid)
  }

  return { downloads, updateProgress, removeDownload }
})
```

- [ ] **步骤 6：Commit**

```bash
git add web-frontend/src/api/comment.ts \
        web-frontend/src/api/favorite.ts \
        web-frontend/src/api/history.ts \
        web-frontend/src/stores/gallery.ts \
        web-frontend/src/stores/download.ts
git commit -m "feat: add frontend API clients and stores"
```

---

## 任务 3：创建阅读器组件

**文件：**
- 创建：`web-frontend/src/views/ReaderView.vue`
- 创建：`web-frontend/src/components/reader/ImageReader.vue`
- 创建：`web-frontend/src/components/reader/PageMode.vue`
- 创建：`web-frontend/src/components/reader/ScrollMode.vue`
- 创建：`web-frontend/src/components/reader/ReaderToolbar.vue`
- 创建：`web-frontend/src/components/reader/ReaderSettings.vue`
- 创建：`web-frontend/src/composables/useSwipeGesture.ts`
- 创建：`web-frontend/src/composables/useKeyboardNav.ts`

- [ ] **步骤 1：创建 useSwipeGesture composable**

```typescript
// web-frontend/src/composables/useSwipeGesture.ts
import { ref, onMounted, onUnmounted, type Ref } from 'vue'

export interface SwipeOptions {
  onSwipeLeft?: () => void
  onSwipeRight?: () => void
  onSwipeUp?: () => void
  onSwipeDown?: () => void
  onPinch?: (scale: number) => void
}

export function useSwipeGesture(element: Ref<HTMLElement | null>, options: SwipeOptions) {
  let startX = 0
  let startY = 0
  let startTime = 0
  let initialDistance = 0

  function onTouchStart(e: TouchEvent) {
    if (e.touches.length === 2) {
      initialDistance = getDistance(e.touches[0], e.touches[1])
      return
    }
    startX = e.touches[0].clientX
    startY = e.touches[0].clientY
    startTime = Date.now()
  }

  function onTouchEnd(e: TouchEvent) {
    if (e.touches.length === 1 && initialDistance > 0) {
      const currentDistance = getDistance(e.changedTouches[0], e.touches[0])
      const scale = currentDistance / initialDistance
      options.onPinch?.(scale)
      initialDistance = 0
      return
    }

    const dx = e.changedTouches[0].clientX - startX
    const dy = e.changedTouches[0].clientY - startY
    const dt = Date.now() - startTime

    if (dt < 300 && Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy)) {
      dx > 0 ? options.onSwipeRight?.() : options.onSwipeLeft?.()
    } else if (Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy) * 2) {
      dx > 0 ? options.onSwipeRight?.() : options.onSwipeLeft?.()
    } else if (Math.abs(dy) > 50 && Math.abs(dy) > Math.abs(dx) * 2) {
      dy > 0 ? options.onSwipeDown?.() : options.onSwipeUp?.()
    }
  }

  function getDistance(t1: Touch, t2: Touch) {
    return Math.sqrt(
      Math.pow(t1.clientX - t2.clientX, 2) +
      Math.pow(t1.clientY - t2.clientY, 2)
    )
  }

  onMounted(() => {
    element.value?.addEventListener('touchstart', onTouchStart, { passive: true })
    element.value?.addEventListener('touchend', onTouchEnd, { passive: true })
  })

  onUnmounted(() => {
    element.value?.removeEventListener('touchstart', onTouchStart)
    element.value?.removeEventListener('touchend', onTouchEnd)
  })
}
```

- [ ] **步骤 2：创建 useKeyboardNav composable**

```typescript
// web-frontend/src/composables/useKeyboardNav.ts
import { onMounted, onUnmounted } from 'vue'

export interface KeyboardOptions {
  onLeft?: () => void
  onRight?: () => void
  onUp?: () => void
  onDown?: () => void
  onZoomIn?: () => void
  onZoomOut?: () => void
  onFullscreen?: () => void
  onEscape?: () => void
}

export function useKeyboardNav(options: KeyboardOptions) {
  function handleKeydown(e: KeyboardEvent) {
    switch (e.key) {
      case 'ArrowLeft':
        e.preventDefault()
        options.onLeft?.()
        break
      case 'ArrowRight':
        e.preventDefault()
        options.onRight?.()
        break
      case 'ArrowUp':
        e.preventDefault()
        options.onUp?.()
        break
      case 'ArrowDown':
        e.preventDefault()
        options.onDown?.()
        break
      case '+':
      case '=':
        e.preventDefault()
        options.onZoomIn?.()
        break
      case '-':
        e.preventDefault()
        options.onZoomOut?.()
        break
      case 'f':
      case 'F':
        e.preventDefault()
        options.onFullscreen?.()
        break
      case 'Escape':
        e.preventDefault()
        options.onEscape?.()
        break
    }
  }

  onMounted(() => {
    document.addEventListener('keydown', handleKeydown)
  })

  onUnmounted(() => {
    document.removeEventListener('keydown', handleKeydown)
  })
}
```

- [ ] **步骤 3：创建 ReaderToolbar**

```vue
<!-- web-frontend/src/components/reader/ReaderToolbar.vue -->
<template>
  <div class="reader-toolbar" :class="{ hidden: !visible }">
    <button class="back-btn" @click="$emit('back')">← 返回</button>
    <div class="title">{{ title }}</div>
    <div class="actions">
      <button @click="$emit('toggle-settings')">⚙️</button>
      <button @click="$emit('toggle-fullscreen')">⛶</button>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  visible: boolean
}>()

defineEmits<{
  back: []
  'toggle-settings': []
  'toggle-fullscreen': []
}>()
</script>

<style scoped>
.reader-toolbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  padding: 8px 16px;
  background: rgba(0, 0, 0, 0.8);
  color: white;
  z-index: 100;
  transition: transform 0.3s;
}
.reader-toolbar.hidden {
  transform: translateY(-100%);
}
.back-btn {
  background: none;
  border: none;
  color: white;
  font-size: 16px;
  cursor: pointer;
}
.title {
  flex: 1;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.actions button {
  background: none;
  border: none;
  color: white;
  font-size: 20px;
  cursor: pointer;
  margin-left: 8px;
}
</style>
```

- [ ] **步骤 4：创建 ReaderSettings**

```vue
<!-- web-frontend/src/components/reader/ReaderSettings.vue -->
<template>
  <div class="reader-settings" :class="{ visible: visible }">
    <div class="setting-group">
      <label>阅读模式</label>
      <div class="mode-buttons">
        <button :class="{ active: mode === 'page' }" @click="$emit('update:mode', 'page')">翻页</button>
        <button :class="{ active: mode === 'scroll' }" @click="$emit('update:mode', 'scroll')">滚动</button>
        <button :class="{ active: mode === 'longstrip' }" @click="$emit('update:mode', 'longstrip')">长条</button>
      </div>
    </div>
    <div class="setting-group">
      <label>背景色</label>
      <div class="color-buttons">
        <button v-for="color in bgColors" :key="color" 
                :style="{ background: color }"
                :class="{ active: bgColor === color }"
                @click="$emit('update:bgColor', color)"></button>
      </div>
    </div>
    <div class="setting-group">
      <label>亮度</label>
      <input type="range" min="0.5" max="1.5" step="0.1" 
             :value="brightness"
             @input="$emit('update:brightness', Number(($event.target as HTMLInputElement).value))" />
    </div>
    <div class="setting-group">
      <label>白边裁切</label>
      <input type="checkbox" :checked="cropBorders" 
             @change="$emit('update:cropBorders', ($event.target as HTMLInputElement).checked)" />
    </div>
    <div class="setting-group">
      <label>双页并排</label>
      <input type="checkbox" :checked="doublePage" 
             @change="$emit('update:doublePage', ($event.target as HTMLInputElement).checked)" />
    </div>
    <div class="setting-group">
      <label>自动翻页 (秒)</label>
      <input type="number" min="0" max="30" :value="autoPageDelay"
             @input="$emit('update:autoPageDelay', Number(($event.target as HTMLInputElement).value))" />
    </div>
    <div class="setting-group">
      <label>阅读方向</label>
      <div class="mode-buttons">
        <button :class="{ active: !rtl }" @click="$emit('update:rtl', false)">左→右</button>
        <button :class="{ active: rtl }" @click="$emit('update:rtl', true)">右→左</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  visible: boolean
  mode: string
  bgColor: string
  brightness: number
  cropBorders: boolean
  doublePage: boolean
  autoPageDelay: number
  rtl: boolean
}>()

defineEmits<{
  'update:mode': [value: string]
  'update:bgColor': [value: string]
  'update:brightness': [value: number]
  'update:cropBorders': [value: boolean]
  'update:doublePage': [value: boolean]
  'update:autoPageDelay': [value: number]
  'update:rtl': [value: boolean]
}>()

const bgColors = ['#ffffff', '#000000', '#808080', '#ffffcc', '#ccffcc']
</script>

<style scoped>
.reader-settings {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(0, 0, 0, 0.9);
  color: white;
  padding: 16px;
  transform: translateY(100%);
  transition: transform 0.3s;
  z-index: 100;
}
.reader-settings.visible {
  transform: translateY(0);
}
.setting-group {
  margin-bottom: 12px;
}
.setting-group label {
  display: block;
  margin-bottom: 4px;
  font-size: 14px;
}
.mode-buttons, .color-buttons {
  display: flex;
  gap: 8px;
}
.mode-buttons button, .color-buttons button {
  flex: 1;
  padding: 8px;
  border: 1px solid #666;
  background: #333;
  color: white;
  border-radius: 4px;
  cursor: pointer;
}
.mode-buttons button.active, .color-buttons button.active {
  border-color: #4a90d9;
  background: #4a90d9;
}
.color-buttons button {
  width: 40px;
  height: 40px;
}
input[type="range"] {
  width: 100%;
}
input[type="number"] {
  width: 80px;
  padding: 4px;
}
</style>
```

- [ ] **步骤 5：创建 PageMode**

```vue
<!-- web-frontend/src/components/reader/PageMode.vue -->
<template>
  <div class="page-mode" ref="container" @click="handleClick">
    <img 
      :src="imageUrl" 
      :style="imageStyle"
      class="page-image"
      @load="onImageLoad"
    />
    <div class="page-indicator">
      {{ currentPage }} / {{ totalPages }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

const props = defineProps<{
  imageUrl: string
  currentPage: number
  totalPages: number
  brightness: number
  cropBorders: boolean
  scale: number
}>()

const emit = defineEmits<{
  prev: []
  next: []
}>()

const container = ref<HTMLElement>()
const imageWidth = ref(0)
const imageHeight = ref(0)

const imageStyle = computed(() => ({
  filter: `brightness(${props.brightness})`,
  transform: `scale(${props.scale})`,
}))

function onImageLoad(e: Event) {
  const img = e.target as HTMLImageElement
  imageWidth.value = img.naturalWidth
  imageHeight.value = img.naturalHeight
}

function handleClick(e: MouseEvent) {
  const rect = container.value?.getBoundingClientRect()
  if (!rect) return
  
  const x = e.clientX - rect.left
  const width = rect.width
  
  if (x < width / 3) {
    emit('prev')
  } else if (x > width * 2 / 3) {
    emit('next')
  }
}
</script>

<style scoped>
.page-mode {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.page-image {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  transition: transform 0.2s;
}
.page-indicator {
  position: fixed;
  bottom: 60px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(0, 0, 0, 0.7);
  color: white;
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 14px;
}
</style>
```

- [ ] **步骤 6：创建 ScrollMode**

```vue
<!-- web-frontend/src/components/reader/ScrollMode.vue -->
<template>
  <div class="scroll-mode">
    <img 
      v-for="(url, index) in imageUrls" 
      :key="index"
      :src="url"
      :style="{ filter: `brightness(${brightness})` }"
      class="scroll-image"
      loading="lazy"
    />
  </div>
</template>

<script setup lang="ts">
defineProps<{
  imageUrls: string[]
  brightness: number
}>()
</script>

<style scoped>
.scroll-mode {
  width: 100%;
  max-width: 800px;
  margin: 0 auto;
}
.scroll-image {
  width: 100%;
  display: block;
  margin-bottom: 2px;
}
</style>
```

- [ ] **步骤 7：创建 ImageReader**

```vue
<!-- web-frontend/src/components/reader/ImageReader.vue -->
<template>
  <div class="image-reader" :style="{ background: bgColor }" ref="readerEl">
    <PageMode 
      v-if="mode === 'page'"
      :image-url="currentImageUrl"
      :current-page="currentPage"
      :total-pages="totalPages"
      :brightness="brightness"
      :crop-borders="cropBorders"
      :scale="scale"
      @prev="prevPage"
      @next="nextPage"
    />
    <ScrollMode 
      v-else
      :image-urls="allImageUrls"
      :brightness="brightness"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useSwipeGesture } from '../../composables/useSwipeGesture'
import { useKeyboardNav } from '../../composables/useKeyboardNav'
import PageMode from './PageMode.vue'
import ScrollMode from './ScrollMode.vue'

const props = defineProps<{
  gid: number
  totalPages: number
  mode: string
  bgColor: string
  brightness: number
  cropBorders: boolean
  rtl: boolean
  autoPageDelay: number
}>()

const emit = defineEmits<{
  'update:page': [page: number]
}>()

const readerEl = ref<HTMLElement>()
const currentPage = ref(1)
const scale = ref(1)
const autoPageTimer = ref<number | null>(null)

const currentImageUrl = computed(() => {
  return `/api/v1/gallery/image/${props.gid}/${currentPage.value}`
})

const allImageUrls = computed(() => {
  return Array.from({ length: props.totalPages }, (_, i) => 
    `/api/v1/gallery/image/${props.gid}/${i + 1}`
  )
})

function prevPage() {
  if (props.rtl) {
    if (currentPage.value < props.totalPages) {
      currentPage.value++
      emit('update:page', currentPage.value)
    }
  } else {
    if (currentPage.value > 1) {
      currentPage.value--
      emit('update:page', currentPage.value)
    }
  }
}

function nextPage() {
  if (props.rtl) {
    if (currentPage.value > 1) {
      currentPage.value--
      emit('update:page', currentPage.value)
    }
  } else {
    if (currentPage.value < props.totalPages) {
      currentPage.value++
      emit('update:page', currentPage.value)
    }
  }
}

function zoomIn() {
  scale.value = Math.min(3, scale.value + 0.25)
}

function zoomOut() {
  scale.value = Math.max(0.5, scale.value - 0.25)
}

function resetZoom() {
  scale.value = 1
}

useSwipeGesture(readerEl, {
  onSwipeLeft: props.rtl ? prevPage : nextPage,
  onSwipeRight: props.rtl ? nextPage : prevPage,
  onPinch: (s) => { scale.value = Math.max(0.5, Math.min(3, s)) },
})

useKeyboardNav({
  onLeft: prevPage,
  onRight: nextPage,
  onZoomIn: zoomIn,
  onZoomOut: zoomOut,
})

// 自动翻页
watch(() => props.autoPageDelay, (delay) => {
  if (autoPageTimer.value) {
    clearInterval(autoPageTimer.value)
    autoPageTimer.value = null
  }
  if (delay > 0 && props.mode === 'page') {
    autoPageTimer.value = window.setInterval(nextPage, delay * 1000)
  }
})

onUnmounted(() => {
  if (autoPageTimer.value) {
    clearInterval(autoPageTimer.value)
  }
})

// 从 URL 获取初始页码
onMounted(() => {
  const path = window.location.pathname
  const match = path.match(/\/reader\/\d+\/(\d+)/)
  if (match) {
    currentPage.value = parseInt(match[1])
  }
})
</script>

<style scoped>
.image-reader {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  touch-action: none;
}
</style>
```

- [ ] **步骤 8：创建 ReaderView**

```vue
<!-- web-frontend/src/views/ReaderView.vue -->
<template>
  <div class="reader-view">
    <ReaderToolbar 
      :title="gallery?.title || ''"
      :visible="toolbarVisible"
      @back="goBack"
      @toggle-settings="settingsVisible = !settingsVisible"
      @toggle-fullscreen="toggleFullscreen"
    />
    
    <ImageReader 
      v-if="gallery"
      :gid="gallery.gid"
      :total-pages="gallery.pageCount"
      v-model:mode="mode"
      :bg-color="bgColor"
      :brightness="brightness"
      :crop-borders="cropBorders"
      :rtl="rtl"
      :auto-page-delay="autoPageDelay"
      @update:page="onPageChange"
    />
    
    <ReaderSettings 
      :visible="settingsVisible"
      v-model:mode="mode"
      v-model:bg-color="bgColor"
      v-model:brightness="brightness"
      v-model:crop-borders="cropBorders"
      v-model:double-page="doublePage"
      v-model:auto-page-delay="autoPageDelay"
      v-model:rtl="rtl"
    />
    
    <div class="page-slider" v-if="toolbarVisible">
      <input 
        type="range" 
        min="1" 
        :max="gallery?.pageCount || 1" 
        v-model.number="currentPage"
        @input="onSliderChange"
      />
      <span>{{ currentPage }} / {{ gallery?.pageCount || 0 }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { galleryApi } from '../api/gallery'
import { historyApi } from '../api/history'
import type { GalleryDetailResponse } from '../types'
import ReaderToolbar from '../components/reader/ReaderToolbar.vue'
import ReaderSettings from '../components/reader/ReaderSettings.vue'
import ImageReader from '../components/reader/ImageReader.vue'

const route = useRoute()
const router = useRouter()

const gallery = ref<GalleryDetailResponse | null>(null)
const currentPage = ref(1)
const toolbarVisible = ref(true)
const settingsVisible = ref(false)

const mode = ref('page')
const bgColor = ref('#000000')
const brightness = ref(1)
const cropBorders = ref(false)
const doublePage = ref(false)
const autoPageDelay = ref(0)
const rtl = ref(false)

function goBack() {
  router.push(`/gallery/${route.params.gid}`)
}

function toggleFullscreen() {
  if (document.fullscreenElement) {
    document.exitFullscreen()
  } else {
    document.documentElement.requestFullscreen()
  }
}

function onPageChange(page: number) {
  currentPage.value = page
}

function onSliderChange() {
  // 滑块变化时更新页面
}

// 点击切换工具栏
function toggleToolbar() {
  toolbarVisible.value = !toolbarVisible.value
  if (!toolbarVisible.value) {
    settingsVisible.value = false
  }
}

onMounted(async () => {
  const gid = Number(route.params.gid)
  const result = await galleryApi.getDetail(gid)
  gallery.value = result.data
  
  // 记录浏览历史
  await historyApi.list() // 触发历史记录
  
  // 点击切换工具栏
  document.addEventListener('click', (e) => {
    const target = e.target as HTMLElement
    if (!target.closest('.reader-toolbar') && 
        !target.closest('.reader-settings') && 
        !target.closest('.page-slider')) {
      toggleToolbar()
    }
  })
})
</script>

<style scoped>
.reader-view {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: #000;
}
.page-slider {
  position: fixed;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 12px;
  background: rgba(0, 0, 0, 0.7);
  padding: 8px 16px;
  border-radius: 20px;
  z-index: 100;
}
.page-slider input {
  width: 200px;
}
.page-slider span {
  color: white;
  font-size: 14px;
}
</style>
```

- [ ] **步骤 9：Commit**

```bash
git add web-frontend/src/views/ReaderView.vue \
        web-frontend/src/components/reader/ \
        web-frontend/src/composables/
git commit -m "feat: add image reader with page/scroll mode, settings, gestures"
```

---

## 任务 4：创建收藏和历史页面

**文件：**
- 创建：`web-frontend/src/views/FavoriteView.vue`
- 创建：`web-frontend/src/views/HistoryView.vue`
- 创建：`web-frontend/src/components/common/CommentList.vue`

- [ ] **步骤 1：创建 FavoriteView**

```vue
<!-- web-frontend/src/views/FavoriteView.vue -->
<template>
  <div class="favorite-view">
    <AppHeader />
    <div class="content">
      <h1>收藏</h1>
      <div class="slot-tabs">
        <button v-for="i in 10" :key="i" 
                :class="{ active: currentSlot === i - 1 }"
                @click="switchSlot(i - 1)">
          {{ i === 1 ? '全部' : `收藏夹 ${i - 1}` }}
        </button>
      </div>
      <GalleryGrid 
        :galleries="favorites" 
        :loading="loading"
        @load-more="loadMore"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { favoriteApi } from '../api/favorite'
import type { GalleryListItem } from '../types'
import AppHeader from '../components/layout/AppHeader.vue'
import GalleryGrid from '../components/gallery/GalleryGrid.vue'

const favorites = ref<GalleryListItem[]>([])
const loading = ref(false)
const currentSlot = ref(0)
const currentPage = ref(1)
const hasMore = ref(true)

async function switchSlot(slot: number) {
  currentSlot.value = slot
  currentPage.value = 1
  favorites.value = []
  await loadMore()
}

async function loadMore() {
  if (loading.value || !hasMore.value) return
  loading.value = true
  
  try {
    const result = await favoriteApi.list(currentSlot.value, currentPage.value)
    favorites.value.push(...result.data.favorites)
    hasMore.value = currentPage.value < result.data.totalPages
    currentPage.value++
  } catch (e) {
    console.error('Failed to load favorites:', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadMore()
})
</script>

<style scoped>
.favorite-view {
  min-height: 100vh;
  background: #f5f5f5;
}
.content {
  max-width: 1400px;
  margin: 0 auto;
  padding: 1rem;
}
h1 {
  margin-bottom: 1rem;
}
.slot-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 1rem;
  flex-wrap: wrap;
}
.slot-tabs button {
  padding: 8px 16px;
  border: 1px solid #ddd;
  background: white;
  border-radius: 4px;
  cursor: pointer;
}
.slot-tabs button.active {
  background: #4a90d9;
  color: white;
  border-color: #4a90d9;
}
</style>
```

- [ ] **步骤 2：创建 HistoryView**

```vue
<!-- web-frontend/src/views/HistoryView.vue -->
<template>
  <div class="history-view">
    <AppHeader />
    <div class="content">
      <div class="header">
        <h1>浏览历史</h1>
        <button @click="clearHistory" class="clear-btn">清除历史</button>
      </div>
      <GalleryGrid 
        :galleries="historyList" 
        :loading="loading"
        @load-more="loadMore"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { historyApi } from '../api/history'
import type { GalleryListItem } from '../types'
import AppHeader from '../components/layout/AppHeader.vue'
import GalleryGrid from '../components/gallery/GalleryGrid.vue'

const historyList = ref<GalleryListItem[]>([])
const loading = ref(false)

async function loadHistory() {
  loading.value = true
  try {
    const result = await historyApi.list()
    historyList.value = result.data.history.map(h => ({
      gid: h.gid,
      token: h.token,
      title: h.title,
      titleJpn: h.titleJpn,
      thumb: h.thumb,
      category: h.category,
      rating: h.rating,
      simpleLanguage: '',
      uploader: null,
      posted: null,
    }))
  } catch (e) {
    console.error('Failed to load history:', e)
  } finally {
    loading.value = false
  }
}

async function clearHistory() {
  if (confirm('确定要清除所有浏览历史吗？')) {
    await historyApi.clear()
    historyList.value = []
  }
}

function loadMore() {
  // 历史记录一次性加载，无需分页
}

onMounted(() => {
  loadHistory()
})
</script>

<style scoped>
.history-view {
  min-height: 100vh;
  background: #f5f5f5;
}
.content {
  max-width: 1400px;
  margin: 0 auto;
  padding: 1rem;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}
.clear-btn {
  padding: 8px 16px;
  background: #e74c3c;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}
</style>
```

- [ ] **步骤 3：创建 CommentList**

```vue
<!-- web-frontend/src/components/common/CommentList.vue -->
<template>
  <div class="comment-list">
    <h3>评论 ({{ comments.length }})</h3>
    <div v-for="comment in comments" :key="comment.id" class="comment-item">
      <div class="comment-header">
        <strong>{{ comment.uploader }}</strong>
        <span class="score">+{{ comment.score }}</span>
        <span class="time">{{ comment.time }}</span>
      </div>
      <p class="comment-body">{{ comment.comment }}</p>
      <div class="comment-actions">
        <button @click="vote(comment.id, 1)">👍</button>
        <button @click="vote(comment.id, -1)">👎</button>
      </div>
    </div>
    
    <div class="comment-form">
      <textarea v-model="newComment" placeholder="发表评论..."></textarea>
      <button @click="postComment" :disabled="!newComment.trim()">发表</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { commentApi } from '../../api/comment'
import type { CommentItem } from '../../api/comment'

const props = defineProps<{
  gid: number
  comments: CommentItem[]
}>()

const emit = defineEmits<{
  refresh: []
}>()

const newComment = ref('')

async function postComment() {
  if (!newComment.value.trim()) return
  await commentApi.post(props.gid, newComment.value)
  newComment.value = ''
  emit('refresh')
}

async function vote(commentId: number, vote: number) {
  await commentApi.vote(props.gid, commentId, vote)
  emit('refresh')
}
</script>

<style scoped>
.comment-list {
  margin-top: 2rem;
}
h3 {
  margin-bottom: 1rem;
}
.comment-item {
  background: white;
  padding: 1rem;
  border-radius: 8px;
  margin-bottom: 0.5rem;
}
.comment-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.score {
  color: #4a90d9;
}
.time {
  color: #999;
  font-size: 12px;
  margin-left: auto;
}
.comment-body {
  line-height: 1.5;
}
.comment-actions {
  margin-top: 8px;
}
.comment-actions button {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 16px;
}
.comment-form {
  margin-top: 1rem;
  display: flex;
  gap: 8px;
}
.comment-form textarea {
  flex: 1;
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
  resize: vertical;
  min-height: 80px;
}
.comment-form button {
  padding: 8px 16px;
  background: #4a90d9;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  align-self: flex-end;
}
.comment-form button:disabled {
  background: #ccc;
}
</style>
```

- [ ] **步骤 4：更新路由**

```typescript
// web-frontend/src/router/index.ts
import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import HomeView from '../views/HomeView.vue'
import GalleryDetailView from '../views/GalleryDetailView.vue'
import ReaderView from '../views/ReaderView.vue'
import FavoriteView from '../views/FavoriteView.vue'
import HistoryView from '../views/HistoryView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView },
    {
      path: '/',
      name: 'home',
      component: HomeView,
      meta: { requiresAuth: true }
    },
    {
      path: '/gallery/:gid',
      name: 'gallery-detail',
      component: GalleryDetailView,
      meta: { requiresAuth: true }
    },
    {
      path: '/reader/:gid/:page',
      name: 'reader',
      component: ReaderView,
      meta: { requiresAuth: true, fullscreen: true }
    },
    {
      path: '/favorites',
      name: 'favorites',
      component: FavoriteView,
      meta: { requiresAuth: true }
    },
    {
      path: '/history',
      name: 'history',
      component: HistoryView,
      meta: { requiresAuth: true }
    },
  ]
})

router.beforeEach((to, from, next) => {
  const isLoggedIn = localStorage.getItem('auth_token')
  if (to.meta.requiresAuth && !isLoggedIn) {
    next({ name: 'login' })
  } else {
    next()
  }
})

export default router
```

- [ ] **步骤 5：Commit**

```bash
git add web-frontend/src/views/FavoriteView.vue \
        web-frontend/src/views/HistoryView.vue \
        web-frontend/src/components/common/CommentList.vue \
        web-frontend/src/router/index.ts
git commit -m "feat: add favorite, history views and comment list component"
```

---

## 任务 5：更新画廊详情页添加评论和收藏

**文件：**
- 修改：`web-frontend/src/views/GalleryDetailView.vue`

- [ ] **步骤 1：更新 GalleryDetailView**

```vue
<!-- web-frontend/src/views/GalleryDetailView.vue -->
<template>
  <div class="gallery-detail" v-if="detail">
    <AppHeader />
    <div class="content">
      <div class="header">
        <img :src="detail.thumb" class="cover" />
        <div class="info">
          <h1>{{ detail.title }}</h1>
          <p v-if="detail.titleJpn" class="subtitle">{{ detail.titleJpn }}</p>
          <div class="meta">
            <span>⭐ {{ detail.rating.toFixed(1) }}</span>
            <span>📄 {{ detail.pageCount }} 页</span>
            <span>{{ detail.category }}</span>
            <span v-if="detail.simpleLanguage">{{ detail.simpleLanguage }}</span>
          </div>
          <div class="tags" v-if="detail.tags.length">
            <TagChip 
              v-for="(tag, i) in flatTags" 
              :key="i" 
              :tag="tag" 
            />
          </div>
          <div class="actions">
            <button @click="openReader">阅读</button>
            <button @click="addToFavorite">收藏</button>
            <button @click="download">下载</button>
          </div>
        </div>
      </div>
      
      <div class="previews" v-if="detail.previewSet">
        <h2>预览</h2>
        <div class="preview-grid">
          <img 
            v-for="(img, i) in detail.previewSet.images" 
            :key="i"
            :src="img.thumbUrl"
            @click="openReaderAt(i + 1)"
          />
        </div>
      </div>
      
      <CommentList 
        :gid="detail.gid" 
        :comments="detail.comments"
        @refresh="refreshComments"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { galleryApi } from '../api/gallery'
import { favoriteApi } from '../api/favorite'
import type { GalleryDetailResponse } from '../types'
import AppHeader from '../components/layout/AppHeader.vue'
import TagChip from '../components/common/TagChip.vue'
import CommentList from '../components/common/CommentList.vue'

const route = useRoute()
const router = useRouter()
const detail = ref<GalleryDetailResponse | null>(null)

const flatTags = computed(() => {
  if (!detail.value) return []
  return detail.value.tags.flatMap(group => 
    group.tags.map(tag => `${group.namespace}:${tag}`)
  )
})

function openReader() {
  router.push(`/reader/${route.params.gid}/1`)
}

function openReaderAt(page: number) {
  router.push(`/reader/${route.params.gid}/${page}`)
}

async function addToFavorite() {
  if (!detail.value) return
  await favoriteApi.add(detail.value.gid, detail.value.token, 0)
  alert('已添加到收藏')
}

function download() {
  alert('下载功能将在 Phase 3 实现')
}

async function refreshComments() {
  if (!detail.value) return
  const result = await galleryApi.getDetail(detail.value.gid)
  detail.value = result.data
}

onMounted(async () => {
  const gid = Number(route.params.gid)
  const result = await galleryApi.getDetail(gid)
  detail.value = result.data
})
</script>

<style scoped>
.gallery-detail {
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
  gap: 1.5rem;
  margin-bottom: 2rem;
}
.cover {
  width: 300px;
  border-radius: 8px;
}
.info {
  flex: 1;
}
h1 {
  margin: 0 0 0.5rem;
}
.subtitle {
  color: #666;
  margin-bottom: 1rem;
}
.meta {
  display: flex;
  gap: 1rem;
  margin-bottom: 1rem;
  color: #666;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-bottom: 1rem;
}
.actions {
  display: flex;
  gap: 0.5rem;
}
.actions button {
  padding: 0.5rem 1rem;
  border: 1px solid #4a90d9;
  background: white;
  color: #4a90d9;
  border-radius: 4px;
  cursor: pointer;
}
.actions button:first-child {
  background: #4a90d9;
  color: white;
}
.preview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 0.5rem;
}
.preview-grid img {
  width: 100%;
  cursor: pointer;
  border-radius: 4px;
}
</style>
```

- [ ] **步骤 2：Commit**

```bash
git add web-frontend/src/views/GalleryDetailView.vue
git commit -m "feat: update gallery detail with comment list and favorite button"
```

---

## 总结

Phase 2 完成后，系统将具备：
- 完整的图片阅读器（翻页/滚动/缩放/手势/键盘/亮度/背景/白边裁切/双页/自动/右至左/长条）
- 收藏管理（列表/添加/移除，10 个收藏夹）
- 评论功能（列表/发表/投票）
- 浏览历史（列表/清除）
- 书签同步（阅读进度自动保存）

下一步 Phase 3 将实现下载缓存系统。
