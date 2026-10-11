<template>
  <!-- 实时连接状态横幅（WP8 任务 3）：仅在断线（give-up 后连接循环终止）或
       鉴权失败时可见——非阻塞一次性提示，点击「重试」显式 connect() 重武装
       连接循环（useWebSocket.connect 的既定语义）。正常连接/重连中不渲染，
       不挤占列表布局。 -->
  <div
    v-if="visible"
    class="connection-banner"
    data-testid="connection-banner"
    role="status"
  >
    <span class="connection-banner__text">{{ message }}</span>
    <button
      v-if="connectionState === 'error'"
      type="button"
      class="connection-banner__retry"
      data-testid="connection-banner-retry"
      @click="connect"
    >
      重试
    </button>
  </div>
</template>

<script setup lang="ts">
/**
 * ConnectionBanner — 实时 WebSocket 连接的可见化（照 AvailabilityBanner 的
 * 40px 横幅形态）。状态源直接取 useWebSocket 模块级导出的共享 refs
 * （connectionState / lastError / authError）——不调 useWebSocket()（那会
 * 注册组件级清理并要求配对 disconnect，横幅是无生命周期约束的被动展示）。
 *
 * 显示条件（互斥分支）：
 * - `connectionState === 'error'`：连接循环 give-up 后的断线——展示
 *   lastError + 「重试」按钮（显式 connect() 重武装 give-up）；
 * - 否则 authError 非空：鉴权失败——展示 authError（重试要等新令牌，无按钮）。
 */
import { computed } from 'vue'
import { authError, connect, connectionState, lastError } from '@/composables/useWebSocket'

const visible = computed(() => connectionState.value === 'error' || authError.value !== null)

const message = computed(() =>
  connectionState.value === 'error'
    ? `实时连接已断开：${lastError.value ?? '未知错误'}`
    : `实时连接鉴权失败：${authError.value ?? ''}`,
)
</script>

<style scoped>
.connection-banner {
  /* 固定高度 40px（与 AvailabilityBanner 同形态）。 */
  height: 40px;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing);
  flex-shrink: 0;
  padding: 0 max(var(--keyline-margin), 4px);
  background: color-mix(in srgb, var(--color-red-500) 12%, var(--color-bg));
  border-bottom: 1px solid var(--color-divider);
  color: var(--text-color-primary);
  font-size: var(--text-super-small); /* 12sp */
}

.connection-banner__text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.connection-banner__retry {
  flex: 0 0 auto;
  padding: 4px 12px;
  border: 1px solid var(--color-primary);
  border-radius: 999px;
  background: transparent;
  color: var(--color-primary);
  font-family: inherit;
  font-size: var(--text-super-small);
  font-weight: 600;
  cursor: pointer;
  transition: background-color 140ms var(--ease-decelerate-quart);
}

.connection-banner__retry:hover {
  background: var(--color-surface-activated);
}

.connection-banner__retry:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}
</style>
