#!/usr/bin/env node
/**
 * live-responsive.mjs — 对线上实例（默认 http://192.168.6.141:8081）做响应式检查：
 * 多设备仿真（触屏 + 手机 UA）× 关键路由 截图到指定目录，供人工/评审检查布局问题。
 *
 * 用法: node e2e/live-responsive.mjs [OUT_DIR]
 *   BASE / GID 环境变量可覆盖；GID 缺省时从首页 UI 点进第一个卡片获得。
 *
 * 说明：线上实例图片打码/不加载属预期（清理管线），本脚本只看布局不判图片内容。
 * 用 BASE=http://localhost:3000 可对本地 dev server 复用同一矩阵。
 */
import { createRequire } from 'node:module'
import fs from 'node:fs'
import path from 'node:path'
const require = createRequire(import.meta.url)
const { chromium, devices } = require('@playwright/test')

const BASE = process.env.BASE || 'http://192.168.6.141:8081'
const OUT = process.argv[2] || '/tmp/responsive-shots'
const GID = process.env.GID || ''

const DEVICE_SETS = [
  { id: 'phone-390x844', device: 'iPhone 13' },
  { id: 'android-360x800', device: 'Pixel 5' }, // 393x851 → 缩到 360x800 视口
  { id: 'narrow-320x568', device: null, viewport: { width: 320, height: 568 } },
  { id: 'tablet-768x1024', device: null, viewport: { width: 768, height: 1024 } },
]

const LIST_ROUTES = [
  { path: '/', slug: 'home' },
  { path: '/search', slug: 'search' },
  { path: '/history', slug: 'history' },
  { path: '/favorites', slug: 'favorites' },
  { path: '/downloads', slug: 'downloads' },
  { path: '/settings/general', slug: 'settings-general' },
  { path: '/admin/download', slug: 'admin-download' },
]

fs.mkdirSync(OUT, { recursive: true })
const browser = await chromium.launch()

async function contextFor(set) {
  if (set.device) {
    const d = { ...devices[set.device] }
    if (set.id === 'android-360x800') {
      d.viewport = { width: 360, height: 800 }
      d.deviceScaleFactor = 2
    }
    return browser.newContext({ locale: 'zh-CN', ...d })
  }
  return browser.newContext({
    locale: 'zh-CN',
    viewport: set.viewport,
    deviceScaleFactor: 2,
    isMobile: true,
    hasTouch: true,
    userAgent:
      'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36',
  })
}

const shot = (page, name) =>
  page.screenshot({ path: path.join(OUT, name), fullPage: false, animations: 'disabled' })

// 详情/阅读器需要真实 gid：从首页 UI 点第一张卡片获得（只走 UI，不直接打 API）
let gid = GID
if (!gid) {
  const ctx = await contextFor(DEVICE_SETS[0])
  const page = await ctx.newPage()
  try {
    await page.goto(`${BASE}/`, { waitUntil: 'load', timeout: 60000 })
    await page.waitForSelector('a[href*="/gallery/"]', { timeout: 30000 })
    const href = await page.getAttribute('a[href*="/gallery/"]', 'href')
    gid = href.split('/').pop()
    console.log(`[live] 从首页获得 gid=${gid}`)
  } catch (e) {
    console.log(`[live] 首页取 gid 失败: ${e.message.split('\n')[0]}`)
  }
  await ctx.close()
}

let captured = 0
for (const set of DEVICE_SETS) {
  const ctx = await contextFor(set)
  const page = await ctx.newPage()
  for (const r of LIST_ROUTES) {
    try {
      await page.goto(`${BASE}${r.path}`, { waitUntil: 'load', timeout: 60000 })
      await page.waitForTimeout(1500)
      await shot(page, `${r.slug}__${set.id}.png`)
      captured++
    } catch (e) {
      console.log(`[live] ${r.slug} @${set.id} 失败: ${e.message.split('\n')[0]}`)
    }
  }
  if (gid) {
    try {
      await page.goto(`${BASE}/gallery/${gid}`, { waitUntil: 'load', timeout: 60000 })
      await page.waitForTimeout(1500)
      await shot(page, `detail__${set.id}.png`)
      captured++
      await page.goto(`${BASE}/reader/${gid}/0`, { waitUntil: 'load', timeout: 60000 })
      await page.waitForTimeout(2500)
      await shot(page, `reader__${set.id}.png`)
      captured++
    } catch (e) {
      console.log(`[live] detail/reader @${set.id} 失败: ${e.message.split('\n')[0]}`)
    }
  }
  await ctx.close()
}

await browser.close()
console.log(`[live] 共 ${captured} 张截图 → ${OUT}`)
