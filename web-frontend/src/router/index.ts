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
      path: '/settings',
      name: 'Settings',
      component: () => import('@/views/SettingsView.vue'),
    },
    {
      path: '/smb-backup',
      name: 'SmbBackup',
      component: () => import('@/views/SmbBackupView.vue'),
    },
  ],
})

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  if (to.name !== 'Login' && !token) {
    next({ name: 'Login' })
  } else {
    next()
  }
})

export default router
