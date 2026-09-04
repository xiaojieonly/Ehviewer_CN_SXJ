/**
 * 隐私打码模式（管理面板-高级 开关，plan-2026-09-04）.
 *
 * 勾选后整个 WebUI 进入"可截图"形态：标题 → 内容序列号 `#<gid>`
 * （`maskedTitle`）；图片则 **照常发起真实请求**（缩略图/页图链路、
 * 服务端缓存预热与访问统计不变），只是渲染像素被
 * `styles/privacy-mask.css`（`<html>.privacy-mask` 作用域）隐藏并在
 * 内容容器垫上占位图 —— 即"访问完整存在，前端只见替代品"。
 *
 * 状态是模块级 `ref`（非 Pinia）：展示组件需在任意调用上下文可读可依赖；
 * 持久化沿 theme 惯例落 localStorage，开关变化同步到 `<html>` 类。
 * 「清除本地数据」（anotherviewer- 前缀全清）会一并重置本开关，符合语义。
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

/** 勾选/取消勾选（AdminAdvanced）；同步持久化，刷新后保持。 */
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
