import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './styles/tokens.css'
import './assets/styles/global.css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)

// Initialize theme store before mount so data-theme is set
// before the first render (avoids flash of wrong theme)
import { useThemeStore } from './stores/theme'
useThemeStore(pinia)

app.mount('#app')

// PWA service worker — production only (see register-sw.ts)
import { registerServiceWorker } from './register-sw'
registerServiceWorker()
