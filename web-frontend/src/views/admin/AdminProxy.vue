<!--
  AdminProxy.vue — 管理面板 · 代理（出站代理配置）.

  控制 WebUI 服务器访问 E-Hentai 时的出站代理（搜索 / 图片 / 下载流量）。
  配置写入 server_config，通过运行时 ProxySelector 立即生效，无需重启；
  不影响客户端（浏览器 / App）访问 WebUI 本身。

  端点:
    - GET  /api/v1/settings             读取当前配置
    - PUT  /api/v1/settings             { proxy: {...} } 保存
    - POST /api/v1/proxy/test           用表单当前值测试连通性
-->
<template>
  <div class="admin-proxy">
    <div class="admin-proxy__column">
      <header class="admin-proxy__header">
        <h1 class="admin-proxy__heading">代理</h1>
        <Transition name="saved">
          <span v-if="savedFlash" class="admin-proxy__saved" role="status">已保存</span>
        </Transition>
      </header>

      <section>
        <SectionHeader title="出站代理" />
        <PrefCard>
          <PrefRow icon="send-dark" title="启用代理" summary="服务器访问 E-Hentai 时通过代理转发（搜索、图片、下载）">
            <AppSwitch
              :model-value="form.enabled"
              aria-label="启用代理"
              :disabled="loading"
              @update:model-value="(v) => (form.enabled = v)"
            />
          </PrefRow>
          <PrefRow icon="settings-dark" title="代理类型" summary="HTTP / HTTPS 代理或 SOCKS5 代理">
            <AppSelect
              :model-value="form.type"
              :options="PROXY_TYPE_OPTIONS"
              :disabled="loading"
              @update:model-value="(v) => (form.type = String(v))"
            />
          </PrefRow>
          <PrefRow icon="mobile-hand-left" title="服务器地址" summary="代理主机与端口">
            <div class="proxy-fields">
              <div class="proxy-fields__host">
                <AppTextField
                  :model-value="form.host"
                  placeholder="127.0.0.1"
                  :disabled="loading"
                  aria-label="代理服务器地址"
                  @update:model-value="(v) => (form.host = v)"
                  @keydown.enter="test"
                />
              </div>
              <div class="proxy-fields__port">
                <AppTextField
                  :model-value="form.port === 0 ? '' : String(form.port)"
                  type="number"
                  placeholder="7890"
                  :disabled="loading"
                  aria-label="代理端口"
                  @update:model-value="onPortInput"
                />
              </div>
            </div>
          </PrefRow>
          <PrefRow icon="sec-primary" title="认证（可选）" summary="代理需要用户名密码时填写" />
          <PrefRow title="用户名">
            <div class="proxy-auth-field">
              <AppTextField
                :model-value="form.username"
                :disabled="loading"
                aria-label="代理用户名"
                @update:model-value="(v) => (form.username = v)"
              />
            </div>
          </PrefRow>
          <PrefRow title="密码">
            <div class="proxy-auth-field">
              <AppTextField
                :model-value="form.password"
                type="password"
                :disabled="loading"
                aria-label="代理密码"
                @update:model-value="(v) => (form.password = v)"
              />
            </div>
          </PrefRow>
        </PrefCard>
        <div class="admin-proxy__actions">
          <button type="button" class="btn-secondary" :disabled="testing || loading" @click="test">
            {{ testing ? '测试中…' : '测试连接' }}
          </button>
          <button type="button" class="btn-primary" :disabled="saving || loading" @click="save">
            {{ saving ? '保存中…' : '保存并应用' }}
          </button>
        </div>

        <Transition name="result">
          <p v-if="testResult" class="proxy-result" :class="{ 'is-error': !testResult.success }" role="status">
            <AppIcon :name="testResult.success ? 'check-dark' : 'alert-red'" class="proxy-result__icon" size="16px" />
            <span>
              {{ testResult.success ? `连接成功，延迟 ${testResult.latencyMs}ms` : `连接失败：${testResult.error || '未知错误'}` }}
            </span>
          </p>
        </Transition>
      </section>

      <p class="admin-proxy__note">
        提示：代理仅作用于服务器访问 E-Hentai 的出站流量，保存后立即生效；本代理不影响浏览器或 App 访问 WebUI 服务器。
      </p>
    </div>

    <!-- Snackbar. -->
    <Transition name="snack">
      <div v-if="snack" class="snackbar" role="status">{{ snack }}</div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { settingsApi, type ProxySettings } from '@/api/settings'
import client from '@/api/client'
import AppIcon from '@/components/atoms/AppIcon.vue'
import { AppSelect, AppSwitch, AppTextField, PrefCard, PrefRow, SectionHeader } from '@/components/form'

interface ProxyTestResult {
  success: boolean
  latencyMs: number
  error: string
}

const PROXY_TYPE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: 'http', label: 'HTTP' },
  { value: 'socks5', label: 'SOCKS5' },
]

const form = reactive<ProxySettings>({
  enabled: false,
  type: 'http',
  host: '',
  port: 0,
  username: '',
  password: '',
})

const loading = ref(true)
const saving = ref(false)
const testing = ref(false)
const savedFlash = ref(false)
const testResult = ref<ProxyTestResult | null>(null)
const snack = ref('')

function flashSaved() {
  savedFlash.value = true
  setTimeout(() => (savedFlash.value = false), 1500)
}

async function load() {
  loading.value = true
  try {
    const settings = await settingsApi.get()
    Object.assign(form, settings.proxy)
  } catch (e) {
    snack.value = `加载失败：${messageOf(e)}`
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  testResult.value = null
  try {
    await settingsApi.update({ proxy: { ...form } })
    flashSaved()
    snack.value = '代理设置已保存并生效'
  } catch (e) {
    snack.value = `保存失败：${messageOf(e)}`
  } finally {
    saving.value = false
  }
}

async function test() {
  testing.value = true
  try {
    const { data } = await client.post('/proxy/test', { ...form })
    testResult.value = data
  } catch (e) {
    testResult.value = { success: false, latencyMs: 0, error: messageOf(e) }
  } finally {
    testing.value = false
  }
}

function onPortInput(value: string): void {
  form.port = Number(value) || 0
}

function messageOf(e: unknown): string {
  return e instanceof Error ? e.message : String(e)
}

onMounted(load)
</script>

<style scoped>
.admin-proxy {
  min-height: 100%;
}

.admin-proxy__column {
  max-width: 720px;
  margin: 0 auto;
  padding: 16px var(--keyline-margin) var(--safe-area-bottom);
}

.admin-proxy__header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.admin-proxy__heading {
  margin: 0;
  font-size: clamp(17px, 20px, 24px);
  font-weight: 600;
}

.admin-proxy__saved {
  padding: 2px 10px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-primary) 14%, transparent);
  color: var(--color-primary);
  font-size: var(--text-super-small);
}

.admin-proxy__actions {
  display: flex;
  gap: 12px;
  margin-top: 14px;
}

.btn-primary,
.btn-secondary {
  padding: 8px 18px;
  border-radius: 999px;
  font-size: var(--text-super-small);
  font-weight: 600;
  cursor: pointer;
}

.btn-primary {
  border: none;
  background: var(--color-primary);
  color: #fff;
}

.btn-secondary {
  border: 1px solid color-mix(in srgb, var(--color-primary) 55%, transparent);
  background: transparent;
  color: var(--color-primary);
}

.btn-primary:disabled,
.btn-secondary:disabled {
  opacity: 0.6;
  cursor: default;
}

.proxy-fields {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.proxy-fields__host {
  width: 150px;
}

.proxy-fields__port {
  width: 92px;
}

.proxy-auth-field {
  width: 180px;
}

.proxy-result {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px 0 0;
  padding: 10px 14px;
  border-radius: var(--card-radius);
  background: color-mix(in srgb, var(--color-primary) 10%, transparent);
  color: var(--color-primary);
  font-size: var(--text-super-small);
}

.proxy-result.is-error {
  background: color-mix(in srgb, #e5484d 10%, transparent);
  color: #e5484d;
}

.proxy-result__icon :deep(svg path) {
  fill: currentColor;
}

.admin-proxy__note {
  margin: 12px 4px 0;
  font-size: var(--text-super-small);
  color: var(--text-color-secondary);
}

.result-enter-active,
.result-leave-active {
  transition: opacity 200ms var(--ease-decelerate-quart), transform 200ms var(--ease-decelerate-quart);
}

.result-enter-from,
.result-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

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
