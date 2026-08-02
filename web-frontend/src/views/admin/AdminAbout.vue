<!--
  AdminAbout.vue — 管理面板「关于」页（Wave 6）.

  展示应用品牌信息：名称与版本（版本构建期由 vite `define` 注入，
  单一来源为根目录 `gradle.properties` 的 `webVersion`）、许可证、
  项目地址与构建信息（技术栈）。
-->
<template>
  <div class="about-view">
    <div class="about-view__column">
      <header class="about-view__header">
        <h1 class="about-view__title">关于</h1>
      </header>

      <section>
        <SectionHeader title="AnotherViewer" />
        <PrefCard class="about-view__hero">
          <AppIcon name="sad-panda-primary" size="56px" class="about-view__logo" />
          <h3 class="about-view__name">AnotherViewer <span>WebUI</span></h3>
          <p class="about-view__version">版本 {{ appVersion }} · 伴侣客户端</p>
          <p class="about-view__note">
            经典画廊浏览器的 Web 复刻——相同的设计语言、三套主题，
            可与本机服务器配对使用。
          </p>
        </PrefCard>
      </section>

      <section>
        <SectionHeader title="信息" />
        <PrefCard>
          <PrefRow title="许可证" summary="Apache License 2.0" />
          <PrefRow title="项目地址" :summary="projectUrl">
            <a
              :href="projectUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="about-view__link"
              aria-label="打开项目地址"
            >
              <AppIcon name="go-to-dark" size="20px" />
            </a>
          </PrefRow>
        </PrefCard>
      </section>

      <section>
        <SectionHeader title="构建信息" />
        <PrefCard>
          <PrefRow title="技术栈" summary="Spring Boot 3.4 · Vue 3 · SQLite" />
          <PrefRow title="前端框架" summary="Vue 3 + TypeScript + Vite" />
        </PrefCard>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import AppIcon from '@/components/atoms/AppIcon.vue'
import { PrefCard, PrefRow, SectionHeader } from '@/components/form'

/** 构建期由 vite define 注入（WEB_VERSION 环境变量，见 vite.config.ts）。 */
const appVersion = __APP_VERSION__

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

/* ---------------------------------- hero ----------------------------------- */

.about-view__link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 6px;
  border-radius: 50%;
  color: var(--drawable-color-secondary);
}

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
