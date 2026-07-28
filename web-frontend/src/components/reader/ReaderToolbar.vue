<template>
  <div
    class="reader-toolbar"
    :class="{ 'reader-toolbar--hidden': !visible }"
    :dir="rtl ? 'rtl' : 'ltr'"
    :aria-hidden="!visible"
  >
    <button
      type="button"
      class="reader-toolbar__btn"
      aria-label="Back to gallery"
      :tabindex="visible ? 0 : -1"
      @click="emit('back')"
    >
      <!-- Material arrow_back (mirrored under RTL via dir) -->
      <svg
        class="reader-toolbar__icon reader-toolbar__icon--back"
        viewBox="0 0 24 24"
        aria-hidden="true"
        focusable="false"
      >
        <path
          d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"
          fill="currentColor"
        />
      </svg>
    </button>

    <h1 class="reader-toolbar__title" :title="title">{{ title }}</h1>

    <button
      type="button"
      class="reader-toolbar__btn"
      aria-label="Reader settings"
      :tabindex="visible ? 0 : -1"
      @click="emit('open-settings')"
    >
      <!-- Material settings gear -->
      <svg class="reader-toolbar__icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
        <path
          d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"
          fill="currentColor"
        />
      </svg>
    </button>
  </div>
</template>

<script setup lang="ts">
/**
 * ReaderToolbar.vue — top overlay bar for the web reader (the Android
 * GalleryActivity is chrome-less; the web replica adds back / title /
 * settings access). Slides out with the scene-translate animation
 * (`--duration-scene-translate` + `--ease-decelerate-quint`, matching
 * `anim/scene_open_enter.xml`) and sits above a semi-transparent dark
 * gradient scrim so the title stays legible over any page.
 *
 * `rtl` mirrors the whole bar (back button on the right, arrow flipped)
 * for right-to-left reading direction.
 */
interface ReaderToolbarProps {
  visible: boolean
  title: string
  /** Mirror layout for RTL reading direction. @default false */
  rtl?: boolean
}

interface ReaderToolbarEmits {
  (e: 'back'): void
  (e: 'open-settings'): void
}

withDefaults(defineProps<ReaderToolbarProps>(), {
  rtl: false,
})
const emit = defineEmits<ReaderToolbarEmits>()
</script>

<style scoped>
.reader-toolbar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  gap: 4px;
  /* 56dp actionBarSize + status-bar / cutout inset; the scrim fills the
     inset area while the buttons sit in the 56dp band below it. */
  height: calc(var(--toolbar-height) + var(--safe-area-top));
  padding: var(--safe-area-top) 4px 0;
  background: linear-gradient(
    to bottom,
    color-mix(in srgb, var(--color-black) 72%, transparent),
    color-mix(in srgb, var(--color-black) 0%, transparent)
  );
  transition:
    transform var(--duration-scene-translate) var(--ease-decelerate-quint),
    visibility 0s linear 0s;
}

.reader-toolbar--hidden {
  transform: translateY(-100%);
  visibility: hidden;
  transition:
    transform var(--duration-scene-translate) var(--ease-decelerate-quint),
    visibility 0s linear var(--duration-scene-translate);
}

.reader-toolbar__title {
  flex: 1;
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: var(--color-white);
  font-size: var(--text-medium); /* 18sp */
  font-weight: 500;
  line-height: 1.3;
  text-align: start;
  text-overflow: ellipsis;
  text-shadow: 0 1px 2px color-mix(in srgb, var(--color-black) 60%, transparent);
  white-space: nowrap;
}

.reader-toolbar__btn {
  display: grid;
  place-items: center;
  flex: 0 0 48px;
  width: 48px;
  height: 48px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--color-white);
  cursor: pointer;
  transition:
    background 150ms var(--ease-decelerate-quart),
    transform 150ms var(--ease-decelerate-quart);
}

.reader-toolbar__btn:hover {
  background: color-mix(in srgb, var(--color-white) 12%, transparent);
}

.reader-toolbar__btn:active {
  background: color-mix(in srgb, var(--color-white) 22%, transparent);
  transform: scale(0.92);
}

.reader-toolbar__btn:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

.reader-toolbar__icon {
  width: 24px;
  height: 24px;
  filter: drop-shadow(0 1px 1px color-mix(in srgb, var(--color-black) 50%, transparent));
}

/* Flip the back arrow when the bar is mirrored for RTL reading. */
.reader-toolbar[dir='rtl'] .reader-toolbar__icon--back {
  transform: scaleX(-1);
}

@media (prefers-reduced-motion: reduce) {
  .reader-toolbar,
  .reader-toolbar--hidden {
    transition-duration: 1ms;
  }
}
</style>
