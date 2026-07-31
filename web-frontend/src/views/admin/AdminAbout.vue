<!--
  AdminAbout.vue — 管理面板「关于」页（Wave 6）.

  展示应用品牌信息：名称与版本（与 package.json 的 `version` 保持一致，
  项目 tsconfig 未开启 resolveJsonModule，故以常量镜像）、许可证、
  项目地址与构建信息（技术栈）。
-->
<template>
  <div class="about-view">
    <div class="about-view__column">
      <header class="about-view__header">
        <h1 class="about-view__title">关于</h1>
      </header>

      <section class="pref-group">
        <h2 class="pref-group__title">AnotherViewer</h2>
        <div class="pref-card about-view__hero">
          <AppIcon name="sad-panda-primary" size="56px" class="about-view__logo" />
          <h3 class="about-view__name">AnotherViewer <span>WebUI</span></h3>
          <p class="about-view__version">版本 {{ appVersion }} · 伴侣客户端</p>
          <p class="about-view__note">
            E-Hentai 浏览器的 Web 复刻——相同的设计语言、三套主题，
            可与本机服务器配对使用。
          </p>
        </div>
      </section>

      <section class="pref-group">
        <h2 class="pref-group__title">信息</h2>
        <div class="pref-card">
          <div class="pref">
            <div class="pref__text">
              <span class="pref__title">许可证</span>
              <span class="pref__summary">Apache License 2.0</span>
            </div>
          </div>
          <div class="pref-divider" />
          <a
            :href="projectUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="pref pref--link"
          >
            <div class="pref__text">
              <span class="pref__title">项目地址</span>
              <span class="pref__summary">{{ projectUrl }}</span>
            </div>
            <AppIcon name="go-to-dark" class="pref__chevron" size="20px" />
          </a>
        </div>
      </section>

      <section class="pref-group">
        <h2 class="pref-group__title">构建信息</h2>
        <div class="pref-card">
          <div class="pref">
            <div class="pref__text">
              <span class="pref__title">技术栈</span>
              <span class="pref__summary">Spring Boot 3.4 · Vue 3 · SQLite</span>
            </div>
          </div>
          <div class="pref-divider" />
          <div class="pref">
            <div class="pref__text">
              <span class="pref__title">前端框架</span>
              <span class="pref__summary">Vue 3 + TypeScript + Vite</span>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import AppIcon from '@/components/atoms/AppIcon.vue'

/** 镜像 package.json 的 `version` 字段（tsconfig 未开启 resolveJsonModule）。 */
const appVersion = '1.0.0'

const projectUrl = 'https://github.com/PegionFish/AnotherViewer'
</script>

<style scoped>
.about-view {
  min-height: 100%;
  background: var(--color-bg);
}

.about-view__column {
  max-width: 760px;
  margin: 0 auto;
  padding: 4px var(--keyline-margin) calc(56px + var(--safe-area-bottom));
}

/* ---------------------------------- header --------------------------------- */

.about-view__header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 4px 4px;
}

.about-view__title {
  margin: 0;
  font-size: clamp(17px, 20px, 24px);
  font-weight: 600;
  letter-spacing: 0.01em;
  color: var(--text-color-primary);
}

/* ----------------------------- preference group --------------------------- */

.pref-group__title {
  margin: 22px 4px 8px;
  font-size: clamp(12px, 14px, 16px);
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-primary);
}

.pref-card {
  background: var(--color-background-floating);
  border-radius: var(--card-radius);
  box-shadow:
    0 var(--card-elevation) 4px var(--shadow-color),
    0 0 1px var(--shadow-color);
  overflow: hidden;
}

.pref-divider {
  height: 1px;
  margin: 0 var(--keyline-margin);
  background: var(--color-divider);
}

/* ------------------------------ preference row ---------------------------- */

.pref {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 16px;
  min-height: 48px;
  padding: 10px var(--keyline-margin);
}

a.pref {
  text-decoration: none;
}

a.pref:hover {
  background: var(--color-surface);
}

.pref__text {
  flex: 1 1 160px;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.pref__title {
  font-size: clamp(14px, 16px, 18px);
  color: var(--text-color-primary);
}

.pref__summary {
  font-size: clamp(11px, 12px, 14px);
  color: var(--text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pref__chevron {
  flex: 0 0 20px;
  color: var(--drawable-color-secondary);
}

/* ---------------------------------- hero ----------------------------------- */

.about-view__hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 28px 24px 24px;
}

.about-view__logo {
  color: var(--color-primary);
}

.about-view__name {
  margin: 12px 0 2px;
  font-size: clamp(20px, 24px, 28px);
  font-weight: 800;
  letter-spacing: -0.01em;
  color: var(--text-color-primary);
}

.about-view__name span {
  color: var(--color-primary);
}

.about-view__version {
  margin: 0 0 10px;
  font-size: clamp(11px, 12px, 14px);
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-color-secondary);
}

.about-view__note {
  margin: 0;
  max-width: 36ch;
  font-size: clamp(13px, 14px, 16px);
  line-height: 1.55;
  color: var(--text-color-secondary);
}
</style>
