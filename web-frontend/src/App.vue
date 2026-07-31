<template>
  <div class="app-layout">
    <NavigationDrawer
      v-if="showChrome"
      v-model:open="drawerOpen"
      :items="navItems"
      :active-item-id="activeNavId"
      :username="authStore.username ?? undefined"
      :theme="themeStore.currentTheme"
      @select="handleNavSelect"
      @toggle-theme="themeStore.toggleTheme()"
    />

    <!-- Hamburger trigger — visible only on narrow viewports where the drawer
         is modal (hidden ≥720px where the drawer is persistent). Positioned
         fixed so it floats above every view's content. -->
    <button
      v-if="showHamburger"
      type="button"
      class="app-hamburger"
      aria-label="打开导航菜单"
      @click="drawerOpen = true"
    >
      <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true">
        <path fill="currentColor" d="M3,6H21V8H3V6M3,11H21V13H3V11M3,16H21V18H3V16Z" />
      </svg>
    </button>

    <main class="app-content" :class="{ 'app-content--full': !showChrome }">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import NavigationDrawer, { DEFAULT_NAV_ITEMS } from '@/components/layout/NavigationDrawer.vue'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import type { NavItem } from '@/types/components'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()

const drawerOpen = ref(false)

/** Use the canonical 8-item list from NavigationDrawer (single source of truth). */
const navItems = DEFAULT_NAV_ITEMS

/** Routes that should hide the hamburger (login has its own layout, reader is fullscreen). */
const CHROME_HIDDEN_ROUTES = new Set(['Login', 'Reader'])

const showChrome = computed(() => !CHROME_HIDDEN_ROUTES.has(route.name as string))
const showHamburger = computed(() => showChrome.value)

const routeToNav: Record<string, string> = {
  '/': 'homepage',
  '/favorites': 'favourite',
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
    whats_hot: '/',
    top_lists: '/',
    favourite: '/favorites',
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

/* Wide viewports: drawer panel is position:static (in-flow), so use flex
   to lay it out side-by-side with the content column.
   height:100vh ensures the drawer panel's height:100% resolves correctly. */
@media (min-width: 720px) {
  .app-layout {
    display: flex;
    height: 100vh;
  }

  .app-content {
    flex: 1 1 auto;
    min-width: 0;
    overflow-y: auto;
  }
}

/* Full-width content when chrome (drawer) is hidden (login / reader). */
.app-content--full {
  margin-left: 0 !important;
}

/* Floating hamburger — narrow viewports only (drawer is modal <720px). */
.app-hamburger {
  position: fixed;
  top: calc(8px + var(--safe-area-top));
  left: 8px;
  z-index: 90;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: var(--color-background-floating);
  color: var(--drawable-color-primary);
  box-shadow: 0 1px 4px var(--shadow-color);
  cursor: pointer;
  transition: background 150ms linear;
}

.app-hamburger:hover {
  background: var(--color-surface);
}

.app-hamburger:active {
  background: var(--color-surface-activated);
}

/* Hidden on wide viewports where the drawer is persistent. */
@media (min-width: 720px) {
  .app-hamburger {
    display: none;
  }
}
</style>
