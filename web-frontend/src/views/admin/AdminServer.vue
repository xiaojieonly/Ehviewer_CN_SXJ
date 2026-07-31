<!--
  AdminServer.vue — 管理面板「服务器」页（Wave 6）.

  复用 AdminLayout 内容区（本组件渲染在其 <router-view /> 内）与 settings 页
  的偏好分组样式（.pref-group / .pref-card / .pref / .switch），并沿用其
  服务端设置持久化模式（PUT /settings）：

    - 缓存路径 / 缓存大小 → settingsApi.update({ cache: { ... } })；
    - SMB 备份开关 → settingsApi.update({ smb: { enabled } })，
      开启后展示「前往备份页面」链接（/smb-backup）；
    - 缓存统计 / 清除缓存：后端暂无对应接口，标注 TODO 并给出提示。

  缓存路径与缓存大小在编辑完成（change 事件）后保存，失败时回滚本地值。
-->
<template>
  <div class="server">
    <div class="server__column">
      <header class="server__header">
        <h1 class="server__title">服务器</h1>
        <span v-if="server" class="server__status" role="status">
          {{ server.smb.enabled ? 'SMB 备份已开启' : 'SMB 备份已关闭' }}
        </span>
      </header>

      <!-- ═══ 缓存 ═══════════════════════════════════════════════════════ -->
      <section class="pref-group">
        <h2 class="pref-group__title">缓存</h2>
        <div class="pref-card">
          <div class="pref">
            <AppIcon name="folder-share-dark" class="pref__icon" />
            <div class="pref__text">
              <span class="pref__title">缓存路径</span>
              <span class="pref__summary">服务器上缓存文件的存放目录</span>
            </div>
            <input
              v-model="pathDraft"
              type="text"
              class="server__input"
              aria-label="缓存路径"
              placeholder="/cache"
              :disabled="!server"
              @change="saveCachePath"
            />
          </div>
          <div class="pref-divider" />
          <div class="pref">
            <AppIcon name="settings-dark" class="pref__icon" />
            <div class="pref__text">
              <span class="pref__title">缓存大小 (MB)</span>
              <span class="pref__summary">为缓存预留的磁盘空间上限</span>
            </div>
            <input
              v-model.number="sizeDraft"
              type="number"
              min="1"
              step="1"
              class="server__input server__input--number"
              aria-label="缓存大小"
              :disabled="!server"
              @change="saveCacheSize"
            />
          </div>
          <div class="pref-divider" />
          <div class="pref">
            <AppIcon name="info-outline-dark" class="pref__icon" />
            <div class="pref__text">
              <span class="pref__title">缓存统计</span>
              <span class="pref__summary">当前缓存占用与条目数</span>
            </div>
            <span class="server__badge" title="后端尚未提供缓存统计接口">TODO</span>
          </div>
          <div class="pref-divider" />
          <button type="button" class="pref pref--action" @click="clearCache">
            <AppIcon name="clear-all-dark" class="pref__icon" />
            <div class="pref__text">
              <span class="pref__title">清除缓存</span>
              <span class="pref__summary">删除服务器上的全部缓存文件</span>
            </div>
            <span class="server__badge" title="后端尚未提供清除缓存接口">TODO</span>
          </button>
        </div>
      </section>

      <!-- ═══ SMB 备份 ══════════════════════════════════════════════════ -->
      <section class="pref-group">
        <h2 class="pref-group__title">SMB 备份</h2>
        <div class="pref-card">
          <div class="pref">
            <AppIcon name="history-black" class="pref__icon" />
            <div class="pref__text">
              <span class="pref__title">SMB 备份</span>
              <span class="pref__summary">
                {{ server?.smb.enabled ? '已开启，备份到 SMB 共享' : '已关闭' }}
              </span>
            </div>
            <button
              type="button"
              class="switch"
              role="switch"
              :aria-checked="server?.smb.enabled ?? false"
              aria-label="SMB 备份"
              :disabled="!server"
              @click="toggleSmb"
            >
              <span class="switch__thumb" />
            </button>
          </div>
          <template v-if="server?.smb.enabled">
            <div class="pref-divider" />
            <router-link to="/smb-backup" class="pref pref--link">
              <AppIcon name="go-to-dark" class="pref__icon" />
              <div class="pref__text">
                <span class="pref__title">前往备份页面</span>
                <span class="pref__summary">配置连接并手动触发同步</span>
              </div>
              <AppIcon name="go-to-dark" class="pref__chevron" size="20px" />
            </router-link>
          </template>
        </div>
      </section>
    </div>

    <!-- Snackbar. -->
    <Transition name="snack">
      <div v-if="snack" class="server__snackbar" role="status">{{ snack }}</div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { Settings } from '@/api/settings'
import { settingsApi } from '@/api/settings'
import AppIcon from '@/components/atoms/AppIcon.vue'

const server = ref<Settings | null>(null)
const pathDraft = ref('')
const sizeDraft = ref<number | null>(null)

const snack = ref('')
let snackTimer: number | undefined

function showSnack(message: string): void {
  snack.value = message
  if (snackTimer) window.clearTimeout(snackTimer)
  snackTimer = window.setTimeout(() => {
    snack.value = ''
  }, 2600)
}

/* ------------------------------- 缓存设置 -------------------------------- */

async function saveCachePath(): Promise<void> {
  if (!server.value) return
  const next = pathDraft.value.trim()
  const previous = server.value.cache.path
  server.value.cache.path = next
  try {
    await settingsApi.update({ cache: { ...server.value.cache, path: next } })
    showSnack('缓存路径已保存')
  } catch (error) {
    server.value.cache.path = previous
    pathDraft.value = previous
    console.error('[AdminServer] failed to save cache path', error)
    showSnack('无法在服务器上保存缓存路径')
  }
}

async function saveCacheSize(): Promise<void> {
  if (!server.value || sizeDraft.value === null) return
  const next = Math.max(1, Math.floor(sizeDraft.value))
  const previous = server.value.cache.sizeMb
  sizeDraft.value = next
  server.value.cache.sizeMb = next
  try {
    await settingsApi.update({ cache: { ...server.value.cache, sizeMb: next } })
    showSnack('缓存大小已保存')
  } catch (error) {
    server.value.cache.sizeMb = previous
    sizeDraft.value = previous
    console.error('[AdminServer] failed to save cache size', error)
    showSnack('无法在服务器上保存缓存大小')
  }
}

function clearCache(): void {
  showSnack('TODO：后端尚未提供清除缓存接口')
}

/* ------------------------------- SMB 备份 -------------------------------- */

async function toggleSmb(): Promise<void> {
  if (!server.value) return
  const next = !server.value.smb.enabled
  server.value.smb.enabled = next
  try {
    await settingsApi.update({ smb: { enabled: next } })
    showSnack(next ? 'SMB 备份已开启' : 'SMB 备份已关闭')
  } catch (error) {
    server.value.smb.enabled = !next
    console.error('[AdminServer] failed to save SMB settings', error)
    showSnack('无法在服务器上保存 SMB 备份设置')
  }
}

/* ---------------------------------- boot ---------------------------------- */

onMounted(async () => {
  try {
    server.value = await settingsApi.get()
    pathDraft.value = server.value.cache.path
    sizeDraft.value = server.value.cache.sizeMb
  } catch (error) {
    console.error('[AdminServer] failed to load settings', error)
    showSnack('无法加载服务器设置')
  }
})
</script>

<style scoped>
/* Content column — rendered inside AdminLayout's scrollable content area. */
.server__column {
  max-width: 760px;
  margin: 0 auto;
  padding: 4px var(--keyline-margin) calc(24px + var(--safe-area-bottom));
}

.server__header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 16px var(--keyline-margin) 4px;
}

.server__title {
  margin: 0;
  font-size: clamp(18px, 22px, 26px);
  font-weight: 700;
  letter-spacing: 0.01em;
  color: var(--text-color-primary);
}

.server__status {
  font-size: clamp(11px, 12px, 14px);
  color: var(--text-color-secondary);
}

/* ----------------------------- 偏好分组（同 settings 页） ----------------- */

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

button.pref {
  width: 100%;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
  font: inherit;
  transition: background-color 120ms var(--ease-decelerate-quart);
}

button.pref:hover {
  background: var(--color-surface);
}

button.pref:active {
  background: var(--color-surface-activated);
}

.pref--link {
  width: 100%;
  color: inherit;
  text-decoration: none;
  cursor: pointer;
  transition: background-color 120ms var(--ease-decelerate-quart);
}

.pref--link:hover {
  background: var(--color-surface);
}

.pref--link:active {
  background: var(--color-surface-activated);
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

.pref__chevron {
  flex: 0 0 20px;
  color: var(--drawable-color-secondary);
}

/* ---------------------------------- 输入框 --------------------------------- */

.server__input {
  flex: 0 1 220px;
  min-width: 140px;
  padding: 8px 10px;
  border: 1px solid var(--color-divider);
  border-radius: var(--card-radius);
  background: transparent;
  font-size: clamp(13px, 14px, 16px);
  color: var(--text-color-primary);
  outline: none;
  transition: border-color 150ms var(--ease-decelerate-quart);
}

.server__input:focus {
  border-color: var(--color-primary);
}

.server__input:disabled {
  opacity: 0.5;
}

.server__input--number {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

/* -------------------------------- TODO 徽标 --------------------------------- */

.server__badge {
  flex: 0 0 auto;
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--text-color-secondary);
  font-size: clamp(10px, 11px, 13px);
  font-weight: 700;
  letter-spacing: 0.05em;
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

.switch:disabled {
  opacity: 0.5;
  cursor: default;
}

/* ---------------------------------- snackbar -------------------------------- */

.server__snackbar {
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
