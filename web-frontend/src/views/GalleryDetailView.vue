<template>
  <div class="detail">
    <AppHeader />
    <div class="content" v-if="gallery">
      <button class="back-btn" @click="$router.back()">← Back</button>
      <div class="detail-card">
        <div class="thumb-section">
          <img v-if="gallery.thumb" :src="gallery.thumb" :alt="gallery.title" class="thumb" />
        </div>
        <div class="info-section">
          <h1>{{ gallery.title }}</h1>
          <h2 v-if="gallery.titleJpn" class="title-jpn">{{ gallery.titleJpn }}</h2>
          <div class="meta">
            <span class="rating">★ {{ gallery.rating?.toFixed(1) }}</span>
            <span class="pages">{{ gallery.pages }} pages</span>
            <span v-if="gallery.uploader" class="uploader">by {{ gallery.uploader }}</span>
          </div>
          <div class="actions">
            <button class="btn-read" @click="$router.push(`/reader/${gallery.gid}`)">Read</button>
            <button
              class="btn-favorite"
              :class="{ active: isFavorited }"
              @click="toggleFavorite"
            >
              {{ isFavorited ? '★ Favorited' : '☆ Favorite' }}
            </button>
          </div>
          <div v-if="gallery.simpleTags?.length" class="tags">
            <TagChip v-for="tag in gallery.simpleTags" :key="tag" :tag="tag" />
          </div>
        </div>
      </div>
      <CommentList
        :comments="comments"
        :loading="commentsLoading"
        @submit="handleCommentSubmit"
        @vote="handleVote"
      />
    </div>
    <div v-else class="loading">Loading...</div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { galleryApi } from '@/api/gallery'
import { commentApi, type CommentItem } from '@/api/comment'
import { favoriteApi } from '@/api/favorite'
import AppHeader from '@/components/layout/AppHeader.vue'
import TagChip from '@/components/common/TagChip.vue'
import CommentList from '@/components/common/CommentList.vue'
import type { GalleryDetail } from '@/types'

const route = useRoute()
const gallery = ref<GalleryDetail | null>(null)
const comments = ref<CommentItem[]>([])
const commentsLoading = ref(false)
const isFavorited = ref(false)

const gid = Number(route.params.gid)

onMounted(async () => {
  try {
    gallery.value = await galleryApi.getDetail(gid)
    isFavorited.value = (gallery.value?.favoriteSlot ?? -1) >= 0
  } catch (e) {
    console.error('Failed to load gallery detail', e)
  }
  commentsLoading.value = true
  try {
    const res = await commentApi.listComments(gid)
    comments.value = res.comments
  } catch (e) {
    console.error('Failed to load comments', e)
  } finally {
    commentsLoading.value = false
  }
})

async function toggleFavorite() {
  if (!gallery.value) return
  if (isFavorited.value) {
    const res = await favoriteApi.removeFavorite(gid, gallery.value.token)
    if (res.success) isFavorited.value = false
  } else {
    const res = await favoriteApi.addFavorite(gid, gallery.value.token)
    if (res.success) isFavorited.value = true
  }
}

async function handleCommentSubmit(text: string) {
  const res = await commentApi.postComment(gid, text)
  if (res.success) {
    const cRes = await commentApi.listComments(gid)
    comments.value = cRes.comments
  }
}

async function handleVote(commentId: number, vote: number) {
  await commentApi.voteComment(gid, commentId, vote)
  const res = await commentApi.listComments(gid)
  comments.value = res.comments
}
</script>

<style scoped>
.detail {
  min-height: 100vh;
  background: #f5f5f5;
}
.content {
  max-width: 900px;
  margin: 0 auto;
  padding: 1rem;
}
.back-btn {
  margin-bottom: 1rem;
  padding: 0.4rem 0.8rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  background: white;
  cursor: pointer;
}
.detail-card {
  background: white;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}
.thumb-section {
  text-align: center;
  padding: 1rem;
  background: #fafafa;
}
.thumb {
  max-width: 100%;
  max-height: 500px;
  border-radius: 4px;
}
.info-section {
  padding: 1.5rem;
}
.info-section h1 {
  margin: 0 0 0.5rem;
  color: #333;
}
.title-jpn {
  color: #666;
  font-size: 1rem;
  font-weight: normal;
  margin-bottom: 1rem;
}
.meta {
  display: flex;
  gap: 1rem;
  margin-bottom: 1rem;
  color: #666;
  font-size: 0.9rem;
}
.rating {
  color: #f39c12;
  font-weight: bold;
}
.actions {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
}
.btn-read {
  padding: 0.5rem 1.2rem;
  background: #4a90d9;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
}
.btn-read:hover {
  background: #357abd;
}
.btn-favorite {
  padding: 0.5rem 1.2rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  background: white;
  cursor: pointer;
  font-size: 0.9rem;
  color: #666;
}
.btn-favorite.active {
  color: #f39c12;
  border-color: #f39c12;
}
.btn-favorite:hover {
  background: #f9f9f9;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}
.loading {
  text-align: center;
  padding: 3rem;
  color: #666;
}
</style>
