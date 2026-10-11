<template>
  <div class="two-pane" :class="{ 'two-pane--wide': wide }" data-testid="two-pane">
    <!-- List column: always rendered（窄屏 = 整页列表，宽屏 = 定宽左栏）。
         DOM 常驻 + 纯类名切换（不做 v-if 双渲染），跨 960px 阈值来回时
         滚动位置与选中态天然不丢（ContentLayout 的滚动容器/KeepAlive 缓存
         机制原样工作）。 -->
    <div class="two-pane__list" data-testid="two-pane-list">
      <slot name="list" :wide="wide" />
    </div>

    <!-- Detail column: wide only（窄屏 display:none，DOM 保留——右栏状态随
         布局切换存续）。无选中时渲染占位符（布局不自动选中任何条目），
         选中态由宿主经 hasSelection 声明、详情内容走 detail 插槽。 -->
    <div
      class="two-pane__detail"
      data-testid="two-pane-detail"
      :aria-hidden="!wide"
    >
      <slot v-if="hasSelection" name="detail" :wide="wide" />
      <div v-else class="two-pane__placeholder" data-testid="two-pane-placeholder">
        <AppIcon name="sad-panda-primary" size="64px" class="two-pane__placeholder-icon" />
        <p class="two-pane__placeholder-text">{{ placeholder }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * TwoPaneLayout — Web 宽屏三栏的中段骨架：宽屏（≥960px，与 Settings 双栏阈值
 * 同寿命）下「列表恒驻左栏（--pane-column-width: 360px）+ 详情右栏」，窄屏
 * （<960px）退化为单列列表（点击进整页详情的现状行为）。
 *
 * 「双栏」指本组件的列表+详情两栏；加上全局常驻 NavigationDrawer（≥720px,
 * 280px）即构成 Web 宽屏的 MD3 三栏（导航/列表/详情），与设置页
 * （SettingsLayout 分组侧栏 + 内容列）同一栅格——次要列宽统一走
 * `--pane-column-width` token。Surface Duo 的跨屏双栏是 Android App 端的
 * 姿态适配，与 Web 无关。
 *
 * 形态判定与 SettingsLayout/SettingsIndex 同源：matchMedia 断点监听
 * （响应式，不做一次性判定），布局由根类名 `two-pane--wide` 驱动——JS 与
 * CSS 单一事实源，跨阈值（旋转/拉窗）来回不丢状态：
 *
 *  - 两列 DOM 恒常驻（窄屏只 `display:none` 右栏），列表滚动位置（
 *    ContentLayout 滚动容器）与选中态（宿主 ref）跨阈值保留；
 *  - 左栏滚动独立：布局本体不滚，列表滚动仍由宿主自身的滚动容器
 *    （ContentLayout）管理，现有 KeepAlive scrollMemory 恢复机制不动。
 *
 * 返回语义（宽屏 + 宿主声明 hasSelection 时）：
 *  - Esc（焦点不在可编辑元素时）→ `clear-selection` 事件，宿主清除选中；
 *  - 浏览器返回（popstate 触发的路由离开）→ 首次拦截为清除选中 + 把
 *    history 步进回来（浏览器已先弹栈，forward 复位让下一次 Back 是真正的
 *    离开），路由导航取消；无选中/窄屏时不拦截。
 *
 * KeepAlive 契约（audit P1-5/P2 同类）：App.vue 缓存列表视图——window 级
 * Escape/popstate 监听随 activated/deactivated 摘挂（remove-before-add），
 * 停用实例不得在后台劫持按键或吞掉返回。
 *
 * 宽屏时 FAB 集群锚回左栏右下角（:deep 穿透 slot 内容——slot 按宿主
 * scope 渲染，穿透选择器只要求左侧锚点是本组件节点）。
 */
import { onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute } from 'vue-router'
import AppIcon from '@/components/atoms/AppIcon.vue'

const props = withDefaults(
  defineProps<{
    /** 双栏断点（px，min-width 语义）。与 Settings 双栏阈值一致。 */
    threshold?: number
    /** 宿主是否存在选中项（宽屏）：驱动 Esc/返回拦截监听的挂摘。 */
    hasSelection?: boolean
    /** 无选中时右栏占位符文案。 */
    placeholder?: string
  }>(),
  { threshold: 960, hasSelection: false, placeholder: '从左侧选择一个画廊查看详情' },
)

const emit = defineEmits<{
  /** 宽屏下 Esc / 浏览器返回首次触发：宿主应清除选中回到占位符。 */
  (e: 'clear-selection'): void
}>()

defineSlots<{
  list(props: { wide: boolean }): unknown
  detail(props: { wide: boolean }): unknown
}>()

/* --------------------------------------------------- viewport wide state --- */

/** 宽屏判定（与 Settings 同阈值）——CSS 侧栏断点是实时的，JS 判定若只查
 *  一次，跨 960px 后两者失步（SettingsIndex 同款教训）。 */
const wide = ref(false)

let wideQuery: MediaQueryList | null = null

function onWideChange(event: MediaQueryListEvent): void {
  wide.value = event.matches
}

onMounted(() => {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return
  wideQuery = window.matchMedia(`(min-width: ${props.threshold}px)`)
  wide.value = wideQuery.matches
  wideQuery.addEventListener('change', onWideChange)
})

onBeforeUnmount(() => {
  wideQuery?.removeEventListener('change', onWideChange)
  wideQuery = null
})

/* ------------------------------------- Esc / browser-back interception --- */

// 组件级 spec 常以最小工厂 mock 掉整个 vue-router 模块（无 useRoute /
// onBeforeRouteLeave 导出）——而 vitest 的 ESM mock 连「读取未导出的绑定」
// 都会抛错（typeof 也逃不掉），所以路由接线用 try/catch 防御：拿不到就跳过。
let route: ReturnType<typeof useRoute> | null = null
try {
  route = useRoute()
} catch {
  route = null
}

/** popstate 已把浏览器弹到上一条目、路由守卫尚未消费的窗口标记。 */
let popstateCaught = false
/** 拦截后的 history.forward() 自带一次 popstate——吞掉，不复位标记。 */
let ignoreNextPop = false
/** KeepAlive 停用守卫：停用实例不挂 window 监听（audit P1-5 同类）。 */
let viewActive = true

function onPopState(): void {
  if (ignoreNextPop) {
    ignoreNextPop = false
    return
  }
  popstateCaught = true
}

/** Esc（焦点不在可编辑元素时）先清选中，再按一次才真正离开页面。 */
function onEscKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Escape' || event.isComposing) return
  const target = event.target as HTMLElement | null
  if (
    target &&
    (target.tagName === 'INPUT' ||
      target.tagName === 'SELECT' ||
      target.tagName === 'TEXTAREA' ||
      target.isContentEditable)
  ) {
    return
  }
  emit('clear-selection')
}

function detachListeners(): void {
  window.removeEventListener('keydown', onEscKeydown)
  window.removeEventListener('popstate', onPopState)
}

/** 监听挂摘与 (wide, hasSelection, 激活态) 同步（remove-before-add）。 */
function refreshListeners(): void {
  detachListeners()
  if (!viewActive || !wide.value || !props.hasSelection) return
  window.addEventListener('keydown', onEscKeydown)
  window.addEventListener('popstate', onPopState)
}

watch([wide, () => props.hasSelection], refreshListeners)

onMounted(refreshListeners)

onActivated(() => {
  viewActive = true
  refreshListeners()
})

onDeactivated(() => {
  viewActive = false
  detachListeners()
})

onBeforeUnmount(() => {
  viewActive = false
  detachListeners()
})

// 任何真实路由落定都作废 popstate 窗口标记：同记录导航（如 /?feed= 切换）
// 不经过 leave 守卫，标记不能跨导航残留（否则会误吞下一次 push）。
watch(
  () => route?.fullPath,
  () => {
    popstateCaught = false
  },
)

try {
  onBeforeRouteLeave(() => {
    if (!wide.value || !props.hasSelection || !popstateCaught) return true
    popstateCaught = false
    // 浏览器已先弹栈（URL 已变）：forward 复位，让下一次 Back 成为真正的
    // 离开；router 把这次 popstate 解析为同址重复导航，视图不受影响。
    ignoreNextPop = true
    history.forward()
    emit('clear-selection')
    return false
  })
} catch {
  /* vue-router 被 spec mock 掉——无守卫可注册（组件测试矩阵不跑路由）。 */
}

defineExpose({
  /** 宽屏判定（测试/宿主可读）。 */
  wide,
})
</script>

<style scoped>
.two-pane {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}

/* --------------------------------------------- narrow（<960px）--- */
/* 单列列表：与接入前完全同构（列表占满，右栏退役）。 */

.two-pane__list {
  position: relative;
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.two-pane__detail {
  display: none;
}

/* ---------------------------------------------- wide（≥960px）--- */
/* 列表恒驻左栏（--pane-column-width，与设置页侧栏同一栅格 token）+ 详情右栏。
   注意方向必须翻回 row：基础形态是 column（窄屏上下堆叠、右栏 display:none），
   忘了翻方向会让 360px 变成 flex-basis 施加在主轴（= 高度）上——列表缩成
   360px 高的横条、详情整宽跌落到下方（T3 上线时翻过的车）。 */

.two-pane--wide {
  flex-direction: row;
}

.two-pane--wide .two-pane__list {
  flex: 0 0 var(--pane-column-width, 360px);
  width: var(--pane-column-width, 360px);
  border-right: 1px solid var(--color-divider);
}

.two-pane--wide .two-pane__detail {
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden; /* 滚动归右栏内容（GalleryDetailPane--pane）自管 */
  background: var(--color-bg);
}

/* 宽屏：FAB 集群锚回左栏右下角（slot 内容按宿主 scope 渲染，:deep 从本
   组件节点向下穿透；fixed → absolute，包含块变为左栏）。 */
.two-pane--wide .two-pane__list :deep(.fab-layout__cluster) {
  position: absolute;
}

/* 无选中占位符：右栏居中的 sadpanda + 提示文案（Android 空态语言）。 */
.two-pane__placeholder {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing);
  padding: var(--keyline-margin);
  user-select: none;
}

.two-pane__placeholder-icon {
  color: var(--drawable-color-primary);
  opacity: 0.45;
}

.two-pane__placeholder-text {
  margin: 0;
  text-align: center;
  font-size: var(--text-little-small);
  color: var(--text-color-secondary);
}
</style>
