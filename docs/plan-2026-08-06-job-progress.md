# 方案：统一后台任务（Job）基础设施、大数据操作进度反馈与下载页卡死修复

**日期**：2026-08-06 · **状态**：执行中 · **关联**：B3 导入契约扩展、R4-2 还原运行态、W6 下载列表

## 1. 背景

1. EhViewer 备份导入为同步长事务（数万行逐行 save，分钟级），前端干等、无进度。
2. 全项目扫描出同类重灾区 4 处：备份导出（GB~TB）、全量清缓存（10GB）、备份还原、EhViewer 导入——均在前端可见路径上同步阻塞。
3. 下载页（/downloads）9000 条一次全量渲染（13-18 万 DOM 节点 + 9000 入场动画 + 9000 `<img>`），无虚拟化 → 页面卡死；且缩略图为站点外链，被 CSP `img-src 'self'` 拦截。

## 2. 目标

- 4 个管理员操作全部异步化：实时进度（阶段/计数/百分比）+ 终态结果 + 跨刷新恢复。
- 统一 Job 基础设施一次建好四处复用；为持久任务（下载类）预留 `JobStore` 扩展点（本次不迁移下载）。
- 下载页：服务端分页 + 虚拟滚动 + 缩略图走 WebUI 代理。

## 3. 架构

```
POST .../async（同步部分：落盘/入参校验）
        │ 202 {jobId, state}
        ▼
JobService（@Component）：注册表 + 专用线程池(2) + 每 type 单活跃护栏
        │ eventPublisher.publishEvent(JobEvent.*)
        ▼
JobEventHandler（@EventListener）→ STOMP /topic/jobs/{jobId} + /topic/jobs/all
                                     （信封 {type,timestamp,version:"1.1",payload}）
查询/恢复：GET /api/v1/jobs/{jobId} · GET /api/v1/jobs/active?type=
```

- **存储**：`JobStore` 接口 + `InMemoryJobStore`（ConcurrentHashMap，COMPLETED/FAILED 任务 TTL 1h 惰性清理，上限 50 条）。`JpaJobStore` 为未来持久任务（下载）预留。
- **线程**：`Executors.newFixedThreadPool(2)` daemon 线程；worker 方法内调用 `@Transactional` 业务方法（线程绑定语义保持）。
- **并发护栏**：每 type 同时只允许 1 个活跃（PENDING/RUNNING）任务；重复提交 409。

---

# 附录 A：冻结接口清单（所有子代理的宪法）

> 任何代理不得发明/改动以下契约。需要变更 → 先改本文档（主代理裁定）再动代码。

## A1. Job JSON schema

```json
{
  "jobId": "job-xxxxxxxx",
  "type": "IMPORT | EXPORT | RESTORE | CACHE_CLEAR",
  "state": "PENDING | RUNNING | COMPLETED | FAILED",
  "stage": "string | null",
  "percent": 0.0,
  "processed": 0,
  "total": 0,
  "startedAt": "epochMillis | null",
  "completedAt": "epochMillis | null",
  "error": "string | null",
  "result": "object | null"
}
```

`result` 按 type：
- **IMPORT** → 既有 `EhImportResponse`：`{success, imported{downloads,history,filters,quickSearches,labels,bookmarks,favorites,dirnames,blackList,galleryTags}, cookies{imported,siteDomain}, skipped}`
- **EXPORT** → `{downloadUrl: "/api/v1/backup/export/{jobId}", filename: "anotherviewer-backup-<stamp>.zip", sizeBytes: long}`
- **RESTORE** → `{success: bool, message: string|null}`
- **CACHE_CLEAR** → `{removed: long, total: long}`

## A2. REST 端点（全部 Bearer token，同 /api 鉴权；错误一律 M-6 信封 `{error:{code,message,traceId,status}}`）

| 端点 | 请求 | 成功响应 | 错误 |
|---|---|---|---|
| `POST /api/v1/backup/import-ehviewer` | multipart: file(.db 必选), cookies(可选), force(默认 false) | **202** `{jobId, state}` | 活跃 IMPORT → **409** `code=CONFLICT`；文件空 → 400 |
| `GET /api/v1/jobs/{jobId}` | — | 200 Job | 404 |
| `GET /api/v1/jobs/active?type=IMPORT\|EXPORT\|RESTORE\|CACHE_CLEAR` | — | 200 Job | 无活跃 → **404** |
| `POST /api/v1/backup/export/async?includeDownloads=` | — | **202** `{jobId, state}` | 活跃 EXPORT → 409 |
| `GET /api/v1/backup/export/{jobId}` | — | **application/zip 流**（job COMPLETED 时） | 未完成 → 409；未知 → 404 |
| `POST /api/v1/backup/restore` | multipart: file(.zip 必选) | **202** `{jobId, state}` | 活跃 RESTORE → 409 |
| `POST /api/v1/image/cache/clear` | — | **202** `{jobId, state}` | 活跃 CACHE_CLEAR → 409 |

**保持不变**：`GET /api/v1/backup/export`（同步，兼容）、`GET /api/v1/backup/state`（restorePending）、`GET /api/v1/smb/progress`、下载/处理全部端点。

## A3. WS 协议（v1.1.0，信封结构不变，version 字段为 "1.1"）

Topic：`/topic/jobs/{jobId}` 与 `/topic/jobs/all`（两个都推，镜像 process 事件）。

| type | payload |
|---|---|
| `job.started` | `{jobId, type, stage, total}` |
| `job.progress` | `{jobId, type, stage, percent, processed, total}` |
| `job.completed` | `{jobId, type, result}` |
| `job.failed` | `{jobId, type, error}` |

percent = processed/total × 100，由 worker 端计算（total=0 时 percent=0）。

## A4. 各 worker 进度语义（stage 文案 + 计数）

| type | 阶段序列 | processed/total 语义 |
|---|---|---|
| IMPORT | 解析备份文件 → 下载记录 → 下载目录 → 历史 → 书签 → 收藏 → 过滤 → 快速搜索 → 下载标签 → 黑名单 → 作品标签 → Cookie → 写入数据库 | 导入前对源库各表 `SELECT COUNT(*)` 求和得 total；逐行导入递增 processed；缺失表不占 total |
| EXPORT | 数据库快照 → 压缩分片 i/n → 打包 zip | total = 分片数(+打包阶段)，processed = 已完成分片 |
| RESTORE | 解包校验 → 还原数据库 → 拷贝下载内容 → 完成 | total = 分片数，processed = 已处理分片 |
| CACHE_CLEAR | 删除缓存文件 | 先 collectFiles 得 total，逐文件删 processed++ |

IMPORT 终态：worker 返回后置 COMPLETED，result 为计数；异常（含事务回滚）置 FAILED + error 文案。RESTORE 完成后照旧置 `backupState.restorePending = true`。

## A5. 下载列表分页

- `GET /api/v1/download/list?label=&offset=0&limit=100&sort=time_desc&q=` → `{downloads, labels, total}`
- 默认 offset=0、limit=100；服务端 clamp limit ∈ [1, 500]。
- 语义：label 空/0 → 全量；否则 findByLabel。total = 当前 label 下的总条数（分页前计数）。
- `offset` 为**行偏移**：服务端换算 pageIndex = offset / limit（前端按 limit 倍数递增，语义精确）。
- `sort` 取值：`time_desc`（默认，添加时间倒序=最新在前）/ `time_asc` / `title_asc` / `title_desc`；未知值回落 `time_desc`。
- `q`（可选）：**服务端**标题/标题日文大小写不敏感 LIKE 搜索（`%`/`_`/`\` 转义防通配符注入）；空/缺省不过滤。搜索时 total = 匹配总数。
- `DownloadListResponse` 增加 `total: Int` 字段。

## A5c. 批量操作跨页全选（all 模式）

- 批量端点请求体（start-range/stop-range/delete-range/move 共用目标语义）：
  `{ids?: number[], all?: boolean, label?: number|null, q?: string|null}`（move 另有 `labelId`）。
- `all=false`（默认）：按 ids 操作，ids 为空 → 400 VALIDATION_ERROR。
- `all=true`：忽略 ids，服务端按 (label, q) 过滤条件投影全集 id 后执行（跨页全选，
  负载留在服务器；label 空/0=全部标签，q 空=全部条目）。
- 前端"全选"后若已加载页全选且 downloads.length < total → 批量操作传 `all=true`
  + 当前 activeLabel/searchQuery。

## A5b. 下载列表设备本地偏好（web-frontend）

- 存储键 `anotherviewer-admin-download-ui`（与 AdminDownload 其余本地设置同键）。
- 字段：`sortMode: DownloadSort`（默认 `time_desc`）、`pageSize: number`（默认 **50**，与 Android 端一致，可选 50/100/200）。
- 旧键迁移：`sortAscending: boolean`（从未接线）→ sortMode（false=time_desc、true=time_asc）；`paginated` 忽略并随下次写入清除。
- `DownloadView` 挂载时读取该偏好作为分页 limit 与 sort 参数。

## A6. 前端 API 形态（web-frontend）

**新文件 `src/api/jobs.ts`**（共享类型 + 查询）：
```ts
export type JobType = 'IMPORT' | 'EXPORT' | 'RESTORE' | 'CACHE_CLEAR'
export type JobState = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
export interface Job { jobId: string; type: JobType; state: JobState; stage: string | null;
  percent: number; processed: number; total: number; startedAt: number | null;
  completedAt: number | null; error: string | null; result: unknown }
export const jobsApi = {
  getJob(jobId: string): Promise<Job>
  getActiveJob(type: JobType): Promise<Job>   // 404 → 抛错，调用方 catch 视为无活跃
}
```

**`src/api/backup.ts` 变更**：`importEhViewer`、`exportBackupAsync`、`downloadExport`、`restoreBackup` 均返回 `Job`（202）；`downloadExport(jobId): Promise<Blob>`（responseType blob）。

**新文件 `src/api/image.ts`**：`getCacheStatus(): Promise<{cacheSize:number}>`、`clearCacheAsync(): Promise<Job>`（AdminServer.vue 从裸 client 迁移到此）。

**`src/composables/useWebSocket.ts`** 新增（镜像 subscribeProcessing）：
```ts
export interface JobWsEnvelope = WsEnvelope<{jobId:string; type:JobType; stage?:string|null;
  percent?:number; processed?:number; total?:number; error?:string; result?:unknown}>
export function subscribeJob(jobId: string, cb: (e: JobWsEnvelope) => void): () => void
export function subscribeJobs(cb: (e: JobWsEnvelope) => void): () => void
```
（组件内走 useWebSocket() 的作用域版本，`subscribeJob`/`subscribeJobs` 加入 UseWebSocketReturn。）

**新组件 `src/components/jobs/JobProgressPanel.vue`**：
- props：`job: Job | null`、`title?: string`（默认「任务」）
- 渲染：PENDING/RUNNING → ProgressSpinner(定量, percent/100) + 进度条 + `stage` + `processed/total` + 百分比；COMPLETED → 「完成」+ 默认文案；FAILED → 红色错误文案。不处理 result 细节（视图层自渲染）。

**localStorage 恢复键**：`anotherviewer-last-job-{type}`（存最近一次 jobId，刷新后重挂）。

## A7. 缩略图代理（仅前端）

`DownloadItem.vue`：`item.thumb` 匹配 `/^https?:\/\//` → 重写为 `/api/v1/image/proxy?url=${encodeURIComponent(thumb)}`；否则原样。

## A8. AdminDownload 遗留开关

「下载列表分页」本地设置（`anotherviewer-admin-download-ui.paginated`）从未接线 → **移除该开关**（设置项 + 模板行 + 测试断言）。

---

# 附录 B：文件所有权与提交纪律

## B1. 文件所有权矩阵

| 代理 | 名下文件（仅这些可写） |
|---|---|
| 主代理(Wave 0) | `docs/plan-2026-08-06-job-progress.md`、`web-frontend/package.json`（已装依赖）、Job 核心（见下） |
| B2 | `service/EhImportService.kt`、`api/ImportController.kt`、`test/.../EhImportServiceTest.kt` |
| B3 | `service/BackupService.kt`、`api/BackupController.kt`、`service/ImageCacheService.kt`、`api/ImageProxyController.kt`、`test/.../BackupServiceTest.kt`、`test/.../BackupControllerTest.kt`、`test/.../ImageCacheServiceTest.kt` |
| B4 | `api/DownloadController.kt`、`dto/DownloadDto.kt`、`repository/DownloadInfoRepository.kt`、`test/.../DownloadControllerTest.kt` |
| C | `contracts/openapi.yaml`、`contracts/websocket-protocol.md` |
| F1a | `api/jobs.ts`(新)、`api/image.ts`(新)、`api/backup.ts`、`composables/useWebSocket.ts`、`components/jobs/JobProgressPanel.vue`(新)、`components/jobs/__tests__/JobProgressPanel.spec.ts`(新) |
| F1b | `views/admin/AdminBackup.vue`、`views/admin/AdminServer.vue`、`views/__tests__/AdminBackup.spec.ts`、`views/__tests__/AdminServer.spec.ts` |
| F2 | `api/download.ts`、`views/DownloadView.vue`、`components/download/DownloadItem.vue`、`views/admin/AdminDownload.vue`、`views/__tests__/DownloadView.spec.ts`、`components/download/__tests__/DownloadItem.spec.ts`、`views/__tests__/AdminDownload.spec.ts` |

跨代理文件只读；需要改动他人文件 → 主代理裁定。Job 核心（JobService/JobStore/JobEvent/JobDto/JobEventHandler/JobController + 测试）由主代理 Wave 0 完成并 commit。

## B2. 提交纪律

1. 每个子代理完成且**名下测试跑绿**后，自行 commit（仓库风格：`feat(web): ...` / `fix(web-frontend): ...` / `chore(contracts): ...`，中文描述），**不 push**。
2. commit 只包含名下文件；测试不绿不许 commit。
3. gradle 编译/测试用 `.gradle-lock` 目录锁串行（mkdir 自旋，失败 sleep 1s 重试；完成后 rmdir）。`trap 'rmdir .gradle-lock' EXIT`。
4. 禁止：`build.sh`、重启服务、改动他人文件、push、force 操作。

## B3. 测试门禁（各代理汇报格式）

汇报必须含：改动文件清单 / 测试命令 / 测试结果 / 对冻结接口的偏离（无则声明无）/ 遗留问题。
