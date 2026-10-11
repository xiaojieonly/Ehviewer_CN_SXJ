import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

export const routes: RouteRecordRaw[] = [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/LoginView.vue'),
    },
    {
      path: '/',
      name: 'Home',
      component: () => import('@/views/HomeView.vue'),
    },
    {
      path: '/gallery/:gid',
      name: 'GalleryDetail',
      component: () => import('@/views/GalleryDetailView.vue'),
      props: true,
    },
    {
      path: '/reader/:gid/:page?',
      name: 'Reader',
      component: () => import('@/views/ReaderView.vue'),
      props: true,
    },
    {
      path: '/favorites',
      name: 'Favorites',
      component: () => import('@/views/FavoriteView.vue'),
    },
    {
      path: '/history',
      name: 'History',
      component: () => import('@/views/HistoryView.vue'),
    },
    {
      path: '/downloads',
      name: 'Downloads',
      component: () => import('@/views/DownloadView.vue'),
    },
    {
      path: '/search',
      name: 'Search',
      component: () => import('@/views/SearchView.vue'),
    },
    {
      path: '/recycle',
      name: 'Recycle',
      component: () => import('@/views/RecycleView.vue'),
    },
    /**
     * A5-1: 统一设置面板 —— 原 /settings（偏好，preferencesApi）与 /admin
     * （服务器，settingsApi/backupApi 等）双面板合并。分组：
     *   偏好   = general / reader / privacy / transfer（路径不变）
     *   服务器 = 原 /admin 十页，整体迁至 /settings/server/*
     * `/settings` exact 不再路由级 redirect：窄屏渲染分组索引页
     * （SettingsIndex），宽屏由其 matchMedia 断点监听 replace 到默认子页
     * （跨 960px 时 change 补发，判定与 CSS 侧栏断点同寿命）。旧 /admin/*
     * 全部 redirect 兜底（见下方记录）。
     */
    {
      path: '/settings',
      component: () => import('@/views/settings/SettingsLayout.vue'),
      children: [
        { path: '', name: 'SettingsIndex', component: () => import('@/views/settings/SettingsIndex.vue') },
        { path: 'general', name: 'SettingsGeneral', component: () => import('@/views/settings/GeneralSettings.vue') },
        { path: 'reader', name: 'SettingsReader', component: () => import('@/views/settings/ReaderSettings.vue') },
        { path: 'privacy', name: 'SettingsPrivacy', component: () => import('@/views/settings/PrivacySettings.vue') },
        { path: 'transfer', name: 'SettingsTransfer', component: () => import('@/views/settings/TransferSettings.vue') },
        { path: 'server/download', name: 'SettingsServerDownload', component: () => import('@/views/admin/AdminDownload.vue') },
        { path: 'server/filter-slots', name: 'SettingsServerFilterSlots', component: () => import('@/views/admin/AdminFilterSlots.vue') },
        { path: 'server/server', name: 'SettingsServerServer', component: () => import('@/views/admin/AdminServer.vue') },
        { path: 'server/backup', name: 'SettingsServerBackup', component: () => import('@/views/admin/AdminBackup.vue') },
        { path: 'server/devices', name: 'SettingsServerDevices', component: () => import('@/views/admin/AdminDevices.vue') },
        { path: 'server/eh', name: 'SettingsServerEh', component: () => import('@/views/admin/AdminEhSession.vue') },
        { path: 'server/access', name: 'SettingsServerAccess', component: () => import('@/views/admin/AdminAccess.vue') },
        { path: 'server/processing', name: 'SettingsServerProcessing', component: () => import('@/views/admin/AdminProcessing.vue') },
        { path: 'server/advanced', name: 'SettingsServerAdvanced', component: () => import('@/views/admin/AdminAdvanced.vue') },
        { path: 'server/about', name: 'SettingsServerAbout', component: () => import('@/views/admin/AdminAbout.vue') },
        // 文件完整性 Wave 3（F1 完整性面板 / P23 存储面板——后者文件由并行代理落地）。
        { path: 'server/integrity', name: 'SettingsServerIntegrity', component: () => import('@/views/admin/AdminIntegrity.vue') },
        { path: 'server/storage', name: 'SettingsServerStorage', component: () => import('@/views/admin/AdminStorage.vue') },
      ],
    },
    // A5-1: 旧 /admin 深链兜底（含 HomeView 的 /admin/eh EH 会话跳转）。
    { path: '/admin', redirect: '/settings/server/download' },
    { path: '/admin/download', redirect: '/settings/server/download' },
    { path: '/admin/filter-slots', redirect: '/settings/server/filter-slots' },
    { path: '/admin/server', redirect: '/settings/server/server' },
    { path: '/admin/backup', redirect: '/settings/server/backup' },
    { path: '/admin/devices', redirect: '/settings/server/devices' },
    { path: '/admin/eh', redirect: '/settings/server/eh' },
    { path: '/admin/access', redirect: '/settings/server/access' },
    { path: '/admin/processing', redirect: '/settings/server/processing' },
    { path: '/admin/advanced', redirect: '/settings/server/advanced' },
    { path: '/admin/about', redirect: '/settings/server/about' },
    // 文件完整性 Wave 3：/admin/integrity、/admin/storage 深链兜底。
    { path: '/admin/integrity', redirect: '/settings/server/integrity' },
    { path: '/admin/storage', redirect: '/settings/server/storage' },
    {
      path: '/smb-backup',
      name: 'SmbBackup',
      component: () => import('@/views/SmbBackupView.vue'),
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'NotFound',
      component: () => import('@/views/NotFoundView.vue'),
    },
  ]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

/**
 * /auth/status 免认证探测结果缓存（audit P2）：免认证部署的匿名会话此前每次
 * 导航都要发一次 /auth/status——TTL 内直接复用。登录/登出路径天然失效：
 * 拿到 token 后守卫短路（不查缓存），而登出/会话失效都会经过 Login 路由
 * （进入即清缓存，见守卫首行），下一跳重新拿真实状态。
 */
interface AuthStatusCacheEntry {
  authRequired: boolean
  cachedAt: number
}

const AUTH_STATUS_TTL_MS = 60_000
let authStatusCache: AuthStatusCacheEntry | null = null

/** 主动失效 /auth/status 缓存（登出/登录接线与测试用）。 */
export function invalidateAuthStatusCache(): void {
  authStatusCache = null
}

router.beforeEach(async (to, _from, next) => {
  if (to.name === 'Login') {
    // 登录页是所有登录/登出动作的必经之路：进页即弃缓存，离开后的第一跳
    // 拿真实状态（不破坏「登录后立即需要真实状态」）。
    invalidateAuthStatusCache()
    next()
    return
  }
  // 检查服务器是否要求登录
  const token = localStorage.getItem('token')
  if (token) {
    next()
    return
  }
  // 无 token：先看 TTL 缓存，再查服务器。
  const now = Date.now()
  if (authStatusCache && now - authStatusCache.cachedAt < AUTH_STATUS_TTL_MS) {
    if (!authStatusCache.authRequired) {
      next() // 服务器不要求登录（缓存），放行
      return
    }
    next({ name: 'Login' })
    return
  }
  try {
    const { authApi } = await import('@/api/auth')
    const status = await authApi.status()
    authStatusCache = { authRequired: status.authRequired, cachedAt: Date.now() }
    if (!status.authRequired) {
      next() // 服务器不要求登录，放行
      return
    }
  } catch {
    // 服务器不可达，走正常登录流程（失败结果不缓存）
  }
  next({ name: 'Login' })
})

export default router
