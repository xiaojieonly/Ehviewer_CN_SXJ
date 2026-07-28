<template>
  <div
    class="progress-spinner"
    :class="[
      `progress-spinner--${size}`,
      indeterminate ? 'progress-spinner--indeterminate' : 'progress-spinner--determinate',
    ]"
    role="progressbar"
    :aria-valuemin="0"
    :aria-valuemax="100"
    :aria-valuenow="indeterminate ? undefined : Math.round(clampedProgress * 100)"
  >
    <svg class="progress-spinner__svg" viewBox="0 0 50 50" xmlns="http://www.w3.org/2000/svg">
      <circle
        class="progress-spinner__circle"
        cx="25"
        cy="25"
        r="20"
        fill="none"
        stroke-linecap="round"
        stroke-width="5"
        :style="circleStyle"
      />
    </svg>
  </div>
</template>

<script setup lang="ts">
/**
 * ProgressSpinner — web replica of `ProgressView.java` (Material-style
 * indeterminate ring). Two size presets per the roadmap mapping:
 * `small` = `--spinner-small` (16px), `large` = `--spinner-large` (76px).
 *
 * Indeterminate mode animates a rotating arc: container rotation + a
 * stroke-dash "trim" cycle (Material grow/shrink), echoing Android's
 * rotation + trim animations. Determinate mode draws a static arc for
 * `progress` ∈ [0, 1], starting at 12 o'clock.
 *
 * Stroke color defaults to `--color-primary` (teal) and can be overridden via
 * the `color` prop.
 */
import { computed } from 'vue'
import type { ProgressSpinnerProps } from '@/types/components'

const props = withDefaults(defineProps<ProgressSpinnerProps>(), {
  color: undefined,
  indeterminate: true,
  progress: 0,
})

/** r=20 in a 50×50 viewBox → circumference ≈ 125.66. */
const CIRCUMFERENCE = 2 * Math.PI * 20

const clampedProgress = computed(() => Math.min(1, Math.max(0, props.progress)))

/**
 * Inline style for the `<circle>`. Stroke is set via CSS (not the attribute)
 * so `var(--color-primary)` resolves correctly. In determinate mode the
 * dasharray encodes the progress arc and the circle is rotated -90° so the
 * arc starts at the top.
 */
const circleStyle = computed(() => {
  const style: Record<string, string> = {
    stroke: props.color ?? 'var(--color-primary)',
  }
  if (!props.indeterminate) {
    style.strokeDasharray = `${clampedProgress.value * CIRCUMFERENCE} ${CIRCUMFERENCE}`
  }
  return style
})
</script>

<style scoped>
.progress-spinner {
  display: inline-block;
  line-height: 0;
}

.progress-spinner--small {
  width: var(--spinner-small); /* 16px */
  height: var(--spinner-small);
}

.progress-spinner--large {
  width: var(--spinner-large); /* 76px */
  height: var(--spinner-large);
}

.progress-spinner__svg {
  display: block;
  width: 100%;
  height: 100%;
}

/* Indeterminate: continuous rotation + trim (grow/shrink) cycle. */
.progress-spinner--indeterminate .progress-spinner__svg {
  animation: progress-spinner-rotate 1.568s linear infinite;
}

.progress-spinner--indeterminate .progress-spinner__circle {
  stroke-dasharray: 1, 200;
  stroke-dashoffset: 0;
  animation: progress-spinner-dash 1.333s ease-in-out infinite;
}

/* Determinate: start the arc at 12 o'clock; animate progress changes. */
.progress-spinner--determinate .progress-spinner__circle {
  transform: rotate(-90deg);
  transform-origin: 50% 50%;
  transition: stroke-dasharray 300ms var(--ease-decelerate-quart);
}

@keyframes progress-spinner-rotate {
  100% {
    transform: rotate(360deg);
  }
}

@keyframes progress-spinner-dash {
  0% {
    stroke-dasharray: 1, 200;
    stroke-dashoffset: 0;
  }
  50% {
    stroke-dasharray: 89, 200;
    stroke-dashoffset: -35;
  }
  100% {
    stroke-dasharray: 89, 200;
    stroke-dashoffset: -124;
  }
}

@media (prefers-reduced-motion: reduce) {
  .progress-spinner--indeterminate .progress-spinner__svg,
  .progress-spinner--indeterminate .progress-spinner__circle {
    animation-duration: 4s;
  }
}
</style>
