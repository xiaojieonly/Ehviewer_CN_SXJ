<!--
  TransferSettings.vue — 设置 · 传输（传输模块）.

  将本机偏好以 JSON 文件导出/导入，用于在 WebUI 实例之间迁移设置；
  与 Android 端的双向同步（服务器同步 → 同步配置/拉取配置）互补。
-->
<template>
  <div class="transfer-settings">
    <section class="pref-group">
      <h2 class="pref-group__title">配置传输</h2>
      <div class="pref-card">
        <div class="pref">
          <AppIcon name="send-dark" class="pref__icon" />
          <div class="pref__text">
            <span class="pref__title">导出设置</span>
            <span class="pref__summary">下载本机全部偏好设置（JSON 文件），可用于迁移到其他服务器</span>
          </div>
          <button type="button" class="btn-primary" :disabled="exporting" @click="exportSettings">
            {{ exporting ? '导出中…' : '导出' }}
          </button>
        </div>
        <div class="pref-divider" />
        <div class="pref">
          <AppIcon name="folder-add-dark" class="pref__icon" />
          <div class="pref__text">
            <span class="pref__title">导入设置</span>
            <span class="pref__summary">从导出的 JSON 文件恢复设置（覆盖本机当前偏好）</span>
          </div>
          <label class="btn-primary file-pick">
            {{ importing ? '导入中…' : '选择文件' }}
            <input
              type="file"
              accept="application/json,.json"
              :disabled="importing"
              data-testid="import-file"
              @change="onFileSelected"
            />
          </label>
        </div>
      </div>
      <p class="transfer-settings__note">
        提示：Android 端可在"设置 → 服务器同步"中通过"同步配置 / 拉取配置"与此服务器双向传输设置。
      </p>
    </section>

    <!-- Snackbar. -->
    <Transition name="snack">
      <div v-if="snack" class="snackbar" role="status">{{ snack }}</div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { preferencesApi, type Preferences } from '@/api/preferences'
import AppIcon from '@/components/atoms/AppIcon.vue'

const exporting = ref(false)
const importing = ref(false)
const snack = ref('')

async function exportSettings() {
  exporting.value = true
  try {
    const prefs = await preferencesApi.get()
    const blob = new Blob([JSON.stringify(prefs, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `anotherviewer-preferences-${new Date().toISOString().slice(0, 10)}.json`
    a.click()
    URL.revokeObjectURL(url)
    snack.value = '设置已导出'
  } catch (e) {
    snack.value = `导出失败：${messageOf(e)}`
  } finally {
    exporting.value = false
  }
}

async function onFileSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  importing.value = true
  try {
    const text = await file.text()
    const parsed = JSON.parse(text) as Preferences
    const payload = sanitize(parsed)
    if (!payload) {
      snack.value = '导入失败：文件不是有效的偏好设置 JSON'
      return
    }
    await preferencesApi.update(payload)
    snack.value = '设置已导入并保存到服务器'
  } catch (e) {
    snack.value = `导入失败：${messageOf(e)}`
  } finally {
    importing.value = false
  }
}

/** 只保留已知字段，忽略未知键，避免脏数据写入服务器。 */
function sanitize(raw: Partial<Preferences>): Partial<Preferences> | null {
  if (!raw || typeof raw !== 'object') return null
  const result: Partial<Preferences> = {}
  if (raw.general && typeof raw.general === 'object') result.general = { ...raw.general }
  if (raw.reader && typeof raw.reader === 'object') result.reader = { ...raw.reader }
  if (raw.privacy && typeof raw.privacy === 'object') result.privacy = { ...raw.privacy }
  if (!result.general && !result.reader && !result.privacy) return null
  return result
}

function messageOf(e: unknown): string {
  return e instanceof Error ? e.message : String(e)
}
</script>

<style scoped>
.transfer-settings {
  min-height: 100%;
  padding: 16px var(--keyline-margin) var(--safe-area-bottom);
}

.transfer-settings__note {
  margin: 12px 4px 0;
  font-size: var(--text-super-small);
  color: var(--text-color-secondary);
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

.file-pick {
  position: relative;
  display: inline-flex;
  align-items: center;
  overflow: hidden;
}

.file-pick input[type='file'] {
  position: absolute;
  inset: 0;
  opacity: 0;
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
</style>
