import { ref, onMounted, onUnmounted } from 'vue'

/**
 * PC 输入门（各视图复用）：`pointer: fine` 且视口 ≥720px。
 * 适用于 PC 形态增强（常驻批量工具条、右键菜单、悬停操作），
 * 触摸/窄屏仍走各自原始的移动端路径（FAB 等）。
 * 对照 GalleryCard F-UX6 的判定实现。
 */
export function usePcInput(): { pcInput: ReturnType<typeof ref<boolean>> } {
  const PC_MIN_WIDTH = 720

  const pcInput = ref(false)
  let pointerMql: MediaQueryList | null = null

  function refresh(): void {
    const finePointer =
      typeof window.matchMedia === 'function' &&
      window.matchMedia('(pointer: fine)').matches
    pcInput.value = finePointer && window.innerWidth >= PC_MIN_WIDTH
  }

  onMounted(() => {
    refresh()
    if (typeof window.matchMedia === 'function') {
      pointerMql = window.matchMedia('(pointer: fine)')
      pointerMql.addEventListener('change', refresh)
      window.addEventListener('resize', refresh)
    }
  })
  onUnmounted(() => {
    pointerMql?.removeEventListener('change', refresh)
    window.removeEventListener('resize', refresh)
  })

  return { pcInput }
}
