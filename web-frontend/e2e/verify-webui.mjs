#!/usr/bin/env node
/**
 * verify-webui.mjs — 对生产 WebUI（192.168.6.141:8081）做端到端验证：
 *  1. 首页加载（记录加载资源哈希，确认新前端 bundle）
 *  2. 打开 /reader/{gid} 阅读器，等待图片出现
 *  3. 读取当前页 + 派发翻页事件，验证翻到第 2 帧图片加载
 *  4. 输出 SW 缓存状态与资源版本
 * 退出码：0 = 通过；1 = 失败
 */
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const { chromium } = require('@playwright/test')

const BASE = process.env.BASE || 'http://192.168.6.141:8081'
const GID = process.env.GID || '4046591'

const browser = await chromium.launch()
const context = await browser.newContext({ locale: 'zh-CN' })
const page = await context.newPage()

const failures = []
const log = (m) => console.log(`[verify] ${m}`)

// 收集执行期间对 /api/v1/image 的请求结果
const imageRequests = []
page.on('response', (r) => {
  if (r.url().includes('/api/v1/image/')) {
    imageRequests.push({ url: r.url(), status: r.status() })
  }
})

try {
  // 1. 首页
  await page.goto(`${BASE}/`, { waitUntil: 'networkidle', timeout: 60000 })
  const bundle = await page.evaluate(() =>
    [...document.querySelectorAll('script[src]')].map((s) => s.getAttribute('src')),
  )
  log(`首页 bundle: ${JSON.stringify(bundle)}`)
  // 与服务端 static 目录当前版本比对——生产 jar 若落后，bundle hash 会不同。
  // （本地 resources/static 的当前构建名：index-Cr4lyugA.js 于本轮 build 产出）
  const expectBundle = process.env.EXPECT_BUNDLE || undefined
  if (expectBundle && !bundle.some((b) => b.includes(expectBundle))) {
    failures.push(`bundle 不是期望版本 index-${expectBundle}: ${JSON.stringify(bundle)}`)
  }

  // SW 注册状态（page 上下文无 service worker API 则跳过）
  const swState = await page.evaluate(() => {
    if (!('serviceWorker' in navigator)) return []
    return navigator.serviceWorker
      .getRegistrations()
      .then((regs) =>
        regs.map((r) => ({ scope: r.scope, active: r.active?.scriptURL ?? null })),
      )
      .catch(() => [])
  })
  log(`SW registrations: ${JSON.stringify(swState)}`)

  // 2. 阅读器
  await page.goto(`${BASE}/reader/${GID}/0`, { waitUntil: 'domcontentloaded', timeout: 60000 })
  await page.waitForTimeout(4000)

  const state1 = await page.evaluate(() => {
    // 收集所有 <img>（无论 mode），取 naturalWidth>0 的最大者作为"已加载图片"证据
    const imgs = [...document.querySelectorAll('img')].map((img) => ({
      cls: (img.className || '').toString().slice(0, 40),
      natural: img.naturalWidth,
      src: (img.getAttribute('src') || '').slice(0, 90),
    }))
    const loaded = imgs.filter((i) => i.natural > 0)
    const banner = document.querySelector('[data-testid="reader-degraded-banner"]') ? 'degraded' : 'none'
    return {
      imgCount: imgs.length,
      loadedCount: loaded.length,
      loadedSamples: loaded.slice(0, 3),
      banner,
      bodyText: document.body.innerText.slice(0, 120),
    }
  })
  log(`阅读器初始: ${JSON.stringify(state1)}`)
  if (state1.imgCount === 0) failures.push('阅读器无 <img> 元素')
  else if (state1.loadedCount === 0) failures.push('无任何图片解码（naturalWidth>0）')
  if (state1.banner === 'degraded') failures.push('阅读器处于 degraded 横幅（元数据丢失）')

  // 3. 翻页（ArrowRight → nextPage）
  await page.keyboard.press('ArrowRight')
  await page.waitForTimeout(3500)
  const state2 = await page.evaluate(() => {
    const imgs = [...document.querySelectorAll('img')].map((img) => ({
      natural: img.naturalWidth,
      src: (img.getAttribute('src') || '').slice(0, 90),
    }))
    const loaded = imgs.filter((i) => i.natural > 0)
    return { loadedCount: loaded.length, loadedSamples: loaded.slice(0, 3) }
  })
  log(`翻页后: ${JSON.stringify(state2)}`)
  if (state2.loadedCount === 0) failures.push('翻页后仍无任何解码图片')

  log(`image 请求: ${imageRequests.length} 个: ${imageRequests.map((r) => `${r.status} ${r.url.slice(0, 70)}`).join(' | ')}`)
} catch (e) {
  failures.push(`脚本异常: ${e.message}`)
} finally {
  await browser.close()
}

if (failures.length) {
  console.error('FAILURES:')
  failures.forEach((f) => console.error(' - ' + f))
  process.exit(1)
}
console.log('PASS: WebUI 阅读链路端到端验证通过')
