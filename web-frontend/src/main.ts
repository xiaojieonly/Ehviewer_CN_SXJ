import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './styles/tokens.css'
import './assets/styles/global.css'
// 隐私打码全局遮蔽（<html>.privacy-mask 作用域；开关见 utils/privacyMask.ts）
import './styles/privacy-mask.css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)

// Initialize theme store before mount so data-theme is set
// before the first render (avoids flash of wrong theme)
import { useThemeStore } from './stores/theme'
useThemeStore(pinia)

// 内容打码模式：服务端状态为权威（开码时后端对 API 响应统一脱敏，
// 对 Agent 等无头客户端同样生效）。启动即拉取，覆盖本地缓存值。
import { privacyApi } from './api/privacy'
import { setPrivacyMaskEnabled } from './utils/privacyMask'
privacyApi
  .getMask()
  .then(({ enabled }) => setPrivacyMaskEnabled(enabled))
  .catch(() => {
    // 拉取失败保持本地缓存值（下次重试）
  })

app.mount('#app')

// PWA service worker — production only (see register-sw.ts)
import { registerServiceWorker } from './register-sw'
registerServiceWorker()
