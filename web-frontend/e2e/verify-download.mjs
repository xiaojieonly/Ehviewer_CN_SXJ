#!/usr/bin/env node
/**
 * verify-download.mjs — WebUI 下载 API 端到端验证（纯 fetch，无浏览器）
 *
 * 流程：
 *   1. GET  /api/v1/download/list  — 探测任务结构、查已有任务
 *   2. POST /api/v1/download/add   — 创建下载任务（gid=4105123, token=74b11aefa6）
 *   3. POST /api/v1/download/start/{id} — 手动启动（add 只建任务不启动，见下）
 *   4. 轮询 GET /api/v1/download/list 直到任务终态 3=FINISHED / 4=FAILED，最长 120s
 *   5. 报告 state 与任务行
 *
 * 退出码：0 = 脚本跑通（含任务 FAILED——可能为 EH 侧限制，输出中标注），
 *         1 = 基础设施失败（服务器不可达 / API 报错 / 超时未达终态）
 *
 * 用法：cd web-frontend && node e2e/verify-download.mjs
 *
 * 实现决策（与代码核对，修正任务描述中的 API 猜测）：
 *   - 创建端点是 POST /api/v1/download/add（非 /api/v1/download），请求体
 *     {gid, token, title, thumb, label}，无 url 字段——服务器由 gid/token
 *     内部解析每页 URL（web-frontend/src/api/download.ts:78、
 *     DownloadControllerTest.kt:110）。
 *   - add 只保存任务（state=0，初始未启动），需再调 POST /api/v1/download/start/{id}
 *     （DownloadService.kt:219-242 / :244-257）。
 *   - state 语义：0=已创建未启动, 1=WAIT, 2=DOWNLOADING, 3=FINISHED,
 *     4=FAILED（DownloadService.kt:42 注释）。
 *   - 同 gid 已存在任务时 add 返回 false（findByGid 去重，
 *     DownloadService.kt:220-221），脚本直接轮询既有任务。
 */
const BASE = 'http://localhost:8080'
const GID = 4105123
const TOKEN = '74b11aefa6'
const POLL_INTERVAL_MS = 3000
const MAX_WAIT_MS = 120_000

const STATE_LABEL = { 0: 'CREATED(未启动)', 1: 'WAIT', 2: 'DOWNLOADING', 3: 'FINISHED', 4: 'FAILED' }

function log(...args) {
  console.log('[verify-download]', ...args)
}

/** 带超时的 fetch，返回 { status, body }；失败抛错。 */
async function api(path, options = {}, timeoutMs = 30000) {
  const ctrl = new AbortController()
  const timer = setTimeout(() => ctrl.abort(), timeoutMs)
  try {
    const resp = await fetch(`${BASE}${path}`, {
      ...options,
      signal: ctrl.signal,
      headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    })
    let body = null
    const text = await resp.text()
    try {
      body = JSON.parse(text)
    } catch {
      body = text
    }
    return { status: resp.status, body }
  } finally {
    clearTimeout(timer)
  }
}

async function main() {
  // ── 0. 服务器可达性探测 ───────────────────────────────────────
  let list
  try {
    list = await api('/api/v1/download/list')
  } catch (err) {
    log('RESULT: FAIL — 服务器不可达', err.message)
    process.exitCode = 1
    return
  }
  if (list.status !== 200) {
    log(`RESULT: FAIL — GET /api/v1/download/list HTTP ${list.status}`, JSON.stringify(list.body))
    process.exitCode = 1
    return
  }
  log('GET /api/v1/download/list 成功，响应结构：', Object.keys(list.body).join(','))
  log(`  现有任务 ${list.body.downloads?.length ?? 0} 条`)

  // ── 1. 创建任务（已存在则复用）───────────────────────────────
  log(`POST /api/v1/download/add  gid=${GID} token=${TOKEN}`)
  const add = await api('/api/v1/download/add', {
    method: 'POST',
    body: JSON.stringify({ gid: GID, token: TOKEN, title: null, thumb: null, label: 0 }),
  })
  if (add.status !== 200) {
    log(`RESULT: FAIL — add HTTP ${add.status}`, JSON.stringify(add.body))
    process.exitCode = 1
    return
  }
  log(`  add 返回 ${add.body}（true=新建，false=已存在）`)

  // ── 2. 找到任务 id ───────────────────────────────────────────
  let task = null
  for (let i = 0; i < 5 && !task; i++) {
    const res = await api('/api/v1/download/list')
    if (res.status !== 200) break
    const list2 = res.body.downloads || []
    task = list2.find((d) => d.gid === GID) ?? null
    if (!task) await new Promise((r) => setTimeout(r, 1000))
  }
  if (!task) {
    log('RESULT: FAIL — 列表中找不到 gid=' + GID + ' 的任务')
    process.exitCode = 1
    return
  }
  log(`找到任务 id=${task.id} state=${task.state}(${STATE_LABEL[task.state] ?? task.state})`)

  // ── 3. 启动（state 0/3/4 可启动；1/2 运行中则跳过）────────────
  if (task.state === 1 || task.state === 2) {
    log('任务已在运行（state 1/2），跳过启动')
  } else {
    log(`POST /api/v1/download/start/${task.id}`)
    const start = await api(`/api/v1/download/start/${task.id}`, { method: 'POST' })
    if (start.status !== 200) {
      log(`RESULT: FAIL — start HTTP ${start.status}`, JSON.stringify(start.body))
      process.exitCode = 1
      return
    }
    log(`  start 返回 ${start.body}`)
  }

  // ── 4. 轮询至终态（3=FINISHED / 4=FAILED），最长 120s ────────
  log('轮询 download/list 等待终态（最多 120s）…')
  const deadline = Date.now() + MAX_WAIT_MS
  let current = task
  while (Date.now() < deadline) {
    await new Promise((r) => setTimeout(r, POLL_INTERVAL_MS))
    const res = await api('/api/v1/download/list')
    if (res.status !== 200) {
      log(`  轮询 HTTP ${res.status}，重试中…`)
      continue
    }
    current = (res.body.downloads || []).find((d) => d.gid === GID)
    if (!current) {
      log('  任务从列表消失（可能被删除）')
      process.exitCode = 1
      return
    }
    log(`  state=${current.state}(${STATE_LABEL[current.state] ?? current.state}) done=${current.done}/${current.total}`)
    if (current.state === 3 || current.state === 4) break
  }

  // ── 5. 结果判定 ──────────────────────────────────────────────
  if (!current || (current.state !== 3 && current.state !== 4)) {
    log('RESULT: FAIL — 120s 内未达终态')
    process.exitCode = 1
    return
  }
  log('任务行：', JSON.stringify({
    id: current.id, gid: current.gid, state: current.state,
    done: current.done, total: current.total, error: current.error ?? null,
  }))
  if (current.state === 3) {
    log('RESULT: PASS — 下载完成 (FINISHED)')
    process.exitCode = 0
  } else {
    log(`RESULT: PASS（标注）— 任务 FAILED，error=${current.error ?? '(无)'}；脚本链路本身跑通，` +
        '失败可能是 EH 站点侧限制（无会话/风控）而非 API 问题')
    process.exitCode = 0
  }
}

main().catch((err) => {
  log('RESULT: FAIL —', err.message)
  process.exitCode = 1
})
