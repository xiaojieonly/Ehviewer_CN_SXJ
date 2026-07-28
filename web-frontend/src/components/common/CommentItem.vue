<template>
  <article class="comment-item">
    <header class="comment-item__head">
      <span class="comment-item__author">{{ comment.uploader }}</span>
      <time class="comment-item__time">{{ comment.time }}</time>
    </header>

    <p class="comment-item__body">{{ comment.comment }}</p>

    <footer class="comment-item__foot">
      <button
        type="button"
        class="comment-item__vote"
        :disabled="voting"
        :aria-label="`Vote up comment by ${comment.uploader}`"
        @click="emit('vote', comment.id, 1)"
      >
        <span class="comment-item__vote-icon" aria-hidden="true">
          <AppIcon name="thumb-up-primary" size="16px" />
        </span>
      </button>

      <span class="comment-item__score" :title="`Score ${comment.score}`">
        {{ formattedScore }}
      </span>

      <button
        type="button"
        class="comment-item__vote comment-item__vote--down"
        :disabled="voting"
        :aria-label="`Vote down comment by ${comment.uploader}`"
        @click="emit('vote', comment.id, -1)"
      >
        <span class="comment-item__vote-icon" aria-hidden="true">
          <AppIcon name="thumb-up-primary" size="16px" />
        </span>
      </button>
    </footer>
  </article>
</template>

<script setup lang="ts">
/**
 * CommentItem — one gallery comment card (web replica of
 * `item_gallery_comment.xml`: user left, time right, comment body below at
 * `textColorPrimary`, keyline horizontal / 8dp vertical padding), extended
 * with the vote controls from the comments scene.
 *
 * Surface: mirrors the `AppCard` CardView.Normal spec (2dp radius,
 * 2dp elevation, theme-aware `--color-surface` background) — the `AppCard`
 * atom itself is gallery-card specific (it renders a gallery thumb/title),
 * so the card chrome is replicated here with the same tokens.
 */
import { computed } from 'vue'
import type { CommentItem } from '@/api/comment'
import AppIcon from '@/components/atoms/AppIcon.vue'

const props = withDefaults(
  defineProps<{
    /** Comment model from the comment API. */
    comment: CommentItem
    /** Whether a vote request for this comment is in flight. @default false */
    voting?: boolean
  }>(),
  {
    voting: false,
  },
)

const emit = defineEmits<{
  /** Vote button tapped — `vote` is `1` (up) or `-1` (down). */
  (e: 'vote', commentId: number, vote: number): void
}>()

/** Android-style signed score display (`+5` / `-2` / `0`). */
const formattedScore = computed(() =>
  props.comment.score > 0 ? `+${props.comment.score}` : `${props.comment.score}`,
)
</script>

<style scoped>
.comment-item {
  /* CardView.Normal surface spec — same tokens as the AppCard atom. */
  margin: 2px;
  padding: 12px var(--keyline-margin);
  background-color: var(--color-surface);
  border-radius: var(--card-radius);
  box-shadow: 0 var(--card-elevation) var(--card-max-elevation) var(--shadow-color);
  transition: box-shadow 160ms var(--ease-decelerate-quart);
}

.comment-item:hover {
  box-shadow: 0 var(--card-elevation) calc(var(--card-max-elevation) * 3) var(--shadow-color);
}

.comment-item__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--spacing);
}

.comment-item__author {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: clamp(13px, var(--text-small), 16px);
  font-weight: 600;
  color: var(--text-color-primary);
}

.comment-item__time {
  flex-shrink: 0;
  font-size: clamp(11px, var(--text-super-small), 14px);
  color: var(--text-color-secondary);
}

.comment-item__body {
  margin: var(--spacing) 0 0;
  font-size: clamp(13px, var(--text-small), 16px);
  line-height: 1.55;
  color: var(--text-color-primary);
  white-space: pre-wrap;
  overflow-wrap: break-word;
}

.comment-item__foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 2px;
  margin-top: 4px;
}

.comment-item__vote {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background-color: transparent;
  color: var(--drawable-color-primary);
  cursor: pointer;
  transition:
    background-color 120ms var(--ease-decelerate-quart),
    color 120ms var(--ease-decelerate-quart);
}

.comment-item__vote:hover:not(:disabled) {
  background-color: var(--color-divider);
  color: var(--color-accent);
}

.comment-item__vote:disabled {
  opacity: 0.45;
  cursor: default;
}

.comment-item__vote:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}

.comment-item__vote-icon {
  display: inline-flex;
  transition: transform 120ms var(--ease-decelerate-quart);
}

.comment-item__vote--down .comment-item__vote-icon {
  transform: rotate(180deg);
}

.comment-item__vote--down:active:not(:disabled) .comment-item__vote-icon {
  transform: rotate(180deg) scale(0.85);
}

.comment-item__vote:not(.comment-item__vote--down):active:not(:disabled) .comment-item__vote-icon {
  transform: scale(0.85);
}

.comment-item__score {
  min-width: 32px;
  text-align: center;
  font-size: clamp(11px, var(--text-super-small), 14px);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--text-color-secondary);
}
</style>
