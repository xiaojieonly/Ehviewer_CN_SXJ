<!--
  PrivacySettings.vue — 设置 · 隐私（对齐管理面板的页面逻辑：页头 + 保存
  反馈 + 图标行 + 偏好分组卡片）.

  变更通过 preferencesStore（防抖 PUT /preferences）持久化；保存成功后
  页头闪现「已保存」。
-->
<template>
  <div class="privacy-settings">
    <div class="privacy-settings__column">
      <header class="privacy-settings__header">
        <h1 class="privacy-settings__title">隐私</h1>
        <Transition name="saved">
          <span v-if="savedFlash" class="privacy-settings__saved" role="status">已保存</span>
        </Transition>
      </header>

      <div v-if="preferencesStore.loading" class="privacy-settings__loading">加载中…</div>

      <template v-else-if="prefs">
        <!-- ═══ 隐私 ═══════════════════════════════════════════════════ -->
        <section class="pref-group">
          <h2 class="pref-group__title">隐私</h2>
          <div class="pref-card">
            <div class="pref">
              <AppIcon name="sec-primary" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">启用统计</span>
                <span class="pref__summary">帮助改进应用体验</span>
              </div>
              <button
                type="button"
                class="switch"
                role="switch"
                :aria-checked="prefs.privacy.enableAnalytics"
                aria-label="启用统计"
                @click="toggleAnalytics"
              >
                <span class="switch__thumb" />
              </button>
            </div>
          </div>
        </section>

        <p class="privacy-settings__note">
          统计数据仅用于改进应用体验：完全匿名，仅包含页面访问与功能使用情况，不涉及任何个人数据。
        </p>
      </template>
    </div>

    <!-- Snackbar. -->
    <Transition name="snack">
      <div v-if="snack" class="privacy-settings__snackbar" role="status">{{ snack }}</div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { usePreferencesStore } from '@/stores/preferences'
import AppIcon from '@/components/atoms/AppIcon.vue'

const preferencesStore = usePreferencesStore()

const prefs = computed(() => preferencesStore.prefs)

/* -------------------------------- handlers ------------------------------- */

function toggleAnalytics(): void {
  if (!prefs.value) return
  preferencesStore.updatePrivacy({ enableAnalytics: !prefs.value.privacy.enableAnalytics })
}

/* ------------------------------- save feedback ---------------------------- */

const savedFlash = ref(false)
let savedTimer: number | undefined

watch(
  () => preferencesStore.saveSeq,
  () => {
    savedFlash.value = true
    if (savedTimer) window.clearTimeout(savedTimer)
    savedTimer = window.setTimeout(() => {
      savedFlash.value = false
    }, 1600)
  },
)

const snack = ref('')
let snackTimer: number | undefined

function showSnack(message: string): void {
  snack.value = message
  if (snackTimer) window.clearTimeout(snackTimer)
  snackTimer = window.setTimeout(() => {
    snack.value = ''
  }, 2600)
}

watch(
  () => preferencesStore.saveError,
  (error) => {
    if (error) showSnack('无法在服务器上保存设置')
  },
)

/* ---------------------------------- boot --------------------------------- */

onMounted(async () => {
  await preferencesStore.load()
  if (preferencesStore.loadError) showSnack('无法加载设置')
})
</script>

<style scoped>
.privacy-settings {
  min-height: 100%;
  background: var(--color-bg);
}

.privacy-settings__column {
  max-width: 760px;
  margin: 0 auto;
  padding: 4px var(--keyline-margin) calc(56px + var(--safe-area-bottom));
}

.privacy-settings__loading {
  padding: 32px 0;
  text-align: center;
  font-size: var(--text-small);
  color: var(--text-color-secondary);
}

/* ---------------------------------- header --------------------------------- */

.privacy-settings__header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 4px 4px;
}

.privacy-settings__title {
  margin: 0;
  font-size: clamp(17px, 20px, 24px);
  font-weight: 600;
  letter-spacing: 0.01em;
  color: var(--text-color-primary);
}

.privacy-settings__saved {
  margin-left: auto;
  padding: 4px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-primary) 14%, transparent);
  color: var(--color-primary);
  font-size: clamp(11px, 12px, 14px);
  font-weight: 700;
  letter-spacing: 0.04em;
}

.saved-enter-active,
.saved-leave-active {
  transition: opacity 200ms var(--ease-decelerate-quart);
}

.saved-enter-from,
.saved-leave-to {
  opacity: 0;
}

/* ----------------------------- preference group --------------------------- */

.pref-group__title {
  margin: 22px 4px 8px;
  font-size: clamp(12px, 14px, 16px);
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-primary);
}

.pref-card {
  background: var(--color-background-floating);
  border-radius: var(--card-radius);
  box-shadow:
    0 var(--card-elevation) 4px var(--shadow-color),
    0 0 1px var(--shadow-color);
  overflow: hidden;
}

/* ------------------------------ preference row ---------------------------- */

.pref {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 16px;
  min-height: 48px;
  padding: 10px var(--keyline-margin);
}

.pref__icon {
  flex: 0 0 24px;
  color: var(--drawable-color-primary);
}

.pref__text {
  flex: 1 1 160px;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.pref__title {
  font-size: clamp(14px, 16px, 18px);
  color: var(--text-color-primary);
}

.pref__summary {
  font-size: clamp(11px, 12px, 14px);
  color: var(--text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---------------------------------- switch --------------------------------- */

.switch {
  position: relative;
  flex: 0 0 36px;
  width: 36px;
  height: 20px;
  border: none;
  border-radius: 999px;
  background: var(--widget-color);
  cursor: pointer;
  transition: background-color 200ms var(--ease-decelerate-quart);
}

.switch[aria-checked='true'] {
  background: color-mix(in srgb, var(--color-accent) 40%, transparent);
}

.switch__thumb {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--color-background-floating);
  box-shadow: 0 1px 2px var(--shadow-color);
  transition:
    transform 200ms var(--ease-decelerate-quart),
    background-color 200ms var(--ease-decelerate-quart);
}

.switch[aria-checked='true'] .switch__thumb {
  transform: translateX(16px);
  background: var(--color-accent);
}

/* ---------------------------------- note ----------------------------------- */

.privacy-settings__note {
  margin: 12px 4px 0;
  font-size: clamp(11px, 12px, 14px);
  line-height: 1.55;
  color: var(--text-color-secondary);
}

/* --------------------------------- snackbar -------------------------------- */

.privacy-settings__snackbar {
  position: fixed;
  left: 50%;
  bottom: calc(24px + var(--safe-area-bottom));
  translate: -50% 0;
  z-index: 300;
  max-width: min(480px, calc(100vw - 32px));
  padding: 12px 20px;
  border-radius: var(--card-radius);
  background: var(--gallery-slider-background);
  color: var(--color-white);
  font-size: clamp(13px, 14px, 16px);
  box-shadow: 0 4px 12px var(--shadow-color);
}

.snack-enter-active,
.snack-leave-active {
  transition:
    opacity var(--duration-scene-opacity) var(--ease-decelerate-quart),
    translate var(--duration-scene-translate) var(--ease-decelerate-quint);
}

.snack-enter-from,
.snack-leave-to {
  opacity: 0;
  translate: -50% 12px;
}

@media (prefers-reduced-motion: reduce) {
  .snack-enter-active,
  .snack-leave-active {
    transition: none;
  }
}
</style>
