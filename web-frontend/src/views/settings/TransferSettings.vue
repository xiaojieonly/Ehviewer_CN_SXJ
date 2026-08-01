<!--
  TransferSettings.vue — 设置 · 传输（对齐管理面板的页面逻辑：页头 + 图标行
  + 偏好分组卡片 + snackbar 反馈）.

  将本机偏好以 JSON 文件导出/导入，用于在 WebUI 实例之间迁移设置；
  与 Android 端的双向同步（服务器同步 → 同步配置/拉取配置）互补。
-->
<template>
  <div class="transfer-settings">
    <div class="transfer-settings__column">
      <header class="transfer-settings__header">
        <h1 class="transfer-settings__title">传输</h1>
      </header>

      <section>
        <SectionHeader title="配置传输" />
        <PrefCard>
          <PrefRow
            icon="send-dark"
            title="导出设置"
            summary="下载本机全部偏好设置（JSON 文件），可用于迁移到其他服务器"
          >
            <button type="button" class="btn-primary" :disabled="exporting" @click="exportSettings">
              {{ exporting ? '导出中…' : '导出' }}
            </button>
          </PrefRow>
          <PrefRow
            icon="folder-add-dark"
            title="导入设置"
            summary="从导出的 JSON 文件恢复设置（覆盖本机当前偏好）"
          >
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
          </PrefRow>
        </PrefCard>
        <p class="transfer-settings__note">
          提示：Android 端可在"设置 → 服务器同步"中通过"同步配置 / 拉取配置"与此服务器双向传输设置。
        </p>
      </section>
    </div>

    <!-- Snackbar. -->
    <Transition name="snack">
      <div v-if="snack" class="snackbar" role="status">{{ snack }}</div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { preferencesApi, type Preferences } from '@/api/preferences'
import { PrefCard, PrefRow, SectionHeader } from '@/components/form'

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
  background: var(--color-bg);
}

.transfer-settings__column {
  max-width: 760px;
  margin: 0 auto;
  padding: 4px var(--keyline-margin) calc(56px + var(--safe-area-bottom));
}

/* ---------------------------------- header --------------------------------- */

.transfer-settings__header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 4px 4px;
}

.transfer-settings__title {
  margin: 0;
  font-size: clamp(17px, 20px, 24px);
  font-weight: 600;
  letter-spacing: 0.01em;
  color: var(--text-color-primary);
}

.transfer-settings__note {
  margin: 12px 4px 0;
  font-size: var(--text-super-small);
  color: var(--text-color-secondary);
}

/* --------------------------------- buttons -------------------------------- */

.btn-primary {
  flex: 0 0 auto;
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

.btn-primary:hover {
  background: var(--color-primary-dark);
}

.btn-primary:active {
  transform: scale(0.97);
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
