import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
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
      path: '/settings',
      component: () => import('@/views/settings/SettingsLayout.vue'),
      children: [
        { path: '', redirect: '/settings/general' },
        { path: 'general', name: 'SettingsGeneral', component: () => import('@/views/settings/GeneralSettings.vue') },
        { path: 'reader', name: 'SettingsReader', component: () => import('@/views/settings/ReaderSettings.vue') },
        { path: 'privacy', name: 'SettingsPrivacy', component: () => import('@/views/settings/PrivacySettings.vue') },
      ],
    },
    {
      path: '/admin',
      component: () => import('@/views/admin/AdminLayout.vue'),
      children: [
        { path: '', redirect: '/admin/download' },
        { path: 'download', name: 'AdminDownload', component: () => import('@/views/admin/AdminDownload.vue') },
        { path: 'server', name: 'AdminServer', component: () => import('@/views/admin/AdminServer.vue') },
        { path: 'access', name: 'AdminAccess', component: () => import('@/views/admin/AdminAccess.vue') },
        { path: 'processing', name: 'AdminProcessing', component: () => import('@/views/admin/AdminProcessing.vue') },
        { path: 'advanced', name: 'AdminAdvanced', component: () => import('@/views/admin/AdminAdvanced.vue') },
        { path: 'about', name: 'AdminAbout', component: () => import('@/views/admin/AdminAbout.vue') },
      ],
    },
    {
      path: '/smb-backup',
      name: 'SmbBackup',
      component: () => import('@/views/SmbBackupView.vue'),
    },
  ],
})

router.beforeEach(async (to, _from, next) => {
  if (to.name === 'Login') {
    next()
    return
  }
  // 检查服务器是否要求登录
  const token = localStorage.getItem('token')
  if (token) {
    next()
    return
  }
  // 无 token 时查询服务器是否需要认证
  try {
    const { authApi } = await import('@/api/auth')
    const status = await authApi.status()
    if (!status.authRequired) {
      next() // 服务器不要求登录，放行
      return
    }
  } catch {
    // 服务器不可达，走正常登录流程
  }
  next({ name: 'Login' })
})

export default router
