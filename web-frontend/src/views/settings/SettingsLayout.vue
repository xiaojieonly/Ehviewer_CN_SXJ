<!--
  SettingsLayout.vue — 设置页布局（与 AdminLayout 同构）.

  Wide viewports (≥960px): fixed 240px sidebar + content column.
  Narrow viewports: the sidebar collapses into a horizontal scrollable tab
  bar pinned at the top of the content area (flex + overflow-x: auto).

  Icons come from the AppIcon registry (best-fit for the settings sections);
  registry `*_dark` icons carry hardcoded fills, so like the navigation
  drawer they are forced to currentColor via :deep().
-->
<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppIcon from '@/components/atoms/AppIcon.vue'

const tabs = [
  { path: '/settings/general', label: '通用', icon: 'settings-dark' },
  { path: '/settings/reader', label: '阅读器', icon: 'book-open-primary' },
  { path: '/settings/privacy', label: '隐私', icon: 'sec-primary' },
  { path: '/settings/transfer', label: '传输', icon: 'send-dark' },
]

const route = useRoute()
const navEl = ref<HTMLElement | null>(null)

// Keep the active tab in view when the narrow-viewport tab bar scrolls.
watch(
  () => route.path,
  () => {
    nextTick(() => {
      navEl.value
        ?.querySelector('.is-active')
        ?.scrollIntoView({ inline: 'center', block: 'nearest' })
    })
  },
  { immediate: true },
)
</script>

<template>
  <div class="settings-layout">
    <aside class="settings-layout__sidebar">
      <h2 class="settings-layout__heading">设置</h2>
      <nav ref="navEl" class="settings-layout__nav">
        <router-link
          v-for="tab in tabs"
          :key="tab.path"
          :to="tab.path"
          class="settings-layout__link"
          :class="{ 'is-active': route.path === tab.path }"
        >
          <AppIcon :name="tab.icon" size="20px" class="settings-layout__link-icon" />
          {{ tab.label }}
        </router-link>
      </nav>
    </aside>
    <main class="settings-layout__content">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.settings-layout {
  display: flex;
  height: 100dvh;
  background: var(--color-bg);
  overflow: hidden;
}

/* --------------------------------- sidebar -------------------------------- */

.settings-layout__sidebar {
  flex: 0 0 auto;
  width: 240px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--color-divider);
  background: var(--color-background-floating);
}

.settings-layout__heading {
  flex: 0 0 auto;
  margin: 0;
  padding: 20px var(--keyline-margin) 12px;
  font-size: clamp(12px, 14px, 16px);
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-primary-text, var(--color-primary-dark));
}

.settings-layout__nav {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding: 0 8px var(--safe-area-bottom);
}

.settings-layout__link {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 44px;
  padding: 0 12px;
  border-radius: var(--card-radius);
  font-size: var(--text-small);
  color: var(--text-color-primary);
  text-decoration: none;
  white-space: nowrap;
  transition:
    background-color 150ms var(--ease-decelerate-quart),
    color 150ms var(--ease-decelerate-quart);
}

.settings-layout__link:hover {
  background: var(--color-surface);
}

.settings-layout__link.is-active {
  background: color-mix(in srgb, var(--color-primary) 12%, transparent);
  color: var(--color-primary-text, var(--color-primary-dark));
  font-weight: 700;
}

.settings-layout__link.is-active .settings-layout__link-icon {
  color: var(--color-primary);
}

/* Registry icons may carry hardcoded fills — force them to follow the row. */
.settings-layout__link-icon :deep(svg path) {
  fill: currentColor;
}

/* --------------------------------- content -------------------------------- */

.settings-layout__content {
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}

/* Narrow viewports: sidebar becomes a top horizontal tab bar. */
@media (max-width: 959px) {
  .settings-layout {
    flex-direction: column;
  }

  .settings-layout__sidebar {
    flex: 0 0 auto;
    flex-direction: row;
    align-items: center;
    gap: 8px;
    width: 100%;
    border-right: none;
    border-bottom: 1px solid var(--color-divider);
    box-shadow: 0 2px 4px var(--shadow-color);
  }

  .settings-layout__heading {
    flex: 0 0 auto;
    padding: 0 var(--keyline-margin);
  }

  .settings-layout__nav {
    position: relative;
    display: flex;
    align-items: center;
    gap: 4px;
    flex: 1 1 auto;
    min-width: 0;
    padding: 8px var(--keyline-margin) 8px 0;
    overflow-x: auto;
    overflow-y: hidden;
    overscroll-behavior-x: contain;
    scrollbar-width: none;
  }

  /* Right-edge fade hinting that the tab bar can scroll. */
  .settings-layout__nav::after {
    content: '';
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    width: 24px;
    background: linear-gradient(to right, transparent, var(--color-bg));
    pointer-events: none;
  }

  .settings-layout__nav::-webkit-scrollbar {
    display: none;
  }

  .settings-layout__link {
    flex: 0 0 auto;
    height: 36px;
    padding: 0 12px;
    background: var(--color-surface);
    color: var(--text-color-secondary);
    font-size: var(--text-super-small);
  }

  .settings-layout__link-icon {
    display: none;
  }
}
</style>
