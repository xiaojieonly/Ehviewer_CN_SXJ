#!/usr/bin/env node
/**
 * verify-reader.mjs — WebUI 画廊阅读链路端到端验证
 *
 * 流程：
 *   1. launch chromium 访问 http://localhost:8080/，等待首页画廊卡片出现
 *   2. 探测 WebUI 详情路由（/gallery/:gid → /gallery/:gid/:token → 首页点击 fallback）
 *   3. 详情页点击 "Read" 按钮进入阅读器
 *   4. 轮询（最多 60s）直到出现至少 1 个 naturalWidth > 0 的阅读器 <img>
 *   5. 输出结果与 /api/v1/image 相关网络请求状态
 *
 * 退出码：0 = 验证通过，1 = 验证失败
 *
 * 用法：cd web-frontend && node e2e/verify-reader.mjs
 *
 * 实现决策（与代码核对）：
 *   - 详情路由为 /gallery/:gid（web-frontend/src/router/index.ts:15），
 *     阅读器路由 /reader/:gid/:page?（同文件 :21）。404 由 NotFoundView
 *     （.not-found__code 文本 "404"）渲染。
 *   - 首页卡片标题：grid 模式 .gallery-card__grid-title，list 模式
 *     .gallery-card__title（components/gallery/GalleryCard.vue）。
 *   - 阅读按钮实际文本是 "Read"（GalleryDetailView.vue:91-98，
 *     .detail-actions__btn--read），非全大写 "READ"。
 *   - 阅读器图片 <img src="/api/v1/image/{gid}/{page}?..." >：
 *     ScrollMode .scroll-mode__img / PageMode img。
 */
import { createRequire } from 'node:module'

// @playwright/test 是 CJS 包；.mjs 里用 createRequire 引入（web-frontend/node_modules）
const require = createRequire(import.meta.url)
const { chromium } = require('@playwright/test')

const BASE = 'http://localhost:8080'
const GID = '4105123'
const TOKEN = '74b11aefa6'

const HOME_CARD = '.gallery-card__grid-title, .gallery-card__title'
const READ_BTN = 'button.detail-actions__btn--read'
const NOT_FOUND = '.not-found__code'
const DETAIL_MARKER = '.detail-actions, .detail-header'

function log(...args) {
  console.log('[verify-reader]', ...args)
}

async function main() {
  const browser = await chromium.launch()
  const page = await browser.newPage()
  page.setDefaultTimeout(15000)

  // 收集 /api/v1/image 相关请求结果
  const imageResponses = []
  page.on('response', (r) => {
    if (r.url().includes('/api/v1/image')) {
      imageResponses.push({ url: r.url(), status: r.status() })
    }
  })
  page.on('requestfailed', (r) => {
    if (r.url().includes('/api/v1/image')) {
      imageResponses.push({ url: r.url(), status: 'FAILED', error: r.failure()?.errorText })
    }
  })

  try {
    // ── 1. 首页画廊列表 ──────────────────────────────────────────
    log(`访问首页 ${BASE}/`)
    const resp = await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' })
    log(`首页 HTTP ${resp ? resp.status() : 'n/a'}`)
    await page.locator(HOME_CARD).first().waitFor({ state: 'visible', timeout: 30000 })
    const cardCount = await page.locator(HOME_CARD).count()
    log(`首页画廊卡片可见，共 ${cardCount} 张`)

    // ── 2. 探测详情路由 ─────────────────────────────────────────
    const detailUrl = await probeDetailRoute(page)
    if (!detailUrl) {
      log('详情路由探测失败（404），fallback 首页点击第一个画廊')
      await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' })
      await page.locator(HOME_CARD).first().waitFor({ state: 'visible', timeout: 30000 })
      await page.locator(HOME_CARD).first().click()
      await page
        .locator(READ_BTN)
        .waitFor({ state: 'visible', timeout: 30000 })
        .catch(() => {})
      log(`fallback 后详情 URL：${page.url()}`)
      if (!(await page.locator(READ_BTN).count())) {
        throw new Error('fallback 后仍未进入详情页')
      }
    } else {
      log(`详情路由探测成功：${detailUrl}`)
    }

    // ── 3. 点击 Read 进入阅读器 ─────────────────────────────────
    const readBtn = page.locator(READ_BTN)
    const btnText = (await readBtn.textContent().catch(() => '')).trim()
    log(`点击 Read 按钮（"${btnText}"）`)
    await readBtn.click()
    await page.waitForURL(/\/reader\//, { timeout: 30000 }).catch(() => {})
    log(`阅读器 URL：${page.url()}`)

    // ── 4. 等待阅读器图片加载 ───────────────────────────────────
    log('等待阅读器 <img>（最多 60s）…')
    await page
      .waitForFunction(
        () => {
          const imgs = [...document.querySelectorAll('img[src*="/api/v1/image"]')]
          return imgs.some((i) => i.naturalWidth > 0)
        },
        { timeout: 60000 },
      )
      .catch(() => {})

    const imgs = await page.evaluate(() =>
      [...document.querySelectorAll('img[src*="/api/v1/image"]')].map((i) => ({
        src: i.getAttribute('src'),
        naturalWidth: i.naturalWidth,
        naturalHeight: i.naturalHeight,
      })),
    )

    const loaded = imgs.filter((i) => i.naturalWidth > 0)
    log(`图片 ${imgs.length} 张，其中加载成功 ${loaded.length} 张`)
    for (const i of loaded.slice(0, 5)) {
      log(`  加载成功：${i.src} naturalWidth=${i.naturalWidth}x${i.naturalHeight}`)
    }
    if (loaded.length === 0) {
      log('  （无加载成功的图片，以下为全部图片的 src/naturalWidth）')
      for (const i of imgs) log(`  ${i.src} naturalWidth=${i.naturalWidth}`)
    }

    // ── 5. 网络请求统计 ─────────────────────────────────────────
    const ok = imageResponses.filter((r) => r.status === 200)
    const bad = imageResponses.filter((r) => r.status !== 200)
    log(`/api/v1/image 请求：共 ${imageResponses.length}，200=${ok.length}，非200=${bad.length}`)
    for (const r of bad) log(`  异常：${r.status} ${r.url}`)
    if (imageResponses.length === 0) log('  （页面未发出 /api/v1/image 请求）')

    // ── 6. 结果判定 ─────────────────────────────────────────────
    if (loaded.length === 0) {
      log('RESULT: FAIL（阅读器无加载成功的图片）')
      process.exitCode = 1
    } else {
      log('RESULT: PASS')
      process.exitCode = 0
    }
  } catch (err) {
    log('RESULT: FAIL —', err.message)
    log(`当前 URL：${page.url()}`)
    process.exitCode = 1
  } finally {
    await browser.close()
  }
}

/**
 * 探测详情路由：/gallery/{gid} → /gallery/{gid}/{token}。
 * 判定逻辑：页面出现 .not-found__code（"404"）或 HTTP 4xx 即视为 404；
 * 出现 Read 按钮即视为详情页成功。都失败返回 null。
 */
async function probeDetailRoute(page) {
  const candidates = [`${BASE}/gallery/${GID}`, `${BASE}/gallery/${GID}/${TOKEN}`]
  for (const url of candidates) {
    log(`探测详情路由 ${url}`)
    try {
      const resp = await page.goto(url, { waitUntil: 'domcontentloaded' })
      if (resp && resp.status() >= 400) {
        log(`  HTTP ${resp.status()}，非详情页`)
        continue
      }
      const is404 = await page
        .locator(NOT_FOUND)
        .first()
        .waitFor({ state: 'visible', timeout: 3000 })
        .then(() => true)
        .catch(() => false)
      if (is404) {
        log('  命中 404 页，跳过')
        continue
      }
      await page.locator(READ_BTN).waitFor({ state: 'visible', timeout: 15000 })
      return url
    } catch {
      // 兜底：等待超时也视为该路由不可用，尝试下一个
    }
  }
  return null
}

main().catch((err) => {
  log('RESULT: FAIL —', err.message)
  process.exitCode = 1
})
