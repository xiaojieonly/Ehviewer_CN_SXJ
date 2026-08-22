import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import GalleryDetailView from '../GalleryDetailView.vue'
import { galleryApi } from '@/api/gallery'
import { commentApi } from '@/api/comment'
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
