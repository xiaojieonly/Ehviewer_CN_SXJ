<!--
  JobProgressPanel.vue — 统一后台任务（Job）进度面板（plan-2026-08-06 A6）。

  以 Job JSON schema（A1）为输入，渲染四种状态：
    - PENDING / RUNNING：ProgressSpinner 定量环（percent/100）+ 进度条 + stage 文案
      + processed/total + 百分比（样式参考 SmbBackupView 的 sync-panel）；
    - COMPLETED：勾 + 「完成」；
    - FAILED：红色 error 文案；
    - job 为 null：不渲染。

  进度经 STOMP /topic/jobs/all 推送（subscribeJob），刷新后用 jobsApi.getJob
  恢复——本组件只做展示，不负责拉取与订阅。
-->
<template>
  <section v-if="job" class="job-panel" role="status" :aria-label="title">
    <header class="job-panel__header">
      <span class="job-panel__title">{{ title }}</span>
      <span v-if="active" class="job-percent">{{ percentText }}</span>
    </header>

    <!-- ═══ PENDING / RUNNING ═══════════════════════════════════════════ -->
    <div v-if="active" class="job-panel__body">
      <ProgressSpinner
        size="large"
        :indeterminate="false"
        :progress="progress"
        color="var(--color-accent)"
      />
      <div class="job-panel__stats">
        <div
          class="job-bar"
          role="progressbar"
          :aria-valuenow="Math.round(clampedPercent)"
          aria-valuemin="0"
          aria-valuemax="100"
        >
          <span class="job-bar__fill" :style="{ width: `${clampedPercent}%` }" />
        </div>
        <div class="job-stats-row">
          <span>{{ job.processed }} / {{ job.total }}</span>
          <span>{{ percentText }}</span>
        </div>
        <p class="job-current" :title="job.stage ?? undefined">
          {{ job.stage || '准备中…' }}
        </p>
      </div>
    </div>

    <!-- ═══ COMPLETED ═══════════════════════════════════════════════════ -->
    <p v-else-if="job.state === 'COMPLETED'" class="job-done">
      <AppIcon name="check-dark" size="20px" />
      <span>完成 Done · 任务已完成 Task completed</span>
    </p>

    <!-- ═══ FAILED ══════════════════════════════════════════════════════ -->
    <p v-else-if="job.state === 'FAILED'" class="job-fail">
      <AppIcon name="alert-red" size="20px" />
      <span>
        <strong>失败 Failed</strong>
        <span class="job-fail__msg">{{ job.error || '未知错误 Unknown error' }}</span>
      </span>
    </p>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Job } from '@/api/jobs'
import ProgressSpinner from '@/components/atoms/ProgressSpinner.vue'
import AppIcon from '@/components/atoms/AppIcon.vue'

const props = withDefaults(
  defineProps<{
    /** 当前任务；null 时整个面板不渲染。 */
    job: Job | null
    /** 面板标题。@default 任务 */
    title?: string
  }>(),
  { title: '任务' },
)

/** PENDING / RUNNING 视为进行中（A1 状态机）。 */
const active = computed<boolean>(
  () => props.job?.state === 'PENDING' || props.job?.state === 'RUNNING',
)

/** 防御性钳制到 [0, 100]（worker 端计算，total=0 时恒为 0）。 */
const clampedPercent = computed<number>(() => {
  const p = props.job?.percent ?? 0
  return Math.min(100, Math.max(0, p))
})

const progress = computed<number>(() => clampedPercent.value / 100)

const percentText = computed<string>(() => `${Math.round(clampedPercent.value)}%`)
</script>

<style scoped>
.job-panel {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--color-divider);
}

.job-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.job-panel__title {
  font-size: clamp(13px, 14px, 16px);
  font-weight: 700;
  color: var(--text-color-primary);
}

.job-percent {
  font-size: clamp(16px, 18px, 22px);
  font-weight: 800;
  color: var(--text-color-primary);
  font-variant-numeric: tabular-nums;
}

.job-panel__body {
  display: flex;
  align-items: center;
  gap: 18px;
}

.job-panel__stats {
  flex: 1 1 auto;
  min-width: 0;
}

.job-bar {
  height: 6px;
  border-radius: 999px;
  background: var(--color-surface-activated);
  overflow: hidden;
}

.job-bar__fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--color-accent);
  transition: width 400ms var(--ease-decelerate-quart);
}

.job-stats-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
  font-size: clamp(11px, 12px, 14px);
  color: var(--text-color-secondary);
  font-variant-numeric: tabular-nums;
}

.job-current {
  margin: 6px 0 0;
  font-size: clamp(11px, 12px, 14px);
  color: var(--text-color-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.job-done {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: clamp(13px, 14px, 16px);
  color: var(--color-deep-green-600);
}

.job-fail {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 0;
  font-size: clamp(13px, 14px, 16px);
  line-height: 1.4;
  color: var(--color-red-500);
}

.job-fail__msg {
  display: block;
  margin-top: 2px;
  font-weight: 400;
  word-break: break-word;
}
</style>
