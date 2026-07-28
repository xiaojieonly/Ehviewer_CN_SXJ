<template>
  <router-view />
</template>

<script setup lang="ts">
import { useThemeStore } from '@/stores/theme'

// Initialize theme on app mount — applies data-theme attribute
// and meta theme-color. The store is already created in main.ts,
// but referencing it here ensures reactivity in the root component.
const themeStore = useThemeStore()
// Keep data-theme reactive if currentTheme changes
document.documentElement.setAttribute('data-theme', themeStore.currentTheme)
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}
body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}
</style>
