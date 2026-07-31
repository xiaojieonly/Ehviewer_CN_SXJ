import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import GalleryDetailView from '../GalleryDetailView.vue'
import { galleryApi } from '@/api/gallery'
import { commentApi } from '@/api/comment'
import type { GalleryDetail } from '@/types'

vi.mock('vue-router', () => ({
  useRouter: () => ({ back: vi.fn(), push: vi.fn() }),
}))

vi.mock('@/api/gallery', () => ({
  galleryApi: { getDetail: vi.fn() },
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

const GID = 12345
const TOKEN = '0123456789abcdef'
const GALLERY_URL = `https://e-hentai.org/g/${GID}/${TOKEN}/`

function galleryDetail(overrides: Partial<GalleryDetail> = {}): GalleryDetail {
  return {
    gid: GID,
    token: TOKEN,
    title: 'Sample gallery',
    titleJpn: '',
    thumb: '',
    category: 2,
    posted: '2026-01-01',
    uploader: '',
    rating: 4.5,
    rated: false,
    simpleLanguage: '',
    simpleTags: [],
    thumbWidth: 250,
    thumbHeight: 350,
    pages: 10,
    favoriteSlot: -1,
    favoriteName: '',
    galleryUrl: GALLERY_URL,
    tags: [],
    imageUrl: '',
    ...overrides,
  }
}

describe('GalleryDetailView share / copy link', () => {
  let wrapper: VueWrapper
  let writeText: ReturnType<typeof vi.fn>

  beforeEach(() => {
    // Share/copy must take the clipboard path: no Web Share API in tests.
    Object.defineProperty(navigator, 'share', {
      value: undefined,
      configurable: true,
    })
    writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
  })

  async function mountDetail(overrides: Partial<GalleryDetail> = {}) {
    vi.mocked(galleryApi.getDetail).mockResolvedValue(galleryDetail(overrides))
    vi.mocked(commentApi.listComments).mockResolvedValue({ comments: [] })
    wrapper = mount(GalleryDetailView, {
      props: { gid: GID },
    })
    await flushPromises()
    return wrapper
  }

  it('copies the server-provided galleryUrl to the clipboard on share', async () => {
    await mountDetail()

    await wrapper.find('.detail-actions__btn--share').trigger('click')
    await flushPromises()

    expect(writeText).toHaveBeenCalledTimes(1)
    // Drift guard: clipboard must receive g.galleryUrl verbatim.
    expect(writeText).toHaveBeenCalledWith(GALLERY_URL)
    expect(wrapper.find('.gallery-detail__toast').text()).toBe('Link copied to clipboard')
  })

  it('falls back to the canonical e-hentai URL when galleryUrl is absent', async () => {
    await mountDetail({ galleryUrl: undefined })

    await wrapper.find('.detail-actions__btn--share').trigger('click')
    await flushPromises()

    expect(writeText).toHaveBeenCalledTimes(1)
    expect(writeText).toHaveBeenCalledWith(`https://e-hentai.org/g/${GID}/${TOKEN}/`)
  })

  it('shows an error toast when the clipboard write fails', async () => {
    writeText.mockRejectedValue(new Error('clipboard blocked'))
    await mountDetail()

    await wrapper.find('.detail-actions__btn--share').trigger('click')
    await flushPromises()

    expect(writeText).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.gallery-detail__toast').text()).toBe('Unable to share')
  })
})
