/**
 * 内容打码模式（管理面板-高级 开关，2026-09-04）。
 *
 * 勾选后整个 WebUI 进入"可截图"形态：标题 → 内容序列号 `#<gid>`
 * （`maskedTitle`）；图片则 **照常发起真实请求**（缩略图/页图链路、
 * 服务端缓存预热与访问统计不变），只是渲染像素被
 * `styles/privacy-mask.css`（`<html>.privacy-mask` 作用域）隐藏并在
 * 内容容器垫上占位图 —— 即"访问完整存在，前端只见替代品"。
 *
 * **状态的服务端权威**：开关同时持久化到服务器（`/privacy/mask`，
 * main.ts 启动时拉取）——开启时后端 PrivacyMaskFilter 对 API 响应统一
 * 脱敏，Agent 等无头客户端同样只能拿到脱敏数据。localStorage 仅作
 * 重载时的乐观引导缓存。
 */
import { ref, watch } from 'vue'

const STORAGE_KEY = 'anotherviewer-privacy-mask'
const HTML_CLASS = 'privacy-mask'

function readStored(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === '1'
  } catch {
    // Storage unavailable (SSR / privacy mode) — default off.
    return false
  }
}

/** 打码开关本体；组件直接读取以建立响应依赖。 */
export const privacyMaskEnabled = ref(readStored())

function applyHtmlClass(enabled: boolean): void {
  if (typeof document !== 'undefined') {
    document.documentElement.classList.toggle(HTML_CLASS, enabled)
  }
}

/** 勾选/取消勾选（AdminAdvanced）；乐观更新本地状态与引导缓存，
 *  权威持久化在服务端（/privacy/mask），启动时以服务端值为准。 */
export function setPrivacyMaskEnabled(enabled: boolean): void {
  privacyMaskEnabled.value = enabled
  try {
    if (enabled) {
      localStorage.setItem(STORAGE_KEY, '1')
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  } catch {
    // ignore write failures — in-memory state still applies this session
  }
}

// 模块加载即同步初始类（首屏无闪烁），并随开关实时切换（sync flush，
// 同 theme.ts——DOM 更新与状态变更同刻生效）。
applyHtmlClass(privacyMaskEnabled.value)
watch(privacyMaskEnabled, applyHtmlClass, { flush: 'sync' })

/** 标题展示：开启时以内容序列号 `#<gid>` 替代；回退链由调用方先拼好。 */
export function maskedTitle(title: string, gid: number): string {
  return privacyMaskEnabled.value ? `#${gid}` : title
}

/**
 * 路径/文件名展示：打码开启时只出前 10 个字符（傻快方案，2026-09-04 用户
 * 裁决——风控看的是界面展示的列表内容，网络层完整传输无风险），关闭时
 * 原样透传（维护作业需要完整路径）。
 */
export function maskedPath(path: string, max = 10): string {
  return privacyMaskEnabled.value ? path.slice(0, max) : path
}
