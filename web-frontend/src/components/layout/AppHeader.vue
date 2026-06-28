<template>
  <header class="app-header">
    <div class="header-content">
      <router-link to="/" class="logo">EhViewer</router-link>
      <div class="nav">
        <router-link to="/favorites" class="nav-link">Favorites</router-link>
        <router-link to="/history" class="nav-link">History</router-link>
        <router-link to="/downloads" class="nav-link">Downloads</router-link>
        <router-link to="/settings" class="nav-link">Settings</router-link>
        <span v-if="authStore.isAuthenticated" class="user-info">
          {{ authStore.username }}
        </span>
        <button v-if="authStore.isAuthenticated" @click="handleLogout" class="btn-logout">
          Logout
        </button>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.app-header {
  background: white;
  border-bottom: 1px solid #e0e0e0;
  padding: 0.75rem 1rem;
}
.header-content {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.logo {
  font-size: 1.5rem;
  font-weight: bold;
  color: #4a90d9;
  text-decoration: none;
}
.nav {
  display: flex;
  align-items: center;
  gap: 1rem;
}
.nav-link {
  color: #555;
  text-decoration: none;
  font-size: 0.9rem;
}
.nav-link:hover {
  color: #4a90d9;
}
.user-info {
  color: #666;
}
.btn-logout {
  padding: 0.3rem 0.8rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  background: white;
  cursor: pointer;
  color: #e74c3c;
}
</style>
