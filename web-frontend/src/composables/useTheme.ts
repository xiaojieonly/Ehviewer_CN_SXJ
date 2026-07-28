import { computed, toRef } from 'vue'
import { useThemeStore } from '@/stores/theme'
import type { Theme } from '@/stores/theme'

export interface ThemeColors {
  bg: string
  surface: string
  textPrimary: string
  textSecondary: string
  primary: string
  accent: string
  divider: string
  toolbar: string
}

/**
 * Composable wrapper around the theme store.
 * Provides reactive theme state and key colors for canvas/SVG usage.
 */
export function useTheme() {
  const store = useThemeStore()

  const theme = toRef(store, 'currentTheme')

  function setTheme(t: Theme) {
    store.setTheme(t)
  }

  function toggleTheme() {
    store.toggleTheme()
  }

  /**
   * Key resolved colors for the current theme.
   * Reads CSS custom properties from the document so they stay in sync
   * with tokens.css. Useful for canvas / SVG rendering where CSS vars
   * cannot be used directly.
   */
  const themeColors = computed<ThemeColors>(() => {
    const style = getComputedStyle(document.documentElement)
    const get = (prop: string) => style.getPropertyValue(prop).trim()
    return {
      bg: get('--color-bg'),
      surface: get('--color-surface'),
      textPrimary: get('--text-color-primary'),
      textSecondary: get('--text-color-secondary'),
      primary: get('--color-primary'),
      accent: get('--color-accent'),
      divider: get('--color-divider'),
      toolbar: get('--color-toolbar'),
    }
  })

  return {
    theme,
    setTheme,
    toggleTheme,
    themeColors,
  }
}
