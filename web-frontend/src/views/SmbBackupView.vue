<!--
  SmbBackupView.vue — SMB 备份屏幕（S6 重做），基于 EhViewer 设计系统。

  结构：
    - 连接列表：每张卡片带状态指示点（最近一次测试结果 / 启用状态），
      当前激活连接以主题色描边；支持测试 / 编辑 / 删除 / 设为激活。
    - 添加 / 编辑表单：Material outlined 输入框（host / port / share / path /
      登录模式 / username / password），内嵌测试结果横幅。
    - 备份与恢复：备份（SmbSyncEngine 常规同步）、高速备份（aggressive）、
      恢复（服务端暂未提供该能力，给出明确提示）、同步进度面板
      （ProgressSpinner 定量环 + 进度条 + 文件数 + 速度）。

  持久化：
    - 连接清单保存在 localStorage（ehviewer-smb-connections）。出于安全
      考虑密码不落盘，仅在会话内保留；
    - 激活连接通过 PUT /smb/config 同步到服务端（单配置模型）；
    - 同步期间每秒轮询 GET /smb/progress。

  旧版 SmbConfigForm / SyncProgress 组件使用硬编码颜色且不在本任务写作用域
  内，因此表单与进度面板在此视图内以设计令牌直接实现。
-->
<template>
  <div class="smb-scene">
    <NavigationDrawer
      v-model:open="drawerOpen"
      :items="DEFAULT_NAV_ITEMS"
      :username="authStore.username ?? undefined"
      :theme="themeStore.currentTheme"
      @select="onNavSelect"
      @toggle-theme="themeStore.toggleTheme()"
    />

    <div class="smb-scene__main">
      <header class="toolbar">
        <button
          type="button"
          class="toolbar__nav"
          aria-label="打开菜单"
          @click="drawerOpen = true"
        >
          <AppIcon name="reorder" />
        </button>
        <h1 class="toolbar__title">SMB 备份</h1>
        <span v-if="syncing" class="toolbar__chip" role="status">
          <span class="toolbar__chip-dot" />
          同步中
        </span>
      </header>

      <main class="smb-body">
        <div class="smb-column">
          <!-- ═══ 连接列表 ═════════════════════════════════════════════ -->
          <section class="smb-group">
            <h2 class="smb-group__title">连接</h2>

            <div v-if="connections.length" class="conn-list">
              <article
                v-for="conn in connections"
                :key="conn.id"
                class="conn-card"
                :class="{ 'is-active': conn.id === activeId }"
              >
                <span
                  class="conn-dot"
                  :class="`conn-dot--${statusOf(conn)}`"
                  :aria-label="`状态：${statusLabel(statusOf(conn))}`"
                />
                <div class="conn-info">
                  <span class="conn-title" :title="connUrl(conn)">{{ connUrl(conn) }}</span>
                  <span class="conn-summary">
                    {{ conn.loginMode === 'USER' ? conn.username || '用户名' : '访客' }}
                    · {{ conn.enabled ? '已启用' : '已停用' }}
                    <template v-if="conn.id === activeId"> · 当前激活</template>
                  </span>
                </div>
                <div class="conn-actions">
                  <button
                    type="button"
                    class="conn-action"
                    aria-label="测试连接"
                    title="测试连接"
                    :disabled="testingId === conn.id"
                    @click="testConnection(conn)"
                  >
                    <AppIcon :name="testingId === conn.id ? 'pause-dark' : 'refresh-dark'" size="20px" />
                  </button>
                  <button
                    v-if="conn.id !== activeId"
                    type="button"
                    class="conn-action"
                    aria-label="设为激活"
                    title="设为激活"
                    @click="activateConnection(conn)"
                  >
                    <AppIcon name="check-dark" size="20px" />
                  </button>
                  <button
                    type="button"
                    class="conn-action"
                    aria-label="编辑"
                    title="编辑"
                    @click="beginEdit(conn)"
                  >
                    <AppIcon name="pencil-dark" size="20px" />
                  </button>
                  <button
                    type="button"
                    class="conn-action conn-action--danger"
                    aria-label="删除"
                    title="删除"
                    @click="confirmDelete(conn)"
                  >
                    <AppIcon name="delete-dark" size="20px" />
                  </button>
                </div>
              </article>
            </div>

            <div v-else class="conn-empty">
              <AppIcon name="folder-share-dark" size="40px" class="conn-empty__icon" />
              <p>尚未配置 SMB 连接</p>
            </div>

            <button type="button" class="btn-add" @click="beginAdd">
              <AppIcon name="plus-dark" size="20px" />
              添加连接
            </button>
          </section>

          <!-- ═══ 添加 / 编辑表单 ═══════════════════════════════════════ -->
          <Transition name="expand">
            <section v-if="form" class="smb-group">
              <h2 class="smb-group__title">{{ formIsNew ? '添加连接' : '编辑连接' }}</h2>
              <div class="form-card">
                <div class="form-grid">
                  <label class="field field--span-8">
                    <input v-model="form.host" type="text" placeholder=" " :disabled="saving" />
                    <span class="field__label">服务器地址</span>
                  </label>
                  <label class="field field--span-4">
                    <input
                      v-model.number="form.port"
                      type="number"
                      min="1"
                      max="65535"
                      placeholder=" "
                      :disabled="saving"
                    />
                    <span class="field__label">端口</span>
                  </label>
                  <label class="field field--span-6">
                    <input v-model="form.share" type="text" placeholder=" " :disabled="saving" />
                    <span class="field__label">共享文件夹</span>
                  </label>
                  <label class="field field--span-6">
                    <input v-model="form.path" type="text" placeholder=" " :disabled="saving" />
                    <span class="field__label">远程路径（可选）</span>
                  </label>
                  <label class="field field--span-6">
                    <select v-model="form.loginMode" :disabled="saving">
                      <option value="GUEST">访客</option>
                      <option value="USER">用户名 / 密码</option>
                    </select>
                    <span class="field__label field__label--static">登录模式</span>
                  </label>
                  <label v-if="form.loginMode === 'USER'" class="field field--span-6">
                    <input v-model="form.username" type="text" placeholder=" " :disabled="saving" />
                    <span class="field__label">用户名</span>
                  </label>
                  <label v-if="form.loginMode === 'USER'" class="field field--span-12">
                    <input
                      v-model="form.password"
                      type="password"
                      placeholder=" "
                      autocomplete="off"
                      :disabled="saving"
                    />
                    <span class="field__label">密码（仅本次会话保存）</span>
                  </label>
                </div>

                <div class="form-switch-row">
                  <span class="form-switch-label">启用 SMB 备份</span>
                  <button
                    type="button"
                    class="switch"
                    role="switch"
                    :aria-checked="form.enabled"
                    aria-label="启用 SMB 备份"
                    @click="form.enabled = !form.enabled"
                  >
                    <span class="switch__thumb" />
                  </button>
                </div>

                <Transition name="banner">
                  <p v-if="formTestResult" class="test-banner" :class="bannerClass" role="status">
                    <AppIcon
                      :name="formTestResult.ok ? 'check-dark' : 'alert-red'"
                      size="18px"
                    />
                    <span>{{ formTestResult.message }}</span>
                  </p>
                </Transition>

                <div class="form-actions">
                  <button
                    type="button"
                    class="btn-secondary"
                    :disabled="testingForm || saving"
                    @click="testForm"
                  >
                    <ProgressSpinner v-if="testingForm" size="small" />
                    <span>测试连接</span>
                  </button>
                  <span class="form-actions__spacer" />
                  <button type="button" class="btn-text" :disabled="saving" @click="form = null">
                    取消
                  </button>
                  <button
                    type="button"
                    class="btn-primary"
                    :disabled="saving || !formValid"
                    @click="saveForm"
                  >
                    <ProgressSpinner v-if="saving" size="small" color="var(--color-white)" />
                    <span>保存</span>
                  </button>
                </div>
              </div>
            </section>
          </Transition>

          <!-- ═══ 备份与恢复 ════════════════════════════════════════════ -->
          <section class="smb-group">
            <h2 class="smb-group__title">备份与恢复</h2>
            <div class="backup-card">
              <p class="backup-hint">
                将下载目录同步到 SMB 存储（经由 SmbSyncEngine，跳过同名同大小文件）。
              </p>
              <div class="backup-actions">
                <button
                  type="button"
                  class="btn-primary"
                  :disabled="syncing || !hasActive"
                  @click="startSync(false)"
                >
                  备份
                </button>
                <button
                  type="button"
                  class="btn-secondary"
                  :disabled="syncing || !hasActive"
                  @click="startSync(true)"
                >
                  高速备份
                </button>
                <button type="button" class="btn-secondary" @click="notifyRestore">
                  恢复
                </button>
                <button
                  v-if="syncing"
                  type="button"
                  class="btn-danger"
                  @click="cancelSync"
                >
                  取消
                </button>
              </div>
              <p v-if="!hasActive" class="backup-note">先添加并激活一个连接，才能开始备份。</p>

              <Transition name="banner">
                <div v-if="progress.state !== 'idle'" class="sync-panel">
                  <div class="sync-panel__header">
                    <span class="sync-state" :class="`sync-state--${progress.state}`">
                      {{ stateText }}
                    </span>
                    <span v-if="syncing" class="sync-percent">{{ percentText }}</span>
                  </div>

                  <div v-if="syncing" class="sync-panel__body">
                    <ProgressSpinner
                      size="large"
                      :indeterminate="false"
                      :progress="percent"
                      color="var(--color-accent)"
                    />
                    <div class="sync-panel__stats">
                      <div class="sync-bar" role="progressbar" :aria-valuenow="Math.round(percent * 100)" aria-valuemin="0" aria-valuemax="100">
                        <span class="sync-bar__fill" :style="{ width: `${percent * 100}%` }" />
                      </div>
                      <div class="sync-stats-row">
                        <span>{{ progress.syncedFiles }} / {{ progress.totalFiles }} 个文件</span>
                        <span>{{ formatSpeed(progress.speed) }}</span>
                      </div>
                      <p class="sync-current" :title="progress.currentFile">
                        {{ progress.currentFile || '准备中…' }}
                      </p>
                    </div>
                  </div>

                  <p v-else-if="progress.state === 'completed'" class="sync-done">
                    同步完成，共处理 {{ progress.totalFiles }} 个文件。
                  </p>
                  <p v-else-if="progress.state === 'failed'" class="sync-fail">
                    同步失败：{{ progress.currentFile || '未知错误' }}
                  </p>
                </div>
              </Transition>
            </div>
          </section>
        </div>
      </main>
    </div>

    <!-- 删除确认对话框。 -->
    <Transition name="dialog">
      <div v-if="deleting" class="dialog-scrim" @click.self="deleting = null">
        <div class="dialog" role="dialog" aria-modal="true" aria-label="删除连接">
          <h2 class="dialog__title">删除连接</h2>
          <p class="dialog__message">
            确定删除 {{ connUrl(deleting) }} 吗？此操作只影响本设备的配置清单。
          </p>
          <div class="dialog__actions">
            <button type="button" class="btn-text" @click="deleting = null">取消</button>
            <button type="button" class="btn-primary btn-primary--danger" @click="deleteConnection">
              删除
            </button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Snackbar。 -->
    <Transition name="snack">
      <div v-if="snack" class="snackbar" role="status">{{ snack }}</div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { NavItem } from '@/types/components'
import type { SmbConfig, SmbSyncProgress } from '@/api/smb'
import { smbApi } from '@/api/smb'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import NavigationDrawer, { DEFAULT_NAV_ITEMS } from '@/components/layout/NavigationDrawer.vue'
import AppIcon from '@/components/atoms/AppIcon.vue'
import ProgressSpinner from '@/components/atoms/ProgressSpinner.vue'

const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()

/* --------------------------------- types ---------------------------------- */

interface SmbConnectionEntry {
  id: string
  host: string
  port: number
  share: string
  path: string
  loginMode: 'GUEST' | 'USER'
  username: string
  enabled: boolean
}

interface SmbFormModel extends SmbConnectionEntry {
  /** 密码仅存在于会话内存，绝不写入 localStorage。 */
  password: string
}

type ConnStatus = 'ok' | 'fail' | 'off' | 'unknown'

/* ------------------------------- constants -------------------------------- */

const CONNECTIONS_KEY = 'ehviewer-smb-connections'
const ACTIVE_KEY = 'ehviewer-smb-active'

const NAV_ROUTES: Readonly<Record<string, string>> = {
  homepage: '/',
  favourite: '/favorites',
  history: '/history',
  downloads: '/downloads',
  settings: '/settings',
}

/* --------------------------------- state ---------------------------------- */

const drawerOpen = ref(false)
const connections = ref<SmbConnectionEntry[]>([])
const activeId = ref<string | null>(null)
const statuses = reactive<Record<string, ConnStatus>>({})

const form = ref<SmbFormModel | null>(null)
const formIsNew = ref(true)
const formTestResult = ref<{ ok: boolean; message: string } | null>(null)
const testingForm = ref(false)
const saving = ref(false)
const testingId = ref<string | null>(null)
const deleting = ref<SmbConnectionEntry | null>(null)

const progress = ref<SmbSyncProgress>({
  state: 'idle',
  totalFiles: 0,
  syncedFiles: 0,
  currentFile: '',
  speed: 0,
})

const snack = ref('')
let snackTimer: number | undefined
let progressTimer: number | undefined

/* ------------------------------- derived ---------------------------------- */

const hasActive = computed<boolean>(
  () => activeId.value !== null && connections.value.some((c) => c.id === activeId.value),
)

const syncing = computed<boolean>(() => progress.value.state === 'syncing')

const percent = computed<number>(() => {
  const { totalFiles, syncedFiles } = progress.value
  if (totalFiles <= 0) return 0
  return Math.min(1, syncedFiles / totalFiles)
})

const percentText = computed<string>(() => `${Math.round(percent.value * 100)}%`)

const stateText = computed<string>(() => {
  const texts: Record<string, string> = {
    idle: '空闲',
    syncing: '同步中',
    completed: '已完成',
    failed: '失败',
  }
  return texts[progress.value.state] ?? '未知'
})

const bannerClass = computed<string>(() =>
  formTestResult.value?.ok ? 'test-banner--ok' : 'test-banner--fail',
)

const formValid = computed<boolean>(() => {
  if (!form.value) return false
  return form.value.host.trim().length > 0 && form.value.share.trim().length > 0
})

function connUrl(conn: SmbConnectionEntry): string {
  const path = conn.path ? `/${conn.path}` : ''
  return `smb://${conn.host}:${conn.port}/${conn.share}${path}`
}

function statusOf(conn: SmbConnectionEntry): ConnStatus {
  if (!conn.enabled) return 'off'
  return statuses[conn.id] ?? 'unknown'
}

function statusLabel(status: ConnStatus): string {
  const labels: Record<ConnStatus, string> = {
    ok: '连接正常',
    fail: '连接失败',
    off: '已停用',
    unknown: '未测试',
  }
  return labels[status]
}

function formatSpeed(bytesPerSecond: number): string {
  if (!Number.isFinite(bytesPerSecond) || bytesPerSecond <= 0) return '— B/s'
  if (bytesPerSecond < 1024) return `${Math.round(bytesPerSecond)} B/s`
  if (bytesPerSecond < 1024 * 1024) return `${(bytesPerSecond / 1024).toFixed(1)} KB/s`
  return `${(bytesPerSecond / 1024 / 1024).toFixed(2)} MB/s`
}

/* ------------------------------- form actions ------------------------------ */

function blankForm(): SmbFormModel {
  return {
    id: `conn-${Date.now()}`,
    host: '',
    port: 445,
    share: '',
    path: '',
    loginMode: 'GUEST',
    username: '',
    password: '',
    enabled: true,
  }
}

function beginAdd(): void {
  formIsNew.value = true
  formTestResult.value = null
  form.value = blankForm()
}

function beginEdit(conn: SmbConnectionEntry): void {
  formIsNew.value = false
  formTestResult.value = null
  form.value = { ...conn, password: '' }
}

/** 表单字段 → API 配置（单配置模型：激活连接即服务端配置）。 */
function toApiConfig(model: SmbFormModel): Partial<SmbConfig> & { password?: string } {
  return {
    host: model.host.trim(),
    port: model.port,
    share: model.share.trim(),
    path: model.path.trim() || null,
    loginMode: model.loginMode,
    username: model.loginMode === 'USER' ? model.username.trim() || null : null,
    password: model.password || undefined,
    enabled: model.enabled,
  }
}

async function testForm(): Promise<void> {
  if (!form.value || !formValid.value) {
    showSnack('请先填写服务器地址与共享文件夹')
    return
  }
  testingForm.value = true
  formTestResult.value = null
  try {
    const result = await smbApi.testConnection(toApiConfig(form.value))
    formTestResult.value = { ok: result.success, message: result.message }
  } catch (error) {
    console.error('[SmbBackupView] test failed', error)
    formTestResult.value = { ok: false, message: '无法连接到服务器' }
  } finally {
    testingForm.value = false
  }
}

async function saveForm(): Promise<void> {
  if (!form.value || !formValid.value) return
  saving.value = true
  try {
    const entry: SmbConnectionEntry = {
      id: form.value.id,
      host: form.value.host.trim(),
      port: form.value.port,
      share: form.value.share.trim(),
      path: form.value.path.trim(),
      loginMode: form.value.loginMode,
      username: form.value.username.trim(),
      enabled: form.value.enabled,
    }
    const index = connections.value.findIndex((c) => c.id === entry.id)
    connections.value =
      index >= 0
        ? connections.value.map((c) => (c.id === entry.id ? entry : c))
        : [...connections.value, entry]

    // 首个连接自动激活；激活连接同步到服务端配置。
    if (activeId.value === null || form.value.id === activeId.value || index < 0) {
      activeId.value = entry.id
    }
    if (entry.id === activeId.value) {
      await smbApi.updateConfig(toApiConfig(form.value))
    }
    persistConnections()
    form.value = null
    showSnack('配置已保存')
  } catch (error) {
    console.error('[SmbBackupView] save failed', error)
    showSnack('保存到服务器失败，清单已在本地更新')
  } finally {
    saving.value = false
  }
}

async function testConnection(conn: SmbConnectionEntry): Promise<void> {
  testingId.value = conn.id
  try {
    const result = await smbApi.testConnection({
      host: conn.host,
      port: conn.port,
      share: conn.share,
      path: conn.path || null,
      loginMode: conn.loginMode,
      username: conn.loginMode === 'USER' ? conn.username || null : null,
    })
    statuses[conn.id] = result.success ? 'ok' : 'fail'
    showSnack(result.success ? '连接正常' : result.message || '连接失败')
  } catch (error) {
    console.error('[SmbBackupView] test failed', error)
    statuses[conn.id] = 'fail'
    showSnack('无法连接到服务器')
  } finally {
    testingId.value = null
  }
}

async function activateConnection(conn: SmbConnectionEntry): Promise<void> {
  activeId.value = conn.id
  persistConnections()
  try {
    await smbApi.updateConfig({
      host: conn.host,
      port: conn.port,
      share: conn.share,
      path: conn.path || null,
      loginMode: conn.loginMode,
      username: conn.loginMode === 'USER' ? conn.username || null : null,
      enabled: conn.enabled,
    })
    showSnack('已切换激活连接')
  } catch (error) {
    console.error('[SmbBackupView] activate failed', error)
    showSnack('切换失败：服务器不可达')
  }
}

function confirmDelete(conn: SmbConnectionEntry): void {
  deleting.value = conn
}

function deleteConnection(): void {
  const target = deleting.value
  if (!target) return
  connections.value = connections.value.filter((c) => c.id !== target.id)
  delete statuses[target.id]
  if (activeId.value === target.id) {
    activeId.value = connections.value[0]?.id ?? null
  }
  persistConnections()
  deleting.value = null
  showSnack('连接已删除')
}

/* -------------------------------- sync ------------------------------------ */

async function startSync(aggressive: boolean): Promise<void> {
  try {
    await smbApi.sync(aggressive)
    startPolling()
    showSnack(aggressive ? '高速同步已开始' : '备份已开始')
  } catch (error) {
    console.error('[SmbBackupView] sync failed to start', error)
    showSnack('无法启动同步：服务器不可达')
  }
}

async function cancelSync(): Promise<void> {
  try {
    await smbApi.cancel()
    showSnack('已请求取消')
  } catch (error) {
    console.error('[SmbBackupView] cancel failed', error)
    showSnack('取消失败：服务器不可达')
  }
}

/** 恢复能力尚未由服务端提供（SmbSyncEngine 目前只做上传方向）。 */
function notifyRestore(): void {
  showSnack('恢复功能暂未开放——当前服务端仅支持备份（上传）方向')
}

async function pollProgress(): Promise<void> {
  try {
    progress.value = await smbApi.getProgress()
    if (progress.value.state !== 'syncing') {
      stopPolling()
      if (progress.value.state === 'completed') showSnack('同步完成')
      if (progress.value.state === 'failed') showSnack('同步失败')
    }
  } catch (error) {
    console.error('[SmbBackupView] progress poll failed', error)
  }
}

function startPolling(): void {
  stopPolling()
  void pollProgress()
  progressTimer = window.setInterval(() => void pollProgress(), 1000)
}

function stopPolling(): void {
  if (progressTimer) {
    window.clearInterval(progressTimer)
    progressTimer = undefined
  }
}

/* -------------------------------- chrome ----------------------------------- */

function onNavSelect(item: NavItem): void {
  router.push(NAV_ROUTES[item.id] ?? '/')
}

function showSnack(message: string): void {
  snack.value = message
  if (snackTimer) window.clearTimeout(snackTimer)
  snackTimer = window.setTimeout(() => {
    snack.value = ''
  }, 2800)
}

/* ------------------------------- persistence ------------------------------- */

function persistConnections(): void {
  try {
    localStorage.setItem(CONNECTIONS_KEY, JSON.stringify(connections.value))
    if (activeId.value) {
      localStorage.setItem(ACTIVE_KEY, activeId.value)
    } else {
      localStorage.removeItem(ACTIVE_KEY)
    }
  } catch {
    // 存储不可用时静默降级。
  }
}

function loadConnections(): void {
  try {
    const raw = localStorage.getItem(CONNECTIONS_KEY)
    if (raw) {
      connections.value = JSON.parse(raw) as SmbConnectionEntry[]
      activeId.value = localStorage.getItem(ACTIVE_KEY) ?? connections.value[0]?.id ?? null
    }
  } catch {
    connections.value = []
  }
}

/* --------------------------------- boot ------------------------------------ */

onMounted(async () => {
  loadConnections()

  // 本地清单为空时，从服务端单配置导入（兼容既有部署）。
  if (connections.value.length === 0) {
    try {
      const config = await smbApi.getConfig()
      if (config) {
        const entry: SmbConnectionEntry = {
          id: `conn-${Date.now()}`,
          host: config.host,
          port: config.port,
          share: config.share,
          path: config.path ?? '',
          loginMode: config.loginMode === 'USER' ? 'USER' : 'GUEST',
          username: config.username ?? '',
          enabled: config.enabled,
        }
        connections.value = [entry]
        activeId.value = entry.id
        persistConnections()
      }
    } catch {
      // 服务端不可达时保持空清单。
    }
  }

  // 若后台已有同步任务，恢复进度轮询。
  try {
    progress.value = await smbApi.getProgress()
    if (progress.value.state === 'syncing') startPolling()
  } catch {
    // ignore
  }
})

onUnmounted(() => {
  stopPolling()
  if (snackTimer) window.clearTimeout(snackTimer)
})
</script>

<style scoped>
/* 场景外壳：横向 flex，≥720px 时抽屉成为静态侧栏。 */
.smb-scene {
  display: flex;
  height: 100dvh;
  background: var(--color-bg);
  overflow: hidden;
}

.smb-scene__main {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

/* --------------------------------- toolbar -------------------------------- */

.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  /* 延伸进状态栏/刘海区域（与 ReaderToolbar 相同的模式）：工具栏背景色
     填充安全区，控件本体位于其下方，等价于 Android 的着色状态栏。 */
  flex: 0 0 calc(var(--toolbar-height) + var(--safe-area-top));
  padding: var(--safe-area-top) 12px 0 4px;
  background: var(--color-toolbar);
  color: var(--color-white);
  box-shadow: 0 2px 4px var(--shadow-color);
  z-index: 10;
}

.toolbar__nav {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--color-white);
  cursor: pointer;
  transition: background-color 150ms var(--ease-decelerate-quart);
}

.toolbar__nav:hover {
  background: color-mix(in srgb, var(--color-white) 12%, transparent);
}

.toolbar__title {
  margin: 0;
  font-size: clamp(17px, 20px, 24px);
  font-weight: 600;
}

.toolbar__chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
  padding: 4px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-white) 16%, transparent);
  font-size: clamp(11px, 12px, 14px);
  font-weight: 700;
}

.toolbar__chip-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-accent);
  animation: chip-pulse 1.2s ease-in-out infinite;
}

@keyframes chip-pulse {
  50% {
    opacity: 0.35;
  }
}

/* ---------------------------------- body ----------------------------------- */

.smb-body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.smb-column {
  max-width: 760px;
  margin: 0 auto;
  /* 底部叠加 Home 指示条安全区，滚动到底时最后一张卡片不被遮挡。 */
  padding: 4px var(--keyline-margin) calc(56px + var(--safe-area-bottom));
}

.smb-group__title {
  margin: 22px 4px 8px;
  font-size: clamp(12px, 14px, 16px);
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-primary);
}

/* ------------------------------ connection list ---------------------------- */

.conn-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.conn-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px var(--keyline-margin);
  border-left: 3px solid transparent;
  border-radius: var(--card-radius);
  background: var(--color-background-floating);
  box-shadow:
    0 var(--card-elevation) 4px var(--shadow-color),
    0 0 1px var(--shadow-color);
  transition:
    border-color 200ms var(--ease-decelerate-quart),
    transform 150ms var(--ease-decelerate-quart),
    box-shadow 200ms var(--ease-decelerate-quart);
}

.conn-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 10px var(--shadow-color);
}

.conn-card.is-active {
  border-left-color: var(--color-primary);
}

/* 状态指示点：绿（正常）/ 红（失败）/ 灰（停用、未测试）。 */
.conn-dot {
  flex: 0 0 10px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  transition: background-color 200ms var(--ease-decelerate-quart);
}

.conn-dot--ok {
  background: var(--color-deep-green-600);
  box-shadow: 0 0 6px color-mix(in srgb, var(--color-deep-green-600) 60%, transparent);
}

.conn-dot--fail {
  background: var(--color-red-500);
  box-shadow: 0 0 6px color-mix(in srgb, var(--color-red-500) 60%, transparent);
}

.conn-dot--off {
  background: var(--grey-500);
}

.conn-dot--unknown {
  background: var(--grey-400);
}

.conn-info {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.conn-title {
  font-size: clamp(14px, 16px, 18px);
  font-weight: 600;
  color: var(--text-color-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conn-summary {
  font-size: clamp(11px, 12px, 14px);
  color: var(--text-color-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conn-actions {
  flex: 0 0 auto;
  display: flex;
  gap: 2px;
}

.conn-action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--drawable-color-primary);
  cursor: pointer;
  transition:
    background-color 150ms var(--ease-decelerate-quart),
    color 150ms var(--ease-decelerate-quart);
}

.conn-action:hover:not(:disabled) {
  background: var(--color-surface);
}

.conn-action:disabled {
  color: var(--drawable-color-secondary);
  cursor: default;
}

.conn-action--danger:hover:not(:disabled) {
  background: color-mix(in srgb, var(--color-red-500) 12%, transparent);
  color: var(--color-red-500);
}

.conn-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 28px 16px;
  border: 1px dashed var(--color-divider);
  border-radius: var(--card-radius);
  color: var(--text-color-secondary);
  font-size: clamp(13px, 14px, 16px);
}

.conn-empty p {
  margin: 0;
}

.conn-empty__icon {
  color: var(--drawable-color-secondary);
}

.btn-add {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  padding: 9px 18px;
  border: none;
  border-radius: var(--card-radius);
  background: var(--color-primary);
  color: var(--color-white);
  font-size: clamp(13px, 14px, 16px);
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 1px 3px var(--shadow-color);
  transition:
    background-color 150ms var(--ease-decelerate-quart),
    transform 120ms var(--ease-decelerate-quart);
}

.btn-add:hover {
  background: var(--color-primary-dark);
}

.btn-add:active {
  transform: scale(0.97);
}

/* ---------------------------------- form ----------------------------------- */

.form-card {
  padding: var(--keyline-margin);
  border-radius: var(--card-radius);
  background: var(--color-background-floating);
  box-shadow:
    0 var(--card-elevation) 4px var(--shadow-color),
    0 0 1px var(--shadow-color);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  gap: 14px 12px;
}

.field--span-4 {
  grid-column: span 4;
}

.field--span-6 {
  grid-column: span 6;
}

.field--span-8 {
  grid-column: span 8;
}

.field--span-12 {
  grid-column: span 12;
}

@media (max-width: 599px) {
  .field--span-4,
  .field--span-6,
  .field--span-8 {
    grid-column: span 12;
  }
}

/* Material outlined 输入框：分隔线边框 → 聚焦主题色。 */
.field {
  position: relative;
  display: block;
}

.field input,
.field select {
  width: 100%;
  padding: 15px 12px 9px;
  border: 1px solid var(--color-divider);
  border-radius: var(--card-radius);
  background: transparent;
  font-size: clamp(14px, 16px, 18px);
  color: var(--text-color-primary);
  caret-color: var(--color-accent);
  outline: none;
  transition: border-color 150ms var(--ease-decelerate-quart);
}

.field select {
  appearance: none;
  cursor: pointer;
}

.field input:focus,
.field select:focus {
  border-color: var(--color-primary);
}

.field input:disabled,
.field select:disabled {
  opacity: 0.6;
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

/* select 无法用 :placeholder-shown，标签常驻浮起。 */
.field__label--static {
  top: 0;
  font-size: clamp(10px, 12px, 13px);
  background: var(--color-background-floating);
}

.field input:focus + .field__label,
.field input:not(:placeholder-shown) + .field__label {
  top: 0;
  font-size: clamp(10px, 12px, 13px);
  color: var(--color-primary);
  background: var(--color-background-floating);
}

.form-switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}

.form-switch-label {
  font-size: clamp(14px, 16px, 18px);
  color: var(--text-color-primary);
}

.switch {
  position: relative;
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

/* 测试结果横幅：成功绿 / 失败红（令牌色）。 */
.test-banner {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 16px 0 0;
  padding: 10px 12px;
  border-radius: var(--card-radius);
  font-size: clamp(13px, 14px, 16px);
  line-height: 1.4;
}

.test-banner--ok {
  background: color-mix(in srgb, var(--color-deep-green-600) 14%, transparent);
  color: var(--color-deep-green-600);
}

.test-banner--fail {
  background: color-mix(in srgb, var(--color-red-500) 12%, transparent);
  color: var(--color-red-500);
}

.banner-enter-active,
.banner-leave-active {
  transition:
    opacity var(--duration-scene-opacity) var(--ease-decelerate-quart),
    transform var(--duration-scene-opacity) var(--ease-decelerate-quart);
}

.banner-enter-from,
.banner-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.form-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--color-divider);
}

.form-actions__spacer {
  flex: 1;
}

/* --------------------------------- buttons --------------------------------- */

.btn-primary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 40px;
  padding: 8px 22px;
  border: none;
  border-radius: var(--card-radius);
  background: var(--color-primary);
  color: var(--color-white);
  font-size: clamp(13px, 14px, 16px);
  font-weight: 700;
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
  opacity: 0.55;
  cursor: default;
}

.btn-primary--danger {
  background: var(--color-red-500);
}

.btn-primary--danger:hover:not(:disabled) {
  background: var(--color-red-500);
  filter: brightness(0.92);
}

.btn-secondary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 40px;
  padding: 8px 18px;
  border: 1px solid var(--color-divider);
  border-radius: var(--card-radius);
  background: transparent;
  color: var(--text-color-theme-primary);
  font-size: clamp(13px, 14px, 16px);
  font-weight: 700;
  cursor: pointer;
  transition:
    border-color 150ms var(--ease-decelerate-quart),
    background-color 150ms var(--ease-decelerate-quart);
}

.btn-secondary:hover:not(:disabled) {
  border-color: var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 8%, transparent);
}

.btn-secondary:disabled {
  opacity: 0.55;
  cursor: default;
}

.btn-danger {
  min-height: 40px;
  padding: 8px 18px;
  border: none;
  border-radius: var(--card-radius);
  background: color-mix(in srgb, var(--color-red-500) 14%, transparent);
  color: var(--color-red-500);
  font-size: clamp(13px, 14px, 16px);
  font-weight: 700;
  cursor: pointer;
  transition: background-color 150ms var(--ease-decelerate-quart);
}

.btn-danger:hover {
  background: color-mix(in srgb, var(--color-red-500) 22%, transparent);
}

.btn-text {
  padding: 9px 14px;
  border: none;
  border-radius: var(--card-radius);
  background: transparent;
  color: var(--text-color-theme-primary);
  font-size: clamp(13px, 14px, 16px);
  font-weight: 700;
  cursor: pointer;
  transition: background-color 150ms var(--ease-decelerate-quart);
}

.btn-text:hover:not(:disabled) {
  background: var(--color-surface);
}

.btn-text:disabled {
  opacity: 0.55;
  cursor: default;
}

/* ------------------------------- backup panel ------------------------------ */

.backup-card {
  padding: var(--keyline-margin);
  border-radius: var(--card-radius);
  background: var(--color-background-floating);
  box-shadow:
    0 var(--card-elevation) 4px var(--shadow-color),
    0 0 1px var(--shadow-color);
}

.backup-hint {
  margin: 0 0 14px;
  font-size: clamp(13px, 14px, 16px);
  line-height: 1.5;
  color: var(--text-color-secondary);
}

.backup-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.backup-note {
  margin: 12px 0 0;
  font-size: clamp(11px, 12px, 14px);
  color: var(--text-color-secondary);
}

.sync-panel {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--color-divider);
}

.sync-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.sync-state {
  padding: 4px 12px;
  border-radius: 999px;
  font-size: clamp(11px, 12px, 14px);
  font-weight: 700;
  letter-spacing: 0.04em;
}

.sync-state--syncing {
  background: color-mix(in srgb, var(--color-accent) 16%, transparent);
  color: var(--text-color-theme-accent);
}

.sync-state--completed {
  background: color-mix(in srgb, var(--color-deep-green-600) 16%, transparent);
  color: var(--color-deep-green-600);
}

.sync-state--failed {
  background: color-mix(in srgb, var(--color-red-500) 14%, transparent);
  color: var(--color-red-500);
}

.sync-state--idle {
  background: var(--color-surface);
  color: var(--text-color-secondary);
}

.sync-percent {
  font-size: clamp(16px, 18px, 22px);
  font-weight: 800;
  color: var(--text-color-primary);
  font-variant-numeric: tabular-nums;
}

.sync-panel__body {
  display: flex;
  align-items: center;
  gap: 18px;
}

.sync-panel__stats {
  flex: 1 1 auto;
  min-width: 0;
}

.sync-bar {
  height: 6px;
  border-radius: 999px;
  background: var(--color-surface-activated);
  overflow: hidden;
}

.sync-bar__fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--color-accent);
  transition: width 400ms var(--ease-decelerate-quart);
}

.sync-stats-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
  font-size: clamp(11px, 12px, 14px);
  color: var(--text-color-secondary);
  font-variant-numeric: tabular-nums;
}

.sync-current {
  margin: 6px 0 0;
  font-size: clamp(11px, 12px, 14px);
  color: var(--text-color-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sync-done {
  margin: 0;
  font-size: clamp(13px, 14px, 16px);
  color: var(--color-deep-green-600);
}

.sync-fail {
  margin: 0;
  font-size: clamp(13px, 14px, 16px);
  color: var(--color-red-500);
}

/* 表单区块展开动画。 */
.expand-enter-active,
.expand-leave-active {
  transition:
    opacity var(--duration-scene-opacity) var(--ease-decelerate-quart),
    transform var(--duration-scene-translate) var(--ease-decelerate-quint);
}

.expand-enter-from,
.expand-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* ---------------------------------- dialog --------------------------------- */

.dialog-scrim {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--black-overlay);
}

.dialog {
  width: min(420px, 100%);
  padding: 20px 20px 12px;
  border-radius: var(--card-radius);
  background: var(--color-background-floating);
  box-shadow: 0 8px 24px var(--shadow-color);
}

.dialog__title {
  margin: 0 0 12px;
  font-size: clamp(16px, 18px, 22px);
  font-weight: 700;
  color: var(--text-color-primary);
}

.dialog__message {
  margin: 0;
  font-size: clamp(13px, 14px, 16px);
  line-height: 1.5;
  color: var(--text-color-secondary);
  word-break: break-all;
}

.dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  margin-top: 16px;
  padding-top: 8px;
  border-top: 1px solid var(--color-divider);
}

.dialog-enter-active,
.dialog-leave-active {
  transition: opacity var(--duration-scene-opacity) var(--ease-decelerate-quart);
}

.dialog-enter-active .dialog,
.dialog-leave-active .dialog {
  transition:
    transform var(--duration-scene-translate) var(--ease-decelerate-quint),
    opacity var(--duration-scene-opacity) var(--ease-decelerate-quart);
}

.dialog-enter-from,
.dialog-leave-to {
  opacity: 0;
}

.dialog-enter-from .dialog,
.dialog-leave-to .dialog {
  transform: translateY(16px) scale(0.97);
  opacity: 0;
}

/* --------------------------------- snackbar -------------------------------- */

.snackbar {
  position: fixed;
  left: 50%;
  /* 避开 Home 指示条（无刘海设备解析为 0）。 */
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
  .toolbar__chip-dot,
  .expand-enter-active,
  .expand-leave-active,
  .dialog-enter-active .dialog,
  .dialog-leave-active .dialog,
  .snack-enter-active,
  .snack-leave-active,
  .banner-enter-active,
  .banner-leave-active {
    animation: none;
    transition: none;
  }
}
</style>
