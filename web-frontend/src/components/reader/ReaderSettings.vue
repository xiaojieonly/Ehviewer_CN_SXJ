<template>
  <Transition name="reader-settings">
    <div
      v-if="visible"
      class="reader-settings"
      role="dialog"
      aria-modal="true"
      aria-label="Reader settings"
    >
      <div class="reader-settings__scrim" @click="emit('close')" />

      <div class="reader-settings__panel">
        <header class="reader-settings__header">
          <h2 class="reader-settings__title">Reader settings</h2>
          <button
            type="button"
            class="reader-settings__close"
            aria-label="Close settings"
            @click="emit('close')"
          >
            <!-- Material close -->
            <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
              <path
                d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"
                fill="currentColor"
              />
            </svg>
          </button>
        </header>

        <!-- Reading direction — Android READING_DIRECTION_LTR/RTL/VERTICAL -->
        <section class="reader-settings__section">
          <h3 class="reader-settings__label" id="reader-settings-direction">
            Reading direction
          </h3>
          <div
            class="reader-settings__segments"
            role="radiogroup"
            aria-labelledby="reader-settings-direction"
          >
            <button
              v-for="option in directionOptions"
              :key="option.value"
              type="button"
              role="radio"
              :aria-checked="direction === option.value"
              class="reader-settings__segment"
              :class="{ 'reader-settings__segment--active': direction === option.value }"
              @click="emit('update:direction', option.value)"
            >
              {{ option.label }}
            </button>
          </div>
        </section>

        <!-- Page mode — auto resolves by viewport aspect (responsive §6) -->
        <section class="reader-settings__section">
          <h3 class="reader-settings__label" id="reader-settings-mode">Page mode</h3>
          <div
            class="reader-settings__segments"
            role="radiogroup"
            aria-labelledby="reader-settings-mode"
          >
            <button
              v-for="option in modeOptions"
              :key="option.value"
              type="button"
              role="radio"
              :aria-checked="pageMode === option.value"
              class="reader-settings__segment"
              :class="{ 'reader-settings__segment--active': pageMode === option.value }"
              @click="emit('update:pageMode', option.value)"
            >
              {{ option.label }}
            </button>
          </div>
        </section>

        <!-- Zoom — 25% steps in [50%, 300%]; the value resets on tap -->
        <section class="reader-settings__section">
          <h3 class="reader-settings__label">Zoom</h3>
          <div class="reader-settings__zoom">
            <button
              type="button"
              class="reader-settings__zoom-step"
              :disabled="zoom <= zoomMin + 0.001"
              aria-label="Zoom out"
              @click="stepZoom(-0.25)"
            >
              −
            </button>
            <button
              type="button"
              class="reader-settings__zoom-value"
              title="Reset to 100%"
              @click="emit('update:zoom', 1)"
            >
              {{ Math.round(zoom * 100) }}%
            </button>
            <button
              type="button"
              class="reader-settings__zoom-step"
              :disabled="zoom >= zoomMax - 0.001"
              aria-label="Zoom in"
              @click="stepZoom(0.25)"
            >
              +
            </button>
          </div>
        </section>

        <!-- Auto-play — Android auto_transfer: advance on a timer -->
        <section class="reader-settings__section">
          <h3 class="reader-settings__label">Auto-play</h3>
          <div class="reader-settings__autoplay">
            <button
              type="button"
              role="switch"
              :aria-checked="autoPlay.enabled"
              class="reader-settings__switch"
              :class="{ 'reader-settings__switch--on': autoPlay.enabled }"
              aria-label="Toggle auto-play"
              @click="toggleAutoPlay"
            >
              <span class="reader-settings__switch-knob" />
            </button>
            <div
              class="reader-settings__chips"
              :class="{ 'reader-settings__chips--disabled': !autoPlay.enabled }"
              role="group"
              aria-label="Auto-play interval"
            >
              <button
                v-for="intervalMs in AUTO_PLAY_INTERVALS_MS"
                :key="intervalMs"
                type="button"
                class="reader-settings__chip"
                :class="{ 'reader-settings__chip--active': autoPlay.intervalMs === intervalMs }"
                :disabled="!autoPlay.enabled"
                @click="emit('update:autoPlay', { enabled: true, intervalMs })"
              >
                {{ intervalMs / 1000 }}s
              </button>
            </div>
          </div>
        </section>

        <!-- Brightness — 0 follows the system; >0 dims via the reader mask -->
        <section class="reader-settings__section">
          <h3 class="reader-settings__label" id="reader-settings-brightness">Brightness</h3>
          <div class="reader-settings__brightness">
            <input
              type="range"
              class="reader-settings__slider"
              min="0"
              max="100"
              step="1"
              :value="brightness"
              aria-labelledby="reader-settings-brightness"
              :style="{ '--brightness-fill': `${brightness}%` }"
              @input="onBrightnessInput"
            />
            <span class="reader-settings__brightness-value">
              {{ brightness === 0 ? 'System' : `${brightness}%` }}
            </span>
          </div>
        </section>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
/**
 * ReaderSettings.vue — bottom sheet with the reader's runtime preferences,
 * mirroring the Android reader's long-press / settings surface:
 *
 * - Reading direction: `READING_DIRECTION_LTR / RTL / VERTICAL`
 * - Page mode: auto (landscape → dual per responsive-strategy §6 rule 3),
 *   single, dual, scroll
 * - Zoom: 25% steps in [50%, 300%] (pinch / double-tap also adjust it live)
 * - Auto-play: toggle + interval (Android `auto_transfer`)
 * - Brightness: 0 = follow system, 1–100 dims the page via a black mask
 *   (the `ColorView` mask in `activity_gallery.xml`)
 *
 * Every value is v-model'd upward; persistence is the parent's concern.
 */
import { AUTO_PLAY_INTERVALS_MS } from './PageMode.vue'
import type { AutoPlayState, PageModePref, ReadingDirection } from './PageMode.vue'

interface ReaderSettingsProps {
  visible: boolean
  direction: ReadingDirection
  pageMode: PageModePref
  zoom: number
  autoPlay: AutoPlayState
  /** 0 = follow system brightness; 1–100 = reader dim mask. */
  brightness: number
}

interface ReaderSettingsEmits {
  (e: 'close'): void
  (e: 'update:direction', direction: ReadingDirection): void
  (e: 'update:pageMode', mode: PageModePref): void
  (e: 'update:zoom', zoom: number): void
  (e: 'update:autoPlay', state: AutoPlayState): void
  (e: 'update:brightness', brightness: number): void
}

const props = defineProps<ReaderSettingsProps>()
const emit = defineEmits<ReaderSettingsEmits>()

const zoomMin = 0.5
const zoomMax = 3

const directionOptions: ReadonlyArray<{ value: ReadingDirection; label: string }> = [
  { value: 'ltr', label: 'Left to right' },
  { value: 'rtl', label: 'Right to left' },
  { value: 'vertical', label: 'Vertical' },
]

const modeOptions: ReadonlyArray<{ value: PageModePref; label: string }> = [
  { value: 'auto', label: 'Auto' },
  { value: 'single', label: 'Single' },
  { value: 'dual', label: 'Dual' },
  { value: 'scroll', label: 'Scroll' },
]

function stepZoom(delta: number) {
  const next = Math.min(zoomMax, Math.max(zoomMin, Math.round((props.zoom + delta) * 100) / 100))
  emit('update:zoom', next)
}

function toggleAutoPlay() {
  emit('update:autoPlay', {
    enabled: !props.autoPlay.enabled,
    intervalMs: props.autoPlay.intervalMs,
  })
}

function onBrightnessInput(event: Event) {
  emit('update:brightness', Number((event.target as HTMLInputElement).value))
}
</script>

<style scoped>
.reader-settings {
  position: absolute;
  inset: 0;
  z-index: 50;
}

.reader-settings__scrim {
  position: absolute;
  inset: 0;
  background: color-mix(in srgb, var(--color-black) 50%, transparent);
}

.reader-settings__panel {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  max-height: 82vh;
  max-height: 82dvh;
  overflow-y: auto;
  padding: 6px 20px calc(20px + env(safe-area-inset-bottom));
  border-radius: 12px 12px 0 0;
  background: var(--grey-900);
  box-shadow: 0 -2px 16px color-mix(in srgb, var(--color-black) 55%, transparent);
}

.reader-settings__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing);
  padding: 10px 0 6px;
}

.reader-settings__title {
  margin: 0;
  color: var(--grey-100);
  font-size: var(--text-little-large); /* 20sp */
  font-weight: 500;
}

.reader-settings__close {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--grey-400);
  cursor: pointer;
  transition:
    background 150ms var(--ease-decelerate-quart),
    color 150ms var(--ease-decelerate-quart);
}

.reader-settings__close:hover {
  background: color-mix(in srgb, var(--color-white) 8%, transparent);
  color: var(--grey-200);
}

.reader-settings__close:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

.reader-settings__close svg {
  width: 20px;
  height: 20px;
}

.reader-settings__section {
  padding: 12px 0;
}

.reader-settings__section + .reader-settings__section {
  border-top: 1px solid var(--color-divider);
}

.reader-settings__label {
  margin: 0 0 10px;
  color: var(--grey-500);
  font-size: var(--text-super-small); /* 12sp */
  font-weight: 500;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

/* --- Segmented radio groups (direction / mode) ----------------------- */

.reader-settings__segments {
  display: flex;
  gap: 3px;
  padding: 3px;
  border-radius: 4px;
  background: var(--grey-850);
}

.reader-settings__segment {
  flex: 1;
  padding: 9px 4px;
  border: none;
  border-radius: var(--card-radius); /* 2dp — the app's card radius */
  background: transparent;
  color: var(--grey-400);
  font-size: var(--text-small); /* 14sp */
  cursor: pointer;
  transition:
    background 150ms var(--ease-decelerate-quart),
    color 150ms var(--ease-decelerate-quart);
}

.reader-settings__segment:hover {
  background: color-mix(in srgb, var(--color-white) 6%, transparent);
  color: var(--grey-200);
}

.reader-settings__segment--active,
.reader-settings__segment--active:hover {
  background: var(--color-primary);
  color: var(--color-white);
  font-weight: 500;
}

.reader-settings__segment:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: -2px;
}

/* --- Zoom ------------------------------------------------------------ */

.reader-settings__zoom {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
}

.reader-settings__zoom-step {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border: 1px solid var(--grey-700);
  border-radius: 50%;
  background: transparent;
  color: var(--grey-100);
  font-size: var(--text-large); /* 22sp */
  line-height: 1;
  cursor: pointer;
  transition:
    border-color 150ms var(--ease-decelerate-quart),
    background 150ms var(--ease-decelerate-quart),
    transform 150ms var(--ease-decelerate-quart);
}

.reader-settings__zoom-step:hover:not(:disabled) {
  border-color: var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 12%, transparent);
}

.reader-settings__zoom-step:active:not(:disabled) {
  transform: scale(0.92);
}

.reader-settings__zoom-step:disabled {
  opacity: 0.35;
  cursor: default;
}

.reader-settings__zoom-step:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.reader-settings__zoom-value {
  min-width: 72px;
  padding: 8px 4px;
  border: none;
  border-radius: var(--card-radius);
  background: transparent;
  color: var(--color-white);
  font-size: var(--text-medium); /* 18sp */
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  transition: background 150ms var(--ease-decelerate-quart);
}

.reader-settings__zoom-value:hover {
  background: color-mix(in srgb, var(--color-white) 8%, transparent);
}

.reader-settings__zoom-value:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

/* --- Auto-play -------------------------------------------------------- */

.reader-settings__autoplay {
  display: flex;
  align-items: center;
  gap: 14px;
}

.reader-settings__switch {
  position: relative;
  flex: 0 0 40px;
  width: 40px;
  height: 22px;
  padding: 0;
  border: none;
  border-radius: 11px;
  background: var(--grey-700);
  cursor: pointer;
  transition: background 200ms var(--ease-decelerate-quart);
}

.reader-settings__switch--on {
  background: var(--color-primary);
}

.reader-settings__switch:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 2px;
}

.reader-settings__switch-knob {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--grey-200);
  box-shadow: 0 1px 2px color-mix(in srgb, var(--color-black) 40%, transparent);
  transition:
    transform 200ms var(--ease-decelerate-quart),
    background 200ms var(--ease-decelerate-quart);
}

.reader-settings__switch--on .reader-settings__switch-knob {
  transform: translateX(18px);
  background: var(--color-white);
}

.reader-settings__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  transition: opacity 200ms var(--ease-decelerate-quart);
}

.reader-settings__chips--disabled {
  opacity: 0.4;
  pointer-events: none;
}

.reader-settings__chip {
  padding: 6px 14px;
  border: 1px solid var(--grey-700);
  border-radius: 14px;
  background: transparent;
  color: var(--grey-300);
  font-size: var(--text-super-small); /* 12sp */
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  transition:
    border-color 150ms var(--ease-decelerate-quart),
    color 150ms var(--ease-decelerate-quart),
    background 150ms var(--ease-decelerate-quart);
}

.reader-settings__chip:hover {
  border-color: var(--grey-500);
  color: var(--grey-100);
}

.reader-settings__chip--active,
.reader-settings__chip--active:hover {
  border-color: var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 15%, transparent);
  color: var(--color-white);
}

.reader-settings__chip:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

/* --- Brightness -------------------------------------------------------- */

.reader-settings__brightness {
  display: flex;
  align-items: center;
  gap: 14px;
}

.reader-settings__slider {
  --brightness-fill: 0%;
  -webkit-appearance: none;
  appearance: none;
  flex: 1;
  height: 32px;
  margin: 0;
  background: transparent;
  cursor: pointer;
}

.reader-settings__slider:focus {
  outline: none;
}

.reader-settings__slider:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
  border-radius: 2px;
}

.reader-settings__slider::-webkit-slider-runnable-track {
  height: 2px;
  border-radius: 1px;
  background: linear-gradient(
    to right,
    var(--color-primary) 0,
    var(--color-primary) var(--brightness-fill),
    var(--grey-700) var(--brightness-fill),
    var(--grey-700) 100%
  );
}

.reader-settings__slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 14px;
  height: 14px;
  margin-top: -6px;
  border: none;
  border-radius: 50%;
  background: var(--color-primary);
  transition: transform 120ms var(--ease-decelerate-quart);
}

.reader-settings__slider:hover::-webkit-slider-thumb {
  transform: scale(1.2);
}

.reader-settings__slider::-moz-range-track {
  height: 2px;
  border-radius: 1px;
  background: var(--grey-700);
}

.reader-settings__slider::-moz-range-progress {
  height: 2px;
  border-radius: 1px;
  background: var(--color-primary);
}

.reader-settings__slider::-moz-range-thumb {
  width: 14px;
  height: 14px;
  border: none;
  border-radius: 50%;
  background: var(--color-primary);
}

.reader-settings__brightness-value {
  flex: 0 0 64px;
  color: var(--grey-300);
  font-size: var(--text-small); /* 14sp */
  font-variant-numeric: tabular-nums;
  text-align: right;
}

/* --- Sheet transition --------------------------------------------------- */

.reader-settings-enter-active,
.reader-settings-leave-active {
  transition: opacity var(--duration-scene-opacity) var(--ease-decelerate-quart);
}

.reader-settings-enter-active .reader-settings__panel,
.reader-settings-leave-active .reader-settings__panel {
  transition: transform var(--duration-scene-translate) var(--ease-decelerate-quint);
}

.reader-settings-enter-from,
.reader-settings-leave-to {
  opacity: 0;
}

.reader-settings-enter-from .reader-settings__panel,
.reader-settings-leave-to .reader-settings__panel {
  transform: translateY(100%);
}

@media (prefers-reduced-motion: reduce) {
  .reader-settings-enter-active,
  .reader-settings-leave-active,
  .reader-settings-enter-active .reader-settings__panel,
  .reader-settings-leave-active .reader-settings__panel {
    transition-duration: 1ms;
  }
}
</style>
