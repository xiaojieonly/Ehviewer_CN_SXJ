import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface DownloadItem {
  gid: number
  token: string
  title: string
  thumb: string
  category: number
  state: number
  total: number
  done: number
}

const STORAGE_KEY = 'ehviewer_downloads'

export const useDownloadStore = defineStore('download', () => {
  const items = ref<DownloadItem[]>(loadFromStorage())

  const activeCount = computed(() => items.value.filter(i => i.state === 0).length)
  const completedCount = computed(() => items.value.filter(i => i.state === 1).length)
  const failedCount = computed(() => items.value.filter(i => i.state === 2).length)

  function loadFromStorage(): DownloadItem[] {
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      return raw ? JSON.parse(raw) : []
    } catch {
      return []
    }
  }

  function saveToStorage() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(items.value))
  }

  function addDownload(item: DownloadItem) {
    if (!items.value.find(i => i.gid === item.gid)) {
      items.value.push(item)
      saveToStorage()
    }
  }

  function removeDownload(gid: number) {
    items.value = items.value.filter(i => i.gid !== gid)
    saveToStorage()
  }

  function updateProgress(gid: number, done: number, total: number) {
    const item = items.value.find(i => i.gid === gid)
    if (item) {
      item.done = done
      item.total = total
      if (done >= total) item.state = 1
      saveToStorage()
    }
  }

  function clearAll() {
    items.value = []
    saveToStorage()
  }

  return {
    items,
    activeCount,
    completedCount,
    failedCount,
    addDownload,
    removeDownload,
    updateProgress,
    clearAll,
  }
})
