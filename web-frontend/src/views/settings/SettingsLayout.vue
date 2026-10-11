<!--
  SettingsLayout.vue — 统一设置面板布局（A5-1 合并原 SettingsLayout/AdminLayout）.

  Wide viewports (≥960px): two-pane — fixed grouped sidebar
  (`--pane-column-width`, 360px，与列表页详情右栏同一栅格 token)
  + content column.
  Narrow viewports (<960px): Android 式层级导航 — 侧栏退役，`/settings`
  exact 渲染分组索引页（SettingsIndex），子页顶部显示返回条（C5 goBack）。
  旧窄屏横排标签条（含汉堡避让与右缘渐隐 CSS）随合并退役。

  Icons come from the AppIcon registry; registry `*_dark` icons carry
  hardcoded fills, so like the navigation drawer they are forced to
  currentColor via :deep().
-->
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/atoms/AppIcon.vue'
import { SETTINGS_GROUPS } from './settingsSections'

const groups = SETTINGS_GROUPS
const route = useRoute()
const router = useRouter()

/** 窄屏层级导航：子页显示返回条；索引页（/settings exact）是根，无返回。 */
const showBackBar = computed(() => route.path !== '/settings')

/** 返回：深链直达（history 无上一页，`back` 为空）时 router.back() 是
 *  no-op——兜底 push 分组索引页（GalleryDetailView.goBack 的 C5 模式）。 */
function goBack(): void {
  if (history.state?.back) {
    router.back()
  } else {
    void router.push('/settings')
  }
}
</script>

<template>
  <div class="settings-layout">
    <!-- Wide (≥960px) grouped sidebar; retired on narrow via CSS. -->
    <aside class="settings-layout__sidebar" data-testid="settings-sidebar">
      <h2 class="settings-layout__heading">设置</h2>
      <nav class="settings-layout__nav">
        <section v-for="group in groups" :key="group.label" class="settings-layout__group">
          <h3 class="settings-layout__group-label">{{ group.label }}</h3>
          <router-link
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            class="settings-layout__link"
            :class="{ 'is-active': route.path === item.path }"
          >
            <AppIcon :name="item.icon" size="20px" class="settings-layout__link-icon" />
            {{ item.label }}
          </router-link>
        </section>
      </nav>
    </aside>
    <main class="settings-layout__content">
      <!-- A5-3: 子页页头返回条，仅窄屏（<960px）可见（CSS 控制）。 -->
      <div v-if="showBackBar" class="settings-layout__backbar">
        <button
          type="button"
          class="settings-layout__back-btn"
          data-testid="settings-back"
          aria-label="返回设置列表"
          @click="goBack"
        >
          <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true">
            <!-- Material arrow_back -->
            <path
              fill="currentColor"
              d="M20,11V13H8L13.5,18.5L12.08,19.92L4.16,12L12.08,4.08L13.5,5.5L8,11H20Z"
            />
          </svg>
        </button>
      </div>
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
  /* 次要列与列表页右栏同一栅格 token（--pane-column-width，MD3 360dp）——
     全局抽屉(280) | 次要列(360) | 内容(flex) 三栏在各页宽度一致。 */
  width: var(--pane-column-width, 360px);
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

.settings-layout__group + .settings-layout__group {
  margin-top: 12px;
}

.settings-layout__group-label {
  margin: 0 12px 4px;
  font-size: var(--text-super-small);
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-color-secondary);
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

/* Narrow-only sub-page back bar (Android toolbar back affordance). Sticky so
   返回始终可达；hidden ≥960px where the grouped sidebar exists. */
.settings-layout__backbar {
  position: sticky;
  top: 0;
  z-index: 5;
  display: none;
  align-items: center;
  height: 48px;
  padding: 0 8px;
  background: var(--color-bg);
  border-bottom: 1px solid var(--color-divider);
}

.settings-layout__back-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--drawable-color-primary);
  cursor: pointer;
  transition: background 150ms linear;
}

.settings-layout__back-btn:hover {
  background: var(--color-surface);
}

/* B4 触屏豁免：粘滞 hover 会把悬停底色永久卡在按钮上。 */
@media (hover: none) {
  .settings-layout__back-btn:hover {
    background: transparent;
  }
}

/* Narrow viewports: hierarchy mode — sidebar retired, back bar active. */
@media (max-width: 959px) {
  .settings-layout__sidebar {
    display: none;
  }

  .settings-layout__backbar {
    display: flex;
  }
}

/* 汉堡可见视口（<720px，或横屏矮视口）：返回条避让浮动汉堡。
   720-959px 宽高视口汉堡已隐藏（常驻抽屉），无需避让。
   --hamburger-clearance 契约消费方（契约本身不动）。 */
@media (max-width: 719px), (min-width: 720px) and (max-height: 479.98px) {
  .settings-layout__backbar {
    padding-left: var(--hamburger-clearance);
  }
}
</style>
