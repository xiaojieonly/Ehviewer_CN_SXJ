/**
 * EH 可达性状态（plan-2026-08-30 §3.1/§0）——前端侧镜像服务器的熔断状态机。
 *
 * **模块级单例（非 pinia）**：响应拦截器（api/client.ts）在 pinia 安装前的
 * 请求（登录页）也可能收到 `EH_UNAVAILABLE` 并需要落标记，模块级 reactive
 * 没有初始化时序问题（对照 composables/useWebSocket 的连接单例风格）。
 * 视图直接 import { availability, loadAvailability, markDown } 消费。
 *
 * 语义（用户已定，不得变更）：
 * - `unknown`（服务器启动后未探测）/ `up` / `down`（记录 downAt、lastReason）；
 * - 一旦 DOWN，会话期不再自动访问 EH——服务器短路；只有手动探测
 *   （probeAvailability，即 AvailabilityBanner 的「重新连接」）才能恢复 UP；
 * - markDown 由响应拦截器与视图的错误处理调用，避免等下一次 load。
 */
import { reactive } from 'vue'
import { siteApi } from '@/api/site'
import type { AvailabilityResponse } from '@/api/site'

export type AvailabilityState = 'unknown' | 'up' | 'down'

/** 前端默认提示文案（与服务器错误消息同义）。 */
export const EH_UNAVAILABLE_MESSAGE = 'EH 平台当前不可达，仅显示本地内容'

export const availability = reactive<{
  /** null = 尚未加载（loading）；见 states: 'unknown' | 'up' | 'down'。 */
  state: AvailabilityState | null
  downAt: number | null
  lastReason: string | null
  lastLoadedAt: number | null
}>({
  state: null,
  downAt: null,
  lastReason: null,
  lastLoadedAt: null,
})

/** 单调请求守卫 + 单飞：多个视图同时 load 只发一次请求。 */
let loadSeq = 0
let inFlight: Promise<void> | null = null

function applyAvailability(response: AvailabilityResponse): void {
  availability.state =
    response.state === 'DOWN' ? 'down' : response.state === 'UP' ? 'up' : 'unknown'
  availability.downAt = response.downAt ?? null
  availability.lastReason = response.lastReason ?? null
  availability.lastLoadedAt = Date.now()
}

/**
 * 读取服务器当前状态（幂等：in-flight 请求复用；过期响应按 loadSeq 丢弃）。
 */
export async function loadAvailability(): Promise<void> {
  if (inFlight !== null) return inFlight
  const seq = ++loadSeq
  const task = (async () => {
    try {
      const res = await siteApi.getAvailability()
      if (seq !== loadSeq) return
      applyAvailability(res)
    } catch (error) {
      if (seq !== loadSeq) return
      console.error('[availability] 状态读取失败（保持当前认知）', error)
    } finally {
      if (seq === loadSeq) inFlight = null
    }
  })()
  inFlight = task
  return task
}

/** 立即置 DOWN（响应拦截器 / 视图 EH 错误处理调用，不等下一次 load）。 */
export function markDown(reason?: string | null): void {
  availability.state = 'down'
  availability.downAt = availability.downAt ?? Date.now()
  availability.lastReason = reason ?? EH_UNAVAILABLE_MESSAGE
}

/** 立即置 UP（手动探测成功）。 */
export function markUp(): void {
  availability.state = 'up'
  availability.downAt = null
  availability.lastReason = null
  availability.lastLoadedAt = Date.now()
}

/** 回到未探测状态（启动后首次调用前；进程重启语义）。 */
export function markUnknown(): void {
  availability.state = 'unknown'
  availability.downAt = null
  availability.lastReason = null
}

/**
 * 原始探测（不改状态）：返回服务器应答，请求失败返回 null。
 * 供 AvailabilityBanner 组合「先 emit 后应用状态」的时序（见
 * applyProbeResult：markUp 会调度父级 v-if 卸载，若 emit 在应用状态后才
 * 触发会被组件的卸载竞态吞掉）。
 */
export async function probeRaw(): Promise<AvailabilityResponse | null> {
  try {
    return await siteApi.probeAvailability()
  } catch {
    return null
  }
}

/** 应用探测结果：UP → 恢复；DOWN/失败 → 置 DOWN；返回「已恢复 UP」。 */
export function applyProbeResult(res: AvailabilityResponse | null): boolean {
  if (res?.state === 'UP') {
    markUp()
    return true
  }
  availability.state = 'down'
  availability.downAt = res?.downAt ?? Date.now()
  availability.lastReason = res?.lastReason ?? EH_UNAVAILABLE_MESSAGE
  availability.lastLoadedAt = Date.now()
  return false
}

/**
 * 手动探测一次（AvailabilityBanner「重新连接」）：
 * 成功（服务器返回 UP）→ 置 UP 返回 true；仍 DOWN 或请求失败 → 维持/置 DOWN
 * 返回 false。（§0.3：只有用户手动动作才允许一次探测。）
 */
export async function probeAvailability(): Promise<boolean> {
  return applyProbeResult(await probeRaw())
}

/** 重新校验（= 读取当前状态；手动恢复路径用 probeAvailability）。 */
export function revalidate(): void {
  void loadAvailability()
}
