<template>
  <!-- EH 熔断提示条（plan-2026-08-30 §0/§3.3.2）：非阻塞、一次性语义；显示条件由
       父视图以 `v-if="availability.state === 'down'"` 控制。点击「重新连接」
       执行一次手动探测（probeRaw + applyProbeResult），成功后 emit refresh 由父视图选择
       刷新列表数据（服务器已恢复，正常请求自然放行）。 -->
  <div
    class="availability-banner"
    data-testid="availability-banner"
    role="status"
  >
    <span class="availability-banner__text">{{ EH_UNAVAILABLE_MESSAGE }}</span>
    <button
      type="button"
      class="availability-banner__retry"
      :disabled="probing"
      @click="onReconnect"
    >
      {{ probing ? '连接中…' : '重新连接' }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { applyProbeResult, EH_UNAVAILABLE_MESSAGE, probeRaw } from '@/stores/availability'

const emit = defineEmits<{
  /** 探测成功、状态恢复 —— 父视图收到后刷新自己的列表。 */
  (e: 'refresh'): void
}>()

const probing = ref(false)

async function onReconnect(): Promise<void> {
  if (probing.value) return
  probing.value = true
  try {
    const res = await probeRaw()
    // emit 必须先行：applyProbeResult 的 markUp 会调度父级 v-if 卸载，若先
    // 应用状态再 emit，卸载竞态会吞掉 refresh 事件（组件届时已 unmount）。
    if (res?.state === 'UP') emit('refresh')
    applyProbeResult(res)
  } finally {
    probing.value = false
  }
}
</script>

<style scoped>
.availability-banner {
  /* 固定高度：HomeView 用它同步偏移浮动 SearchBar（--availability-offset）。
     父视图依赖该值，改动时需同步。 */
  height: 40px;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing);
  flex-shrink: 0;
  padding: 0 max(var(--keyline-margin), 4px);
  background: color-mix(in srgb, var(--color-primary) 12%, var(--color-bg));
  border-bottom: 1px solid var(--color-divider);
  color: var(--text-color-primary);
  font-size: var(--text-super-small); /* 12sp */
}

.availability-banner__text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.availability-banner__retry {
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

.availability-banner__retry:hover:not(:disabled) {
  background: var(--color-surface-activated);
}

.availability-banner__retry:disabled {
  opacity: 0.5;
  cursor: default;
}

.availability-banner__retry:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}
</style>
