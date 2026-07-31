<template>
  <header class="app-header">
    <div class="header-content">
      <router-link to="/" class="logo">AnotherViewer</router-link>
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
/* Themed against design tokens so the header blends with the safe-area
   strip above it (which shows the view's --color-bg) in all three themes.
   --color-background-floating matches --color-bg in Light/Dark/Black. */
.app-header {
  background: var(--color-background-floating);
  border-bottom: 1px solid var(--color-divider);
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
  color: var(--color-primary);
  text-decoration: none;
}
.nav {
  display: flex;
  align-items: center;
  gap: 1rem;
}
.nav-link {
  color: var(--text-color-secondary);
  text-decoration: none;
  font-size: 0.9rem;
}
.nav-link:hover {
  color: var(--color-primary);
}
.user-info {
  color: var(--text-color-secondary);
}
.btn-logout {
  padding: 0.3rem 0.8rem;
  border: 1px solid var(--color-divider);
  border-radius: 4px;
  background: var(--color-background-floating);
  cursor: pointer;
  color: #e74c3c;
}
</style>
