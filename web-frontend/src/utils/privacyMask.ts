/**
 * 隐私打码模式（管理面板-高级 开关，plan-2026-09-04）.
 *
 * 勾选后整个 WebUI 进入"可截图"形态，便于把界面分享给他人协作而不泄露
 * 内容：
 *   - 标题 → 内容序列号 `#<gid>`（`maskedTitle`）；
 *   - 图片 → 中性占位图（`maskedImageSrc`），真实 URL 不再发起请求。
 *
 * 状态是模块级 `ref`（非 Pinia）：`pageImageUrl` 等纯 URL 工具也会消费，
 * 需要在任意调用上下文可读可依赖；持久化沿 theme 惯例落 localStorage。
 * 「清除本地数据」（anotherviewer- 前缀全清）会一并重置本开关，符合语义。
 */
import { ref } from 'vue'

const STORAGE_KEY = 'anotherviewer-privacy-mask'

function readStored(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === '1'
  } catch {
    // Storage unavailable (SSR / privacy mode) — default off.
    return false
  }
}

/** 打码开关本体；组件/工具函数直接读取以建立响应依赖。 */
export const privacyMaskEnabled = ref(readStored())

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

/**
 * 3:4 竖版中性占位图（灰底 + 山/日图形）。`object-fit: cover` 下作缩略图、
 * `contain` 下作阅读页均成立；深浅主题皆可读。
 */
const PLACEHOLDER_SVG =
  "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 300 400'>" +
  "<rect width='300' height='400' fill='#b0bec5'/>" +
  "<circle cx='108' cy='122' r='28' fill='#78909c'/>" +
  "<path d='M30 330L128 208L186 280L218 244L270 330Z' fill='#78909c'/>" +
  '</svg>'

export const PRIVACY_PLACEHOLDER_SRC = `data:image/svg+xml,${encodeURIComponent(PLACEHOLDER_SVG)}`

/** 图片 src：开启时返回占位图（真实地址根本不发请求），关闭时原样透传。 */
export function maskedImageSrc(url: string | null | undefined): string {
  return privacyMaskEnabled.value ? PRIVACY_PLACEHOLDER_SRC : (url ?? '')
}

/** 标题展示：开启时以内容序列号 `#<gid>` 替代；回退链由调用方先拼好。 */
export function maskedTitle(title: string, gid: number): string {
  return privacyMaskEnabled.value ? `#${gid}` : title
}
