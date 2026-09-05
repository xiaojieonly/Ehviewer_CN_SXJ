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

    <main
      class="app-content"
      :class="{ 'app-content--full': !showChrome || isFocusedScene }"
    >
      <router-view v-slot="{ Component, route }">
        <KeepAlive :include="CACHED_VIEWS" :max="8">
          <component :is="Component" :key="cachedViewKey(Component, route)" />
        </KeepAlive>
      </router-view>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, type Component } from 'vue'
import { useRoute, useRouter, type RouteLocationNormalized } from 'vue-router'
import NavigationDrawer, { DEFAULT_NAV_ITEMS, NAV_TARGET_PATHS } from '@/components/layout/NavigationDrawer.vue'
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

/**
 * Routes that should hide the app chrome (login has its own layout, reader
 * is fullscreen, and the search / SMB backup scenes render their OWN
 * NavigationDrawer + hamburger — showing the shell drawer alongside would
 * produce the duplicated "double sidebar" quirk).
 */
const CHROME_HIDDEN_ROUTES = new Set(['Login', 'Reader', 'Search', 'SmbBackup'])

/**
 * KeepAlive 列表缓存（Android Scene 栈等价）：从阅读器/详情返回列表时，
 * 已加载的分页与滚动位置原位还原，不重新加载。详情/阅读器刻意不缓存
 * （每次进入都要新鲜数据——收藏态、阅读进度）。:max 防缓存膨胀。
 */
const CACHED_VIEWS = ['HomeView', 'DownloadView', 'FavoriteView', 'HistoryView', 'SearchView']

/**
 * 仅缓存视图按 fullPath 分实例（`/` 与 `/?feed=popular` 是两个独立列表，
 * 互不串状态）；阅读器读 route.params.page 但从不回写 URL，其余视图不
 * 缓存，不 key 也不会被 KeepAlive 停用。
 */
function cachedViewKey(Component: Component | null | undefined, route: RouteLocationNormalized): string | undefined {
  const name = (Component as { type?: { __name?: string } } | null | undefined)?.type?.__name
  return name && CACHED_VIEWS.includes(name) ? route.fullPath : undefined
}

const showChrome = computed(() => !CHROME_HIDDEN_ROUTES.has(route.name as string))

/**
 * Gallery detail is a focused scene (Android parity): it carries its own
 * back-arrow header, so the shell hamburger and its reserved left slot only
 * collide with it. The persistent wide-screen drawer stays available — only
 * the floating hamburger + slot are dropped.
 */
const isFocusedScene = computed(() => route.name === 'GalleryDetail')

const showHamburger = computed(() => showChrome.value && !isFocusedScene.value)

const routeToNav: Record<string, string> = {
  '/': 'homepage',
  '/favorites': 'favourite',
  '/history': 'history',
  '/downloads': 'downloads',
  '/settings': 'settings',
  '/admin': 'admin',
  '/search': 'homepage',
}

const activeNavId = computed(() => {
  if (route.path.startsWith('/settings')) return 'settings'
  if (route.path.startsWith('/admin')) return 'admin'
  return routeToNav[route.path] ?? null
})

function handleNavSelect(item: NavItem) {
  drawerOpen.value = false
  // B2: the id → path map lives in NavigationDrawer (NAV_TARGET_PATHS) —
  // the same table the drawer's `<a href>` renders, so link and handler can
  // never drift. subscription / whats_hot / top_lists share the home route
  // and select the feed via the `feed` query param (frozen feed contract).
  const target = NAV_TARGET_PATHS[item.id] ?? '/'
  // Compare the full path (query included) — path-only comparison would treat
  // /?feed=popular and /?feed=toplist as the same target and skip the push.
  if (route.fullPath !== target) {
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

/* Hamburger-visible viewports (<720px, plus landscape-short phones that are
   wide but under 480px tall): reserve a fixed slot under the floating
   hamburger (40px + 8px left edge + 8px gap, plus safe-area inset) so view
   headers / controls never collide with it. */
@media (max-width: 719px), (min-width: 720px) and (max-height: 479.98px) {
  .app-content {
    padding-left: calc(48px + var(--safe-area-left));
  }
}

/* Wide, tall-enough viewports: drawer panel is position:static (in-flow), so
   use flex to lay it out side-by-side with the content column.
   height:100vh ensures the drawer panel's height:100% resolves correctly. */
@media (min-width: 720px) and (min-height: 480px) {
  .app-layout {
    display: flex;
    height: 100vh;
  }

  .app-content {
    flex: 1 1 auto;
    min-width: 0;
    overflow-y: auto;
    padding-left: 0;
  }
}

/* Full-width content when the hamburger slot must not apply: chrome-less
   routes (login / reader) and the focused gallery-detail scene (own back
   header, hamburger hidden). */
.app-content--full {
  margin-left: 0 !important;
  padding-left: 0 !important;
}

/* Floating hamburger — narrow viewports only (drawer is modal <720px). */
.app-hamburger {
  position: fixed;
  top: calc(8px + var(--safe-area-top));
  left: calc(8px + var(--safe-area-left));
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

/* B4 触屏豁免：粘滞 hover 会把悬停底色永久卡在按钮上。 */
@media (hover: none) {
  .app-hamburger:hover {
    background: var(--color-background-floating);
  }
}

/* Hidden on wide, tall-enough viewports where the drawer is persistent
   (landscape-short phones keep the hamburger — see the padding rule). */
@media (min-width: 720px) and (min-height: 480px) {
  .app-hamburger {
    display: none;
  }
}
</style>
