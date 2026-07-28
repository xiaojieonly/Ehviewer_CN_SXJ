<template>
  <button
    v-if="clickable"
    type="button"
    class="tag-chip tag-chip--clickable"
    :title="title"
    @click="emit('click', tag, namespace)"
  >
    <span class="tag-chip__dot" :style="dotStyle" aria-hidden="true" />
    <span class="tag-chip__label">{{ tag }}</span>
  </button>
  <span v-else class="tag-chip" :title="title">
    <span class="tag-chip__dot" :style="dotStyle" aria-hidden="true" />
    <span class="tag-chip__label">{{ tag }}</span>
  </span>
</template>

<script setup lang="ts">
/**
 * TagChip — one EH tag inside a namespace group row on the gallery detail
 * screen (web equivalent of the `AutoWrapLayout` children inflated by
 * `GalleryDetailScene.bindTags`).
 *
 * Android paints every tag with `tagBackgroundColor`
 * (`RoundSideRectDrawable` — fully rounded left/right sides). The WebUI
 * design direction instead keeps chips on the neutral card surface
 * (`--color-surface`) with primary text, and carries the namespace
 * distinction in a small color-coded dot. Dot colors are references into
 * the frozen token palette (`--color-cat-*` / grey scale) — no hardcoded
 * hex in this component.
 */
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    /** Tag text (without the `namespace:` prefix). */
    tag: string
    /** EH namespace (`language`, `parody`, `artist`, `male`, …) driving the dot color. */
    namespace?: string
    /** Whether the chip acts as a button (pointer + hover feedback). @default true */
    clickable?: boolean
  }>(),
  {
    namespace: undefined,
    clickable: true,
  },
)

const emit = defineEmits<{
  /** Chip tapped — parent builds a `namespace:tag` search query. */
  (e: 'click', tag: string, namespace: string | undefined): void
}>()

/**
 * Namespace → color token mapping. Values are `var()` references into
 * `tokens.css` (the 10 category hues + grey fallback), keeping this
 * component fully theme-aware.
 */
const NAMESPACE_COLOR_TOKENS: Readonly<Record<string, string>> = {
  reclass: 'var(--color-cat-misc)',
  language: 'var(--color-cat-non-h)',
  parody: 'var(--color-cat-image-set)',
  character: 'var(--color-cat-cosplay)',
  group: 'var(--color-cat-doujinshi)',
  artist: 'var(--color-cat-artist-cg)',
  male: 'var(--color-cat-game-cg)',
  female: 'var(--color-cat-asian-porn)',
  mixed: 'var(--color-cat-western)',
  cosplayer: 'var(--color-cat-asian-porn)',
  other: 'var(--color-cat-manga)',
}

const dotStyle = computed(() => ({
  backgroundColor:
    (props.namespace ? NAMESPACE_COLOR_TOKENS[props.namespace] : undefined) ?? 'var(--grey-500)',
}))

const title = computed(() => (props.namespace ? `${props.namespace}:${props.tag}` : props.tag))
</script>

<style scoped>
.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  padding: 4px 12px;
  border: none;
  /* RoundSideRectDrawable — fully rounded sides (Android tag background). */
  border-radius: 999px;
  background-color: var(--color-surface);
  color: var(--text-color-primary);
  font-family: inherit;
  font-size: clamp(11px, var(--text-super-small), 14px);
  line-height: 1.4;
  text-align: left;
  white-space: nowrap;
  transition:
    background-color 120ms var(--ease-decelerate-quart),
    box-shadow 120ms var(--ease-decelerate-quart),
    transform 120ms var(--ease-decelerate-quart);
}

.tag-chip__dot {
  flex-shrink: 0;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background-color: var(--grey-500); /* fallback; overridden inline */
}

.tag-chip__label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tag-chip--clickable {
  cursor: pointer;
}

.tag-chip--clickable:hover {
  background-color: var(--color-surface-activated);
  box-shadow: 0 1px 3px var(--shadow-color);
  transform: translateY(-1px);
}

.tag-chip--clickable:active {
  transform: translateY(0) scale(0.97);
  box-shadow: none;
}

.tag-chip:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}
</style>
