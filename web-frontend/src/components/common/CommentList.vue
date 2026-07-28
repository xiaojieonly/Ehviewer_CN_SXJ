<template>
  <div class="comment-list">
    <!-- Loading: ProgressView replica, centered (Android comment scene) -->
    <div v-if="loading" class="comment-list__loading">
      <ProgressSpinner size="small" />
    </div>

    <TransitionGroup
      v-else-if="comments.length > 0"
      tag="div"
      name="comment"
      class="comment-list__items"
    >
      <CommentItem
        v-for="comment in comments"
        :key="comment.id"
        :comment="comment"
        :voting="votingId === comment.id"
        @vote="(commentId, vote) => emit('vote', commentId, vote)"
      />
    </TransitionGroup>

    <!-- Post box pinned at the bottom (Android comment input row) -->
    <form class="comment-list__form" @submit.prevent="submitComment">
      <label class="comment-list__label" for="comment-list-input">Comment</label>
      <textarea
        id="comment-list-input"
        v-model="draft"
        class="comment-list__input"
        rows="3"
        placeholder="Say something about this gallery…"
        :disabled="posting"
      />
      <div class="comment-list__form-foot">
        <button
          type="submit"
          class="comment-list__submit"
          :disabled="!draft.trim() || posting"
        >
          {{ posting ? 'Posting…' : 'Post' }}
        </button>
      </div>
    </form>
  </div>
</template>

<script setup lang="ts">
/**
 * CommentList — the gallery detail comment section body: a vertical stack
 * of `CommentItem` cards (Android `gallery_detail_comments.xml` +
 * `item_gallery_comment.xml`) with the post input pinned at the bottom.
 *
 * Empty-state copy is owned by the host screen (Android shows a centered
 * `textColorThemeAccent` status line — "No comments" / "No more comments"),
 * so this component renders nothing between the spinner and the form when
 * the list is empty.
 *
 * Data flow is fully controlled by the parent: the list is a prop, and
 * posting / voting emit events the parent performs against `commentApi`.
 */
import { ref } from 'vue'
import type { CommentItem as CommentModel } from '@/api/comment'
import ProgressSpinner from '@/components/atoms/ProgressSpinner.vue'
import CommentItem from './CommentItem.vue'

withDefaults(
  defineProps<{
    /** Comments to render (already fetched by the parent). */
    comments: CommentModel[]
    /** Initial comment fetch in progress. */
    loading: boolean
    /** A post request is in flight (disables the form). @default false */
    posting?: boolean
    /** `id` of the comment whose vote request is in flight, if any. */
    votingId?: number | null
  }>(),
  {
    posting: false,
    votingId: null,
  },
)

const emit = defineEmits<{
  /** Post button tapped with the trimmed draft text. */
  (e: 'submit', comment: string): void
  /** Vote button tapped on a comment (`1` up / `-1` down). */
  (e: 'vote', commentId: number, vote: number): void
}>()

const draft = ref('')

function submitComment() {
  const text = draft.value.trim()
  if (!text) return
  emit('submit', text)
  draft.value = ''
}
</script>

<style scoped>
.comment-list__loading {
  display: flex;
  justify-content: center;
  padding: var(--keyline-margin) 0;
}

.comment-list__items {
  display: flex;
  flex-direction: column;
  gap: var(--spacing);
}

/* List transitions (Vue TransitionGroup, `comment-*` classes) */
.comment-enter-active {
  transition:
    opacity 240ms var(--ease-decelerate-quart),
    transform 240ms var(--ease-decelerate-quart);
}

.comment-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.comment-move {
  transition: transform 240ms var(--ease-decelerate-quart);
}

/* ------------------------------------------------------------ post form --- */
.comment-list__form {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: calc(var(--spacing) + 4px);
}

.comment-list__label {
  font-size: clamp(11px, var(--text-super-small), 14px);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--text-color-secondary);
}

.comment-list__input {
  width: 100%;
  min-height: 72px;
  padding: 10px 12px;
  resize: vertical;
  border: 1px solid var(--color-divider);
  border-radius: var(--card-radius);
  background-color: var(--color-surface);
  color: var(--text-color-primary);
  font-family: inherit;
  font-size: clamp(13px, var(--text-small), 16px);
  line-height: 1.5;
  transition: border-color 120ms var(--ease-decelerate-quart);
}

.comment-list__input::placeholder {
  color: var(--text-color-secondary);
  opacity: 0.75;
}

.comment-list__input:focus {
  outline: none;
  border-color: var(--color-primary);
}

.comment-list__input:disabled {
  opacity: 0.6;
}

.comment-list__form-foot {
  display: flex;
  justify-content: flex-end;
}

.comment-list__submit {
  min-height: 40px;
  padding: 0 24px;
  border: none;
  border-radius: var(--card-radius);
  background-color: var(--color-primary);
  color: var(--color-white);
  font-family: inherit;
  font-size: clamp(13px, var(--text-small), 16px);
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  cursor: pointer;
  transition:
    background-color 120ms var(--ease-decelerate-quart),
    transform 120ms var(--ease-decelerate-quart),
    opacity 120ms var(--ease-decelerate-quart);
}

.comment-list__submit:hover:not(:disabled) {
  background-color: var(--color-primary-dark);
}

.comment-list__submit:active:not(:disabled) {
  transform: scale(0.98);
}

.comment-list__submit:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.comment-list__submit:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 2px;
}
</style>
