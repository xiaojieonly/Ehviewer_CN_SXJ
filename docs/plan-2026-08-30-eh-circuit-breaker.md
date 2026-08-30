# 方案：EH 可达性熔断（Availability Circuit Breaker）、下载页分页对齐与本地数据驻留

**日期**：2026-08-30 · **状态**：✅ 完成（2026-08-30 收口：backend/frontend/契约三路并行实现+终验） · **关联**：perf 评估（192.168.6.141 迟滞）、`docs/observability.md` §2.3

> 执行摘要（2026-08-30）：EhAvailabilityService 状态机+熔断接线、DownloadDirIndex 内存索引、countByState/metrics 索引化、详情来源顺序修正、前端 availability store/banner/token 透传、下载页分页条（50/100/200/300/500）全部落地。
> 终验：后端 826 tests ✓、前端 978 tests ✓、typecheck ✓；本地实例实测 DOWN 下 image-proxy miss 3.6ms（原 60s）、stream 未缓存 404 8ms（原 60s）、metrics 15ms（原 150-200ms）、详情本地行 11ms（原 0.4-1.5s）。

## 0. 范围与语义（用户已定，执行者不得变更）

1. **私有网络前提**：目标部署关闭公网访问，带宽压力不构成问题；优化只解决"用户可感知的迟滞"，不追求吞吐。
2. **本地模块不受上游影响**：缓存列表、设置、历史、下载库列表等不依赖 EH 的业务，其数据链路必须是纯本地延迟（已实测 5-90ms）。
3. **EH 401/404/未登录等失败 = 熔断信号**：一旦某功能判定"EH 平台当前不可达/拒绝服务"（连接失败、超时、404、5xx、反爬 403），则：
   - **提示用户**（非阻塞、一次性），说明"EH 平台当前不可达，仅显示本地内容"；
   - **当前会话期间**（服务器运行时）**不再自动访问 EH**：所有自动触发的 EH 上游请求一律快速短路返回 404（`EH_UNAVAILABLE`），不看上游、不排队、不重试；
   - **只有用户手动触发刷新/访问任务**（下拉刷新、重试按钮、显式恢复操作、登录等用户主动动作）才允许**一次探测**：成功 → 恢复 UP；失败 → 维持 DOWN 并再次提示；
   - **只读本地**：已下载画廊可完整阅读（本地 detail + 推送文件 + 本地缩略图），浏览历史/收藏/下载列表照常。

## 1. 背景

perf 实测（2026-08-30）：
- `/api/v1/image/proxy` 在 EH 不可达时每条 miss **阻塞 60s**（30s connect+30s read），首页 25-50 张缩略图全部吃到该代价，页面"卡死"1 分钟才落 placeholder；404 不进缓存，刷新重来。
- 详情页每次打开同步抓 EH 补强（0.4-1.5s），失败也不落状态。
- `/api/v1/health` 60s 窗口首请求阻塞 10s HEAD 探活。
- `/api/v1/metrics/dashboard` 恒定 150-200ms：`findByState(3).size` 全表加载 9171 条实体 + `getDiskEntryCount()` 全盘扫描 cache。
- `download/maintenance/preview`（393ms）与 `countPushedPages`/`findPushedPageFile`（每次 `listFiles`）均为纯本地但无缓存。

与 App（Android）的行为对齐：App 端 EH 失败即停止静默重试、留用户手动操作；WebUI 目前缺少等价的状态缓存。

## 2. 目标

- **G1**：EH 不可达被检测后，后续**所有**自动上游访问在毫秒级直接短路（本地链路不受影响）。
- **G2**：用户收到明确且不打扰的提示；手动恢复路径存在且语义明确（试探一次，见 §0.3）。
- **G3**：下载库/本地内容在 EH 断开时保持 100% 功能（列表、标签、详情、分页阅读、缩略图 placeholder 或本地封面）。
- **G4**：metrics/主线纯本地端点的开销回归 ms 级（消除全表加载与全盘扫描）。
- **G5**：该机制可在后续接入"强制离线模式"（用户显式切换，不再探测）。

## 3. 架构

```
用户动作(手动)─────► 探测(单飞, 超时≤5s) ──成功──► UP
       ▲  ▲                                  ▲
提示UI │  │                                  │失败
       │  └─ POST /api/v1/site/availability  ┘ (HALF-OPEN→DOWN)
       │                  │
       ▼                  ▼
 DOWN: EhAvailabilityService.isBlocked() == true
   ├─ 自动上游路由（search/feed/detail-enrich/image fetch/proxy/fetchImageUrl/
   │   prefetch/下载worker重试/健康HEAD）→ 直接 404 EH_UNAVAILABLE，无网络IO
   ├─ 本地路由（history/download/favorite/settings/preferences/metrics/cache
   │   status/推送文件/本地详情）→ 完全不受影响
   └─ health 的 galleryApi 组件报告 DOWN（不再占用请求线程）
```

### 3.1 服务端：`EhAvailabilityService`（@Service，进程级状态机）

- 状态：`UNKNOWN | UP | DOWN`（DOWN 记录 `downAt`、`lastReason`；UNKNOWN 只在启动后未探测时存在，实际请求视同 UP 但失败即 DOWN）。
- 判定失败：`connect/read/write 超时、UnknownHost、连接拒绝、5xx、404（站点级：根路径/搜索列表）、403/429（ECF 反爬）`。**不是**失败：单画廊页 404（Invalid page 语义仍由 GalleryLookupService 处理）、用户输入的非法搜索词、509（限流视为站点可达）。
- `probeNow()`：单飞（`AtomicBoolean`/`CompletableFuture`），HEAD `https://e-hentai.org`（或当前站点）超时 5s，复用 HealthController 的探测逻辑并**收拢到该类**（HealthController 只读其结果，HTTP 请求不再放在请求线程上——G1 的前置）。
- `isBlocked()`：DOWN 时返回 true；不自动恢复（无 TTL 到期重探——**自动恢复违反 §0.3**；只允许 `probeNow()`（手动）恢复）。
- 可观测：`GET /api/v1/site/availability`（公开，供前端提示与诊断页）。`POST` 同一路径执行手动探测（鉴权同 /api）。
- 不落库、不与同步/设备状态交互（服务器单用户模型，ADR-0001；会话期=进程运行期，重启回落 UNKNOWN）。

### 3.2 熔断点接线（顺序：入口判断 → 短路）

| 调用方 | 位置 | DOWN 时行为 |
|---|---|---|
| 上游搜索/feed/detail | GalleryService.searchGallery / feedGallery / topListFeed / getGalleryDetail（enrich 分支） | 立即返回 `success=false`，错误携带 code |
| 图片流 | ImageProxyController.streamGalleryImage（cache/pushed 命中之后、fetch 之前） | 立即 404 `EH_UNAVAILABLE`（未命中的页） |
| 缩略图代理 | ImageProxyController.proxyImage miss | 立即 404 `EH_UNAVAILABLE` |
| URL/页数解析 | GalleryLookupService.detail/fetchImageUrl/fetchPageCount | 抛 `EhUnavailableException`（统一被上游 catch 转为 404） |
| 预取 | PrefetchService.prefetchAround | 直接 skip（debug 日志） |
| 下载 worker 自动重试 | DownloadService（start/轮询路径） | 不发起；任务状态保持 pending 并给 UI 提示（"EH 不可达，待恢复"） |
| 健康检查 | HealthController.checkGalleryApi | 读 EhAvailabilityService（结果缓存 60s→ 由状态机时间戳取代） |
| 用户登录 | AuthController/eh-login、site 相关手动动作 | **放行**（手动访问语义，其内部失败计入状态机） |

404 响应复用 `errorEnvelope`，新增 code `EH_UNAVAILABLE`（message 中文提示，含"仅显示本地内容"）。`contracts/openapi.yaml` 补充该错误码到相关端点错误表。

### 3.3 下载库本地缓存机制（与熔断解耦，独立收益）

- **`DownloadDirIndex`（新，内存索引）**：启动即对 `downloads/` 建索引：`gid → {pageFiles[pageno→ext, size, mtime], totalBytes, dirMtime}`；失效策略：目录 mtime 变化（推送/删除/清理落盘后）、或被下载生命周期事件触发失效、每 60s 兜底重扫本轮未变目录（简化：入口文件数+dirmtime 比较，O(目录数) 元数据遍历而非全文件遍历）。
- 消费方改造：
  - `DownloadService`/`countPushedPages` → `index.pageCount(gid)`；
  - `ImageProxyController.findPushedPageFile` → `index.findPage(gid, page)`（索引命中直接返回路径+扩展名；索引 miss 再 listFiles 并回填）；
  - `DownloadMaintenanceService.preview` → 基于索引（冗余/无效检测并行化），预览不再全盘扫描；执行清理按索引失效后重扫（一致性保证）。
  - `getDiskEntryCount()`/`diskSizeBytes` → 索引维护的运行值（`ImageCacheService` 的 `collectFiles` 同样改为索引或按需）。
- **Metrics**：`getCompletedDownloadCount/getFailedDownloadCount` → `downloadRepository.countByState(3)`（JPA 生成 COUNT SQL）；`getDiskEntryCount` 用索引。预期 `/metrics` 与 `/dashboard` 从 150-200ms → <10ms。
- **下载缩略图本地化**：下载行的 `thumb` 指向 EH 时，DOWN 状态直接走 placeholder（前端已有 `onImgError` 兜底）；**新增**：索引提供 `downloads/<gid>/{cover 或 0001}.*` 作为本地封面候选（`/api/v1/image/local-cover/{gid}`？—— 待定：可用 `/api/v1/image/{gid}/0` 已推送文件路径复用，前端把 DOWN 态 thumb 重写为本地文件，见 §3.4 前端）。

### 3.4.0.1 下载页每页条数与快速定位（Android 对齐，2026-08-30 补充）

Android 参照（`DownloadsScene.java`）：
- `perPageCountChoices = {50, 100, 200, 300, 500}`；分页指示器控件 `PaginationIndicator`（`initPaginationIndicator(pageSize, choices, visibleCount, indexPage)`），**仅当可见条数 ≥ paginationSize(500) 时显示**，支持页码跳转 + 每页条数切换 + 滚动回位（`MyPageChangeListener.onPerPageCountChanged`）。
- WebUI 现状缺口：
  1. `downloadListSettings.ts` 的 `DOWNLOAD_PAGE_SIZES = [50, 100, 200]` **与 Android 不一致**（缺 300/500）；
  2. 每页条数目前**只能在 AdminDownload 设置页**修改（`:189`），下载页本身无控件——与 Android“下载页内嵌分页条”不一致；
  3. 无页码条/跳页能力（当前是无限加载到尾部，快速定位只能靠过滤/搜索，无法直接跳页）；
  4. 服务端 `listDownloads` 的 `limit` 上限 `coerceIn(1,500)`（DownloadService.kt:124）**够** Android 最大 500，无需扩。
- 实现（前端为主）：
  - DownloadView 顶部/底部增加分页条：`第 x / y 页 · N 条/页 [50|100|200|300|500]`（复用 downloadListSettings 的 localStorage 键，组件加载即生效）；页数 >1 时显示，`total ≤ PAGE_SIZE` 时隐藏（对齐 Android `visibleCount < paginationSize` 语义——WebUI 用 `total ≤ pageSize` 判定更合理，保留同义注释）。
  - 跳页改为**服务端 offset 直取**：`offset = (page-1)*pageSize`（现有 API 已支持，无需后端改动）。
  - `DOWNLOAD_PAGE_SIZES` 对齐为 `[50, 100, 200, 300, 500]`；AdminDownload 选择器同步受益（同一常量）。
  - 长列表快速定位：页码条 + 现有 FastScroller 缩略图位置提示协同（PageMode 等价：跳页即 `loadPage(pageIdx)`，虚拟滚动滚回顶部）。

### 3.4.1 数据驻留策略（"整个数据库加载到内存"可行性结论）

实测数据（dev 库 7.7MB，部署库量级相近）：
- 行数：download_info **9,001**、download_dirname **23,577**、filter 839、server_config 901、history_info 120、其余 <2。
- 文本量：download_info 的 title+titleJpn+thumb 文本合计 **~1.8MB**；全库实体数据约数 MB 级。

**判断：技术上可行（实体对象化约 30-60MB 堆，JVM 默认上限即可），但工程上明确不做**，原因：

1. **架构不匹配**：本系统 DB 是唯一权威（同步 pull、下载 worker、App 推送、导入还原都是独立写路径）。全量驻留内存 = 每处写都要失效/重建缓存，引入缓存一致性 bug 面，而收益被 SQLite 本地毫秒级读取抵消（列表/详情/历史实测 5-90ms）。
2. **真正慢点不在此**：`/metrics` 150-200ms 来自 `findByState(3).size`（加载全部 9001 实体计数）与 `collectFiles` 全盘扫描——修复手段是 `countByState` SQL + 目录索引（§3.2），不是全库内存。
3. **替代方案（采纳）**：只驻留**热点小表**（download_info 行级 Caffeine + 失效钩子、filter/quick_search/label/server_config 等 <1K 行的表全量载入），冷表守恒走 SQL；这即"内存数据库"实践中 L1 缓存的常规形态，风险可控、收益等同。


#### 3.4.0 点击条目逻辑缺陷（2026-08-30 审查，连同修复）

问题清单（实测 0.4-1.5s 长尾 + 功能缺口）：

- **P-A 详情入口不带 token**：FavoriteView.vue:388、HistoryView.vue:372、DownloadView.vue:607 都是 `router.push('/gallery/${gid}')`，丢弃本地已有 `token`（history/favorite/download 行都有）。
  - 后果 1：`getGalleryDetail`（GalleryService.kt:293-315）先查 history/download 行——**收藏条目若无历史/下载行，`token==null` 直接返回 null**，收藏页点开详情 100% 失败（"Gallery not found"）。
  - 后果 2：命中 history 时 `enrichHistoryDetail` 同步抓 EH 补强评论（0.4-1.5s），本地 8 成数据被上游拖慢。
- **P-B 阅读器入口不带 token**：GalleryDetailView.vue:382 与 DownloadView.vue:611 都是 `/reader/{gid}`（无 token）。ReaderView.load()（ReaderView.vue:520+）`getDetail(gid)` 无 token → 服务端无本地行时详情失败 → 降级 totalPages=1，阅读器实质上不可用；有历史行时每次为了一页源数据抓 EH。
- **P-C 服务端详情来源顺序**：`getGalleryDetail` 先 history 后 download；实际"下载行是完整本地数据源（含 pages）"（downloadDetailDto，GalleryService.kt:322），应先查 download，且不抓 EH 即可渲染；history 分支只在 download/history 二者无本地行时上游直取 + 落历史。
- **P-D 详情页无 token 查询参数透传**：GalleryDetailView 已解析 `route.query.token`（entryToken，GalleryDetailView.vue:256）并传给后端——保留，仅需各列表入口补 token（P-A）。搜索结果入口（HomeView/SearchView `openGallery`）已带 token，无回退问题。

修复方案（对齐 Android 语义：App 端列表点击带 token，详情/阅读优先本地数据）：

1. **列表入口统一带 token**：HistoryView/FavoriteView/DownloadView `openGallery`/`onItemOpen` → `router.push({ path: '/gallery/${gid}', query: { token } })`（GalleryInfo/DownloadItem 已含 token）。
2. **阅读器入口带 token**：GalleryDetailView.read()、DownloadView.onItemRead() → `/reader/${gid}?token=…`；ReaderView.load() 读取 `route.query.token` 并传给 `getDetail`。
3. **服务端详情顺序调整**：`getGalleryDetail` → ① download 行（downloadDetailDto，含本地 pages，零上游）；② history 行（enrich 减配：**不再无条件抓 EH**——站点可达时应并行补评论（后台/异步），不可达时静默本地 DTO；列表/详情端到端不再 1s 级等待）；③ token 非空 → 上游直取；④ 无 token 且无本地行 → null。同步保持：历史行更新（addToHistory）不受影响。
4. **收藏条目详情**：收藏行无历史/下载时，上游不可达但本地收藏行有 token/title/thumb——新增本地收藏明细 DTO（favoriteDetailDto，同 downloadDetailDto 模式），保证 EH 断网时收藏条目详情可打开（阅读器有本地数据即可翻页——注意收藏行无 pages（收藏行没有 total？收藏只提供 token，pages=0 时阅读器 cannot open——**阅读器需在无 pages 时兜底用 `POST /gallery/history/{gid}` 触发历史落行服务端抓 pages，或服务端收藏行 + detailCache 异步补全 pages**——见 §3.4.0-P-C 附注 2）。

附注：
- EH 可达性状态机（§3.1）是 P-C 的收口：enrich/detail 同步抓取在 DOWN 时被熔断短路，P-A/P-B 修复后本地 token 直达，**EH 断网时点击已览/已藏/已下载条目全程无 1s 级等待且可完整阅读**（公开 list → 详情 → 阅读器闭环）。
- 推荐实现点：`getGalleryDetail` 增加 `favoriteDetailDto` + 阅读器 `totalPages` 缺失时的 async prefix（收藏行无 pages；可服务端 `resolvePageCount` 带缓存，网络不可达时 pages=1 + PageMode 提示），避免客户端每图重试坏链。



- **全局状态**：`stores/availability.ts`——启动时 `GET /api/v1/site/availability`（一次），监听状态；`DOWN` 时：
  - 所有 `client` 请求预检短路（已知 DOWN 且为自动请求 → 不发，直接抛 `EhUnavailableError`）；手动操作（refresh/重试/登录页）带 `bypass` 标记放行；
  - **提示**：App 级 snackbar/banner（复用现有 toast 样式意图）："EH 平台当前不可达，仅显示本地内容" + [重试] 按钮（`POST /site/availability` 探测成功后全局恢复并自动重放当前视图数据）。
  - 提示只出现一次（同会话），不重复弹；可用 `HomeView` 现有 empty-state 提示位扩展。
- **视图行为**：
  - HomeView：feed/search 失败（`EH_UNAVAILABLE`）→ 内容区显示本地历史（已实现空关键词回滚）+ 顶部横条提示；
  - GalleryDetailView：EH 不可达时详情来自本地历史/下载行（已有回退），失败文案区分"EH 不可达"；
  - PageMode：图片 404 `EH_UNAVAILABLE` → 不再自动重试风暴（停指数退避），进入"第 N 页加载失败（EH 不可达）"终态 + 手动重试；
  - DownloadView：本地列表不受影响；缩略图 placeholder；下载新任务按钮在 DOWN 时禁用（提示"待 EH 恢复"）——**待定**：也可允许排队（任务留 pending，恢复后自动开始），倾向后者（与 App 语义一致，见 §2 G5）。
- **SW（sw.js）**：无改动（服务缓存只影响静态资源；API 不缓存成功视图以免 DOWN 状态被陈旧数据掩盖——现有逻辑维持）。

## 4. 契约增量（附录 A · 冻结清单）

1. 错误码枚举新增：`EH_UNAVAILABLE`（HTTP 404，`error.code`，用于 image/stream；list 类端点保持 `success=false` 信封并把 code 放入 message 或新增可选字段 `errorCode` —— 二选一，**建议**统一扩展 GalleryListResponse/TopListResponse 可选字段 `cause?: "EH_UNAVAILABLE"`，向后兼容）。 **[done 契约]** `contracts/openapi.yaml` 已标注：image/proxy 与 image/{gid}/{page} 的 404 描述补充 `code=EH_UNAVAILABLE`（message 含"仅显示本地内容"，熔断时无网络 IO；image/proxy 为 ErrorResponse 信封、流图为 ImageErrorResponse 复用现有错误字段）。服务端实现 **[pending]**（backend agent）。
2. `GET /api/v1/site/availability` → `{state: "UP|DOWN|UNKNOWN", downAt, lastReason}`（公开只读）。 **[done 契约]** openapi 已新增（tags Health，security 公开）；按实现约定响应追加 `lastProbeAt`（最近一次探测时间，比 §4 原表多一个可选字段）。
3. `POST /api/v1/site/availability` → 同结构（执行一次探测，鉴权）。 **[done 契约]** openapi 已标注（手动恢复唯一路径，无自动探测/TTL 重探）。
4. `GalleryListResponse`/`GalleryDetailDto`：可选 `cause` 字段（缺省 null，不影响旧客户端）。 **[done 契约]** openapi 已扩充，范围含 TopListResponse（用户约定）：三者均加 `cause`（enum `EH_UNAVAILABLE`，nullable，缺省 null/省略）。
5. 同步（sync）契约不变；本机制不产生同步记录（服务器本地运行时状态，ADR-0001）。 **[确认]** 本次未改 `sync-schemas.json` 与同步路径。

## 5. 不做（范围排除）

- 公网/高吞吐优化（§0.1）。
- 跨设备状态传播（只有服务器单实例会熔断）。
- 熔断自动恢复/半开自动探测（§0.3 明确只有手动）。
- App（Android）侧改动（App 端已有等价失败语义；下游波次再评估）。
- DB 换成 PostgreSQL（ADR-0005 另议）。

## 6. 验收（DoD）

- 模拟断网（防火墙 drop e-hentai.org）：
  1. 首页刷新 → 列表（本地历史）正常，缩略图全部 placeholder，不再出现 60s 阻塞；
  2. 打开已下载画廊详情 → 本地数据秒开；阅读器翻页用本地缓存/推送文件速度正常；
  3. 打开未缓存画廊 → 快速 404 + 提示，且**无** 60s 级网络等待；
  4. 抓包验证：DOWN 期间无任何 e-hentai.org 出站 TCP（除用户手动探测）；
  5. 手动"重试"→ 网络恢复后一切复原；保持断网则提示仍在、操作仍秒级失败；
  6. `/metrics`、`/metrics/dashboard` <20ms；`download/maintenance/preview`（索引命中）<50ms。
- 测试补齐：EhAvailabilityService 状态机单测、DownloadDirIndex 失效单测、Controller 短路路径测试、前端 availability store + 视图提示测试（E2E 基线截图 2 张：DOWN 提示位、placeholder 列表）。

## 7. 任务分解（实现顺序）

1. EhAvailabilityService + availability 端点（迁移 HealthController 探活 + 单飞）【P0】
2. 熔断点接线：GalleryService（search/feed/toplist/enrich）、GalleryLookupService、ImageProxyController（proxy/stream）、PrefetchService【P0】
3. DownloadDirIndex + countByState + ImageCacheService 索引化【P0】
4. 前端 availability store + EhUnavailableError + 提示 UI + 视图适配【P1】
5. 契约/测试/README·observability 更新【P1】
6. 终验（§6 DoD 段落），产出证据到 MASTER 文档【P1】

## 8. 复验证据格式（提交时）

- `docs/plan-2026-08-30-eh-circuit-breaker.md` 更新状态与结果；
- DoD 各条：耗时数据表（前/后）、断网回归截图/记录、出站验证（`ss -tpn | grep :443` 或 `strace`）。
