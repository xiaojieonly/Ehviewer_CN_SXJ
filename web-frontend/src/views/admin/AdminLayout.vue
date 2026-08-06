<!--
  AdminLayout.vue — 管理面板布局（Wave 4）.

  Wide viewports (≥960px): fixed 240px sidebar + content column.
  Narrow viewports: the sidebar collapses into a horizontal scrollable tab
  bar pinned at the top of the content area (flex + overflow-x: auto).

  Icons come from the AppIcon registry (best-fit for the admin sections);
  registry `*_dark` icons carry hardcoded fills, so like the navigation
  drawer they are forced to currentColor via :deep().
-->
<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppIcon from '@/components/atoms/AppIcon.vue'

const sections = [
  { path: '/admin/download', label: '下载', icon: 'download-dark' },
  { path: '/admin/filter-slots', label: '筛选槽位', icon: 'magnify-dark' },
  { path: '/admin/server', label: '服务器', icon: 'settings-dark' },
  { path: '/admin/backup', label: '备份', icon: 'download-box-dark' },
  { path: '/admin/devices', label: '设备', icon: 'mobile-hand-left' },
  { path: '/admin/eh', label: 'EH 会话', icon: 'cookie-brown' },
  { path: '/admin/access', label: '访问', icon: 'sec-primary' },
  { path: '/admin/processing', label: '图像处理', icon: 'similar-primary' },
  { path: '/admin/advanced', label: '高级', icon: 'dots-vertical-secondary-dark' },
  { path: '/admin/about', label: '关于', icon: 'info-dark' },
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
  <div class="admin-layout">
    <aside class="admin-layout__sidebar">
      <h2 class="admin-layout__heading">管理面板</h2>
      <nav ref="navEl" class="admin-layout__nav">
        <router-link
          v-for="s in sections"
          :key="s.path"
          :to="s.path"
          class="admin-layout__link"
          :class="{ 'is-active': route.path === s.path }"
        >
          <AppIcon :name="s.icon" size="20px" class="admin-layout__link-icon" />
          {{ s.label }}
        </router-link>
      </nav>
    </aside>
    <main class="admin-layout__content">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  height: 100dvh;
  background: var(--color-bg);
  overflow: hidden;
}

/* --------------------------------- sidebar -------------------------------- */

.admin-layout__sidebar {
  flex: 0 0 auto;
  width: 240px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--color-divider);
  background: var(--color-background-floating);
}

.admin-layout__heading {
  flex: 0 0 auto;
  margin: 0;
  padding: 20px var(--keyline-margin) 12px;
  font-size: clamp(12px, 14px, 16px);
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-primary-text, var(--color-primary-dark));
}

.admin-layout__nav {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding: 0 8px var(--safe-area-bottom);
}

.admin-layout__link {
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

.admin-layout__link:hover {
  background: var(--color-surface);
}

.admin-layout__link.is-active {
  background: color-mix(in srgb, var(--color-primary) 12%, transparent);
  color: var(--color-primary-text, var(--color-primary-dark));
  font-weight: 700;
}

.admin-layout__link.is-active .admin-layout__link-icon {
  color: var(--color-primary);
}

/* Registry icons may carry hardcoded fills — force them to follow the row. */
.admin-layout__link-icon :deep(svg path) {
  fill: currentColor;
}

/* --------------------------------- content -------------------------------- */

.admin-layout__content {
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}

/* Narrow viewports: sidebar becomes a top horizontal tab bar. */
@media (max-width: 959px) {
  .admin-layout {
    flex-direction: column;
  }

  .admin-layout__sidebar {
    flex: 0 0 auto;
    flex-direction: row;
    align-items: center;
    gap: 8px;
    width: 100%;
    border-right: none;
    border-bottom: 1px solid var(--color-divider);
    box-shadow: 0 2px 4px var(--shadow-color);
  }

  .admin-layout__heading {
    flex: 0 0 auto;
    padding: 0 var(--keyline-margin);
  }

  .admin-layout__nav {
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
  .admin-layout__nav::after {
    content: '';
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    width: 24px;
    background: linear-gradient(to right, transparent, var(--color-bg));
    pointer-events: none;
  }

  .admin-layout__nav::-webkit-scrollbar {
    display: none;
  }

  .admin-layout__link {
    flex: 0 0 auto;
    height: 36px;
    padding: 0 12px;
    background: var(--color-surface);
    color: var(--text-color-secondary);
    font-size: var(--text-super-small);
  }

  .admin-layout__link-icon {
    display: none;
  }
}
</style>
