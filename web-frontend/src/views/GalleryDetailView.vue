<template>
  <div class="gallery-detail">
    <!-- Loading state (Android: centered ProgressView) -->
    <div v-if="loading" class="gallery-detail__state">
      <ProgressSpinner size="large" />
    </div>

    <!-- Tip state (Android: sadpanda + tip text, retry on tap) -->
    <div v-else-if="error !== null || gallery === null" class="gallery-detail__state">
      <AppIcon name="sad-panda-primary" size="96px" class="gallery-detail__sadpanda" />
      <p class="gallery-detail__tip">{{ error ?? 'Gallery not found' }}</p>
      <button type="button" class="gallery-detail__retry" @click="load">Retry</button>
    </div>

    <main v-else class="gallery-detail__main">
      <!-- ① Header band — teal `galleryDetailHeaderBackground` with white
             title text, exactly like `gallery_detail_header.xml` -->
      <section class="detail-header" aria-label="Gallery information">
        <button
          type="button"
          class="detail-header__back"
          aria-label="Go back"
          @click="goBack"
        >
          <AppIcon name="arrow-left-dark" size="24px" />
        </button>

        <div class="detail-header__hero">
          <!-- 128×192dp (2:3) detail thumbnail — `gallery_detail_thumb_*` -->
          <div class="detail-header__thumb">
            <img
              v-if="gallery.thumb"
              :src="coverSrc"
              :alt="`Cover of ${gallery.title}`"
              width="128"
              height="192"
              loading="eager"
              decoding="async"
            />
          </div>

          <div class="detail-header__info">
            <h1 class="detail-header__title">{{ gallery.title }}</h1>
            <p v-if="gallery.titleJpn" class="detail-header__title-jpn">
              {{ gallery.titleJpn }}
            </p>

            <button
              v-if="gallery.uploader"
              type="button"
              class="detail-header__uploader"
              :title="`Galleries by ${gallery.uploader}`"
              @click="searchUploader"
            >
              {{ gallery.uploader }}
            </button>

            <dl class="detail-header__meta">
              <div class="detail-header__meta-item">
                <dt>Posted</dt>
                <dd>{{ gallery.posted || '—' }}</dd>
              </div>
              <div class="detail-header__meta-item">
                <dt>Pages</dt>
                <dd>{{ gallery.pages }}</dd>
              </div>
              <div v-if="gallery.simpleLanguage" class="detail-header__meta-item">
                <dt>Language</dt>
                <dd>{{ gallery.simpleLanguage }}</dd>
              </div>
            </dl>

            <div class="detail-header__rating">
              <RatingStars :rating="gallery.rating" />
              <span class="detail-header__rating-num">{{ gallery.rating.toFixed(1) }}</span>
            </div>

            <CategoryChip
              v-if="categoryKey"
              :category="categoryKey"
              class="detail-header__category"
            />
          </div>
        </div>
      </section>

      <div class="gallery-detail__body">
        <!-- ② Action bar — the `action_card` (CardView.Normal) pulled up
               over the header band, icon-over-label buttons, 48dp targets -->
        <section class="detail-actions" aria-label="Gallery actions">
          <button
            type="button"
            class="detail-actions__btn detail-actions__btn--read"
            @click="read"
          >
            <AppIcon name="book-open" size="24px" />
            <span class="detail-actions__label">Read</span>
          </button>

          <span class="detail-actions__divider" aria-hidden="true" />

          <button
            type="button"
            class="detail-actions__btn detail-actions__btn--download"
            :disabled="downloadState !== 'idle'"
            @click="download"
          >
            <AppIcon name="download" size="24px" />
            <span class="detail-actions__label">{{ downloadLabel }}</span>
          </button>

          <span class="detail-actions__divider" aria-hidden="true" />

          <button
            type="button"
            class="detail-actions__btn detail-actions__btn--favorite"
            :class="{ 'is-active': isFavorited }"
            :disabled="favoritePending"
            :aria-pressed="isFavorited"
            @click="toggleFavorite"
          >
            <AppIcon
              :key="favPopKey"
              :name="isFavorited ? 'heart' : 'heart-outline-primary'"
              size="24px"
              :class="{ 'detail-actions__heart--pop': favPopKey > 0 }"
            />
            <span class="detail-actions__label">
              {{ isFavorited ? 'Favorited' : 'Favorite' }}
            </span>
          </button>

          <span class="detail-actions__divider" aria-hidden="true" />

          <button
            type="button"
            class="detail-actions__btn detail-actions__btn--share"
            @click="share"
          >
            <AppIcon name="share-primary" size="24px" />
            <span class="detail-actions__label">Share</span>
          </button>
        </section>

        <!-- ③ Tags — namespace-grouped rows (`gallery_detail_tags.xml`) -->
        <section class="detail-tags" aria-label="Gallery tags">
          <template v-if="tagGroups.length > 0">
            <div v-for="group in tagGroups" :key="group.namespace" class="tag-row">
              <span class="tag-row__ns" :title="group.namespace">{{ group.namespace }}</span>
              <div class="tag-row__tags">
                <TagChip
                  v-for="tag in group.tags"
                  :key="tag"
                  :tag="tag"
                  :namespace="group.namespace"
                  @click="onTagClick"
                />
              </div>
            </div>
          </template>
          <p v-else class="detail-tags__empty">No tags</p>
        </section>

        <!-- ④ Comments (`gallery_detail_comments.xml`) -->
        <section class="detail-comments" aria-label="Gallery comments">
          <p v-if="commentsStatus" class="detail-comments__status">{{ commentsStatus }}</p>
          <CommentList
            :comments="comments"
            :loading="commentsLoading"
            :posting="posting"
            :voting-id="votingId"
            @submit="onSubmitComment"
            @vote="onVoteComment"
          />
        </section>
      </div>
    </main>

    <!-- Material snackbar-style feedback -->
    <Transition name="toast">
      <div v-if="toastMessage !== null" class="gallery-detail__toast" role="status">
        {{ toastMessage }}
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
/**
 * GalleryDetailView — S2, web replica of Android `GalleryDetailScene`
 * (`scene_gallery_detail.xml` + `gallery_detail_*.xml` includes):
 *
 *  ① colored header band (`galleryDetailHeaderBackgroundColor`) with the
 *     128×192dp thumbnail, white title (20sp), uploader, meta row,
 *     `RatingStars` and `CategoryChip`;
 *  ② the `action_card` (CardView.Normal) overlapping the band's bottom
 *     edge — Read / Download / Favorite / Share, icon over label;
 *  ③ namespace-grouped tag rows (`GalleryDetailScene.bindTags`);
 *  ④ comment cards with vote controls + post box (`bindComments`).
 *
 * Scene states replicate the FrameLayout ViewTransition: centered
 * `ProgressView` while loading, sadpanda tip with retry on failure.
 *
 * All colors resolve through `tokens.css` custom properties, so the three
 * Android themes (light / dark / black) work unchanged.
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { galleryApi } from '@/api/gallery'
import { commentApi, type CommentItem } from '@/api/comment'
import { favoriteApi } from '@/api/favorite'
import { downloadApi } from '@/api/download'
import { CATEGORY_BY_BIT } from '@/types/components'
import type { GalleryDetail } from '@/types'
import { rewriteSiteAssetUrl } from '@/utils/siteAsset'
import AppIcon from '@/components/atoms/AppIcon.vue'
import ProgressSpinner from '@/components/atoms/ProgressSpinner.vue'
import RatingStars from '@/components/atoms/RatingStars.vue'
import CategoryChip from '@/components/atoms/CategoryChip.vue'
import TagChip from '@/components/common/TagChip.vue'
import CommentList from '@/components/common/CommentList.vue'

const props = defineProps<{
  /** Gallery id from the `/gallery/:gid` route (router `props: true`). */
  gid: string | number
}>()

const router = useRouter()

/* ------------------------------------------------------------- state --- */
const gallery = ref<GalleryDetail | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
/** Monotonic guard so stale responses (superseded navigation) are dropped. */
let loadSeq = 0

const comments = ref<CommentItem[]>([])
const commentsLoading = ref(false)
const posting = ref(false)
const votingId = ref<number | null>(null)

const isFavorited = ref(false)
const favoritePending = ref(false)
/** Remount key that replays the heart pop animation on every toggle. */
const favPopKey = ref(0)
const downloadState = ref<'idle' | 'busy' | 'done'>('idle')

const toastMessage = ref<string | null>(null)
let toastTimer: ReturnType<typeof setTimeout> | undefined

/* ---------------------------------------------------------- derived --- */
const galleryId = computed(() => Number(props.gid))
/** Token from the entry link (?token=): lets the backend fetch the detail
 *  straight from the site when the gid is not in local history. */
const route = useRoute()
const entryToken = computed(() => (typeof route.query.token === 'string' ? route.query.token : undefined))

/**
 * R4-9: the cover `thumb` may point at the unresolvable Gallery Site host
 * (`e-hentai.org` family); rewrite those through the server's same-origin
 * image proxy so the cover actually loads. Non-site URLs pass through.
 */
const coverSrc = computed(() =>
  gallery.value ? rewriteSiteAssetUrl(gallery.value.thumb) : '',
)

/** Numeric `SiteConfig` category bit → `GalleryCategory` key. */
const categoryKey = computed(() =>
  gallery.value ? CATEGORY_BY_BIT[gallery.value.category] : undefined,
)

interface TagGroup {
  namespace: string
  tags: string[]
}

/** Flat `TagInfo[]` → namespace-grouped rows (Android `GalleryTagGroup[]`). */
const tagGroups = computed<TagGroup[]>(() => {
  const groups = new Map<string, string[]>()
  for (const info of gallery.value?.tags ?? []) {
    const ns = info.namespace?.trim() || 'temp'
    const bucket = groups.get(ns)
    if (bucket) {
      bucket.push(info.tag)
    } else {
      groups.set(ns, [info.tag])
    }
  }
  return Array.from(groups, ([namespace, tags]) => ({ namespace, tags }))
})

/** Android `comments_text` status line ("No comments" / count). */
const commentsStatus = computed(() => {
  if (commentsLoading.value) return ''
  const n = comments.value.length
  return n === 0 ? 'No comments' : `${n} comment${n === 1 ? '' : 's'}`
})

const downloadLabel = computed(() => {
  switch (downloadState.value) {
    case 'busy':
      return 'Adding…'
    case 'done':
      return 'Downloaded'
    default:
      return 'Download'
  }
})

/* ---------------------------------------------------------- loading --- */
async function load() {
  const seq = ++loadSeq
  loading.value = true
  error.value = null
  let detail: GalleryDetail | null = null
  try {
    detail = await galleryApi.getDetail(galleryId.value, entryToken.value)
    if (seq !== loadSeq) return
    gallery.value = detail
    isFavorited.value = (detail.favoriteSlot ?? -1) >= 0
  } catch (e) {
    if (seq !== loadSeq) return
    gallery.value = null
    error.value = e instanceof Error ? e.message : 'Failed to load gallery detail'
  } finally {
    if (seq === loadSeq) loading.value = false
  }
  void loadComments(detail?.comments)
}

/**
 * 评论数据源：优先用详情接口随画廊返回的站点真实评论（GalleryDetail.comments），
 * 缺省（本地历史详情 / 旧数据）时回退 Web 内存评论服务。
 */
async function loadComments(fromDetail?: CommentItem[] | null) {
  const seq = loadSeq
  commentsLoading.value = true
  try {
    if (fromDetail && fromDetail.length > 0) {
      if (seq !== loadSeq) return
      comments.value = fromDetail
      return
    }
    const res = await commentApi.listComments(galleryId.value)
    if (seq !== loadSeq) return
    comments.value = res.comments ?? []
  } catch (e) {
    console.error('Failed to load comments', e)
  } finally {
    if (seq === loadSeq) commentsLoading.value = false
  }
}

/* ----------------------------------------------------------- actions --- */
function goBack() {
  router.back()
}

/** Open the reader (Android `read` button → GalleryActivity). */
function read() {
  router.push(`/reader/${galleryId.value}`)
}

async function download() {
  const g = gallery.value
  if (!g || downloadState.value !== 'idle') return
  downloadState.value = 'busy'
  try {
    await downloadApi.add(g.gid, g.token, g.title, g.thumb)
    downloadState.value = 'done'
    showToast('Added to downloads')
  } catch (e) {
    console.error('Failed to add download', e)
    downloadState.value = 'idle'
    showToast('Download failed')
  }
}

/** Optimistic favorite toggle (Android heart / heart_outline swap). */
async function toggleFavorite() {
  const g = gallery.value
  if (!g || favoritePending.value) return
  favoritePending.value = true
  const next = !isFavorited.value
  isFavorited.value = next
  favPopKey.value++
  try {
    const res = next
      ? await favoriteApi.addFavorite(g.gid, g.token)
      : await favoriteApi.removeFavorite(g.gid, g.token)
    if (res.success) {
      showToast(next ? 'Favorited' : 'Removed from favorites')
    } else {
      isFavorited.value = !next
      showToast('Favorite update failed')
    }
  } catch (e) {
    console.error('Failed to toggle favorite', e)
    isFavorited.value = !next
    showToast('Favorite update failed')
  } finally {
    favoritePending.value = false
  }
}

/** Web Share API with clipboard fallback (Android `share` → system sheet). */
async function share() {
  const g = gallery.value
  if (!g) return
  const url = g.galleryUrl ?? `https://e-hentai.org/g/${g.gid}/${g.token}/`
  if (typeof navigator.share === 'function') {
    try {
      await navigator.share({ title: g.title, url })
    } catch {
      /* User dismissed the share sheet — not an error. */
    }
    return
  }
  try {
    await navigator.clipboard.writeText(url)
    showToast('Link copied to clipboard')
  } catch (e) {
    console.error('Failed to copy link', e)
    showToast('Unable to share')
  }
}

/** Tag tapped → `namespace:tag` keyword search (Android tag onClick). */
function onTagClick(tag: string, namespace: string | undefined) {
  const keyword = namespace ? `${namespace}:${tag}` : tag
  router.push({ path: '/', query: { keyword } })
}

/** Uploader tapped → uploader search (Android uploader onClick). */
function searchUploader() {
  const uploader = gallery.value?.uploader
  if (!uploader) return
  router.push({ path: '/', query: { keyword: `uploader:${uploader}` } })
}

/* ---------------------------------------------------------- comments --- */
async function onSubmitComment(text: string) {
  posting.value = true
  try {
    const res = await commentApi.postComment(galleryId.value, text)
    if (res.success) {
      await loadComments()
      showToast('Comment posted')
    } else {
      showToast('Failed to post comment')
    }
  } catch (e) {
    console.error('Failed to post comment', e)
    showToast('Failed to post comment')
  } finally {
    posting.value = false
  }
}

async function onVoteComment(commentId: number, vote: number) {
  if (votingId.value !== null) return
  votingId.value = commentId
  try {
    await commentApi.voteComment(galleryId.value, commentId, vote)
    await loadComments()
  } catch (e) {
    console.error('Failed to vote on comment', e)
    showToast('Vote failed')
  } finally {
    votingId.value = null
  }
}

/* ------------------------------------------------------------- toast --- */
function showToast(message: string) {
  toastMessage.value = message
  if (toastTimer !== undefined) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toastMessage.value = null
  }, 2200)
}

/* ------------------------------------------------------------- route --- */
/** Reset all per-gallery state before (re)loading a different gid. */
function reset() {
  gallery.value = null
  loading.value = true
  error.value = null
  comments.value = []
  commentsLoading.value = false
  posting.value = false
  votingId.value = null
  isFavorited.value = false
  favoritePending.value = false
  downloadState.value = 'idle'
}

// Reload whenever the route's gallery id changes — the view is reused for
// detail→detail navigation (uploader / tag links) without remounting.
watch(
  galleryId,
  () => {
    reset()
    void load()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  if (toastTimer !== undefined) clearTimeout(toastTimer)
})
</script>

<style scoped>
.gallery-detail {
  min-height: 100vh;
  /* Standalone PWA: the whole document clears the status bar / notch at the
     top (header row + detail band shift down together) and the home
     indicator at the bottom (comment post box at the document end keeps its
     clearance). Both resolve to 0 on devices without cutouts. */
  padding: var(--safe-area-top) 0 var(--safe-area-bottom);
  background-color: var(--color-bg);
  color: var(--text-color-primary);
}

/* ------------------------------------------------- scene states --- */
.gallery-detail__state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--keyline-margin);
  padding: 96px var(--keyline-margin);
}

.gallery-detail__sadpanda {
  color: var(--drawable-color-primary);
  opacity: 0.6;
}

/* Android tip view: 228dp wide, centered, medium text. */
.gallery-detail__tip {
  max-width: 228px;
  margin: 0;
  text-align: center;
  font-size: clamp(14px, var(--text-little-small), 18px);
  color: var(--text-color-secondary);
}

.gallery-detail__retry {
  min-height: 40px;
  padding: 0 24px;
  border: none;
  border-radius: var(--card-radius);
  background-color: transparent;
  color: var(--button-text-color);
  font-family: inherit;
  font-size: clamp(13px, var(--text-small), 16px);
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  cursor: pointer;
  transition: background-color 120ms var(--ease-decelerate-quart);
}

.gallery-detail__retry:hover {
  background-color: var(--color-divider);
}

.gallery-detail__retry:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}

/* -------------------------------------------- ① header band --- */
.detail-header {
  position: relative;
  /* color_bg: galleryDetailHeaderBackgroundColor, 4dp elevation */
  background: linear-gradient(
    180deg,
    var(--color-primary-dark) 0%,
    var(--gallery-detail-header-background) 100%
  );
  box-shadow: 0 2px 4px var(--shadow-color);
  padding-bottom: 48px; /* leaves room for the overlapping action card */
}

/* Soft top-right light bloom — token color via color-mix, theme-safe. */
.detail-header::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: radial-gradient(
    120% 90% at 85% -10%,
    color-mix(in srgb, var(--color-white) 16%, transparent),
    transparent 62%
  );
}

.detail-header__back {
  position: relative;
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  margin: 4px 0 0 4px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background-color: transparent;
  color: var(--gallery-detail-header-title-color);
  cursor: pointer;
  transition: background-color 120ms var(--ease-decelerate-quart);
}

.detail-header__back:hover {
  background-color: var(--color-divider);
}

.detail-header__back:focus-visible {
  outline: 2px solid var(--gallery-detail-header-title-color);
  outline-offset: -2px;
}

.detail-header__hero {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: var(--keyline-margin);
  max-width: 840px;
  margin: 0 auto;
  padding: var(--spacing) var(--keyline-margin) 0;
}

/* FixedThumb 128×192dp — fixed across viewports (responsive-strategy §5). */
.detail-header__thumb {
  flex: 0 0 var(--thumb-detail-width);
  width: var(--thumb-detail-width);
  height: var(--thumb-detail-height);
  overflow: hidden;
  background-color: var(--black-overlay);
  box-shadow: 0 2px 6px var(--shadow-color);
}

.detail-header__thumb img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 300ms var(--ease-decelerate-quint);
}

.detail-header__thumb:hover img {
  transform: scale(1.04);
}

.detail-header__info {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--spacing);
  min-width: 0;
  flex: 1;
  padding-top: 4px;
}

/* text_little_large (20sp) — fluid per responsive-strategy §2.1 */
.detail-header__title {
  margin: 0;
  font-size: clamp(17px, var(--text-little-large), 24px);
  font-weight: 500;
  line-height: 1.3;
  color: var(--gallery-detail-header-title-color);
  overflow-wrap: break-word;
  /* Android: maxLines 5 + end ellipsize */
  display: -webkit-box;
  -webkit-line-clamp: 5;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.detail-header__title-jpn {
  margin: 0;
  font-size: clamp(13px, var(--text-small), 16px);
  line-height: 1.4;
  /* Secondary text on the colored band: header title color stepped down,
     which equals --text-color-secondary on the dark/black bands. */
  color: var(--gallery-detail-header-title-color);
  opacity: 0.72;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.detail-header__uploader {
  max-width: 100%;
  padding: 0;
  border: none;
  background: none;
  font-family: inherit;
  font-size: clamp(13px, var(--text-small), 16px);
  color: var(--color-accent);
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
  transition: filter 120ms var(--ease-decelerate-quart);
}

.detail-header__uploader:hover {
  text-decoration: underline;
  filter: brightness(1.15);
}

.detail-header__uploader:focus-visible {
  outline: 2px solid var(--gallery-detail-header-title-color);
  outline-offset: 2px;
}

.detail-header__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  margin: 0;
  font-size: clamp(11px, var(--text-super-small), 14px);
}

.detail-header__meta-item {
  display: flex;
  align-items: baseline;
  gap: 5px;
}

.detail-header__meta-item dt {
  color: var(--gallery-detail-header-title-color);
  opacity: 0.55;
}

.detail-header__meta-item dd {
  margin: 0;
  color: var(--gallery-detail-header-title-color);
  opacity: 0.9;
  font-variant-numeric: tabular-nums;
}

.detail-header__rating {
  display: flex;
  align-items: center;
  gap: var(--spacing);
}

.detail-header__rating-num {
  font-size: clamp(11px, var(--text-super-small), 14px);
  font-variant-numeric: tabular-nums;
  color: var(--gallery-detail-header-title-color);
  opacity: 0.9;
}

/* Android header category: bold + ALL CAPS (inherits into the chip). */
.detail-header__category {
  font-weight: 700;
  text-transform: uppercase;
}

/* -------------------------------------------- ② action bar card --- */
.gallery-detail__body {
  max-width: 840px;
  margin: 0 auto;
  padding: 0 var(--keyline-margin) var(--keyline-margin);
}

.detail-actions {
  display: flex;
  margin-top: -32px; /* pulls the card over the header band's bottom edge */
  position: relative;
  z-index: 1;
  overflow: hidden;
  background-color: var(--color-surface);
  border-radius: var(--card-radius);
  box-shadow: 0 var(--card-elevation) var(--card-max-elevation) var(--shadow-color);
}

.detail-actions__btn {
  flex: 1 1 0;
  min-width: 0;
  min-height: 48px; /* 48dp touch target (ButtonInCard minHeight) */
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 8px;
  border: none;
  background-color: transparent;
  font-family: inherit;
  font-size: clamp(11px, var(--text-super-small), 14px);
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  cursor: pointer;
  transition:
    background-color 120ms var(--ease-decelerate-quart),
    opacity 120ms var(--ease-decelerate-quart);
}

.detail-actions__btn:hover:not(:disabled) {
  background-color: var(--color-divider);
}

.detail-actions__btn:active:not(:disabled) {
  background-color: var(--color-surface-activated);
}

.detail-actions__btn:disabled {
  opacity: 0.55;
  cursor: default;
}

.detail-actions__btn:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

/* Android: download = textColorThemePrimary, read = textColorThemeAccent */
.detail-actions__btn--read {
  color: var(--color-accent-text, var(--text-color-theme-accent));
}

.detail-actions__btn--download {
  color: var(--text-color-theme-primary);
}

.detail-actions__btn--favorite {
  color: var(--text-color-primary);
}

.detail-actions__btn--favorite.is-active {
  color: var(--color-accent);
}

.detail-actions__btn--share {
  color: var(--text-color-primary);
}

/* 1dp dividerColor between in-card buttons, 8dp vertical inset */
.detail-actions__divider {
  flex: 0 0 1px;
  align-self: stretch;
  margin: var(--spacing) 0;
  background-color: var(--color-divider);
}

@keyframes heart-pop {
  0% {
    transform: scale(1);
  }
  45% {
    transform: scale(1.35);
  }
  100% {
    transform: scale(1);
  }
}

.detail-actions__heart--pop {
  animation: heart-pop 320ms var(--ease-decelerate-quart);
}

/* -------------------------------------------- ③ tags section --- */
.detail-tags {
  margin-top: var(--keyline-margin);
  padding-top: var(--keyline-margin);
  border-top: 1px solid var(--color-divider);
}

.tag-row {
  display: grid;
  grid-template-columns: clamp(72px, 22%, 104px) 1fr;
  gap: 4px 12px;
  align-items: start;
}

.tag-row + .tag-row {
  margin-top: var(--spacing);
}

.tag-row__ns {
  padding-top: 5px;
  font-size: clamp(11px, var(--text-super-small), 14px);
  line-height: 1.4;
  color: var(--text-color-secondary);
  text-align: right;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tag-row__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing);
  min-width: 0;
}

.detail-tags__empty {
  margin: 0;
  padding: var(--spacing) 0;
  text-align: center;
  font-size: clamp(13px, var(--text-small), 16px);
  color: var(--color-accent-text, var(--text-color-theme-accent));
}

/* -------------------------------------------- ④ comments section --- */
.detail-comments {
  margin-top: var(--keyline-margin);
  padding-top: var(--keyline-margin);
  border-top: 1px solid var(--color-divider);
}

/* Android comments_text: centered, textColorThemeAccent */
.detail-comments__status {
  margin: 0 0 var(--spacing);
  text-align: center;
  font-size: clamp(13px, var(--text-small), 16px);
  color: var(--color-accent-text, var(--text-color-theme-accent));
}

/* ------------------------------------------------------- toast --- */
.gallery-detail__toast {
  position: fixed;
  left: 50%;
  /* Clear the home indicator in standalone PWA mode (0 where absent). */
  bottom: calc(32px + var(--safe-area-bottom));
  transform: translateX(-50%);
  z-index: 300;
  max-width: calc(100vw - 32px);
  padding: 10px 16px;
  border-radius: var(--card-radius);
  background-color: var(--grey-850);
  color: var(--grey-100);
  font-size: clamp(13px, var(--text-small), 16px);
  box-shadow: 0 2px 8px var(--shadow-color);
}

.toast-enter-active,
.toast-leave-active {
  transition:
    opacity var(--duration-scene-opacity) var(--ease-decelerate-quart),
    transform var(--duration-scene-opacity) var(--ease-decelerate-quart);
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translate(-50%, 8px);
}

/* --------------------------------------- entrance choreography --- */
@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.detail-header__hero {
  opacity: 1;
  animation: rise var(--duration-scene-translate) var(--ease-decelerate-quint);
}

.gallery-detail__body > section {
  /* `backwards` (not `both`): natural state opacity 1, never stuck invisible
     when WebKit fails to run the staggered entrance (T-1 regression). */
  opacity: 1;
  animation: rise var(--duration-scene-translate) var(--ease-decelerate-quint) backwards;
}

.gallery-detail__body > section:nth-child(1) {
  animation-delay: 60ms;
}

.gallery-detail__body > section:nth-child(2) {
  animation-delay: 130ms;
}

.gallery-detail__body > section:nth-child(3) {
  animation-delay: 200ms;
}

@media (prefers-reduced-motion: reduce) {
  .detail-header__hero,
  .gallery-detail__body > section,
  .detail-actions__heart--pop {
    animation: none;
  }

  .detail-header__thumb img,
  .detail-actions__btn,
  .gallery-detail__toast {
    transition: none;
  }
}
</style>
