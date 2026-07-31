<!--
  AdminAccess.vue — 管理面板 · 访问控制（Wave 6, Agent 6C）.

  Server-backed security settings (GET/PUT /api/v1/settings → `security`):
    - requireAuth    Boolean  是否需要登录才能访问服务器
    - sessionTimeout Long     会话超时（秒），默认 86400

  NOTE: 后端 SettingsService 在收到 security 段时会同时写入两个字段，
  因此前端始终提交完整的 security 对象（而非单字段补丁）。

  TODO(Wave 6): authApi 暂无 change-password 端点，修改密码表单按钮禁用，
  待后端实现 POST /auth/change-password 后再接入。
-->
<template>
  <div class="admin-access">
    <div class="admin-access__column">
      <header class="admin-access__header">
        <h1 class="admin-access__heading">访问控制</h1>
        <Transition name="saved">
          <span v-if="savedFlash" class="admin-access__saved" role="status">已保存</span>
        </Transition>
      </header>

      <!-- ═══ Login ═══════════════════════════════════════════════════════ -->
      <section class="pref-group">
        <h2 class="pref-group__title">登录</h2>
        <div class="pref-card">
          <div class="pref">
            <AppIcon name="sec-primary" class="pref__icon" />
            <div class="pref__text">
              <span class="pref__title">需要登录</span>
              <span class="pref__summary">访问此服务器需要用户名和密码</span>
            </div>
            <button
              type="button"
              class="switch"
              role="switch"
              :aria-checked="security.requireAuth"
              aria-label="需要登录"
              :disabled="loading"
              @click="toggleRequireAuth"
            >
              <span class="switch__thumb" />
            </button>
          </div>
          <div v-if="!security.requireAuth" class="access-warning" role="alert">
            <AppIcon name="info-dark" class="access-warning__icon" size="18px" />
            <span>关闭后，局域网内任何人都可以访问此服务器</span>
          </div>
          <div class="pref-divider" />
          <div class="pref">
            <AppIcon name="history-black" class="pref__icon" />
            <div class="pref__text">
              <span class="pref__title">Session 超时（秒）</span>
              <span class="pref__summary">无操作后会话自动失效的时长</span>
            </div>
            <input
              class="pref__number"
              type="number"
              min="60"
              max="2592000"
              step="60"
              :value="security.sessionTimeout"
              :disabled="loading"
              aria-label="Session 超时（秒）"
              @change="onTimeoutChange"
            />
          </div>
        </div>
      </section>

      <!-- ═══ Change password ═════════════════════════════════════════════ -->
      <section class="pref-group">
        <h2 class="pref-group__title">修改密码</h2>
        <div class="pref-card">
          <div class="access-form">
            <!-- TODO(Wave 6): 后端暂无 change-password 端点，接入 authApi 后启用 -->
            <label class="field">
              <input v-model="password.old" type="password" placeholder=" " autocomplete="current-password" />
              <span class="field__label">旧密码</span>
            </label>
            <label class="field">
              <input v-model="password.new" type="password" placeholder=" " autocomplete="new-password" />
              <span class="field__label">新密码</span>
            </label>
            <label class="field">
              <input v-model="password.confirm" type="password" placeholder=" " autocomplete="new-password" />
              <span class="field__label">确认新密码</span>
            </label>
            <button type="button" class="btn-primary" disabled title="后端暂未提供修改密码接口">
              修改密码
            </button>
          </div>
          <p class="access-form__note">后端暂未提供修改密码接口，敬请期待。</p>
        </div>
      </section>
    </div>

    <!-- Snackbar. -->
    <Transition name="snack">
      <div v-if="snack" class="snackbar" role="status">{{ snack }}</div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { Settings } from '@/api/settings'
import { settingsApi } from '@/api/settings'
import AppIcon from '@/components/atoms/AppIcon.vue'

/** 后端 SettingsResponse.security 的字段（settings.ts 尚未声明，见组件头注释）。 */
interface SecuritySettings {
  requireAuth: boolean
  sessionTimeout: number
}

interface SettingsWithSecurity extends Settings {
  security: SecuritySettings
}

const DEFAULT_SECURITY: SecuritySettings = {
  requireAuth: false,
  sessionTimeout: 86400,
}

const loading = ref(true)
const security = reactive<SecuritySettings>({ ...DEFAULT_SECURITY })
const password = reactive({ old: '', new: '', confirm: '' })

const savedFlash = ref(false)
let savedTimer: number | undefined

const snack = ref('')
let snackTimer: number | undefined

function flashSaved(): void {
  savedFlash.value = true
  if (savedTimer) window.clearTimeout(savedTimer)
  savedTimer = window.setTimeout(() => {
    savedFlash.value = false
  }, 1600)
}

function showSnack(message: string): void {
  snack.value = message
  if (snackTimer) window.clearTimeout(snackTimer)
  snackTimer = window.setTimeout(() => {
    snack.value = ''
  }, 2600)
}

/** 始终提交完整的 security 段——后端会同时写入 requireAuth 与 sessionTimeout。 */
async function persistSecurity(patch: Partial<SecuritySettings>): Promise<void> {
  Object.assign(security, patch)
  try {
    await settingsApi.update({ security: { ...security } } as Partial<Settings>)
    flashSaved()
  } catch (error) {
    console.error('[AdminAccess] failed to persist security settings', error)
    showSnack('无法保存访问设置')
  }
}

async function toggleRequireAuth(): Promise<void> {
  await persistSecurity({ requireAuth: !security.requireAuth })
}

function onTimeoutChange(event: Event): void {
  const raw = Number((event.target as HTMLInputElement).value)
  if (!Number.isFinite(raw) || raw < 60) {
    showSnack('Session 超时需大于等于 60 秒')
    return
  }
  const clamped = Math.min(2592000, Math.round(raw))
  if (clamped !== security.sessionTimeout) {
    void persistSecurity({ sessionTimeout: clamped })
  }
}

onMounted(async () => {
  try {
    const settings = (await settingsApi.get()) as SettingsWithSecurity
    Object.assign(security, DEFAULT_SECURITY, settings.security)
  } catch (error) {
    console.error('[AdminAccess] failed to load settings', error)
    showSnack('无法加载服务器设置')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
/* Content column — scrolls inside AdminLayout's content pane. */
.admin-access {
  height: 100%;
}

.admin-access__column {
  max-width: 760px;
  margin: 0 auto;
  padding: 4px var(--keyline-margin) calc(56px + var(--safe-area-bottom));
}

/* --------------------------------- header --------------------------------- */

.admin-access__header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 4px 4px;
}

.admin-access__heading {
  margin: 0;
  font-size: clamp(20px, 24px, 28px);
  font-weight: 800;
  letter-spacing: -0.01em;
  color: var(--text-color-primary);
}

.admin-access__saved {
  margin-left: auto;
  padding: 4px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-primary) 12%, transparent);
  color: var(--text-color-theme-primary);
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
/* Reuses SettingsView's group/card/row/switch/field spec (roadmap §卡片规范). */

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

.pref-divider {
  height: 1px;
  margin: 0 var(--keyline-margin);
  background: var(--color-divider);
}

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

/* ---------------------------- warning callout ----------------------------- */

.access-warning {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 var(--keyline-margin) 12px;
  padding: 10px 14px;
  border-radius: var(--card-radius);
  background: var(--color-red-500);
  color: var(--color-white);
  font-size: clamp(12px, 13px, 15px);
  font-weight: 700;
  line-height: 1.4;
}

.access-warning__icon {
  flex: 0 0 auto;
}

.access-warning__icon :deep(svg path) {
  fill: currentColor;
}

/* --------------------------------- switch --------------------------------- */

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

.switch:disabled {
  opacity: 0.5;
  cursor: default;
}

/* ------------------------------ number input ------------------------------ */

.pref__number {
  flex: 0 0 120px;
  width: 120px;
  padding: 8px 10px;
  border: 1px solid var(--color-divider);
  border-radius: var(--card-radius);
  background: transparent;
  font-size: clamp(13px, 14px, 16px);
  font-variant-numeric: tabular-nums;
  color: var(--text-color-primary);
  text-align: right;
  outline: none;
  transition: border-color 150ms var(--ease-decelerate-quart);
}

.pref__number:focus {
  border-color: var(--color-primary);
}

.pref__number:disabled {
  opacity: 0.5;
}

/* --------------------------- change-password form ------------------------- */

.access-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 14px var(--keyline-margin) 4px;
}

.access-form .btn-primary {
  align-self: flex-end;
}

.access-form__note {
  margin: 10px var(--keyline-margin) 14px;
  font-size: clamp(11px, 12px, 14px);
  color: var(--text-color-secondary);
}

/* Outlined material field (divider border → primary on focus). */
.field {
  position: relative;
  display: block;
}

.field input {
  width: 100%;
  padding: 14px 12px 10px;
  border: 1px solid var(--color-divider);
  border-radius: var(--card-radius);
  background: transparent;
  font-size: clamp(14px, 16px, 18px);
  color: var(--text-color-primary);
  outline: none;
  transition: border-color 150ms var(--ease-decelerate-quart);
}

.field input:focus {
  border-color: var(--color-primary);
}

.field__label {
  position: absolute;
  left: 10px;
  top: 50%;
  translate: 0 -50%;
  padding: 0 4px;
  font-size: clamp(14px, 16px, 18px);
  color: var(--text-color-secondary);
  pointer-events: none;
  transition:
    top 150ms var(--ease-decelerate-quart),
    font-size 150ms var(--ease-decelerate-quart),
    color 150ms var(--ease-decelerate-quart);
}

.field input:focus + .field__label,
.field input:not(:placeholder-shown) + .field__label {
  top: 0;
  font-size: clamp(10px, 12px, 13px);
  color: var(--color-primary);
  background: var(--color-background-floating);
}

/* --------------------------------- buttons -------------------------------- */

.btn-primary {
  padding: 9px 22px;
  border: none;
  border-radius: var(--card-radius);
  background: var(--color-primary);
  color: var(--color-white);
  font-size: clamp(13px, 14px, 16px);
  font-weight: 700;
  letter-spacing: 0.02em;
  cursor: pointer;
  box-shadow: 0 1px 3px var(--shadow-color);
  transition:
    background-color 150ms var(--ease-decelerate-quart),
    transform 120ms var(--ease-decelerate-quart);
}

.btn-primary:hover:not(:disabled) {
  background: var(--color-primary-dark);
}

.btn-primary:active:not(:disabled) {
  transform: scale(0.97);
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: default;
}

/* --------------------------------- snackbar -------------------------------- */

.snackbar {
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
  .saved-enter-active,
  .saved-leave-active,
  .snack-enter-active,
  .snack-leave-active {
    transition: none;
  }
}
</style>
