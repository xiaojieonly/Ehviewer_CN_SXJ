import { test, expect, type Page } from '@playwright/test'

/* -------------------------------------------------------------------------- */
/* W4 抽屉回归（<720px 模态抽屉 / ≥720px 常显侧栏）                             */
/*                                                                            */
/* 覆盖实现：                                                                  */
/*  - src/App.vue —— `button.app-hamburger`（仅窄视口显示，≥720px CSS 隐藏）    */
/*  - src/components/layout/NavigationDrawer.vue —— panel（translateX 开合）、 */
/*    scrim（data-testid="drawer-scrim"，点击收起）、菜单项                     */
/*    （data-testid="drawer-item"，选择后跳转并收起抽屉）                       */
/*                                                                            */
/* 目标服务：已在运行的 WebUI 后端（baseURL 在 playwright.drawer.config.ts      */
/* 中参数化：E2E_BASE_URL，默认 http://localhost:8085）。                      */
/* -------------------------------------------------------------------------- */

const MOBILE_VIEWPORT = { width: 375, height: 720 }
const DESKTOP_VIEWPORT = { width: 1280, height: 800 }

/** tokens.css `--drawer-width`（drawer_max_width 280dp）。 */
const DRAWER_WIDTH = 280

/** 与其他 e2e 一致：冻结动画/过渡，transform 断言才是确定性的。 */
const STABILIZE_CSS =
  '*,*::before,*::after{animation:none!important;transition:none!important;caret-color:transparent!important;scroll-behavior:auto!important}'

function initScript({ theme, css }: { theme: string; css: string }) {
  try {
    window.localStorage.setItem('anotherviewer-theme', theme)
    // 路由守卫见 token 即放行（后端 authRequired=false，token 不会被校验）
    window.localStorage.setItem('token', 'e2e-drawer-token')
  } catch {
    /* storage may be unavailable; non-fatal */
  }
  const apply = () => {
    const s = document.createElement('style')
    s.setAttribute('data-e2e-stabilize', '')
    s.textContent = css
    document.head.appendChild(s)
  }
  if (document.head) apply()
  else document.addEventListener('DOMContentLoaded', apply)
}

/* -------------------------------------------------------------------------- */
/* 后端可达性探针：连不上时给出带指引的失败（换 E2E_BASE_URL 或先起后端）        */
/* -------------------------------------------------------------------------- */

test.beforeAll(async ({ request, baseURL }) => {
  const probe = `${baseURL ?? 'http://localhost:8085'}/api/v1/auth/status`
  try {
    const res = await request.get(probe, { timeout: 5_000 })
    if (!res.ok()) {
      throw new Error(`HTTP ${res.status()}`)
    }
  } catch (e) {
    throw new Error(
      `WebUI 后端不可达（${probe}）：${e instanceof Error ? e.message : String(e)}。` +
        '本套件连接「已运行」的后端（任务约定 :8085）；' +
        '请先启动后端，或用 E2E_BASE_URL=http://<host>:<port> 指向其他实例。',
    )
  }
})

/* -------------------------------------------------------------------------- */
/* Helpers                                                                    */
/* -------------------------------------------------------------------------- */

const PANEL = 'aside.navigation-drawer__panel'
const SCRIM = '[data-testid="drawer-scrim"]'
const HAMBURGER = 'button.app-hamburger'

function drawerItem(page: Page, label: string) {
  return page
    .locator(`${PANEL} [data-testid="drawer-item"]`)
    .filter({ has: page.locator('.navigation-drawer__item-label', { hasText: label }) })
}

/** 面板 bounding box；窄视口收起时为 translateX(-100%)（box.x ≈ -280）。 */
async function panelBox(page: Page) {
  const box = await page.locator(PANEL).boundingBox()
  if (!box) throw new Error('drawer panel not in DOM')
  return box
}

async function expectDrawerClosed(page: Page) {
  const box = await panelBox(page)
  // 收起 = 面板整体移出视口左缘（右缘 ≤ 0，1px 容差吸收亚像素舍入）
  expect(box.x + box.width).toBeLessThanOrEqual(1)
  await expect(page.locator(SCRIM)).toHaveCount(0)
}

async function expectDrawerOpen(page: Page) {
  const box = await panelBox(page)
  expect(Math.abs(box.x)).toBeLessThanOrEqual(1)
  expect(box.width).toBe(DRAWER_WIDTH)
}

async function openDrawer(page: Page) {
  await page.locator(HAMBURGER).click()
  await expectDrawerOpen(page)
}

/* -------------------------------------------------------------------------- */
/* <720px：模态抽屉                                                            */
/* -------------------------------------------------------------------------- */

test.describe('drawer @375x720 (modal, <720px)', () => {
  test.use({ viewport: MOBILE_VIEWPORT })

  test.beforeEach(async ({ page }) => {
    await page.addInitScript(initScript, { theme: 'light', css: STABILIZE_CSS })
    await page.goto('/')
    // App 壳挂载完成（汉堡可见 = chrome 路由已渲染）
    await expect(page.locator(HAMBURGER)).toBeVisible()
  })

  test('抽屉默认收起：面板移出视口且无遮罩', async ({ page }) => {
    await expectDrawerClosed(page)
  })

  test('汉堡按钮打开抽屉（可重复开合循环）', async ({ page }) => {
    // 开
    await openDrawer(page)
    await expect(page.locator(SCRIM)).toBeVisible()

    // 合（经遮罩）后再开 —— 汉堡→遮罩→汉堡循环可反复
    await page.locator(SCRIM).click({ position: { x: 330, y: 400 } })
    await expectDrawerClosed(page)

    await openDrawer(page)
    await expect(page.locator(SCRIM)).toBeVisible()
  })

  test('遮罩点击关闭抽屉', async ({ page }) => {
    await openDrawer(page)
    // scrim 全屏但面板盖住左 280px —— 点在面板右侧的可见遮罩区
    await page.locator(SCRIM).click({ position: { x: 330, y: 400 } })
    await expectDrawerClosed(page)
  })

  test('导航项跳转路由并自动收起抽屉', async ({ page }) => {
    // 收藏 → /favorites
    await openDrawer(page)
    await drawerItem(page, '收藏').click()
    await expect(page).toHaveURL(/\/favorites$/)
    await expectDrawerClosed(page)

    // 设置 → /settings（重定向到 /settings/general）
    await openDrawer(page)
    await drawerItem(page, '设置').click()
    await expect(page).toHaveURL(/\/settings\/general$/)
    await expectDrawerClosed(page)
  })
})

/* -------------------------------------------------------------------------- */
/* ≥1280px：常显侧栏、无汉堡                                                    */
/* -------------------------------------------------------------------------- */

test.describe('drawer @1280x800 (persistent sidebar, >=720px)', () => {
  test.use({ viewport: DESKTOP_VIEWPORT })

  test('侧栏常显、无汉堡按钮、无遮罩，且导航后保持常显', async ({ page }) => {
    await page.addInitScript(initScript, { theme: 'light', css: STABILIZE_CSS })
    await page.goto('/')

    // 无汉堡（DOM 存在但 CSS display:none）
    await expect(page.locator(HAMBURGER)).toBeHidden()

    // 侧栏无需任何交互即在流内常显（position:static，x=0）
    const box = await panelBox(page)
    expect(Math.abs(box.x)).toBeLessThanOrEqual(1)
    expect(box.width).toBe(DRAWER_WIDTH)
    await expect(page.locator(SCRIM)).toHaveCount(0)

    // 导航跳转后侧栏依旧常显（不出现模态遮罩）
    await drawerItem(page, '收藏').click()
    await expect(page).toHaveURL(/\/favorites$/)
    const after = await panelBox(page)
    expect(Math.abs(after.x)).toBeLessThanOrEqual(1)
    await expect(page.locator(SCRIM)).toHaveCount(0)
  })
})
