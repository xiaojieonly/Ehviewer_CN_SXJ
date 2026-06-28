<template>
  <div class="comment-list">
    <h3>Comments</h3>
    <div class="comment-input">
      <textarea v-model="newComment" placeholder="Write a comment..." rows="3"></textarea>
      <button @click="submitComment" :disabled="!newComment.trim() || submitting" class="btn-submit">
        {{ submitting ? 'Posting...' : 'Post' }}
      </button>
    </div>
    <div v-if="loading" class="loading">Loading comments...</div>
    <div v-else-if="comments.length === 0" class="empty">No comments yet</div>
    <div v-else class="comments">
      <div v-for="comment in comments" :key="comment.id" class="comment-item">
        <div class="comment-header">
          <span class="author">{{ comment.uploader }}</span>
          <span class="time">{{ comment.time }}</span>
        </div>
        <div class="comment-body">{{ comment.comment }}</div>
        <div class="comment-footer">
          <button class="btn-vote" @click="$emit('vote', comment.id, 1)">👍 {{ comment.score }}</button>
          <button class="btn-vote" @click="$emit('vote', comment.id, -1)">👎</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { CommentItem } from '@/api/comment'

defineProps<{
  comments: CommentItem[]
  loading: boolean
}>()

const emit = defineEmits<{
  submit: [comment: string]
  vote: [commentId: number, vote: number]
}>()

const newComment = ref('')
const submitting = ref(false)

async function submitComment() {
  if (!newComment.value.trim()) return
  submitting.value = true
  try {
    emit('submit', newComment.value.trim())
    newComment.value = ''
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.comment-list {
  margin-top: 2rem;
}
.comment-list h3 {
  margin-bottom: 1rem;
  color: #333;
}
.comment-input {
  margin-bottom: 1.5rem;
}
.comment-input textarea {
  width: 100%;
  padding: 0.6rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 0.9rem;
  resize: vertical;
  font-family: inherit;
}
.comment-input textarea:focus {
  outline: none;
  border-color: #4a90d9;
}
.btn-submit {
  margin-top: 0.5rem;
  padding: 0.4rem 1rem;
  background: #4a90d9;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}
.btn-submit:disabled {
  background: #b0c4de;
  cursor: not-allowed;
}
.loading, .empty {
  text-align: center;
  padding: 1.5rem;
  color: #666;
}
.comments {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.comment-item {
  background: white;
  border: 1px solid #eee;
  border-radius: 6px;
  padding: 1rem;
}
.comment-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 0.5rem;
}
.author {
  font-weight: 500;
  color: #333;
}
.time {
  font-size: 0.8rem;
  color: #999;
}
.comment-body {
  color: #555;
  line-height: 1.5;
  white-space: pre-wrap;
}
.comment-footer {
  margin-top: 0.5rem;
  display: flex;
  gap: 0.5rem;
}
.btn-vote {
  padding: 0.2rem 0.5rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  background: white;
  cursor: pointer;
  font-size: 0.8rem;
}
.btn-vote:hover {
  background: #f5f5f5;
}
</style>
