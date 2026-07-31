<!--
  PrivacySettings.vue — "隐私" tab of /settings (Wave 5).

  Mirrors the WebUI-applicable subset of the Android privacy preferences.
  Persistence goes through the preferences store (debounced PUT /preferences).
-->
<template>
  <div class="privacy-settings">
    <div v-if="preferencesStore.loading" class="privacy-settings__loading">加载中…</div>

    <template v-else-if="prefs">
      <!-- ═══ Privacy ═══════════════════════════════════════════════════ -->
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
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { usePreferencesStore } from '@/stores/preferences'
import AppIcon from '@/components/atoms/AppIcon.vue'

const preferencesStore = usePreferencesStore()

const prefs = computed(() => preferencesStore.prefs)

/* -------------------------------- handlers ------------------------------- */

function toggleAnalytics(): void {
  if (!prefs.value) return
  preferencesStore.updatePrivacy({ enableAnalytics: !prefs.value.privacy.enableAnalytics })
}

/* ---------------------------------- boot --------------------------------- */

onMounted(() => {
  void preferencesStore.load()
})
</script>

<style scoped>
.privacy-settings {
  padding: 4px var(--keyline-margin) calc(56px + var(--safe-area-bottom));
}

.privacy-settings__loading {
  padding: 32px 0;
  text-align: center;
  font-size: var(--text-small);
  color: var(--text-color-secondary);
}

.privacy-settings__note {
  margin: 12px 4px 0;
  font-size: clamp(11px, 12px, 14px);
  line-height: 1.55;
  color: var(--text-color-secondary);
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
</style>
