<template>
  <div class="favorite-view">
    <AppHeader />
    <div class="content">
      <h2>Favorites</h2>
      <div v-if="loading" class="loading">Loading...</div>
      <div v-else-if="favorites.length === 0" class="empty">No favorites yet</div>
      <div v-else class="favorites-grid">
        <div
          v-for="item in favorites"
          :key="item.gid"
          class="favorite-card"
          @click="$router.push(`/gallery/${item.gid}`)"
        >
          <div class="thumb-wrapper">
            <img v-if="item.thumb" :src="item.thumb" :alt="item.title" class="thumb" loading="lazy" />
          </div>
          <div class="card-info">
            <h3 class="title">{{ item.title }}</h3>
            <div class="meta">
              <span class="rating">★ {{ item.rating?.toFixed(1) }}</span>
              <span class="category">{{ item.category }}</span>
            </div>
          </div>
        </div>
      </div>
      <div v-if="totalPages > 1" class="pagination">
        <button :disabled="currentPage <= 1" @click="loadPage(currentPage - 1)">Previous</button>
        <span>Page {{ currentPage }} / {{ totalPages }}</span>
        <button :disabled="currentPage >= totalPages" @click="loadPage(currentPage + 1)">Next</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { favoriteApi, type FavoriteItem } from '@/api/favorite'
import AppHeader from '@/components/layout/AppHeader.vue'

const favorites = ref<FavoriteItem[]>([])
const loading = ref(false)
const currentPage = ref(1)
const totalPages = ref(1)

async function loadPage(page: number) {
  loading.value = true
  try {
    const res = await favoriteApi.listFavorites(0, page)
    favorites.value = res.favorites
    currentPage.value = res.currentPage
    totalPages.value = res.totalPages
  } catch (e) {
    console.error('Failed to load favorites', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => loadPage(1))
</script>

<style scoped>
.favorite-view {
  min-height: 100vh;
  background: #f5f5f5;
}
.content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 1rem;
}
.content h2 {
  margin-bottom: 1rem;
  color: #333;
}
.favorites-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 1rem;
}
.favorite-card {
  background: white;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
  cursor: pointer;
  transition: box-shadow 0.2s;
}
.favorite-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}
.thumb-wrapper {
  aspect-ratio: 1;
  background: #fafafa;
}
.thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.card-info {
  padding: 0.5rem;
}
.title {
  font-size: 0.85rem;
  margin: 0 0 0.3rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #333;
}
.meta {
  display: flex;
  justify-content: space-between;
  font-size: 0.75rem;
  color: #666;
}
.rating {
  color: #f39c12;
}
.loading, .empty {
  text-align: center;
  padding: 2rem;
  color: #666;
}
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 1rem;
  margin-top: 1.5rem;
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
