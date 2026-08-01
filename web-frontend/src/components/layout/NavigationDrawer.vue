<template>
  <div class="navigation-drawer" :class="{ 'is-open': open }">
    <!-- Scrim: modal backdrop on narrow viewports (hidden ≥720px via CSS). -->
    <Transition name="navigation-drawer-scrim">
      <div
        v-if="open"
        class="navigation-drawer__scrim"
        data-testid="drawer-scrim"
        @click="emit('update:open', false)"
      />
    </Transition>

    <aside
      class="navigation-drawer__panel"
      role="navigation"
      aria-label="主导航"
    >
      <!-- Header: 160px, low-poly placeholder bg, 64px avatar, 14px username. -->
      <slot name="header">
        <div class="navigation-drawer__header" :style="headerStyle">
          <div class="navigation-drawer__avatar-wrap">
            <img
              v-if="avatarUrl"
              :src="avatarUrl"
              :alt="username || 'avatar'"
              class="navigation-drawer__avatar"
            />
            <span
              v-else
              class="navigation-drawer__avatar navigation-drawer__avatar--fallback"
              aria-hidden="true"
            >{{ avatarInitial }}</span>
          </div>
          <span class="navigation-drawer__username">{{ username || 'AnotherViewer' }}</span>
        </div>
      </slot>

      <!-- Menu: 9-item single-select group (nav_drawer_main.xml + admin). -->
      <nav class="navigation-drawer__menu" role="menu" aria-label="菜单">
        <template v-for="item in items" :key="item.id">
          <!-- Web-only admin entry is visually separated from the Android
               mirror items by a divider. -->
          <div v-if="item.id === 'admin'" class="navigation-drawer__divider" role="separator" />
          <slot name="item" :item="item" :active="item.id === activeId">
            <button
              type="button"
              class="navigation-drawer__item"
              role="menuitemradio"
              :aria-checked="item.id === activeId"
              :class="{ 'is-active': item.id === activeId }"
              data-testid="drawer-item"
              @click="onItemClick(item)"
            >
              <AppIcon :name="item.icon" size="24px" class="navigation-drawer__item-icon" />
              <span class="navigation-drawer__item-label">{{ item.label }}</span>
            </button>
          </slot>
        </template>
      </nav>

      <!-- Footer: LimitsCountView replica (quota) + theme toggle. -->
      <div class="navigation-drawer__footer">
        <slot name="footer">
          <div
            v-if="quota"
            class="navigation-drawer__quota"
            data-testid="drawer-quota"
          >
            <span class="navigation-drawer__quota-label">配额</span>
            <span class="navigation-drawer__quota-text">{{ quota.current }} / {{ quota.total }}</span>
            <button
              type="button"
              class="navigation-drawer__icon-btn"
              aria-label="刷新配额"
              @click="emit('refresh-quota')"
            >
              <AppIcon name="refresh-dark" size="20px" />
            </button>
          </div>

          <button
            type="button"
            class="navigation-drawer__theme-toggle"
            data-testid="drawer-theme-toggle"
            :aria-label="`切换主题（当前：${theme}）`"
            @click="onThemeToggle"
          >
            <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true">
              <!-- Material brightness_6 -->
              <path
                fill="currentColor"
                d="M20,15.31L23.31,12L20,8.69V4H15.31L12,0.69L8.69,4H4V8.69L0.69,12L4,15.31V20H8.69L12,23.31L15.31,20H20V15.31ZM12,18V6A6,6 0 0,1 18,12A6,6 0 0,1 12,18Z"
              />
            </svg>
            <span>切换主题</span>
          </button>
        </slot>
      </div>
    </aside>
  </div>
</template>

<script lang="ts">
import type { NavItem } from '@/types/components'

/**
 * The canonical 8 drawer menu entries — exact ids/titles from Android
 * `res/menu/nav_drawer_main.xml` (homepage / subscription / whats_hot /
 * top_lists / favourite / history / downloads / settings), Chinese labels
 * per the CN fork's strings, icons from the converted VectorDrawable
 * registry (`v_*_black_x24.svg`). A 9th web-only entry (admin panel) is
 * appended after settings.
 */
export const DEFAULT_NAV_ITEMS: NavItem[] = [
  { id: 'homepage', label: '首页', icon: 'homepage-black' },
  { id: 'subscription', label: '订阅', icon: 'eh-subscription-black' },
  { id: 'whats_hot', label: '热门', icon: 'fire-black' },
  { id: 'top_lists', label: '排行榜', icon: 'top-lists' },
  { id: 'favourite', label: '收藏', icon: 'heart-black' },
  { id: 'history', label: '历史', icon: 'history-black' },
  { id: 'downloads', label: '下载', icon: 'download-black' },
  { id: 'settings', label: '设置', icon: 'settings-black' },
  { id: 'admin', label: '管理面板', icon: 'settings-dark' },
]
</script>

<script setup lang="ts">
/**
 * NavigationDrawer — web replica of the Android DrawerLayout
 * (roadmap §导航结构): 280px panel with a 160px header (sadpanda low-poly
 * background placeholder, 64px circular avatar, 14px white username), the
 * 9-item single-select menu group, and a footer with the LimitsCountView
 * quota replica + theme toggle.
 *
 * Responsive behavior: modal overlay with scrim on narrow viewports;
 * persistent sidebar on wide ones (≥720px, `--breakpoint-lg`).
 *
 * Interface: implements the frozen `NavigationDrawerProps/Emits/Slots`
 * contract, additively extended with `modelValue` / `update:modelValue`
 * (active route id v-model) and a `theme-toggle` emit alias so both the
 * contract style (`activeItemId` + `select`) and the F5 task style
 * (`v-model` + `theme-toggle`) work unchanged.
 */
import { computed } from 'vue'
import AppIcon from '@/components/atoms/AppIcon.vue'
import type {
  AppTheme,
  DrawerQuota,
  NavigationDrawerEmits,
  NavigationDrawerSlots,
} from '@/types/components'
// DEFAULT_NAV_ITEMS comes from the sibling plain <script> block above
// (shared module scope — no import needed).

const props = withDefaults(
  defineProps<{
    /** Whether the drawer is open. v-model:open. */
    open: boolean
    /** Menu entries. @default DEFAULT_NAV_ITEMS (the canonical 8) */
    items?: NavItem[]
    /** `id` of the selected item (contract style), or null when none. */
    activeItemId?: string | null
    /** Active route id (v-model style, F5 task spec). Takes precedence. */
    modelValue?: string | null
    /** Logged-in user name shown in the header (14px white). */
    username?: string
    /** Avatar image URL; rendered as a 64px circle. */
    avatarUrl?: string
    /** Custom header background image URL (defaults to low-poly gradient). */
    headerBackgroundUrl?: string
    /** Quota for the footer LimitsCountView replica; null hides it. */
    quota?: DrawerQuota | null
    /** Current theme, reflected in the footer toggle's accessible label. */
    theme?: AppTheme
  }>(),
  {
    items: () => [...DEFAULT_NAV_ITEMS],
    activeItemId: null,
    modelValue: null,
    username: undefined,
    avatarUrl: undefined,
    headerBackgroundUrl: undefined,
    quota: null,
    theme: 'light',
  },
)

const emit = defineEmits<
  NavigationDrawerEmits & {
    /** v-model:modelValue — active route id changed (F5 task spec). */
    (e: 'update:modelValue', id: string): void
    /** Theme toggle tapped — alias of `toggle-theme` (F5 task spec). */
    (e: 'theme-toggle'): void
  }
>()

defineSlots<NavigationDrawerSlots>()

/** Effective selection: v-model wins over the one-way `activeItemId`. */
const activeId = computed<string | null>(
  () => props.modelValue ?? props.activeItemId ?? null,
)

const avatarInitial = computed(() => (props.username ?? '').charAt(0).toUpperCase() || 'E')

const headerStyle = computed(() =>
  props.headerBackgroundUrl
    ? {
        backgroundImage: `url(${props.headerBackgroundUrl})`,
        backgroundSize: 'cover',
        backgroundPosition: 'center',
      }
    : {},
)

function onItemClick(item: NavItem): void {
  emit('select', item)
  emit('update:modelValue', item.id)
  // Android closes the drawer after a navigation selection.
  emit('update:open', false)
}

function onThemeToggle(): void {
  emit('toggle-theme')
  emit('theme-toggle')
}
</script>

<style scoped>
.navigation-drawer {
  /* Wrapper carries no layout of its own — the panel is fixed/static. */
  display: contents;
}

/* ---------------------------------- scrim ------------------------------- */
.navigation-drawer__scrim {
  position: fixed;
  inset: 0;
  background: var(--black-overlay);
  z-index: 99;
}

.navigation-drawer-scrim-enter-active,
.navigation-drawer-scrim-leave-active {
  transition: opacity 200ms var(--ease-decelerate-quart);
}

.navigation-drawer-scrim-enter-from,
.navigation-drawer-scrim-leave-to {
  opacity: 0;
}

/* ---------------------------------- panel ------------------------------- */
.navigation-drawer__panel {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  z-index: 100;
  display: flex;
  flex-direction: column;
  width: var(--drawer-width); /* 280px — drawer_max_width */
  background: var(--color-bg);
  box-shadow: 2px 0 4px var(--shadow-color);
  transform: translateX(-100%);
  transition: transform 250ms var(--ease-decelerate-quart);
}

.navigation-drawer.is-open .navigation-drawer__panel {
  transform: translateX(0);
}

/* Wide viewports (≥ --breakpoint-lg: 720px): persistent sidebar. */
@media (min-width: 720px) {
  .navigation-drawer__panel {
    position: static;
    z-index: auto;
    height: 100%;
    transform: none;
    box-shadow: none;
    border-right: 1px solid var(--color-divider);
  }

  .navigation-drawer__scrim {
    display: none;
  }
}

/* ---------------------------------- header ------------------------------ */
.navigation-drawer__header {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  flex-shrink: 0;
  /* 160px + status-bar inset; the gradient bleeds into the inset area while
     the avatar / username stay in the 160px band below it. */
  height: calc(var(--drawer-header-height) + var(--safe-area-top));
  padding: calc(var(--keyline-margin) + var(--safe-area-top)) var(--keyline-margin) var(--keyline-margin);
  /* sadpanda_low_poly placeholder — low-poly teal gradient. */
  background: linear-gradient(135deg, var(--color-primary-dark) 0%, var(--color-primary) 100%);
}

.navigation-drawer__avatar {
  display: block;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  object-fit: cover;
}

.navigation-drawer__avatar--fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.3);
  color: var(--color-white);
  font-size: var(--text-super-large);
  font-weight: 500;
}

.navigation-drawer__username {
  font-size: var(--text-small); /* 14px */
  color: var(--color-white);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ----------------------------------- menu ------------------------------- */
.navigation-drawer__menu {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding: var(--spacing) 0;
}

.navigation-drawer__item {
  display: flex;
  align-items: center;
  width: 100%;
  height: 48px;
  padding: 0 var(--keyline-margin);
  border: none;
  background: transparent;
  cursor: pointer;
  font: inherit;
  text-align: left;
  /* Icon starts at 16px keyline; label lands on the 72px Material keyline. */
  gap: 32px;
  color: var(--text-color-primary);
  transition: background 150ms linear;
}

.navigation-drawer__item:hover {
  background: rgba(0, 0, 0, 0.06);
}

.navigation-drawer__item-icon {
  color: var(--drawable-color-primary);
}

/* The registry's *_black icons carry a hardcoded #000 fill — force them to
   follow the row color so all three themes render correctly. */
.navigation-drawer__item-icon :deep(svg path) {
  fill: currentColor;
}

.navigation-drawer__item-label {
  font-size: var(--text-small); /* 14px */
  white-space: nowrap;
}

/* Single-checked group: the active row is tinted primary. */.navigation-drawer__item.is-active {
  background: rgba(0, 150, 136, 0.12);
  background: color-mix(in srgb, var(--color-primary) 12%, transparent);
  color: var(--color-primary-text, var(--color-primary-dark));
}

.navigation-drawer__item.is-active .navigation-drawer__item-icon {
  color: var(--color-primary);
}

/* Divider above the web-only admin entry. */
.navigation-drawer__divider {
  height: 1px;
  margin: var(--spacing) var(--keyline-margin);
  background: var(--color-divider);
}

/* ---------------------------------- footer ------------------------------ */
.navigation-drawer__footer {
  flex-shrink: 0;
  border-top: 1px solid var(--color-divider);
  /* Bottom inset keeps the quota / theme controls clear of the home indicator. */
  padding: var(--spacing) var(--keyline-margin) calc(var(--spacing) + var(--safe-area-bottom));
}

.navigation-drawer__quota {
  display: flex;
  align-items: center;
  gap: var(--spacing);
  min-height: 36px;
}

.navigation-drawer__quota-label {
  font-size: var(--text-super-small);
  color: var(--text-color-secondary);
}

.navigation-drawer__quota-text {
  flex: 1;
  font-size: var(--text-super-small);
  font-weight: 700;
  color: var(--text-color-theme-primary);
}

.navigation-drawer__icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--drawable-color-primary);
  cursor: pointer;
  transition: background 150ms linear;
}

.navigation-drawer__icon-btn:hover {
  background: rgba(0, 0, 0, 0.06);
}

.navigation-drawer__theme-toggle {
  display: flex;
  align-items: center;
  gap: 32px;
  width: 100%;
  height: 44px;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  font: inherit;
  font-size: var(--text-small);
  color: var(--text-color-primary);
  text-align: left;
}

.navigation-drawer__theme-toggle svg {
  flex-shrink: 0;
  color: var(--drawable-color-primary);
}
</style>
