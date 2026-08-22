import { describe, it, expect, vi, beforeEach, afterEach, type Mock } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import FavoriteView from '../FavoriteView.vue'
import client from '@/api/client'
import type { FavoriteItem } from '@/api/favorite'
import { filterSlotsApi } from '@/api/filterSlots'
import { preferencesApi } from '@/api/preferences'

const { pushMock } = vi.hoisted(() => ({ pushMock: vi.fn() }))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock }),
}))

vi.mock('@/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn(), delete: vi.fn() },
}))

vi.mock('@/api/filterSlots', () => ({
  filterSlotsApi: { get: vi.fn(), put: vi.fn() },
}))

vi.mock('@/api/preferences', () => ({
  preferencesApi: { get: vi.fn(), update: vi.fn() },
}))

/**
 * FilterSlotBar stub — F1's component is thin (chips + select emit); the stub
 * keeps this spec decoupled from its implementation. Clicking a chip emits
 * `select(id)` exactly like the real one (「全部」= null).
 */
vi.mock('@/components/FilterSlotBar.vue', () => ({
  default: {
    name: 'FilterSlotBar',
    props: ['slots', 'activeId'],
    emits: ['select'],
    template: `
      <nav class="filter-slot-bar">
        <button type="button" class="filter-slot-bar__chip" :class="{ 'filter-slot-bar__chip--active': activeId === null }" @click="$emit('select', null)">全部</button>
        <button v-for="s in slots" :key="s.id" type="button" class="filter-slot-bar__chip" :class="{ 'filter-slot-bar__chip--active': activeId === s.id }" @click="$emit('select', s.id)">{{ s.name }}</button>
      </nav>`,
  },
}))

/** Named-regex filter slot fixture (A5d contract: {id, name, pattern}). */
const FILTER_SLOTS = [
  { id: 's1', name: 'Artist', pattern: 'artist' },
  { id: 's2', name: 'Doujin', pattern: 'doujin|doujinshi' },
]

function makeFavorite(gid: number, overrides: Partial<FavoriteItem> = {}): FavoriteItem {
  return {
    gid,
    token: `tok${gid}`,
    title: `Favorite ${gid}`,
    titleJpn: '',
    thumb: '',
    category: 2,
    rating: 4,
    uploader: 'uploader',
    posted: '',
    favoriteSlot: 0,
    ...overrides,
  }
}

describe('FavoriteView (搜索 + 筛选槽位 A5d)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    setActivePinia(createPinia())
    pushMock.mockClear()
    vi.mocked(filterSlotsApi.get).mockResolvedValue([])
    vi.mocked(preferencesApi.get).mockReturnValue(new Promise(() => {})) // never settles
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
  })

  /** Mount with one page of favorites served by the client.get mock. */
  async function mountFavorites(items: FavoriteItem[]): Promise<VueWrapper> {
    vi.mocked(client.get).mockResolvedValue({
      data: { favorites: items, totalPages: 1, currentPage: 1 },
    })
    wrapper = mount(FavoriteView)
    await flushPromises()
    await flushPromises()
    return wrapper
  }

  /** Click the filter-slot chip with the given name (「全部」= null). */
  async function clickFilterChip(name: string): Promise<void> {
    const chip = wrapper
      .findAll('.filter-slot-bar__chip')
      .find((c) => c.text() === name)!
    await chip.trigger('click')
    await flushPromises()
    await flushPromises()
  }

  it('loads the folder list through /favorite/list with slot/page params', async () => {
    await mountFavorites([makeFavorite(1), makeFavorite(2)])
    expect(client.get).toHaveBeenCalledWith('/favorite/list', {
      params: { slot: '0', page: '1' },
    })
    expect(wrapper.text()).toContain('Favorite 1')
  })

  it('switching the folder chip reloads with the new slot param', async () => {
    await mountFavorites([makeFavorite(1)])
    await wrapper.findAll('.slot-bar__chip').find((c) => c.text() === 'Favorites 3')!.trigger('click')
    await flushPromises()
    await flushPromises()
    expect(client.get).toHaveBeenLastCalledWith('/favorite/list', {
      params: { slot: '3', page: '1' },
    })
  })

  it('activates a filter slot and requests q=pattern&regex=true', async () => {
    vi.mocked(filterSlotsApi.get).mockResolvedValue(FILTER_SLOTS)
    await mountFavorites([makeFavorite(1)])
    expect(wrapper.findAll('.filter-slot-bar__chip')).toHaveLength(3) // 全部 + 2

    await clickFilterChip('Artist')
    expect(client.get).toHaveBeenLastCalledWith('/favorite/list', {
      params: { slot: '0', page: '1', q: 'artist', regex: 'true' },
    })
    // 互斥：选槽位清空搜索词。
    const input = wrapper.find('.search-bar__input').element as HTMLInputElement
    expect(input.value).toBe('')

    // 回到「全部」→ 恢复无过滤加载（不携带 q/regex）。
    await clickFilterChip('全部')
    expect(client.get).toHaveBeenLastCalledWith('/favorite/list', {
      params: { slot: '0', page: '1' },
    })
  })

  it('debounces a typed search to q=word and cancels the active slot', async () => {
    vi.useFakeTimers()
    vi.mocked(filterSlotsApi.get).mockResolvedValue(FILTER_SLOTS)
    await mountFavorites([makeFavorite(1), makeFavorite(2)])

    await clickFilterChip('Artist')
    await wrapper.find('.search-bar__input').setValue('futa')
    // 防抖 400ms 内不触发请求。
    await vi.advanceTimersByTimeAsync(200)
    expect(client.get).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    // 输入搜索取消槽位 → LIKE 搜索（无 regex 参数）。
    expect(client.get).toHaveBeenLastCalledWith('/favorite/list', {
      params: { slot: '0', page: '1', q: 'futa' },
    })
    const artistChip = wrapper
      .findAll('.filter-slot-bar__chip')
      .find((c) => c.text() === 'Artist')!
    expect(artistChip.classes()).not.toContain('filter-slot-bar__chip--active')
    vi.useRealTimers()
  })

  it('clear button restores the unfiltered list', async () => {
    vi.useFakeTimers()
    await mountFavorites([makeFavorite(1)])
    await wrapper.find('.search-bar__input').setValue('futa')
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()
    expect(client.get).toHaveBeenLastCalledWith('/favorite/list', {
      params: { slot: '0', page: '1', q: 'futa' },
    })

    await wrapper.find('.search-bar__clear').trigger('click')
    await flushPromises()
    expect(client.get).toHaveBeenLastCalledWith('/favorite/list', {
      params: { slot: '0', page: '1' },
    })
    vi.useRealTimers()
  })

  /* ---------------- F4 REGEX_INVALID 错误识别 --------------- */

  /** API 错误信封（{error:{code,message,traceId,status}}）的 axios 形状。 */
  function apiError(code: string): {
    response: { status: number; data: { error: { code: string; message: string; traceId: string; status: number } } }
  } {
    return {
      response: {
        status: 400,
        data: { error: { code, message: '正则表达式无效', traceId: '0123456789abcdef', status: 400 } },
      },
    }
  }

  it('names REGEX_INVALID when the backend rejects the filter pattern (F4)', async () => {
    vi.mocked(client.get).mockRejectedValue(apiError('REGEX_INVALID'))
    wrapper = mount(FavoriteView)
    await flushPromises()
    await flushPromises()

    // 首屏失败（无内容）→ 错误态 + 专属文案（不再是泛化的 Failed to load）。
    expect(wrapper.find('[data-testid="content-state-error"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('正则无效')
    // Toast teleports to body — assert on document.
    expect(document.querySelector('.toast')?.textContent).toContain('正则无效')
  })

  it('keeps the generic error tip for non-REGEX failures (F4)', async () => {
    vi.mocked(client.get).mockRejectedValue(new Error('boom'))
    wrapper = mount(FavoriteView)
    await flushPromises()
    await flushPromises()

    expect(wrapper.find('[data-testid="content-state-error"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('正则无效')
    expect(document.querySelector('.toast')).toBeNull()
  })

  /* ---------------- F5 loadingMore 卡死竞态 --------------- */

  it('resets loadingMore when an in-flight append is superseded by a replace (F5)', async () => {
    // Page 1 resolves; the page-2 append hangs until the test releases it.
    let releaseAppend: (value: unknown) => void = () => {}
    const getMock = client.get as unknown as Mock
    getMock.mockImplementation((_url: string, config?: { params: { page: string } }) => {
      if (config?.params.page === '1') {
        return Promise.resolve({
          data: { favorites: [makeFavorite(1)], totalPages: 2, currentPage: 1 },
        })
      }
      return new Promise((resolve) => {
        releaseAppend = resolve
      })
    })
    wrapper = mount(FavoriteView)
    await flushPromises()
    await flushPromises()
    expect(wrapper.find('[data-testid="content-state-content"]').exists()).toBe(true)

    // 滚动近底 → 触发 append（第 2 页，挂起中）。
    const scroller = wrapper.find('.fast-scroller__container').element as HTMLElement
    scroller.dispatchEvent(new Event('scroll'))
    await flushPromises()
    expect(wrapper.find('[data-testid="content-loading-more"]').exists()).toBe(true)

    // 飞行中的 append 被切换收藏夹标签（replace，seq 作废旧请求）取代。
    await wrapper.findAll('.slot-bar__chip').find((c) => c.text() === 'Favorites 3')!.trigger('click')
    await flushPromises()

    // 释放被作废的 append——loadingMore 必须复位（页脚 spinner 消失）。
    releaseAppend({ data: { favorites: [makeFavorite(2)], totalPages: 2, currentPage: 2 } })
    await flushPromises()
    expect(wrapper.find('[data-testid="content-loading-more"]').exists()).toBe(false)
  })
})
