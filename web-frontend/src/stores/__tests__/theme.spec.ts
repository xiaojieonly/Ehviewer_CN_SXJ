import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useThemeStore } from '../theme'
import type { Theme } from '../theme'

describe('theme store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
    // Remove any meta theme-color tags from previous tests
    document.querySelectorAll('meta[name="theme-color"]').forEach((el) => el.remove())
  })

  describe('initialization', () => {
    it('defaults to light when no stored theme and no system preference', () => {
      // happy-dom matchMedia returns matches: false by default
      const store = useThemeStore()
      expect(store.currentTheme).toBe('light')
    })

    it('restores theme from localStorage', () => {
      localStorage.setItem('anotherviewer-theme', 'black')
      const store = useThemeStore()
      expect(store.currentTheme).toBe('black')
    })

    it('ignores invalid localStorage values', () => {
      localStorage.setItem('anotherviewer-theme', 'neon')
      const store = useThemeStore()
      expect(store.currentTheme).toBe('light')
    })
  })

  describe('setTheme', () => {
    it('sets the theme', () => {
      const store = useThemeStore()
      store.setTheme('dark')
      expect(store.currentTheme).toBe('dark')
    })

    it('sets data-theme attribute on documentElement', () => {
      const store = useThemeStore()
      store.setTheme('black')
      expect(document.documentElement.getAttribute('data-theme')).toBe('black')
    })

    it('persists to localStorage', () => {
      const store = useThemeStore()
      store.setTheme('dark')
      expect(localStorage.getItem('anotherviewer-theme')).toBe('dark')
    })

    it('updates meta theme-color', () => {
      const store = useThemeStore()
      store.setTheme('dark')
      const meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]')
      expect(meta).not.toBeNull()
      expect(meta!.content).toBe('#323232')
    })
  })

  describe('toggleTheme (cycling)', () => {
    it('cycles light → dark → black → light', () => {
      const store = useThemeStore()
      expect(store.currentTheme).toBe('light')

      store.toggleTheme()
      expect(store.currentTheme).toBe('dark')

      store.toggleTheme()
      expect(store.currentTheme).toBe('black')

      store.toggleTheme()
      expect(store.currentTheme).toBe('light')
    })

    it('updates data-theme attribute on each cycle', () => {
      const store = useThemeStore()

      store.toggleTheme()
      expect(document.documentElement.getAttribute('data-theme')).toBe('dark')

      store.toggleTheme()
      expect(document.documentElement.getAttribute('data-theme')).toBe('black')

      store.toggleTheme()
      expect(document.documentElement.getAttribute('data-theme')).toBe('light')
    })
  })

  describe('data-theme attribute', () => {
    it('is set on store creation', () => {
      useThemeStore()
      expect(document.documentElement.getAttribute('data-theme')).toBe('light')
    })

    it('reflects stored theme on creation', () => {
      localStorage.setItem('anotherviewer-theme', 'dark')
      useThemeStore()
      expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
    })
  })

  describe('meta theme-color', () => {
    it.each<[Theme, string]>([
      ['light', '#009688'],
      ['dark', '#323232'],
      ['black', '#000000'],
    ])('sets correct color for %s theme', (theme, expectedColor) => {
      const store = useThemeStore()
      store.setTheme(theme)
      const meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]')
      expect(meta!.content).toBe(expectedColor)
    })

    it('creates meta tag if not present', () => {
      expect(document.querySelector('meta[name="theme-color"]')).toBeNull()
      useThemeStore()
      expect(document.querySelector('meta[name="theme-color"]')).not.toBeNull()
    })
  })
})
