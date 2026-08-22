import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import GalleryDetailView from '../GalleryDetailView.vue'
import { galleryApi } from '@/api/gallery'
import { commentApi } from '@/api/comment'
import { favoriteApi } from '@/api/favorite'
import { downloadApi } from '@/api/download'
import type { CommentItem } from '@/api/comment'
import type { GalleryDetail } from '@/types'

const { pushMock } = vi.hoisted(() => ({ pushMock: vi.fn() }))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock }),
  useRoute: () => ({ query: {} }),
}))

vi.mock('@/api/gallery', () => ({
  galleryApi: { getDetail: vi.fn(), search: vi.fn(), feed: vi.fn(), getQuickSearches: vi.fn() },
}))

vi.mock('@/api/comment', () => ({
  commentApi: { listComments: vi.fn(), postComment: vi.fn(), voteComment: vi.fn() },
}))

vi.mock('@/api/favorite', () => ({
  favoriteApi: { addFavorite: vi.fn(), removeFavorite: vi.fn() },
}))

vi.mock('@/api/download', () => ({
  downloadApi: { add: vi.fn() },
}))

function makeDetail(overrides: Partial<GalleryDetail> = {}): GalleryDetail {
  return {
    gid: 42,
    token: 'tok42',
    title: 'Detail Gallery',
    titleJpn: '',
    thumb: '',
    category: 2,
    posted: '',
    uploader: 'someone',
    rating: 4,
    rated: false,
    simpleLanguage: '',
    simpleTags: [],
    thumbWidth: 0,
    thumbHeight: 0,
    pages: 10,
    favoriteSlot: -1,
    favoriteName: '',
    tags: [],
    imageUrl: '',
    ...overrides,
  }
}

function makeComment(id: number): CommentItem {
  return { id, uploader: `user${id}`, comment: `body ${id}`, time: '2026-08-01', score: 0 }
}

describe('GalleryDetailView (F6 评论加载失败态)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    pushMock.mockClear()
    vi.mocked(galleryApi.getDetail).mockResolvedValue(makeDetail())
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
  })

  /** Mount with the comment fetch failing once, then settle both loads. */
  async function mountWithFailingComments(): Promise<VueWrapper> {
    vi.mocked(commentApi.listComments).mockRejectedValueOnce(new Error('boom'))
    wrapper = mount(GalleryDetailView, { props: { gid: '42' } })
    await flushPromises()
    await flushPromises()
    return wrapper
  }

  it('shows an error placeholder with retry instead of faking "No comments" (F6)', async () => {
    await mountWithFailingComments()

    // 失败占位可见，且不再伪装「No comments」。
    expect(wrapper.find('[data-testid="comments-retry"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('评论加载失败')
    expect(wrapper.text()).not.toContain('No comments')
    // 状态行（计数）在失败期间隐藏。
    expect(wrapper.find('.detail-comments__status').exists()).toBe(false)
  })

  it('recovers to the comment list after a successful retry', async () => {
    await mountWithFailingComments()

    // 重试成功 → 占位消失、评论渲染、状态行恢复计数。
    vi.mocked(commentApi.listComments).mockResolvedValue({ comments: [makeComment(1)] })
    await wrapper.find('[data-testid="comments-retry"]').trigger('click')
    await flushPromises()
    await flushPromises()

    expect(wrapper.find('[data-testid="comments-retry"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('评论加载失败')
    expect(wrapper.find('.detail-comments__status').text()).toBe('1 comment')
  })

  it('keeps the count status line when comments load fine (对照)', async () => {
    vi.mocked(galleryApi.getDetail).mockResolvedValue(
      makeDetail({ comments: undefined }),
    )
    vi.mocked(commentApi.listComments).mockResolvedValue({
      comments: [makeComment(1), makeComment(2)],
    })
    wrapper = mount(GalleryDetailView, { props: { gid: '42' } })
    await flushPromises()
    await flushPromises()

    expect(wrapper.find('[data-testid="comments-retry"]').exists()).toBe(false)
    expect(wrapper.find('.detail-comments__status').text()).toBe('2 comments')
  })
})

/** Shared mount target for the T-F2 describe blocks below. */
let wrapper: VueWrapper | undefined

/** Resolve + reject handles for hand-driven API promises. */
interface Deferred<T> {
  promise: Promise<T>
  resolve: (value: T) => void
  reject: (reason?: unknown) => void
}

function deferred<T>(): Deferred<T> {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

async function mountDetail(overrides: Partial<GalleryDetail> = {}): Promise<VueWrapper> {
  vi.mocked(galleryApi.getDetail).mockResolvedValue(makeDetail(overrides))
  vi.mocked(commentApi.listComments).mockResolvedValue({ comments: [] })
  wrapper = mount(GalleryDetailView, { props: { gid: '42' } })
  await flushPromises()
  await flushPromises()
  return wrapper
}

describe('GalleryDetailView (T-F2) — download button state machine', () => {
  let d: Deferred<boolean>

  beforeEach(() => {
    pushMock.mockClear()
    d = deferred<boolean>()
    vi.mocked(downloadApi.add).mockReturnValue(d.promise)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.clearAllMocks()
  })

  it('flips to busy immediately and lands on done with a toast on success', async () => {
    wrapper = await mountDetail()
    const btn = wrapper.find('.detail-actions__btn--download')
    expect(btn.text()).toBe('Download')

    await btn.trigger('click')
    // Optimistic flip before the request settles: disabled + "Adding…".
    expect(btn.attributes('disabled')).toBeDefined()
    expect(wrapper.find('.detail-actions__btn--download').text()).toBe('Adding…')

    d.resolve(true)
    await flushPromises()

    const settled = wrapper.find('.detail-actions__btn--download')
    expect(settled.text()).toBe('Downloaded')
    expect(settled.attributes('disabled')).toBeDefined() // terminal 'done' stays non-idle
    expect(wrapper.find('.gallery-detail__toast').text()).toBe('Added to downloads')
    expect(downloadApi.add).toHaveBeenCalledWith(42, 'tok42', 'Detail Gallery', '')
  })

  it('falls back to idle with an error toast when the add fails', async () => {
    wrapper = await mountDetail()

    await wrapper.find('.detail-actions__btn--download').trigger('click')
    d.reject(new Error('disk full'))
    await flushPromises()

    const btn = wrapper.find('.detail-actions__btn--download')
    expect(btn.text()).toBe('Download')
    expect(btn.attributes('disabled')).toBeUndefined()
    expect(wrapper.find('.gallery-detail__toast').text()).toBe('Download failed')
  })

  it('ignores clicks while a request is already in flight', async () => {
    wrapper = await mountDetail()

    await wrapper.find('.detail-actions__btn--download').trigger('click')
    await wrapper.find('.detail-actions__btn--download').trigger('click')
    d.resolve(true)
    await flushPromises()

    expect(downloadApi.add).toHaveBeenCalledTimes(1)
  })
})

describe('GalleryDetailView (T-F2) — optimistic favorite toggle', () => {
  beforeEach(() => {
    pushMock.mockClear()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.clearAllMocks()
  })

  function favButton(w: VueWrapper) {
    return w.find('.detail-actions__btn--favorite')
  }

  it('flips the heart optimistically while the request is pending, then confirms', async () => {
    const d = deferred<{ success: boolean }>()
    vi.mocked(favoriteApi.addFavorite).mockReturnValue(d.promise)
    wrapper = await mountDetail({ favoriteSlot: -1 })

    expect(favButton(wrapper).attributes('aria-pressed')).toBe('false')
    await favButton(wrapper).trigger('click')

    // Optimistic flip happens BEFORE the API resolves.
    const btn = favButton(wrapper)
    expect(btn.attributes('aria-pressed')).toBe('true')
    expect(btn.classes()).toContain('is-active')
    expect(btn.text()).toContain('Favorited')
    expect(btn.attributes('disabled')).toBeDefined() // favoritePending guard

    d.resolve({ success: true })
    await flushPromises()

    expect(favButton(wrapper).attributes('aria-pressed')).toBe('true')
    expect(favButton(wrapper).attributes('disabled')).toBeUndefined()
    expect(wrapper.find('.gallery-detail__toast').text()).toBe('Favorited')
    expect(favoriteApi.addFavorite).toHaveBeenCalledWith(42, 'tok42')
  })

  it('rolls back the optimistic flip when the API reports success:false', async () => {
    vi.mocked(favoriteApi.addFavorite).mockResolvedValue({ success: false })
    wrapper = await mountDetail({ favoriteSlot: -1 })

    await favButton(wrapper).trigger('click')
    await flushPromises()

    expect(favButton(wrapper).attributes('aria-pressed')).toBe('false')
    expect(favButton(wrapper).classes()).not.toContain('is-active')
    expect(wrapper.find('.gallery-detail__toast').text()).toBe('Favorite update failed')
  })

  it('rolls back when the API rejects outright', async () => {
    vi.mocked(favoriteApi.addFavorite).mockRejectedValue(new Error('network'))
    wrapper = await mountDetail({ favoriteSlot: -1 })

    await favButton(wrapper).trigger('click')
    await flushPromises()

    expect(favButton(wrapper).attributes('aria-pressed')).toBe('false')
    expect(wrapper.find('.gallery-detail__toast').text()).toBe('Favorite update failed')
  })

  it('calls removeFavorite and flips off for an already-favorited gallery', async () => {
    vi.mocked(favoriteApi.removeFavorite).mockResolvedValue({ success: true })
    wrapper = await mountDetail({ favoriteSlot: 3 }) // slot ≥ 0 → starts favorited

    expect(favButton(wrapper).attributes('aria-pressed')).toBe('true')
    await favButton(wrapper).trigger('click')
    await flushPromises()

    expect(favoriteApi.removeFavorite).toHaveBeenCalledWith(42, 'tok42')
    expect(favButton(wrapper).attributes('aria-pressed')).toBe('false')
    expect(wrapper.find('.gallery-detail__toast').text()).toBe('Removed from favorites')
  })
})

describe('GalleryDetailView (T-F2) — comment posting success flow', () => {
  beforeEach(() => {
    pushMock.mockClear()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.clearAllMocks()
  })

  it('posts the trimmed draft, refreshes the list and toasts on success', async () => {
    vi.mocked(commentApi.postComment).mockResolvedValue({ success: true })
    // Detail carries site comments → the initial load never hits listComments.
    wrapper = await mountDetail({ comments: [makeComment(1)] })
    expect(commentApi.listComments).toHaveBeenCalledTimes(0)

    await wrapper.find('#comment-list-input').setValue('  hello world  ')
    await wrapper.find('.comment-list__submit').trigger('submit')
    await flushPromises()
    await flushPromises()

    expect(commentApi.postComment).toHaveBeenCalledWith(42, 'hello world')
    // Success path reloads the comment list from the source of truth.
    expect(commentApi.listComments).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.gallery-detail__toast').text()).toBe('Comment posted')
  })

  it('shows the failure toast without reloading when the post is rejected', async () => {
    vi.mocked(commentApi.postComment).mockResolvedValue({ success: false })
    wrapper = await mountDetail()

    await wrapper.find('#comment-list-input').setValue('nope')
    await wrapper.find('.comment-list__submit').trigger('submit')
    await flushPromises()

    expect(wrapper.find('.gallery-detail__toast').text()).toBe('Failed to post comment')
    expect(commentApi.listComments).toHaveBeenCalledTimes(1) // no refresh on failure
  })

  it('surfaces the same failure toast when the request throws', async () => {
    vi.mocked(commentApi.postComment).mockRejectedValue(new Error('boom'))
    wrapper = await mountDetail()

    await wrapper.find('#comment-list-input').setValue('nope')
    await wrapper.find('.comment-list__submit').trigger('submit')
    await flushPromises()

    expect(wrapper.find('.gallery-detail__toast').text()).toBe('Failed to post comment')
  })

  it('re-enables the form after posting completes', async () => {
    const d = deferred<{ success: boolean }>()
    vi.mocked(commentApi.postComment).mockReturnValue(d.promise)
    wrapper = await mountDetail()

    await wrapper.find('#comment-list-input').setValue('in flight')
    await wrapper.find('.comment-list__submit').trigger('submit')
    expect(wrapper.find('.comment-list__input').attributes('disabled')).toBeDefined()

    d.resolve({ success: true })
    await flushPromises()
    await flushPromises()

    expect(wrapper.find('.comment-list__input').attributes('disabled')).toBeUndefined()
  })
})

describe('GalleryDetailView (T-F2) — share with clipboard fallback', () => {
  beforeEach(() => {
    pushMock.mockClear()
    // happy-dom has no Web Share API — pin it explicitly per test.
    Object.defineProperty(navigator, 'share', { configurable: true, value: undefined, writable: true })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    Object.defineProperty(navigator, 'share', { configurable: true, value: undefined })
    delete (navigator as { share?: unknown }).share
    vi.clearAllMocks()
  })

  it('copies the canonical gallery URL to the clipboard when Web Share is unavailable', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
    wrapper = await mountDetail({ galleryUrl: undefined })

    await wrapper.find('.detail-actions__btn--share').trigger('click')
    await flushPromises()

    // No galleryUrl in the detail → built from gid/token.
    expect(writeText).toHaveBeenCalledWith('https://e-hentai.org/g/42/tok42/')
    expect(wrapper.find('.gallery-detail__toast').text()).toBe('Link copied to clipboard')
  })

  it('prefers the server-provided galleryUrl over the constructed one', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
    wrapper = await mountDetail({ galleryUrl: 'https://exhentai.org/g/42/tok42/' })

    await wrapper.find('.detail-actions__btn--share').trigger('click')
    await flushPromises()

    expect(writeText).toHaveBeenCalledWith('https://exhentai.org/g/42/tok42/')
  })

  it('degrades to "Unable to share" when the clipboard rejects', async () => {
    const writeText = vi.fn().mockRejectedValue(new Error('denied'))
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
    wrapper = await mountDetail()

    await wrapper.find('.detail-actions__btn--share').trigger('click')
    await flushPromises()

    expect(wrapper.find('.gallery-detail__toast').text()).toBe('Unable to share')
  })

  it('uses navigator.share when available and never touches the clipboard', async () => {
    const shareFn = vi.fn().mockResolvedValue(undefined)
    const writeText = vi.fn()
    Object.defineProperty(navigator, 'share', { configurable: true, value: shareFn, writable: true })
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
    wrapper = await mountDetail({ galleryUrl: 'https://exhentai.org/g/42/tok42/' })

    await wrapper.find('.detail-actions__btn--share').trigger('click')
    await flushPromises()

    expect(shareFn).toHaveBeenCalledWith({
      title: 'Detail Gallery',
      url: 'https://exhentai.org/g/42/tok42/',
    })
    expect(writeText).not.toHaveBeenCalled()
  })
})

describe('GalleryDetailView (T-F2) — tag tap navigation', () => {
  beforeEach(() => {
    pushMock.mockClear()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.clearAllMocks()
  })

  async function mountWithTags(): Promise<VueWrapper> {
    return mountDetail({
      tags: [
        { namespace: 'female', tag: 'big breasts' },
        { namespace: 'artist', tag: 'someone' },
      ],
    })
  }

  it('navigates to the namespaced keyword search when a tag chip is tapped', async () => {
    wrapper = await mountWithTags()
    const chips = wrapper.findAll('.tag-chip--clickable')

    expect(chips.length).toBe(2)
    await chips[0].trigger('click')
    expect(pushMock).toHaveBeenCalledWith({ path: '/', query: { keyword: 'female:big breasts' } })

    await chips[1].trigger('click')
    expect(pushMock).toHaveBeenCalledWith({ path: '/', query: { keyword: 'artist:someone' } })
  })
})

describe('GalleryDetailView (T-F2) — route param change reuses the component', () => {
  beforeEach(() => {
    pushMock.mockClear()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.clearAllMocks()
  })

  it('resets state and reloads without remounting when gid changes', async () => {
    wrapper = await mountDetail({ title: 'First Gallery' })
    expect(wrapper.find('.detail-header__title').text()).toBe('First Gallery')
    expect(galleryApi.getDetail).toHaveBeenCalledTimes(1)

    vi.mocked(galleryApi.getDetail).mockResolvedValue(makeDetail({ gid: 77, title: 'Second Gallery' }))
    await wrapper.setProps({ gid: '77' })

    // reset(): back into the loading scene, old content dropped.
    expect(wrapper.find('.gallery-detail__state').exists()).toBe(true)
    expect(wrapper.find('.detail-header__title').exists()).toBe(false)

    await flushPromises()
    await flushPromises()

    expect(wrapper.find('.detail-header__title').text()).toBe('Second Gallery')
    expect(galleryApi.getDetail).toHaveBeenCalledTimes(2)
    expect(galleryApi.getDetail).toHaveBeenLastCalledWith(77, undefined)
    // Comments follow the new gallery too.
    expect(commentApi.listComments).toHaveBeenLastCalledWith(77)
  })

  it('carries the favorited state reset across navigations', async () => {
    wrapper = await mountDetail({ favoriteSlot: 2 })
    expect(wrapper.find('.detail-actions__btn--favorite').attributes('aria-pressed')).toBe('true')

    vi.mocked(galleryApi.getDetail).mockResolvedValue(makeDetail({ gid: 9 }))
    await wrapper.setProps({ gid: '9' })
    await flushPromises()
    await flushPromises()

    expect(wrapper.find('.detail-actions__btn--favorite').attributes('aria-pressed')).toBe('false')
    expect(wrapper.find('.detail-actions__btn--download').text()).toBe('Download')
  })
})
