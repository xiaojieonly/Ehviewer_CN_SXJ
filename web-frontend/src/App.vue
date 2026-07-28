<template>
  <div class="app-layout">
    <NavigationDrawer
      v-model:open="drawerOpen"
      :items="navItems"
      :active-item-id="activeNavId"
      :username="authStore.username ?? undefined"
      :theme="themeStore.currentTheme"
      @select="handleNavSelect"
      @toggle-theme="themeStore.toggleTheme()"
    />
    <main class="app-content">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import NavigationDrawer from '@/components/layout/NavigationDrawer.vue'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import type { NavItem } from '@/types/components'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()

const drawerOpen = ref(false)

const navItems: NavItem[] = [
  { id: 'homepage', label: '首页', icon: 'homepage-black' },
  { id: 'subscription', label: '订阅', icon: 'eh-subscription-black' },
  { id: 'whats-hot', label: '热门', icon: 'fire-black' },
  { id: 'top-lists', label: '排行', icon: 'top-lists' },
  { id: 'favourites', label: '收藏', icon: 'heart-black' },
  { id: 'history', label: '历史', icon: 'history-black' },
  { id: 'downloads', label: '下载', icon: 'download-black' },
  { id: 'settings', label: '设置', icon: 'settings-black' },
]

const routeToNav: Record<string, string> = {
  '/': 'homepage',
  '/favorites': 'favourites',
  '/history': 'history',
  '/downloads': 'downloads',
  '/settings': 'settings',
  '/search': 'homepage',
}

const activeNavId = computed(() => routeToNav[route.path] ?? null)

function handleNavSelect(item: NavItem) {
  drawerOpen.value = false
  const paths: Record<string, string> = {
    homepage: '/',
    subscription: '/',
    'whats-hot': '/',
    'top-lists': '/',
    favourites: '/favorites',
    history: '/history',
    downloads: '/downloads',
    settings: '/settings',
  }
  const target = paths[item.id] ?? '/'
  if (route.path !== target) {
    router.push(target)
  }
}

// Close drawer on navigation (mobile)
watch(() => route.path, () => {
  drawerOpen.value = false
})

// Keep data-theme in sync
watch(
  () => themeStore.currentTheme,
  (theme) => {
    document.documentElement.setAttribute('data-theme', theme)
  },
  { immediate: true }
)
</script>

<style scoped>
.app-layout {
  min-height: 100vh;
}

.app-content {
  min-height: 100vh;
  transition: margin-left var(--duration-scene-translate) var(--ease-decelerate-quint);
}

/* Persistent drawer on wide viewports — content shifts right */
@media (min-width: 600px) {
  .app-content {
    margin-left: var(--drawer-width);
  }
}
</style>
