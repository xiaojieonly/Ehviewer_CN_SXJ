import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import AdminFilterSlots from '../admin/AdminFilterSlots.vue'
import { filterSlotsApi, type FilterSlot } from '@/api/filterSlots'

vi.mock('@/api/filterSlots', () => ({
  filterSlotsApi: { get: vi.fn(), put: vi.fn() },
}))

function slotsOf(count: number): FilterSlot[] {
  return Array.from({ length: count }, (_, i) => ({
    id: `s${i + 1}`,
    name: `槽位${i + 1}`,
    pattern: `pattern${i + 1}`,
  }))
}

describe('AdminFilterSlots (筛选槽位)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    vi.mocked(filterSlotsApi.get).mockResolvedValue([])
    vi.mocked(filterSlotsApi.put).mockImplementation(async (slots) => slots)
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
  })

  async function mountView() {
    wrapper = mount(AdminFilterSlots)
    await flushPromises()
    return wrapper
  }

  it('renders the title, description and the loaded slot list', async () => {
    vi.mocked(filterSlotsApi.get).mockResolvedValue([
      { id: 'a', name: '画风A', pattern: '^[Aa]rt' },
      { id: 'b', name: '汉化', pattern: '汉化' },
    ])
    const w = await mountView()
    expect(w.text()).toContain('筛选槽位')
    expect(w.text()).toContain('命名正则筛选，下载/收藏/历史页可一键套用 · 服务端存储，随备份导出')
    expect(w.findAll('.slot-row')).toHaveLength(2)
    expect(w.text()).toContain('画风A')
    expect(w.text()).toContain('^[Aa]rt')
    expect(w.text()).not.toContain('还没有筛选槽位')
  })

  it('shows the empty hint when no slots are configured', async () => {
    const w = await mountView()
    expect(w.text()).toContain('还没有筛选槽位')
  })

  it('adds a valid slot: calls put with the new list, echoes and clears the form', async () => {
    const w = await mountView()
    await w.find('input[aria-label="名称"]').setValue('画风A')
    await w.find('input[aria-label="正则表达式"]').setValue('art')
    await w.findAll('button').find((b) => b.text() === '添加')!.trigger('click')
    await flushPromises()

    expect(filterSlotsApi.put).toHaveBeenCalledTimes(1)
    const sent = vi.mocked(filterSlotsApi.put).mock.calls[0][0]
    expect(sent).toHaveLength(1)
    expect(sent[0]).toMatchObject({ name: '画风A', pattern: 'art' })
    expect(sent[0].id).toBeTruthy()

    expect(w.findAll('.slot-row')).toHaveLength(1)
    expect(w.text()).toContain('画风A')
    expect((w.find('input[aria-label="名称"]').element as HTMLInputElement).value).toBe('')
    expect((w.find('input[aria-label="正则表达式"]').element as HTMLInputElement).value).toBe('')
  })

  it('rejects an invalid regex with a red hint and never calls put', async () => {
    const w = await mountView()
    await w.find('input[aria-label="名称"]').setValue('坏正则')
    await w.find('input[aria-label="正则表达式"]').setValue('(')
    await w.findAll('button').find((b) => b.text() === '添加')!.trigger('click')

    expect(w.text()).toContain('正则表达式无效')
    expect(filterSlotsApi.put).not.toHaveBeenCalled()
    expect(w.findAll('.slot-row')).toHaveLength(0)
  })

  it('rejects an empty name without calling put', async () => {
    const w = await mountView()
    await w.find('input[aria-label="正则表达式"]').setValue('art')
    await w.findAll('button').find((b) => b.text() === '添加')!.trigger('click')

    expect(w.text()).toContain('名称不能为空')
    expect(filterSlotsApi.put).not.toHaveBeenCalled()
  })

  it('deletes a slot: calls put with the remaining list', async () => {
    vi.mocked(filterSlotsApi.get).mockResolvedValue(slotsOf(2))
    const w = await mountView()
    await w.find('[aria-label="删除 槽位1"]').trigger('click')
    await flushPromises()

    expect(filterSlotsApi.put).toHaveBeenCalledTimes(1)
    expect(vi.mocked(filterSlotsApi.put).mock.calls[0][0].map((s) => s.name)).toEqual(['槽位2'])
    expect(w.findAll('.slot-row')).toHaveLength(1)
  })

  it('disables the add button and shows the limit hint at 20 slots', async () => {
    vi.mocked(filterSlotsApi.get).mockResolvedValue(slotsOf(20))
    const w = await mountView()

    expect(w.text()).toContain('最多 20')
    const add = w.findAll('button').find((b) => b.text() === '添加')!
    expect(add.attributes('disabled')).toBeDefined()
  })

  it('rolls back and shows a snack when persistence fails', async () => {
    vi.mocked(filterSlotsApi.get).mockResolvedValue(slotsOf(1))
    vi.mocked(filterSlotsApi.put).mockRejectedValue(new Error('network'))
    const w = await mountView()
    await w.find('[aria-label="删除 槽位1"]').trigger('click')
    await flushPromises()

    expect(w.findAll('.slot-row')).toHaveLength(1)
    expect(w.text()).toContain('保存失败')
  })
})
