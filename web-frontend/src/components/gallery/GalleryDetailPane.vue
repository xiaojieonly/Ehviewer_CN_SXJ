<template>
  <div
    class="gallery-detail-pane"
    :class="{ 'gallery-detail-pane--pane': pane }"
    data-testid="gallery-detail-pane"
  >
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
          @click="emit('back')"
        >
          <AppIcon name="arrow-left-dark" size="24px" />
        </button>

        <div class="detail-header__hero">
          <!-- 128×192dp (2:3) detail thumbnail — `gallery_detail_thumb_*` -->
          <div class="detail-header__thumb">
            <img
              v-if="gallery.thumb"
              :src="coverSrc"
              :alt="displayTitle"
              width="128"
              height="192"
              loading="eager"
              decoding="async"
            />
          </div>

          <div class="detail-header__info">
            <h1 class="detail-header__title">{{ displayTitle }}</h1>
            <p
              v-if="gallery.titleJpn && !privacyMaskEnabled && showJpnSubtitle"
              class="detail-header__title-jpn"
            >
              {{ gallery.titleJpn }}
            </p>

            <!-- 隐私打码：上传者属敏感内容，一律隐藏。 -->
            <button
              v-if="gallery.uploader && !privacyMaskEnabled"
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
              <div v-if="showPagesRow" class="detail-header__meta-item">
                <dt>Pages</dt>
                <dd>{{ gallery.pages }}</dd>
              </div>
              <div v-if="gallery.simpleLanguage" class="detail-header__meta-item">
                <dt>Language</dt>
                <dd>{{ gallery.simpleLanguage }}</dd>
              </div>
            </dl>

            <div v-if="showRatingBlock" class="detail-header__rating">
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

        <!-- ③ Tags — namespace-grouped rows (`gallery_detail_tags.xml`).
             隐私打码：标签是内容关键词，一律不出（回退到空态占位）。 -->
        <section class="detail-tags" aria-label="Gallery tags">
          <template v-if="tagGroups.length > 0 && !privacyMaskEnabled">
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

        <!-- ④ Comments (`gallery_detail_comments.xml`).
             隐私打码：评论文本是内容，整段不出（回退到空态占位）。
             T1：整个评论区受 general.showGalleryComment 门控——v-if 在
             section 渲染层（含打码占位一并隐藏）；评论请求照发不跳过：
             prefs 加载时序与 loadComments 挂钩会引入「先隐藏后补拉」的
             补偿 watch，复杂度不值，幂等 GET 留着无副作用。 -->
        <section v-if="showCommentsSection" class="detail-comments" aria-label="Gallery comments">
          <template v-if="!privacyMaskEnabled">
            <p v-if="commentsStatus" class="detail-comments__status">{{ commentsStatus }}</p>
            <!-- F6: 加载失败不再伪装「No comments」——错误占位 + 重试入口。 -->
            <div v-if="commentsError" class="detail-comments__failed" role="alert">
              <span class="detail-comments__failed-text">评论加载失败</span>
              <button
                type="button"
                class="detail-comments__retry"
                data-testid="comments-retry"
                @click="reloadComments"
              >
                重试
              </button>
            </div>
            <CommentList
              :comments="comments"
              :loading="commentsLoading"
              :posting="posting"
              :voting-id="votingId"
              @submit="onSubmitComment"
              @vote="onVoteComment"
            />
          </template>
          <p v-else class="detail-comments__status">No comments</p>
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
 * GalleryDetailPane — the gallery detail body (S2), extracted from
 * GalleryDetailView.vue so two hosts can render the identical scene:
 *
 *  - 整页详情 GalleryDetailView（`/gallery/:gid` 深链，行为照旧）；
 *  - 双栏右栏 TwoPaneLayout（平板对齐 T3：宽屏列表页选中即原位预览）。
 *
 * Scene structure (web replica of Android `GalleryDetailScene`):
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
 * Host contract:
 *  - `gid`    — the gallery to load; changing it resets state and reloads
 *               in place (detail→detail navigation without remounting), so
 *               the two-pane right column updates without route changes.
 *  - `token`  — entry token: the full-page host forwards `?token=`（深链
 *               透传），the two-pane host forwards the selected row's token.
 *  - `pane`   — two-pane right-column mode: the pane becomes a flex child
 *               that scrolls itself（宿主列不滚，避免双滚动条）。
 *  - `back`   — the header back arrow no longer navigates by itself; the
 *               host decides（整页宿主 = goBack 深链兜底，双栏宿主 = 清除
 *               选中回到占位符）。
 *
 * All colors resolve through `tokens.css` custom properties, so the three
 * Android themes (light / dark / black) work unchanged.
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { isEhUnavailableError } from '@/api/client'
import { availability, loadAvailability, markDown } from '@/stores/availability'
import { galleryApi } from '@/api/gallery'
import { commentApi, type CommentItem } from '@/api/comment'
import { favoriteApi } from '@/api/favorite'
import { downloadApi } from '@/api/download'
import { CATEGORY_BY_BIT } from '@/types/components'
import type { GalleryDetail } from '@/types'
import { rewriteSiteAssetUrl } from '@/utils/siteAsset'
import { maskedTitle, privacyMaskEnabled } from '@/utils/privacyMask'
import { isJpnSubtitleVisible } from '@/utils/jpnSubtitle'
import { usePreferencesStore } from '@/stores/preferences'
import AppIcon from '@/components/atoms/AppIcon.vue'
import ProgressSpinner from '@/components/atoms/ProgressSpinner.vue'
import RatingStars from '@/components/atoms/RatingStars.vue'
import CategoryChip from '@/components/atoms/CategoryChip.vue'
import TagChip from '@/components/common/TagChip.vue'
import CommentList from '@/components/common/CommentList.vue'

const props = withDefaults(
  defineProps<{
    /** Gallery id（整页宿主 = 路由参数；双栏宿主 = 选中条目 gid）。 */
    gid: string | number
    /** Entry token：整页宿主透传 ?token=，双栏宿主透传行内 token。 */
    token?: string | null
    /** 双栏右栏形态：撑满宿主列并自滚（宿主列不滚）。 */
    pane?: boolean
  }>(),
  { token: undefined, pane: false },
)

const emit = defineEmits<{
  /** Header back arrow — host decides（整页 = 路由返回，双栏 = 清选中）。 */
  (e: 'back'): void
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
/** F6: 评论加载失败——占位提示替代「No comments」，提供重试入口。 */
const commentsError = ref(false)
/** EH 熔断（plan-2026-08-30 §0）：评论抓取失败时静默——顶栏提示已说明原因，
 *  不再显示「评论加载失败」，也不伪装「No comments」。 */
const commentsUnavailable = ref(false)
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

/**
 * T1/T2：详情页展示开关读 `general` 偏好（showGalleryPages / showGalleryRating /
 * showGalleryComment / showJpnTitle）。prefs 未加载时一律按「显示」渲染防闪没，
 * 加载完成后按服务器真实值——注意 showGalleryPages 与 showJpnTitle 的协议默认
 * 是 false（api/preferences.ts），与任务书里「默认 true」的表述冲突，此处以
 * API 文件为准：仅「未加载」这个过渡态按 true 兜底。
 */
const preferencesStore = usePreferencesStore()
const generalPrefs = computed(() => preferencesStore.prefs?.general)

/** 「页数」信息行（general.showGalleryPages）。 */
const showPagesRow = computed(() =>
  generalPrefs.value ? generalPrefs.value.showGalleryPages === true : true,
)

/** 评分块（general.showGalleryRating）。 */
const showRatingBlock = computed(() =>
  generalPrefs.value ? generalPrefs.value.showGalleryRating === true : true,
)

/** 评论区 section（general.showGalleryComment）。 */
const showCommentsSection = computed(() =>
  generalPrefs.value ? generalPrefs.value.showGalleryComment === true : true,
)

/** 日文标题副题（general.showJpnTitle，打码守卫在模板里另判）。 */
const showJpnSubtitle = computed(() => isJpnSubtitleVisible(generalPrefs.value))

/**
 * R4-9: the cover `thumb` may point at the unresolvable Gallery Site host
 * (`e-hentai.org` family); rewrite those through the server's same-origin
 * image proxy so the cover actually loads. Non-site URLs pass through.
 * 隐私打码不改 src——真实请求照发，像素由全局遮蔽样式隐藏。
 */
const coverSrc = computed(() =>
  gallery.value ? rewriteSiteAssetUrl(gallery.value.thumb) : '',
)

/** 隐私打码：标题 → 内容序列号（alt 文本同步打码，坏图不泄题）。 */
const displayTitle = computed(() =>
  gallery.value ? maskedTitle(gallery.value.title, gallery.value.gid) : '',
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
  if (commentsLoading.value || commentsError.value || commentsUnavailable.value) return ''
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
  // 偏好懒预热（HomeView/FavoriteView 同款守卫）：深链直达详情页时没有其他
  // 视图代为加载，T1/T2 开关会永远停在「未加载兜底」态。
  if (!preferencesStore.prefs && !preferencesStore.loading) {
    void preferencesStore.load()
  }
  const seq = ++loadSeq
  loading.value = true
  error.value = null
  let detail: GalleryDetail | null = null
  try {
    detail = await galleryApi.getDetail(galleryId.value, props.token ?? undefined)
    if (seq !== loadSeq) return
    if (!detail) {
      // 服务器无本地行且 EH 上游不可达时返回 200 null（P-C 短路语义）：
      // DOWN 熔断文案；否则保留「Gallery not found」常规语义。深链直入
      // 时 availability 尚未加载（state=null），先对齐一次服务器状态再断言。
      if (availability.state === null) {
        await loadAvailability()
        if (seq !== loadSeq) return
      }
      error.value =
        availability.state === 'down'
          ? 'EH 平台当前不可达，仅显示本地内容'
          : 'Gallery not found'
      return
    }
    gallery.value = detail
    // Android favoriteSlot 契约：-2=未收藏，-1=默认收藏夹，0-9=自定义槽位
    // （与 GalleryCard 的 `>= -1` 判定一致）。
    isFavorited.value = (detail.favoriteSlot ?? -2) >= -1
  } catch (e) {
    if (seq !== loadSeq) return
    gallery.value = null
    if (isEhUnavailableError(e)) {
      // EH 熔断（§0）：只读本地内容，提示位说明后台不可达（与横幅同文案）。
      markDown()
      error.value = 'EH 平台当前不可达，仅显示本地内容'
    } else {
      error.value = e instanceof Error ? e.message : 'Failed to load gallery detail'
    }
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
  commentsError.value = false
  commentsUnavailable.value = false
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
    if (seq !== loadSeq) return
    // EH 熔断：静默（不占位、不伪装 No comments）——横幅/文案已提示不可达。
    if (isEhUnavailableError(e)) commentsUnavailable.value = true
    else commentsError.value = true
  } finally {
    if (seq === loadSeq) commentsLoading.value = false
  }
}

/** F6: 评论加载失败后的重试（重新走完整数据源链路）。 */
function reloadComments() {
  void loadComments()
}

/* ----------------------------------------------------------- actions --- */

/** Open the reader (Android `read` button → GalleryActivity). */
function read() {
  const token = gallery.value?.token
  router.push({
    path: `/reader/${galleryId.value}`,
    query: token ? { token } : {},
  })
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

async function copyText(text: string): Promise<boolean> {
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch {
      /* fall through to legacy copy */
    }
  }
  try {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.setAttribute('readonly', '')
    ta.style.position = 'fixed'
    ta.style.left = '-9999px'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.focus()
    ta.select()
    ta.setSelectionRange(0, text.length)
    const ok = document.execCommand('copy')
    document.body.removeChild(ta)
    return ok
  } catch {
    return false
  }
}

/** Web Share API with clipboard fallback (Android `share` → system sheet). */
async function share() {
  const g = gallery.value
  if (!g) return
  // 隐私打码：站点真实地址（含 token）绝不离开本页——只分享本站 ID 链接
  // + 序号标题，与 GalleryCard.copyLink 同一地址语义。
  const url = privacyMaskEnabled.value
    ? `${window.location.origin}/gallery/${g.gid}`
    : (g.galleryUrl ?? `https://e-hentai.org/g/${g.gid}/${g.token}/`)
  const title = maskedTitle(g.title, g.gid)
  if (typeof navigator.share === 'function') {
    try {
      await navigator.share({ title, url })
      return
    } catch {
      /* User dismissed or share failed — fall back to copy. */
    }
  }
  const copied = await copyText(url)
  if (copied) {
    showToast('Link copied to clipboard')
  } else {
    window.prompt('Copy this link', url)
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

/* ------------------------------------------------------------- gid ---- */
/** Reset all per-gallery state before (re)loading a different gid. */
function reset() {
  gallery.value = null
  loading.value = true
  error.value = null
  comments.value = []
  commentsLoading.value = false
  commentsUnavailable.value = false
  posting.value = false
  votingId.value = null
  isFavorited.value = false
  favoritePending.value = false
  downloadState.value = 'idle'
}

// Reload whenever the gallery id changes — both hosts reuse the instance for
// detail→detail switches（整页 = 路由参数变化，双栏 = 选中项变化）without
// remounting.
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
.gallery-detail-pane {
  min-height: 100%;
  background-color: var(--color-bg);
  color: var(--text-color-primary);
}

/* 双栏右栏形态：flex 子项撑满宿主列、自滚（宿主列 overflow hidden，避免
   双滚动条）；整页形态由 .gallery-detail 宿主壳提供视口高度与安全区。 */
.gallery-detail-pane--pane {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
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

/* Label can never bleed into the neighbouring button (320px phones give each
   of the 4 buttons <60px; "DOWNLOAD" at 12px bold caps is ~69px). */
.detail-actions__label {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Narrow phones: tighten the type so the four labels keep real gaps instead
   of ellipsizing (the ellipsis above is only the last-resort guard). */
@media (max-width: 479px) {
  .detail-actions__btn {
    gap: 4px;
    padding: 10px 2px;
    font-size: 11px;
    letter-spacing: 0;
  }
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

/* F6: comment load failure — inline placeholder + retry (ContentLayout's
   error-state pattern scaled to a section). */
.detail-comments__failed {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing);
  margin-bottom: var(--spacing);
}

.detail-comments__failed-text {
  font-size: clamp(13px, var(--text-small), 16px);
  color: var(--text-color-secondary);
}

.detail-comments__retry {
  padding: 6px 18px;
  border: none;
  border-radius: var(--card-radius);
  background: var(--color-primary);
  color: var(--color-white);
  font-family: inherit;
  font-size: var(--text-small);
  cursor: pointer;
  transition: background 150ms linear;
}

.detail-comments__retry:active {
  background: var(--color-primary-dark);
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
