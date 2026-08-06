import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { defineComponent, nextTick, ref } from 'vue'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { useFilterSlots } from '../useFilterSlots'
import { filterSlotsApi, type FilterSlot } from '@/api/filterSlots'

vi.mock('@/api/filterSlots', () => ({
  filterSlotsApi: { get: vi.fn(), put: vi.fn() },
}))

const SLOTS: FilterSlot[] = [
  { id: 's1', name: '画风A', pattern: 'art' },
  { id: 's2', name: '汉化', pattern: '汉化' },
]

/** 在组件 setup 中运行 composable（onMounted 需要组件上下文）。 */
function mountComposable() {
  const searchQuery = ref('')
  let use!: ReturnType<typeof useFilterSlots>
  const wrapper: VueWrapper = mount(
    defineComponent({
      setup() {
        use = useFilterSlots(searchQuery)
        return { searchQuery }
      },
      template: '<div />',
    }),
  )
  return { wrapper, searchQuery, use: () => use }
}

describe('useFilterSlots', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    vi.mocked(filterSlotsApi.get).mockResolvedValue(SLOTS)
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
  })

  it('loads the slot list from the api on mount', async () => {
    const { wrapper: w, use } = mountComposable()
    wrapper = w
    await flushPromises()
    expect(filterSlotsApi.get).toHaveBeenCalledTimes(1)
    expect(use().slots.value).toEqual(SLOTS)
  })

  it('falls back to an empty list silently when loading fails', async () => {
    vi.mocked(filterSlotsApi.get).mockRejectedValue(new Error('network'))
    const { wrapper: w, use } = mountComposable()
    wrapper = w
    await flushPromises()
    expect(use().slots.value).toEqual([])
  })

  it('selectSlot activates the slot and clears the search query', async () => {
    const { wrapper: w, searchQuery, use } = mountComposable()
    wrapper = w
    await flushPromises()
    searchQuery.value = '某画风'
    use().selectSlot('s1')
    expect(use().activeSlotId.value).toBe('s1')
    expect(use().activeSlot.value).toEqual(SLOTS[0])
    expect(searchQuery.value).toBe('')

    use().selectSlot(null)
    expect(use().activeSlotId.value).toBeNull()
    expect(use().activeSlot.value).toBeNull()
  })

  it('typing a non-empty search query cancels the active slot', async () => {
    const { wrapper: w, searchQuery, use } = mountComposable()
    wrapper = w
    await flushPromises()
    use().selectSlot('s2')
    expect(use().activeSlotId.value).toBe('s2')

    searchQuery.value = 'abc'
    await nextTick()
    expect(use().activeSlotId.value).toBeNull()
  })
})
