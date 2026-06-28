<template>
  <div class="home">
    <AppHeader />
    <div class="content">
      <div class="search-bar">
        <input
          v-model="searchKeyword"
          type="text"
          placeholder="Search galleries..."
          @keyup.enter="doSearch"
        />
        <button @click="doSearch" class="btn-search">Search</button>
      </div>
      <GalleryGrid :items="galleries" :loading="loading" @select="goToDetail" />
      <div v-if="total > pageSize" class="pagination">
        <button :disabled="page === 0" @click="prevPage">Previous</button>
        <span>Page {{ page + 1 }} / {{ Math.ceil(total / pageSize) }}</span>
        <button :disabled="(page + 1) * pageSize >= total" @click="nextPage">Next</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { galleryApi } from '@/api/gallery'
import AppHeader from '@/components/layout/AppHeader.vue'
import GalleryGrid from '@/components/gallery/GalleryGrid.vue'
import type { GalleryInfo } from '@/types'

const router = useRouter()
const galleries = ref<GalleryInfo[]>([])
const loading = ref(false)
const searchKeyword = ref('')
const page = ref(0)
const pageSize = 20
const total = ref(0)

async function loadGalleries() {
  loading.value = true
  try {
    const res = await galleryApi.search(undefined, undefined, page.value, pageSize)
    galleries.value = res.data
    total.value = res.total
  } catch (e) {
    console.error('Failed to load galleries', e)
  } finally {
    loading.value = false
  }
}

async function doSearch() {
  page.value = 0
  loading.value = true
  try {
    const res = await galleryApi.search(searchKeyword.value || undefined, undefined, 0, pageSize)
    galleries.value = res.data
    total.value = res.total
  } catch (e) {
    console.error('Search failed', e)
  } finally {
    loading.value = false
  }
}

function goToDetail(gid: number) {
  router.push(`/gallery/${gid}`)
}

function prevPage() {
  if (page.value > 0) {
    page.value--
    loadGalleries()
  }
}

function nextPage() {
  if ((page.value + 1) * pageSize < total.value) {
    page.value++
    loadGalleries()
  }
}

onMounted(loadGalleries)
</script>

<style scoped>
.home {
  min-height: 100vh;
  background: #f5f5f5;
}
.content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 1rem;
}
.search-bar {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
}
.search-bar input {
  flex: 1;
  padding: 0.6rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 1rem;
}
.btn-search {
  padding: 0.6rem 1.2rem;
  background: #4a90d9;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 1rem;
  margin-top: 1rem;
}
.pagination button {
  padding: 0.4rem 1rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  background: white;
  cursor: pointer;
}
.pagination button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
