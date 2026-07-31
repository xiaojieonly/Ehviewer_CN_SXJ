<!--
  SettingsLayout.vue — shell for the nested /settings routes: toolbar +
  horizontal tab strip (通用 / 阅读器 / 隐私) + <router-view> content.
  Style follows the original settings page's toolbar / column conventions
  with the existing design-token variables.
-->
<script setup lang="ts">
import { useRoute } from 'vue-router'

const tabs = [
  { path: '/settings/general', label: '通用' },
  { path: '/settings/reader', label: '阅读器' },
  { path: '/settings/privacy', label: '隐私' },
  { path: '/settings/transfer', label: '传输' },
]

const route = useRoute()
</script>

<template>
  <div class="settings-layout">
    <header class="toolbar">
      <h1 class="toolbar__title">设置</h1>
    </header>
    <nav class="settings-tabs">
      <router-link
        v-for="tab in tabs"
        :key="tab.path"
        :to="tab.path"
        class="settings-tabs__tab"
        :class="{ 'is-active': route.path === tab.path }"
      >
        {{ tab.label }}
      </router-link>
    </nav>
    <main class="settings-content">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.settings-layout {
  display: flex;
  flex-direction: column;
  height: 100dvh;
  background: var(--color-bg);
  overflow: hidden;
}

/* --------------------------------- toolbar -------------------------------- */

.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 calc(var(--toolbar-height) + var(--safe-area-top));
  padding: var(--safe-area-top) 16px 0 16px;
  background: var(--color-toolbar);
  color: var(--color-white);
  box-shadow: 0 2px 4px var(--shadow-color);
  z-index: 10;
}

.toolbar__title {
  margin: 0;
  font-size: clamp(17px, 20px, 24px);
  font-weight: 600;
  letter-spacing: 0.01em;
}

/* ----------------------------------- tabs --------------------------------- */

.settings-tabs {
  display: flex;
  align-items: stretch;
  flex: 0 0 auto;
  gap: 4px;
  padding: 8px var(--keyline-margin) 0;
  background: var(--color-toolbar);
  color: var(--color-white);
}

.settings-tabs__tab {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 40px;
  padding: 0 16px;
  border-radius: var(--card-radius);
  font-size: var(--text-small);
  font-weight: 600;
  color: color-mix(in srgb, var(--color-white) 78%, transparent);
  text-decoration: none;
  transition: background 150ms var(--ease-decelerate-quart);
}

.settings-tabs__tab:hover {
  background: color-mix(in srgb, var(--color-white) 12%, transparent);
}

.settings-tabs__tab.is-active {
  background: color-mix(in srgb, var(--color-white) 22%, transparent);
  color: var(--color-white);
}

/* --------------------------------- content -------------------------------- */

.settings-content {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}
</style>
