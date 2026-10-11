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

    <main class="app-content">
      <!-- 实时连接状态横幅（WP8 任务 3）：断线 / 鉴权失败可见化。正常连接
           不占位（v-if），不挤列表布局。 -->
      <ConnectionBanner />
      <router-view v-slot="{ Component, route }">
        <KeepAlive :include="CACHED_VIEWS" :max="8">
          <component :is="Component" :key="cachedViewKey(Component, route)" />
        </KeepAlive>
      </router-view>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, type Component } from 'vue'
import { useRoute, useRouter, type RouteLocationNormalized } from 'vue-router'
import NavigationDrawer, { DEFAULT_NAV_ITEMS, NAV_TARGET_PATHS } from '@/components/layout/NavigationDrawer.vue'
import ConnectionBanner from '@/components/common/ConnectionBanner.vue'
import { useWebSocket, TOPIC_SYNC, type SyncUpdateEnvelope } from '@/composables/useWebSocket'
import { syncInvalidation } from '@/stores/syncInvalidation'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import type { NavItem } from '@/types/components'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()

const drawerOpen = ref(false)

/** Use the canonical 9-item list from NavigationDrawer (single source of truth). */
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
const CACHED_VIEWS = ['HomeView', 'DownloadView', 'FavoriteView', 'HistoryView', 'SearchView', 'RecycleView']

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
  '/recycle': 'recycle',
  '/settings': 'settings',
  '/search': 'homepage',
}

// A5-2: /admin 子树并入 /settings —— startsWith('/settings') 已覆盖
// /settings/server/*（原 /admin 十页），旧 /admin 由路由表 redirect 兜底。
const activeNavId = computed(() => {
  if (route.path.startsWith('/settings')) return 'settings'
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

/**
 * 实时对账（WP8 任务 2）：应用壳持有唯一的 /topic/sync 订阅——多端写操作
 * 广播 `sync.update`（payload.entities 列出被改动的实体），各列表视图经
 * syncInvalidation.consume(entity) 感知并静默重拉。订阅先于 connect 注册
 * （registry 记录后由 CONNECTED 回调的 resubscribeAll 补挂；断线重连自动
 * 重订阅）。App 壳不卸载，引用计数随进程存续。
 */
const ws = useWebSocket()

onMounted(() => {
  ws.subscribe<SyncUpdateEnvelope>(TOPIC_SYNC, (env) => {
    if (env?.type === 'sync.update') syncInvalidation.bump(env.payload?.entities ?? [])
  })
  ws.connect()
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
   wide but under 480px tall): the floating hamburger no longer reserves a
   whole content column. Each view's fixed top chrome row dodges it via
   `--hamburger-clearance` (tokens.css); the list scrolls under the
   hamburger FAB-style at full width. */

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
  }
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
