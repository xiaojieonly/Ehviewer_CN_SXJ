<template>
  <nav class="filter-slot-bar" aria-label="筛选槽位">
    <button
      type="button"
      class="filter-slot-bar__chip"
      :class="{ 'filter-slot-bar__chip--active': activeId === null }"
      :aria-pressed="activeId === null"
      @click="$emit('select', null)"
    >
      全部
    </button>
    <button
      v-for="slot in slots"
      :key="slot.id"
      type="button"
      class="filter-slot-bar__chip"
      :class="{ 'filter-slot-bar__chip--active': activeId === slot.id }"
      :aria-pressed="activeId === slot.id"
      :title="slot.pattern"
      @click="$emit('select', slot.id)"
    >
      {{ slot.name }}
    </button>
  </nav>
</template>

<script setup lang="ts">
/**
 * FilterSlotBar — 筛选槽位选择条（下载/收藏/历史列表页共用）。
 * 「全部」chip + 各槽位 name chip；激活 chip 高亮（--active + aria-pressed）。
 * 风格参考 DownloadView 的 label-tabs（横向滚动条 + 主色高亮）。
 */
import type { FilterSlot } from '@/api/filterSlots'

defineProps<{
  slots: FilterSlot[]
  activeId: string | null
}>()

defineEmits<{
  (e: 'select', id: string | null): void
}>()
</script>

<style scoped>
.filter-slot-bar {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
  overflow-x: auto;
  padding: 8px max(var(--keyline-margin, 16px), 4px);
  background: var(--color-bg);
  border-bottom: 1px solid var(--color-divider);
  scrollbar-width: none;
}

.filter-slot-bar::-webkit-scrollbar {
  display: none;
}

.filter-slot-bar__chip {
  flex: 0 0 auto;
  padding: 6px 14px;
  border: 1px solid var(--color-divider);
  border-radius: 999px;
  background: transparent;
  color: var(--text-color-secondary);
  font-family: inherit;
  font-size: var(--text-super-small, 13px);
  font-weight: 500;
  letter-spacing: 0.02em;
  cursor: pointer;
  transition:
    color 160ms var(--ease-decelerate-quart),
    border-color 160ms var(--ease-decelerate-quart),
    background-color 160ms var(--ease-decelerate-quart);
}

.filter-slot-bar__chip:hover {
  color: var(--text-color-primary);
  border-color: var(--color-primary);
}

.filter-slot-bar__chip--active {
  background: color-mix(in srgb, var(--color-primary) 14%, transparent);
  border-color: var(--color-primary);
  color: var(--color-primary-text, var(--color-primary-dark));
  font-weight: 700;
}

.filter-slot-bar__chip:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}
</style>
