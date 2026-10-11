import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, DOMWrapper, type VueWrapper } from '@vue/test-utils'
import RecycleView from '../RecycleView.vue'
import { recycleApi } from '@/api/recycle'
import type { RecycleItem } from '@/api/recycle'

const { pushMock } = vi.hoisted(() => ({ pushMock: vi.fn() }))

// RecycleView 自身不用路由（无跳转），但 TwoPaneLayout 的 Esc/popstate 接线
// 会调 useRoute —— 提供最小 mock（HistoryView/DownloadView 同款）。
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock }),
  useRoute: () => ({ fullPath: '/recycle' }),
}))

vi.mock('@/api/recycle', () => ({
  recycleApi: { list: vi.fn(), restore: vi.fn(), purge: vi.fn() },
}))

/* ---------------------- 双栏断点桩（窄屏，HistoryView 同款） ---------------- */

type MqlListener = (event: MediaQueryListEvent) => void

function stubMatchMedia(matches: boolean): void {
  const listeners = new Set<MqlListener>()
  vi.stubGlobal(
    'matchMedia',
    vi.fn().mockImplementation((query: string) => ({
      matches,
      media: query,
      addEventListener: (_type: string, cb: MqlListener) => {
        listeners.add(cb)
      },
      removeEventListener: (_type: string, cb: MqlListener) => {
        listeners.delete(cb)
      },
    })),
  )
}

/* ------------------------------- fixtures --------------------------------- */

let deletedAtSeq = 0

function makeRecycleItem(overrides: Partial<RecycleItem> = {}): RecycleItem {
  deletedAtSeq += 1
  return {
    entity: 'favorites',
    key: deletedAtSeq,
    title: `Deleted ${deletedAtSeq}`,
    deletedAt: 1_728_600_000_000 + deletedAtSeq * 1000,
    remainingDays: 5,
    ...overrides,
  }
}

beforeEach(() => {
  stubMatchMedia(false)
  pushMock.mockClear()
  vi.mocked(recycleApi.restore).mockResolvedValue({ restored: 0, failed: 0 })
  vi.mocked(recycleApi.purge).mockResolvedValue({ purged: 0 })
})

afterEach(() => {
  wrapper?.unmount()
  vi.unstubAllGlobals()
  vi.clearAllMocks()
})

let wrapper: VueWrapper

async function mountRecycle(items: RecycleItem[], total = items.length) {
  // 每次调用返回新副本：组件的乐观移除会就地 splice items 数组（HistoryView
  // 分页 mock 同款语义），fixture 与重复调用（静默重拉）都不能共享实例。
  vi.mocked(recycleApi.list).mockImplementation(async () => ({
    items: [...items],
    total,
    page: 1,
    pageSize: 50,
  }))
  wrapper = mount(RecycleView)
  await flushPromises()
  await flushPromises()
  return wrapper
}

describe('RecycleView (WP8 回收站)', () => {
  it('空态文案：回收站为空 + 7 天保留说明', async () => {
    await mountRecycle([])
    const empty = wrapper.find('[data-testid="content-state-empty"]')
    expect(empty.exists()).toBe(true)
    expect(empty.text()).toContain('回收站为空。删除的收藏、历史、下载等记录会在这里保留 7 天。')
  })

  it('首屏 list(page=1,pageSize=50) 直传（page 1 起，无 -1 换算）', async () => {
    await mountRecycle([makeRecycleItem()])
    expect(recycleApi.list).toHaveBeenCalledWith(1, 50)
  })

  it('行渲染：badge 剩 N 天（≤1 天红色强调）、meta 实体 chip、subtitle 删除于', async () => {
    await mountRecycle([
      makeRecycleItem({ entity: 'favorites', title: 'A', remainingDays: 5 }),
      makeRecycleItem({ entity: 'quickSearches', title: 'B', remainingDays: 1 }),
    ])

    const rows = wrapper.findAll('.app-list-row')
    expect(rows).toHaveLength(2)

    const badges = wrapper.findAll('.recycle-list .recycle-remaining')
    expect(badges).toHaveLength(2)
    expect(badges[0].text()).toBe('剩 5 天')
    expect(badges[0].classes()).not.toContain('recycle-remaining--danger')
    expect(badges[1].text()).toBe('剩 1 天')
    expect(badges[1].classes()).toContain('recycle-remaining--danger')

    const chips = wrapper.findAll('[data-testid="recycle-entity-chip"]')
    expect(chips.map((c) => c.text())).toEqual(['收藏', '快速搜索'])

    const subtitles = wrapper.findAll('.app-list-row__subtitle')
    expect(subtitles[0].text()).toMatch(/^删除于 /)
  })

  it('搜索条客户端过滤当前页 title（不打 API）', async () => {
    await mountRecycle([
      makeRecycleItem({ title: 'Alpha Gallery' }),
      makeRecycleItem({ title: 'Beta Gallery' }),
    ])
    expect(recycleApi.list).toHaveBeenCalledTimes(1)

    await wrapper.find('.search-bar__input').setValue('beta')
    await flushPromises()
    expect(wrapper.findAll('.app-list-row')).toHaveLength(1)
    expect(wrapper.text()).toContain('Beta Gallery')
    expect(recycleApi.list).toHaveBeenCalledTimes(1)

    await wrapper.find('.search-bar__clear').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.app-list-row')).toHaveLength(2)
  })

  /* ---------------- 多选（choice mode）与 select-bar ---------------- */

  it('右键条目进入多选并预选该行；select-bar 含 全选/恢复/立即清除(danger)/退出 与计数', async () => {
    await mountRecycle([makeRecycleItem(), makeRecycleItem()])
    expect(wrapper.find('.select-bar').exists()).toBe(false)

    await wrapper.findAll('.app-list-row')[1].trigger('contextmenu')
    expect(wrapper.find('.select-bar').exists()).toBe(true)
    expect(wrapper.find('.select-bar__count').text()).toBe('共 2 条 · 已选 1 条')

    const buttons = wrapper.findAll('.select-bar__btn')
    expect(buttons.map((b) => b.text())).toEqual(['全选', '恢复', '立即清除'])
    expect(buttons[2].classes()).toContain('select-bar__btn--danger')

    await wrapper.find('.select-bar__close').trigger('click')
    expect(wrapper.find('.select-bar').exists()).toBe(false)
  })

  it('全选 → 恢复确认（普通）→ restore 携带 entity+key、乐观移除 + toast + 当前页静默重拉', async () => {
    const items = [makeRecycleItem(), makeRecycleItem()]
    await mountRecycle(items)
    await wrapper.find('.app-list-row').trigger('contextmenu')
    await wrapper.findAll('.select-bar__btn').find((b) => b.text() === '全选')!.trigger('click')
    expect(wrapper.find('.select-bar__count').text()).toBe('共 2 条 · 已选 2 条')

    await wrapper.findAll('.select-bar__btn').find((b) => b.text() === '恢复')!.trigger('click')
    const scrim = document.querySelector('.dialog-scrim')
    expect(scrim).not.toBeNull()
    expect(scrim!.querySelector('.dialog__message')!.textContent).toContain('将选中的 2 项恢复到原列表？')

    vi.mocked(recycleApi.restore).mockResolvedValue({ restored: 2, failed: 0 })
    await new DOMWrapper(scrim!.querySelector<HTMLButtonElement>('.dialog__btn--primary')!).trigger('click')
    await flushPromises()
    await flushPromises()

    expect(recycleApi.restore).toHaveBeenCalledWith([
      { entity: items[0].entity, key: items[0].key },
      { entity: items[1].entity, key: items[1].key },
    ])
    expect(document.querySelector('.dialog-scrim')).toBeNull()
    expect(document.querySelector('.toast')?.textContent).toContain('已恢复 2 项')
    // 乐观移除 + 当前页静默对账重拉（loadPage(currentPage, {silent:true})）——
    // mock 仍返回原 2 行，重拉后回到 content 态。
    expect(recycleApi.list).toHaveBeenCalledTimes(2)
    expect(recycleApi.list).toHaveBeenLastCalledWith(1, 50)
    expect(wrapper.findAll('.app-list-row')).toHaveLength(2)
  })

  it('restore failed>0 → toast 追加「N 项恢复失败」', async () => {
    await mountRecycle([makeRecycleItem()])
    await wrapper.find('.app-list-row').trigger('contextmenu')
    await wrapper.findAll('.select-bar__btn').find((b) => b.text() === '恢复')!.trigger('click')
    await flushPromises()

    vi.mocked(recycleApi.restore).mockResolvedValue({ restored: 0, failed: 1 })
    await new DOMWrapper(
      document.querySelector('.dialog-scrim')!.querySelector<HTMLButtonElement>('.dialog__btn--primary')!,
    ).trigger('click')
    await flushPromises()

    expect(document.querySelector('.toast')?.textContent).toContain('1 项恢复失败')
  })

  it('清除确认（danger）文案含「不可恢复」；purge 携带选中 items', async () => {
    const items = [makeRecycleItem(), makeRecycleItem()]
    await mountRecycle(items)
    await wrapper.find('.app-list-row').trigger('contextmenu')
    await wrapper.findAll('.select-bar__btn').find((b) => b.text() === '立即清除')!.trigger('click')

    const message = document.querySelector('.dialog__message')!.textContent ?? ''
    expect(message).toContain('立即清除选中的 1 项？清除后不可恢复。')
    expect(document.querySelector('.dialog__btn--danger')).not.toBeNull()

    vi.mocked(recycleApi.purge).mockResolvedValue({ purged: 1 })
    await new DOMWrapper(
      document.querySelector('.dialog-scrim')!.querySelector<HTMLButtonElement>('.dialog__btn--danger')!,
    ).trigger('click')
    await flushPromises()

    expect(recycleApi.purge).toHaveBeenCalledWith([{ entity: items[0].entity, key: items[0].key }])
    expect(document.querySelector('.toast')?.textContent).toContain('已清除 1 项')
  })

  it('FAB 清除到期 → purge() 无 items（body {}），确认文案含「不可恢复」', async () => {
    await mountRecycle([makeRecycleItem()])
    await wrapper
      .findAll('.fab--mini')
      .find((b) => b.attributes('aria-label') === '清除到期条目')!
      .trigger('click')

    const message = document.querySelector('.dialog__message')!.textContent ?? ''
    expect(message).toContain('立即清除全部到期条目？清除后不可恢复。')

    vi.mocked(recycleApi.purge).mockResolvedValue({ purged: 1 })
    await new DOMWrapper(
      document.querySelector('.dialog-scrim')!.querySelector<HTMLButtonElement>('.dialog__btn--danger')!,
    ).trigger('click')
    await flushPromises()

    expect(recycleApi.purge).toHaveBeenCalledTimes(1)
    // 缺省调用：items 参数为 undefined → API 层 body 为 {}。
    expect(vi.mocked(recycleApi.purge).mock.calls[0]?.[0]).toBeUndefined()
  })

  it('加载失败（无内容）→ 错误态 + 重试按钮', async () => {
    vi.mocked(recycleApi.list).mockRejectedValue(new Error('boom'))
    wrapper = mount(RecycleView)
    await flushPromises()
    await flushPromises()

    expect(wrapper.find('[data-testid="content-state-error"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('加载回收站失败')
  })

  /* ---------------- 分页条（同构历史/下载页） ---------------- */

  it('分页条：total > pageSize 显示，页码/条数切换直传', async () => {
    vi.mocked(recycleApi.list).mockImplementation(async (page = 1, pageSize = 50) => {
      const start = ((page as number) - 1) * (pageSize as number)
      const count = Math.max(0, Math.min(pageSize as number, 120 - start))
      return {
        items: Array.from({ length: count }, (_, i) => makeRecycleItem({ title: `R ${start + i + 1}` })),
        total: 120,
        page: page as number,
        pageSize: pageSize as number,
      }
    })
    wrapper = mount(RecycleView)
    await flushPromises()
    await flushPromises()

    const bar = wrapper.find('[data-testid="recycle-pagination"]')
    expect(bar.exists()).toBe(true)
    expect(bar.find('.pagination-bar__info').text()).toBe('第 1 / 3 页 · 120 条')

    await bar
      .findAll('.pagination-bar__page')
      .find((b) => b.text() === '3')!
      .trigger('click')
    await flushPromises()
    await flushPromises()

    expect(recycleApi.list).toHaveBeenLastCalledWith(3, 50)
    expect(wrapper.findAll('.app-list-row')).toHaveLength(20)
  })
})
