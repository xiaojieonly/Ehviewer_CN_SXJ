<!--
  AdminDevices.vue — 管理面板 · 设备（配对模块）.

  Server-backed pairing (Wave P):
    - POST /api/v1/auth/pair        生成短时配对码（10 分钟有效，单次使用）
    - GET  /api/v1/sync/devices     已配对设备列表
    - DELETE /api/v1/sync/devices/{deviceId}  撤销设备

  Android 端输入服务器地址 + 配对码即可完成配对，无需密码。
-->
<template>
  <div class="admin-devices">
    <div class="admin-devices__column">
      <header class="admin-devices__header">
        <h1 class="admin-devices__heading">设备与配对</h1>
        <Transition name="saved">
          <span v-if="savedFlash" class="admin-devices__saved" role="status">已刷新</span>
        </Transition>
      </header>

      <!-- ═══ Pairing code ═══════════════════════════════════════════════ -->
      <section class="pref-group">
        <h2 class="pref-group__title">配对码</h2>
        <div class="pref-card">
          <div class="pref">
            <AppIcon name="mobile-hand-left" class="pref__icon" />
            <div class="pref__text">
              <span class="pref__title">生成配对码</span>
              <span class="pref__summary">在 Android 端"服务器同步 → 配对服务器"中输入地址与配对码即可连接，无需密码</span>
            </div>
            <button type="button" class="btn-primary" :disabled="generating" @click="generate">
              {{ code ? '重新生成' : '生成配对码' }}
            </button>
          </div>

          <div v-if="code" class="pair-box" role="status">
            <p class="pair-box__hint">配对码（10 分钟内有效，单次使用）：</p>
            <div class="pair-box__row">
              <code class="pair-box__code" data-testid="pair-code">{{ code }}</code>
              <button type="button" class="btn-ghost" @click="copyCode">复制</button>
            </div>
            <p class="pair-box__expiry">
              有效期至 {{ expiresText }}
              <button
                type="button"
                class="pair-box__copy"
                data-testid="copy-pair-code"
                aria-label="复制配对码"
                @click="copyCode"
              >
                复制配对码
              </button>
            </p>
          </div>
        </div>
      </section>

      <!-- ═══ Devices ════════════════════════════════════════════════════ -->
      <section class="pref-group">
        <h2 class="pref-group__title">已配对设备</h2>
        <div class="pref-card">
          <div v-if="devices.length === 0" class="devices-empty">暂无已配对设备</div>
          <div v-for="device in devices" :key="device.deviceId" class="device-row">
            <AppIcon name="mobile-hand-left" class="device-row__icon" size="20px" />
            <div class="device-row__text">
              <span class="device-row__name">{{ device.deviceName }}</span>
              <span class="device-row__meta">
                {{ platformLabel(device.platform) }} · {{ formatTime(device.lastSeen) }} 活跃
              </span>
            </div>
            <button
              type="button"
              class="device-row__revoke"
              :aria-label="`撤销 ${device.deviceName}`"
              @click="confirmRevoke(device)"
            >
              撤销
            </button>
          </div>
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
import { computed, onMounted, ref } from 'vue'
import { devicesApi, type DeviceInfo } from '@/api/devices'
import AppIcon from '@/components/atoms/AppIcon.vue'

const code = ref('')
const expiresAt = ref(0)
const generating = ref(false)
const devices = ref<DeviceInfo[]>([])
const savedFlash = ref(false)
const snack = ref('')

const expiresText = computed(() =>
  expiresAt.value > 0 ? new Date(expiresAt.value).toLocaleTimeString() : '',
)

async function generate() {
  generating.value = true
  try {
    const pair = await devicesApi.generatePairCode()
    code.value = pair.code
    expiresAt.value = pair.expiresAt
    snack.value = '配对码已生成'
  } catch (e) {
    snack.value = `生成失败：${messageOf(e)}`
  } finally {
    generating.value = false
  }
}

async function copyCode() {
  try {
    await navigator.clipboard.writeText(code.value)
    snack.value = '配对码已复制'
  } catch {
    snack.value = '复制失败，请手动输入'
  }
}

function flashSaved() {
  savedFlash.value = true
  setTimeout(() => (savedFlash.value = false), 1500)
}

async function loadDevices() {
  try {
    devices.value = await devicesApi.list()
  } catch (e) {
    snack.value = `加载设备失败：${messageOf(e)}`
  }
}

async function confirmRevoke(device: DeviceInfo) {
  if (!window.confirm(`确定撤销设备「${device.deviceName}」？该设备的同步令牌将立即失效。`)) return
  try {
    await devicesApi.revoke(device.deviceId)
    devices.value = devices.value.filter((d) => d.deviceId !== device.deviceId)
    flashSaved()
    snack.value = '设备已撤销'
  } catch (e) {
    snack.value = `撤销失败：${messageOf(e)}`
  }
}

function platformLabel(platform: string): string {
  if (platform === 'android') return 'Android'
  if (platform === 'ios') return 'iOS'
  if (platform === 'webui') return 'WebUI'
  return platform || '未知'
}

function formatTime(timestamp: number): string {
  if (!timestamp) return '从未'
  return new Date(timestamp).toLocaleString()
}

function messageOf(e: unknown): string {
  return e instanceof Error ? e.message : String(e)
}

onMounted(loadDevices)
</script>

<style scoped>
.admin-devices {
  min-height: 100%;
}

.admin-devices__column {
  max-width: 720px;
  margin: 0 auto;
  padding: 16px var(--keyline-margin) var(--safe-area-bottom);
}

.admin-devices__header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.admin-devices__heading {
  margin: 0;
  font-size: clamp(17px, 20px, 24px);
  font-weight: 600;
}

.admin-devices__saved {
  padding: 2px 10px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-primary) 14%, transparent);
  color: var(--color-primary);
  font-size: var(--text-super-small);
}

/* --------------------------------- pair box -------------------------------- */

.pair-box {
  padding: 16px;
  border-radius: var(--card-radius);
  background: color-mix(in srgb, var(--color-primary) 8%, transparent);
  border: 1px dashed color-mix(in srgb, var(--color-primary) 45%, transparent);
}

.pair-box__hint {
  margin: 0 0 8px;
  font-size: var(--text-super-small);
  color: var(--text-color-secondary);
}

.pair-box__row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pair-box__code {
  font-size: clamp(22px, 30px, 34px);
  font-weight: 800;
  letter-spacing: 0.35em;
  color: var(--color-primary);
  user-select: all;
}

.pair-box__expiry {
  margin: 8px 0 0;
  font-size: var(--text-super-small);
  color: var(--text-color-secondary);
}

.pair-box__copy {
  margin-left: 8px;
  padding: 0;
  border: none;
  background: none;
  color: var(--color-primary);
  font-size: var(--text-super-small);
  cursor: pointer;
  text-decoration: underline;
}

/* --------------------------------- devices -------------------------------- */

.devices-empty {
  padding: 20px;
  text-align: center;
  font-size: var(--text-small);
  color: var(--text-color-secondary);
}

.device-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
}

.device-row + .device-row {
  border-top: 1px solid var(--color-divider);
}

.device-row__icon {
  flex: 0 0 auto;
  color: var(--text-color-secondary);
}

.device-row__text {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.device-row__name {
  font-size: var(--text-small);
  font-weight: 600;
  color: var(--text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.device-row__meta {
  font-size: var(--text-super-small);
  color: var(--text-color-secondary);
}

.device-row__revoke {
  flex: 0 0 auto;
  padding: 6px 14px;
  border: 1px solid color-mix(in srgb, var(--color-danger, #e5484d) 55%, transparent);
  border-radius: 999px;
  background: transparent;
  color: var(--color-danger, #e5484d);
  font-size: var(--text-super-small);
  cursor: pointer;
}

.device-row__revoke:hover {
  background: color-mix(in srgb, var(--color-danger, #e5484d) 10%, transparent);
}

/* --------------------------------- buttons -------------------------------- */

.btn-primary {
  flex: 0 0 auto;
  padding: 8px 16px;
  border: none;
  border-radius: 999px;
  background: var(--color-primary);
  color: #fff;
  font-size: var(--text-super-small);
  font-weight: 600;
  cursor: pointer;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: default;
}

.btn-ghost {
  padding: 6px 14px;
  border: 1px solid color-mix(in srgb, var(--color-primary) 55%, transparent);
  border-radius: 999px;
  background: transparent;
  color: var(--color-primary);
  font-size: var(--text-super-small);
  cursor: pointer;
}

/* --------------------------------- snackbar -------------------------------- */

.snackbar {
  position: fixed;
  bottom: calc(24px + var(--safe-area-bottom));
  left: 50%;
  transform: translateX(-50%);
  padding: 10px 18px;
  border-radius: 999px;
  background: var(--text-color-primary);
  color: var(--color-bg);
  font-size: var(--text-super-small);
  z-index: 100;
}

.snack-enter-active,
.snack-leave-active {
  transition: opacity 200ms var(--ease-decelerate-quart), transform 200ms var(--ease-decelerate-quart);
}

.snack-enter-from,
.snack-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(8px);
}

.saved-enter-active,
.saved-leave-active {
  transition: opacity 200ms var(--ease-decelerate-quart);
}

.saved-enter-from,
.saved-leave-to {
  opacity: 0;
}
</style>
