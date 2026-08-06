import { computed, onMounted, ref, watch, type ComputedRef, type Ref } from 'vue'
import { filterSlotsApi, type FilterSlot } from '@/api/filterSlots'

export interface UseFilterSlots {
  /** 服务端槽位列表（挂载时加载，失败静默为空数组）。 */
  slots: Ref<FilterSlot[]>
  /** 当前选中的槽位 id；null 表示「全部」。 */
  activeSlotId: Ref<string | null>
  /** 当前选中的槽位实体（id 不在列表中时为 null）。 */
  activeSlot: ComputedRef<FilterSlot | null>
  /**
   * 选槽位：设置 activeSlotId 并清空 searchQuery。
   * 互斥双向：选槽位清搜索、输入搜索取消槽位（见下方 watch）。
   */
  selectSlot: (id: string | null) => void
}

/**
 * 筛选槽位（FilterSlot）三页共用状态：下载/收藏/历史列表页共享同一组
 * 命名正则预设，槽位与搜索框互斥——选择槽位时清空搜索词，输入搜索时
 * 自动取消当前槽位。
 */
export function useFilterSlots(searchQuery: Ref<string>): UseFilterSlots {
  const slots = ref<FilterSlot[]>([])
  const activeSlotId = ref<string | null>(null)

  const activeSlot = computed(
    () => slots.value.find((s) => s.id === activeSlotId.value) ?? null,
  )

  function selectSlot(id: string | null): void {
    activeSlotId.value = id
    searchQuery.value = ''
  }

  // 输入搜索取消槽位（互斥的另一半）。selectSlot 清空搜索会触发本 watch，
  // 但空串不满足非空条件，不会误取消刚选中的槽位。
  watch(searchQuery, (query) => {
    if (query && query.length > 0) activeSlotId.value = null
  })

  onMounted(async () => {
    try {
      slots.value = await filterSlotsApi.get()
    } catch (error) {
      console.error('[useFilterSlots] failed to load slots', error)
      slots.value = []
    }
  })

  return { slots, activeSlotId, activeSlot, selectSlot }
}
