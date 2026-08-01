<!--
  NotFoundView.vue — 404 catch-all route fallback.

  A centered scene on the themed window background, aligned with the
  LoginView style: brand mascot mark, oversized 404 display, "页面不存在"
  copy and a primary action that navigates back to the home screen.
-->
<template>
  <main class="not-found">
    <div class="not-found__card">
      <AppIcon name="sad-panda-primary" size="72px" class="not-found__mark" />
      <h1 class="not-found__code">404</h1>
      <p class="not-found__message">页面不存在</p>
      <button type="button" class="not-found__action" @click="goHome">返回首页</button>
    </div>
  </main>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import AppIcon from '@/components/atoms/AppIcon.vue'

const router = useRouter()

function goHome(): void {
  router.push('/')
}
</script>

<style scoped>
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100dvh;
  padding: calc(24px + var(--safe-area-top)) 24px calc(24px + var(--safe-area-bottom));
  background: var(--color-bg);
}

.not-found__card {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.not-found__mark {
  color: var(--color-primary);
  margin-bottom: 18px;
}

.not-found__code {
  margin: 0;
  font-size: clamp(72px, 96px, 120px);
  font-weight: 800;
  letter-spacing: -0.03em;
  line-height: 1;
  color: var(--text-color-primary);
}

.not-found__message {
  margin: 12px 0 28px;
  font-size: clamp(14px, 16px, 18px);
  color: var(--text-color-secondary);
}

.not-found__action {
  min-width: 140px;
  min-height: 46px;
  padding: 0 24px;
  border: none;
  border-radius: var(--card-radius);
  background: var(--color-primary);
  color: var(--color-white);
  font-size: clamp(14px, 16px, 18px);
  font-weight: 700;
  letter-spacing: 0.02em;
  cursor: pointer;
  box-shadow: 0 2px 6px var(--shadow-color);
  transition:
    background-color 150ms var(--ease-decelerate-quart),
    box-shadow 150ms var(--ease-decelerate-quart),
    transform 120ms var(--ease-decelerate-quart);
}

.not-found__action:hover {
  background: var(--color-primary-dark);
  box-shadow: 0 4px 10px var(--shadow-color);
}

.not-found__action:active {
  transform: scale(0.985);
}

@media (prefers-reduced-motion: reduce) {
  .not-found__mark {
    transition: none;
  }
}
</style>
