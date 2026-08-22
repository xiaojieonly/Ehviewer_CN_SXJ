<!--
  AdminEhSession.vue — 管理面板「EH 会话」页（阶段二 P2）：E-Hentai 总控页.

  汇集 E-Hentai 相关的服务器侧配置：

    - 站点选择：GET /auth/eh-session 返回 gallerySite（0=e-hentai，1=exhentai），
      切换走 PUT /auth/eh-site {gallerySite}。
    - 会话状态：EH 登录在 Android App 内完成（WebView 通过 Cloudflare/CAPTCHA），
      cookie 经 ehSession 同步实体自动上传；本页 POST /auth/eh-logout 撤销、
      展示 cookie 列表，并提供「刷新状态」检测同步结果。
    - 出站代理：GET/PUT /api/v1/settings {proxy} 读取/保存，POST /api/v1/proxy/test
      用表单当前值测试连通性。

  已登录时列出身份 cookie（可逐行复制），并给出当前站点的常用外链
  （uconfig / mytags / 首页，随 gallerySite 在 e-hentai.org 与 exhentai.org 间切换）。
  样式沿用 AdminServer 的内容列 + 偏好分组范式，snackbar 反馈同一模式。
-->
<template>
  <div class="eh-session">
    <div class="eh-session__column">
      <header class="eh-session__header">
        <h1 class="eh-session__title">EH 会话</h1>
        <Transition name="saved">
          <span v-if="savedFlash" class="eh-session__saved" role="status">已保存</span>
        </Transition>
        <span
          v-if="session"
          class="eh-session__badge"
          :class="`eh-session__badge--${badgeKind}`"
          role="status"
        >
          {{ badgeLabel }}
        </span>
      </header>

      <!-- ═══ 站点 ═══════════════════════════════════════════════════════ -->
      <section>
        <SectionHeader title="站点" />
        <PrefCard>
          <PrefRow
            icon="top-lists"
            title="站点"
            summary="搜索、阅读与下载所使用的画廊站点"
          >
            <AppSegmented
              :model-value="String(gallerySite)"
              :options="SITE_OPTIONS"
              aria-label="站点"
              @update:model-value="onSiteChange"
            />
          </PrefRow>
        </PrefCard>
      </section>

      <!-- ═══ 登录引导（未登录 / 已过期） ═════════════════════════════════ -->
      <section v-if="!session || !session.signedIn || session.expired">
        <SectionHeader title="EH 会话登录" />
        <PrefCard>
          <PrefRow
            icon="mobile-hand-left"
            title="在 Android 上登录"
            summary="EH 登录请在 Android App 中完成，登录成功后会自动同步到本服务器"
          >
            <div class="eh-session__guide-actions">
              <button
                type="button"
                class="eh-session__btn eh-session__btn--secondary"
                @click="goPairDevices"
              >
                配对设备
              </button>
              <button
                type="button"
                class="eh-session__btn eh-session__btn--primary"
                :disabled="checkingSession"
                @click="checkSession"
              >
                {{ checkingSession ? '检查中…' : '刷新状态' }}
              </button>
            </div>
          </PrefRow>
        </PrefCard>
        <p class="eh-session__note">
          提示：EH 登录需在 Android App 中完成（App 内置浏览器可正常通过 Cloudflare 等验证）。若设备尚未配对，请先到「设备与配对」生成配对码；登录后 App 会自动将会话 cookie 同步到本服务器，点击「刷新状态」确认同步结果。
        </p>
      </section>

      <!-- ═══ 已登录：身份 cookie 列表 ═══════════════════════════════════ -->
      <section v-else>
        <SectionHeader title="身份 Cookie" />
        <PrefCard>
          <PrefRow
            v-for="c in session.cookies"
            :key="c.name"
            icon="cookie-brown"
            :title="c.name"
            :summary="c.domain"
          >
            <div class="eh-session__cookie">
              <span class="eh-session__cookie-value" :title="c.value">{{ c.value }}</span>
              <button
                type="button"
                class="eh-session__copy"
                :aria-label="`复制 ${c.name}`"
                title="复制"
                @click="copyCookie(c)"
              >
                <AppIcon name="copy" size="16px" />
              </button>
            </div>
          </PrefRow>
        </PrefCard>

        <div class="eh-session__actions">
          <button
            type="button"
            class="eh-session__btn eh-session__btn--danger"
            :disabled="loggingOut"
            @click="logout"
          >
            {{ loggingOut ? '登出中…' : '登出' }}
          </button>
        </div>
      </section>

      <!-- ═══ 代理 ═══════════════════════════════════════════════════════ -->
      <section>
        <SectionHeader title="代理" />
        <PrefCard>
          <PrefRow icon="send-dark" title="启用代理" summary="服务器访问上游站点时通过代理转发（搜索、图片、下载）">
            <AppSwitch
              :model-value="proxyForm.enabled"
              aria-label="启用代理"
              :disabled="proxyLoading"
              @update:model-value="(v) => (proxyForm.enabled = v)"
            />
          </PrefRow>
          <PrefRow icon="settings-dark" title="代理类型" summary="HTTP / HTTPS 代理或 SOCKS5 代理">
            <AppSelect
              :model-value="proxyForm.type"
              :options="PROXY_TYPE_OPTIONS"
              :disabled="proxyLoading"
              @update:model-value="(v) => (proxyForm.type = String(v))"
            />
          </PrefRow>
          <PrefRow icon="mobile-hand-left" title="服务器地址" summary="代理主机与端口">
            <div class="proxy-fields">
              <div class="proxy-fields__host">
                <AppTextField
                  :model-value="proxyForm.host"
                  placeholder="127.0.0.1"
                  :disabled="proxyLoading"
                  aria-label="代理服务器地址"
                  @update:model-value="(v) => (proxyForm.host = v)"
                  @keydown.enter="testProxyConnection"
                />
              </div>
              <div class="proxy-fields__port">
                <AppTextField
                  :model-value="proxyForm.port === 0 ? '' : String(proxyForm.port)"
                  type="number"
                  placeholder="7890"
                  :disabled="proxyLoading"
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
                :model-value="proxyForm.username"
                :disabled="proxyLoading"
                aria-label="代理用户名"
                @update:model-value="(v) => (proxyForm.username = v)"
              />
            </div>
          </PrefRow>
          <PrefRow title="密码">
            <div class="proxy-auth-field">
              <AppTextField
                :model-value="proxyForm.password"
                type="password"
                :placeholder="proxyPasswordSet && proxyForm.password === '' ? '已设置（留空保持不变）' : ''"
                :disabled="proxyLoading"
                aria-label="代理密码"
                @update:model-value="(v) => (proxyForm.password = v)"
              />
            </div>
          </PrefRow>
        </PrefCard>
        <div class="eh-session__actions">
          <button
            type="button"
            class="eh-session__btn eh-session__btn--secondary"
            :disabled="proxyTesting || proxyLoading"
            @click="testProxyConnection"
          >
            {{ proxyTesting ? '测试中…' : '测试连接' }}
          </button>
          <button
            type="button"
            class="eh-session__btn eh-session__btn--primary"
            :disabled="proxySaving || proxyLoading"
            @click="saveProxy"
          >
            {{ proxySaving ? '保存中…' : '保存并应用' }}
          </button>
        </div>

        <Transition name="result">
          <p
            v-if="proxyTestResult"
            class="proxy-result"
            :class="{ 'is-error': !proxyTestResult.success }"
            role="status"
          >
            <AppIcon
              :name="proxyTestResult.success ? 'check-dark' : 'alert-red'"
              class="proxy-result__icon"
              size="16px"
            />
            <span>
              {{
                proxyTestResult.success
                  ? `连接成功，延迟 ${proxyTestResult.latencyMs}ms`
                  : `连接失败：${proxyTestResult.error || '未知错误'}`
              }}
            </span>
          </p>
        </Transition>

        <p class="eh-session__note">
          提示：代理仅作用于服务器访问上游站点的出站流量，保存后立即生效；本代理不影响浏览器或 App 访问 WebUI 服务器。
        </p>
      </section>

      <!-- ═══ E-Hentai 外链 ═════════════════════════════════════════════ -->
      <section>
        <SectionHeader title="E-Hentai" />
        <PrefCard>
          <PrefRow icon="go-to-dark" title="用户设置" summary="打开 uconfig 设置页">
            <button
              type="button"
              class="eh-session__link"
              aria-label="打开用户设置"
              @click="openLink(ehBase + '/uconfig.php')"
            >
              <AppIcon name="go-to-dark" size="20px" />
            </button>
          </PrefRow>
          <PrefRow icon="top-lists" title="我的标签" summary="打开 mytags 页面">
            <button
              type="button"
              class="eh-session__link"
              aria-label="打开我的标签"
              @click="openLink(ehBase + '/mytags')"
            >
              <AppIcon name="go-to-dark" size="20px" />
            </button>
          </PrefRow>
          <PrefRow icon="homepage-black" :title="ehLabel + ' 首页'" :summary="`打开 ${ehBase}`">
            <button
              type="button"
              class="eh-session__link"
              :aria-label="`打开 ${ehLabel} 首页`"
              @click="openLink(ehBase)"
            >
              <AppIcon name="go-to-dark" size="20px" />
            </button>
          </PrefRow>
        </PrefCard>
      </section>
    </div>

    <!-- Snackbar. -->
    <Transition name="snack">
      <div v-if="snack" class="eh-session__snackbar" role="status">{{ snack }}</div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi, type EhSessionCookie, type EhSessionResponse } from '@/api/auth'
import { settingsApi, type ProxySettings } from '@/api/settings'
import { testProxy, type ProxyTestResult } from '@/api/proxy'
import AppIcon from '@/components/atoms/AppIcon.vue'
import {
  AppSegmented,
  AppSelect,
  AppSwitch,
  AppTextField,
  PrefCard,
  PrefRow,
  SectionHeader,
} from '@/components/form'

const SITE_OPTIONS = [
  { value: '0', label: 'e-hentai（无需登录）' },
  { value: '1', label: 'exhentai（需登录）' },
]

const PROXY_TYPE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: 'http', label: 'HTTP' },
  { value: 'socks5', label: 'SOCKS5' },
]

const router = useRouter()

const session = ref<EhSessionResponse | null>(null)
const gallerySite = ref(0)
const siteSwitching = ref(false)
const checkingSession = ref(false)
const loggingOut = ref(false)

const proxyForm = reactive<ProxySettings>({
  enabled: false,
  type: 'http',
  host: '',
  port: 0,
  username: '',
  password: '',
})
const proxyLoading = ref(true)
const proxySaving = ref(false)
const proxyTesting = ref(false)
const savedFlash = ref(false)
const proxyPasswordSet = ref(false)
const proxyTestResult = ref<ProxyTestResult | null>(null)

const snack = ref('')
let snackTimer: number | undefined
let savedFlashTimer: number | undefined

function showSnack(message: string): void {
  snack.value = message
  if (snackTimer) window.clearTimeout(snackTimer)
  snackTimer = window.setTimeout(() => {
    snack.value = ''
  }, 2600)
}

const badgeKind = computed(() => {
  if (!session.value) return 'unknown'
  if (session.value.expired) return 'expired'
  if (session.value.signedIn) return 'signed-in'
  return 'signed-out'
})

const badgeLabel = computed(() => {
  if (!session.value) return ''
  if (session.value.expired) return '已过期'
  if (session.value.signedIn) return '已登录'
  return '未登录'
})

const ehBase = computed(() => (gallerySite.value === 1 ? 'https://exhentai.org' : 'https://e-hentai.org'))
const ehLabel = computed(() => (gallerySite.value === 1 ? 'exhentai' : 'e-hentai'))

function messageOf(thrown: unknown, fallback: string): string {
  const err = thrown as {
    response?: { data?: { message?: string } }
    message?: string
  }
  return err.response?.data?.message ?? err.message ?? fallback
}

async function refresh(): Promise<void> {
  try {
    const state = await authApi.ehSession()
    session.value = state
    gallerySite.value = state.gallerySite ?? 0
  } catch (e) {
    console.error('[AdminEhSession] failed to load EH session', e)
    showSnack('无法获取 EH 会话状态')
  }
}

async function onSiteChange(value: string): Promise<void> {
  const site = Number(value)
  if (site === gallerySite.value || siteSwitching.value) return
  const previous = gallerySite.value
  gallerySite.value = site
  siteSwitching.value = true
  try {
    const response = await authApi.setEhSite(site)
    if (response.success) {
      showSnack('站点已切换')
    } else {
      gallerySite.value = previous
      showSnack(response.message || '站点切换失败')
    }
  } catch (thrown) {
    gallerySite.value = previous
    showSnack(messageOf(thrown, '站点切换失败'))
  } finally {
    siteSwitching.value = false
  }
}

/** 跳到「设备与配对」页生成配对码。 */
function goPairDevices(): void {
  void router.push('/admin/devices')
}

/** 重新拉取 EH 会话状态，检测 Android 端登录是否已同步到本服务器。 */
async function checkSession(): Promise<void> {
  if (checkingSession.value) return
  checkingSession.value = true
  try {
    await refresh()
    const state = session.value
    showSnack(
      state && state.signedIn && !state.expired
        ? 'EH 会话已同步到服务器'
        : state?.expired
          ? 'EH 会话已过期，请在 Android 重新登录'
          : '尚未检测到 EH 会话，请在 Android 完成登录',
    )
  } finally {
    checkingSession.value = false
  }
}

async function logout(): Promise<void> {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    const response = await authApi.ehLogout()
    showSnack(response.success ? '已登出 EH 会话' : response.message || '登出失败')
    await refresh()
  } catch (thrown) {
    showSnack(messageOf(thrown, '登出失败，请检查服务器'))
  } finally {
    loggingOut.value = false
  }
}

async function copyCookie(c: EhSessionCookie): Promise<void> {
  try {
    await navigator.clipboard.writeText(c.value)
    showSnack(`已复制 ${c.name}`)
  } catch {
    showSnack('复制失败')
  }
}

function openLink(url: string): void {
  window.open(url, '_blank', 'noopener')
}

/* ------------------------------ 代理 ------------------------------ */

function flashSaved() {
  savedFlash.value = true
  if (savedFlashTimer) window.clearTimeout(savedFlashTimer)
  savedFlashTimer = window.setTimeout(() => {
    savedFlash.value = false
  }, 1500)
}

async function loadProxy(): Promise<void> {
  proxyLoading.value = true
  try {
    const settings = await settingsApi.get()
    Object.assign(proxyForm, settings.proxy)
    proxyPasswordSet.value = settings.proxy.proxyPasswordSet ?? false
  } catch (e) {
    showSnack(`加载失败：${messageOf(e, '加载失败')}`)
  } finally {
    proxyLoading.value = false
  }
}

async function saveProxy(): Promise<void> {
  proxySaving.value = true
  proxyTestResult.value = null
  try {
    const { password: pwd, ...proxy } = proxyForm
    const payload = pwd ? { ...proxy, password: pwd } : proxy
    await settingsApi.update({ proxy: payload as ProxySettings })
    if (pwd) {
      proxyPasswordSet.value = true
      proxyForm.password = ''
    }
    flashSaved()
    showSnack('代理设置已保存并生效')
  } catch (e) {
    showSnack(`保存失败：${messageOf(e, '保存失败')}`)
  } finally {
    proxySaving.value = false
  }
}

async function testProxyConnection(): Promise<void> {
  proxyTesting.value = true
  try {
    proxyTestResult.value = await testProxy({ ...proxyForm })
  } catch (e) {
    proxyTestResult.value = { success: false, latencyMs: 0, error: messageOf(e, '未知错误') }
  } finally {
    proxyTesting.value = false
  }
}

function onPortInput(value: string): void {
  proxyForm.port = Number(value) || 0
}

onBeforeUnmount(() => {
  if (snackTimer) window.clearTimeout(snackTimer)
  if (savedFlashTimer) window.clearTimeout(savedFlashTimer)
})

onMounted(() => {
  refresh()
  loadProxy()
})
</script>

<style scoped>
/* Content column — rendered inside AdminLayout's scrollable content area. */
.eh-session__column {
  max-width: 760px;
  margin: 0 auto;
  padding: 4px var(--keyline-margin) calc(24px + var(--safe-area-bottom));
}

.eh-session__header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 16px var(--keyline-margin) 4px;
}

.eh-session__title {
  margin: 0;
  font-size: clamp(18px, 22px, 26px);
  font-weight: 700;
  letter-spacing: 0.01em;
  color: var(--text-color-primary);
}

/* ------------------------------- 状态徽标 -------------------------------- */

.eh-session__saved {
  padding: 3px 10px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-primary) 14%, transparent);
  color: var(--color-primary-text, var(--color-primary-dark));
  font-size: var(--text-super-small);
  font-weight: 700;
  letter-spacing: 0.05em;
}

.eh-session__badge {
  flex: 0 0 auto;
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--text-color-secondary);
  font-size: clamp(10px, 11px, 13px);
  font-weight: 700;
  letter-spacing: 0.05em;
}

.eh-session__badge--signed-in {
  background: color-mix(in srgb, var(--color-primary) 14%, transparent);
  color: var(--color-primary-text, var(--color-primary-dark));
}

.eh-session__badge--expired {
  background: color-mix(in srgb, #e5484d 12%, transparent);
  color: #e5484d;
}

/* ------------------------------- 引导操作 -------------------------------- */

.eh-session__guide-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

/* ------------------------------- cookie 行 ------------------------------ */

.eh-session__cookie {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 240px;
}

.eh-session__cookie-value {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: clamp(11px, 12px, 14px);
  color: var(--text-color-secondary);
}

.eh-session__copy {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  padding: 4px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--drawable-color-secondary);
  cursor: pointer;
}

.eh-session__copy:hover {
  background: var(--color-surface);
}

/* ------------------------------- 操作按钮 -------------------------------- */

.eh-session__actions {
  display: flex;
  gap: 12px;
  margin-top: 14px;
}

.eh-session__btn {
  min-width: 96px;
  padding: 8px 18px;
  border-radius: 999px;
  font-size: var(--text-super-small);
  font-weight: 600;
  cursor: pointer;
  transition:
    background-color 150ms var(--ease-decelerate-quart),
    box-shadow 150ms var(--ease-decelerate-quart);
}

.eh-session__btn:disabled {
  opacity: 0.6;
  cursor: default;
}

.eh-session__btn--primary {
  border: none;
  background: var(--color-primary);
  color: var(--color-white);
}

.eh-session__btn--secondary {
  border: 1px solid color-mix(in srgb, var(--color-primary) 55%, transparent);
  background: transparent;
  color: var(--color-primary-text, var(--color-primary-dark));
}

.eh-session__btn--danger {
  border: 1px solid color-mix(in srgb, #e5484d 55%, transparent);
  background: transparent;
  color: #e5484d;
}

/* -------------------------------- 代理 -------------------------------- */

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

.eh-session__note {
  margin: 12px 4px 0;
  font-size: var(--text-super-small);
  color: var(--text-color-secondary);
}

/* -------------------------------- 外链按钮 -------------------------------- */

.eh-session__link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 6px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--drawable-color-secondary);
  cursor: pointer;
}

.eh-session__link:hover {
  background: var(--color-surface);
}

/* ---------------------------------- snackbar -------------------------------- */

.eh-session__snackbar {
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

.saved-enter-active,
.saved-leave-active {
  transition: opacity 200ms var(--ease-decelerate-quart);
}

.saved-enter-from,
.saved-leave-to {
  opacity: 0;
}

.result-enter-active,
.result-leave-active {
  transition:
    opacity 200ms var(--ease-decelerate-quart),
    transform 200ms var(--ease-decelerate-quart);
}

.result-enter-from,
.result-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

@media (prefers-reduced-motion: reduce) {
  .snack-enter-active,
  .snack-leave-active {
    transition: none;
  }
}
</style>
