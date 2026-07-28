<template>
  <span
    class="app-icon"
    :style="iconStyle"
    role="img"
    :aria-label="name"
    v-html="svgContent"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { icons } from '@/assets/icons'

const props = withDefaults(
  defineProps<{
    /** Icon name as derived by the icon registry (e.g. "magnify-dark"). */
    name: string
    /** CSS width/height applied to the wrapper span. */
    size?: string
    /** Optional colour override — replaces `currentColor` in the SVG markup. */
    color?: string
  }>(),
  {
    size: '24px',
    color: undefined,
  },
)

const svgContent = computed<string>(() => {
  const raw = icons[props.name]
  if (!raw) {
    // Unknown icon — render nothing (keeps layout via the sized span).
    return ''
  }

  let svg = raw

  // Strip fixed width/height attributes so the SVG scales to the wrapper.
  svg = svg.replace(/\s(?:width|height)="[^"]*"/g, '')

  // Apply colour override when requested.
  if (props.color) {
    svg = svg.replace(/currentColor/g, props.color)
  }

  return svg
})

const iconStyle = computed(() => ({
  width: props.size,
  height: props.size,
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  flexShrink: 0,
}))
</script>

<style scoped>
.app-icon :deep(svg) {
  width: 100%;
  height: 100%;
  fill: currentColor;
}
</style>
