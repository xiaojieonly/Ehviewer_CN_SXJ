import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export type Theme = 'light' | 'dark' | 'black'

const THEMES: Theme[] = ['light', 'dark', 'black']
const STORAGE_KEY = 'ehviewer-theme'

/** Meta theme-color values per theme (matches --color-toolbar in tokens.css) */
const META_THEME_COLORS: Record<Theme, string> = {
  light: '#009688',
  dark: '#323232',
  black: '#000000',
}

function getSystemTheme(): Theme {
  if (typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: dark)').matches) {
    return 'dark'
  }
  return 'light'
}

function getStoredTheme(): Theme | null {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored && THEMES.includes(stored as Theme)) {
      return stored as Theme
    }
  } catch {
    // localStorage unavailable (SSR / privacy mode)
  }
  return null
}

function applyTheme(theme: Theme) {
  document.documentElement.setAttribute('data-theme', theme)

  // Update <meta name="theme-color"> for PWA / browser chrome
  let meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]')
  if (!meta) {
    meta = document.createElement('meta')
    meta.name = 'theme-color'
    document.head.appendChild(meta)
  }
  meta.content = META_THEME_COLORS[theme]
}

export const useThemeStore = defineStore('theme', () => {
  const currentTheme = ref<Theme>(getStoredTheme() ?? getSystemTheme())

  // Apply immediately on store creation
  applyTheme(currentTheme.value)

  // React to changes (sync flush so DOM updates are immediate)
  watch(currentTheme, (theme) => {
    applyTheme(theme)
    try {
      localStorage.setItem(STORAGE_KEY, theme)
    } catch {
      // ignore write failures
    }
  }, { flush: 'sync' })

  // Listen for system preference changes (only when no explicit user choice)
  if (typeof window !== 'undefined' && window.matchMedia) {
    const mql = window.matchMedia('(prefers-color-scheme: dark)')
    mql.addEventListener('change', (e) => {
      if (!getStoredTheme()) {
        currentTheme.value = e.matches ? 'dark' : 'light'
      }
    })
  }

  function setTheme(theme: Theme) {
    currentTheme.value = theme
  }

  /** Cycle light → dark → black → light */
  function toggleTheme() {
    const idx = THEMES.indexOf(currentTheme.value)
    currentTheme.value = THEMES[(idx + 1) % THEMES.length]
  }

  return {
    currentTheme,
    setTheme,
    toggleTheme,
  }
})
