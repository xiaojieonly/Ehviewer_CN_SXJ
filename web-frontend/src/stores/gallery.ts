import { defineStore } from 'pinia'
import { ref } from 'vue'
import { galleryApi } from '@/api/gallery'
import { commentApi } from '@/api/comment'
import { favoriteApi } from '@/api/favorite'
import type { GalleryDetail } from '@/types'
import type { CommentItem } from '@/api/comment'

export const useGalleryStore = defineStore('gallery', () => {
  const currentGallery = ref<GalleryDetail | null>(null)
  const comments = ref<CommentItem[]>([])
  const isFavorited = ref(false)
  const loading = ref(false)

  async function loadDetail(gid: number) {
    loading.value = true
    try {
      currentGallery.value = await galleryApi.getDetail(gid)
      isFavorited.value = (currentGallery.value?.favoriteSlot ?? -1) >= 0
    } finally {
      loading.value = false
    }
  }

  async function loadComments(gid: number) {
    const res = await commentApi.listComments(gid)
    comments.value = res.comments
  }

  async function postComment(gid: number, text: string) {
    const res = await commentApi.postComment(gid, text)
    if (res.success) {
      await loadComments(gid)
    }
    return res
  }

  async function voteComment(gid: number, commentId: number, vote: number) {
    return commentApi.voteComment(gid, commentId, vote)
  }

  async function toggleFavorite(gid: number, token: string) {
    if (isFavorited.value) {
      const res = await favoriteApi.removeFavorite(gid, token)
      if (res.success) isFavorited.value = false
      return res
    } else {
      const res = await favoriteApi.addFavorite(gid, token)
      if (res.success) isFavorited.value = true
      return res
    }
  }

  function reset() {
    currentGallery.value = null
    comments.value = []
    isFavorited.value = false
  }

  return {
    currentGallery,
    comments,
    isFavorited,
    loading,
    loadDetail,
    loadComments,
    postComment,
    voteComment,
    toggleFavorite,
    reset,
  }
})
