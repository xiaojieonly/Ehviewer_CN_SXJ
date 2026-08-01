# AnotherViewer 全面代码审计报告

> 日期：2026-08-01 ｜ 分支：`BiLi_PC_Gamer` ｜ 范围：`ehviewer-web`（后端）、`web-frontend`（前端）、`app/`（Android，重点 webui 包）、`mock-server`、`contracts/`、`ehviewer-core`
> 方法：4 个并行子代理深度审计 + lead 逐条复核关键发现（复核过的不再标注，其余标注"待复核"）
> 之前 session 已覆盖：parser 一致性、品牌更名、UI/UX（见 `ui-ux-review-2026-08-01.md`）、HTTP 冒烟。本报告聚焦**遗漏的功能性/安全性/契约性问题**。

---

## 严重度统计

🔴 CRITICAL 3 ｜ 🟠 HIGH 13 ｜ 🟡 MEDIUM 17 ｜ 🔵 LOW 20+

---

## A. 🔴 CRITICAL

### A1. 阅读器端到端断裂：`GET /api/v1/image/{galleryId}/{page}` 后端未实现
- 前端阅读器全链路依赖此端点：`web-frontend/src/components/reader/PageMode.vue:100`（`pageImageUrl`），被 `ScrollMode.vue:99`、`DualPageMode.vue:186`、`ImageReader.vue:282` 使用；openapi.yaml:818 也定义了它。
- 后端 `ImageProxyController.kt` 只有 `/proxy`、`/cache/status`、`/cache/clear` —— **没有 `/{galleryId}/{page}`**。服务器对所有阅读器图片请求返回 404 → 阅读器每一页都显示 "Failed to load page"。
- 附带：契约中该端点的 `?enhanced=` 参数、`Range`/206、429 也都未实现。
- 修复：在 `ImageProxyController` 实现流式代理端点（解析页面 → 拉图 → 流式转发 + Range + 缓存），或临时把前端指向 `/proxy?url=`。

### A2. 默认零认证 + CORS 通配 + 无 CSRF：任何网页可远程驾驶整个 API
- `ServerConfigService.kt:41`：`security.require_auth` 默认 **false**（首启即写入 DB）。
- `WebConfig.kt:11-14`：`allowedOriginPatterns("*")` + `allowCredentials(true)`。
- `SecurityConfig.kt:29`：CSRF 关闭。
- 后果（已复核链路）：受害者在局域网内打开任意恶意网页，该网页 JS 可无凭据调用所有 `/api/**` —— 读取浏览历史/收藏/代理密码、触发下载、清空历史、撤销设备。即使开启认证，`*`+credentials 也是错误的 CORS 配置。
- 修复：默认 `require_auth=true`（或要求显式环境变量才开服务）；CORS 改为实际前端源列表；保留 CSRF 关闭需结合 bearer 认证合理性重审。

### A3. Android WebUI 默认 HTTP 被网络安全配置拦截（API 28+ 全功能失效）
- `app/src/main/res/xml/network_security_config.xml:28`：`<base-config cleartextTrafficPermitted="true"/>` **被注释掉**，仅 ehtracker.org 放行明文。
- `WebUiConfig.java:31,42` 默认 `http`，`WebUiSyncFragment.java:190,228` 协议下拉默认 http。
- 后果：Android 9+ 上所有默认配置的 WebUI 连接（同步/远程阅读/委托下载）抛 `UnknownServiceException: CLEARTEXT communication not permitted`，功能全部静默失败，除非用户手动选 https。
- 修复：二选一——`base-config` 放行明文（LAN 信任模型，配文档警告），或强制 https 并在 UI 明确报错。

---

## B. 🟠 HIGH

### 后端安全

**B1. WebSocket 完全无认证 + 生产部署不可连（双 bug）**
- `WebSocketConfig.kt:19`：origin 白名单只允许 `localhost:3000/:5173`，SPA 是同源从 `IP:8080` 提供的 → 生产 SockJS 连接被拒（contract 附录 C.1 已标注）。
- 没有任何 `ChannelInterceptor`，STOMP CONNECT 的 token（websocket-protocol.md §1.3）从未校验 → 局域网任何客户端可订阅 `/topic/download/*` 观察下载活动。

**B2. 同步/设备层信任客户端：无用户隔离、可跨用户撤销设备、无 tombstone**
- `SyncService.kt:52-57,94-252`：pull 读全局表、合并函数完全不区分 `username`；`237-252` 任意伪造 deviceId 自动建设备；`EhAuthService.kt:141-147` 任意已认证用户可撤销任意设备（DoS）。
- `mergeFavorite`（:94-111）丢弃 `deleted=true` 推送 → **删除永远不会传播**（与 Android 端 B7 呼应，收藏删除后下次 pull 复活）。
- `mergeFilter`/`mergeQuickSearch`（:191-233）跳过契约的"本地优先"与 2 秒偏差内"enabled=true 优先"规则；tombstone 从不落库。
- `syncDownloadLabel` 实体类型完全缺失（sync-schemas.json:399 定义了）。

**B3. Token 永不过期 + 明文落库 + 重启全失效**
- `EhAuthService.kt:24` tokenStore 为内存 Map 无 TTL；`KEY_SESSION_TIMEOUT` 只读不执行；`SyncDeviceEntity.kt:30-31` 明文存 token；服务器重启所有已配对设备全部失效且无恢复路径。

**B4. 未认证可登出 + 开放注册**
- `SecurityConfig.kt:33-34`：`/auth/logout` permitAll → 任何局域网客户端可静默清空共享 EH cookie（拖垮图片代理）；`/auth/register` 开放 → 开了认证=形同虚设（单一角色、无首个用户限制）。

**B5. 代理/SMB 密码明文存储且回显**
- `SettingsService.kt:40-47` GET `/settings` 直接返回代理密码；`ServerConfigEntity.kt:12-13` 明文 TEXT 落库；`SmbConfigEntity.kt:30-31` SMB 密码明文。`EncryptionService`（AES-GCM + key-path 配置）已写好却是死代码（4.2）。

**B6. SSRF 潜伏点**
- `ArchiveController.kt:18-21` `downloadArchive(gid, url)` 接受任意用户 URL——当前是 stub 无实际请求，一旦实现即 SSRF。实现时须校验 host。当前无活动 SSRF（`/image/proxy` 只读缓存、下载 URL 来自 EH 解析、健康检查固定 URL）。

### 后端逻辑

**B7. 处理管线整体损坏**
- `ProcessingController.kt:39-41`：`pages = 0..0`（TODO）——"处理全部页面"实际只处理 1 页（还是第 0 页）。
- `ImageProcessingService.kt:189`：`if (failed == 0) DONE else DONE` 复制粘贴 bug——部分失败也报 DONE。
- `:151` outputPath 计算后从未使用；`NoopProcessor` 把"增强图"写在输入旁（缓存目录），且**没有任何端点提供增强图**（契约 `/image/{id}/{page}?enhanced=` 不存在，见 A1）。
- 处理事件（Started/Progress/Completed）无 WS 监听器 → `/topic/process/*` 静默。

**B8. 下载管理器无视自身并发配置 + 暂停/删除竞态**
- `DownloadService.kt:31` `workerPool` 创建后从未使用；`:121-125` 每个下载裸 `Thread`；`:157-160` `startAll` 全量并发启动；`maxConcurrentGalleries/Images` 等设置从未被读取 → 无界线程+磁盘。
- `pauseDownload` 用 `Thread.interrupt()`，OkHttp 不可中断 → 暂停后文件仍写（最多 30s）；`deleteDownload` 与运行线程竞态：线程后置 `repository.save` 可复活已删行（state=4 幽灵下载）。
- `fetchPageCount` 失败返回 1 → 网络错误时静默产出"1 页已完成"下载（假数据损坏）。

**B9. 收藏分页 off-by-one + slot 参数无效**
- `FavoriteService.kt:12-16`：`drop(page * pageSize)` 0-based，而契约/前端 page=1 起 → **前 20 条收藏永远被跳过**；`slot` 接受但从不使用 → 收藏夹筛选无效。

**B10. SMB 备份 bug 群**
- `SmbBackupService.kt:138` 硬编码 `File("./data/downloads")`，无视 `config.download.path` 自定义路径；
- `aggressive` 标志（:88,:118）从未读取；
- `cancelSync`（:107-112）在线程退出前就置 `isSyncing=false` → 新旧同步并发写同一远端路径；
- 与 DownloadService 无协调 → 边写边拷产生残文件。

### 前端

**B11. SearchView / FavoriteView / DownloadView 无请求序列守卫（陈旧响应覆盖新结果）**
- `SearchView.vue:407-431`：无 `requestSeq`（HomeView.vue 有），快速连续搜索时旧响应后到会覆盖新结果/总页数；`onLoadMore` 也会附加过期页；`:416` 无 gid 去重。
- `FavoriteView.vue:163-183`：快速切换收藏夹槽位 1→2，槽 1 的迟到响应替换槽 2 列表。
- `DownloadView.vue:159-173` 同样无守卫。
- `stores/preferences.ts:14-25`：`load()` 后到者胜——设置页快速切换可能把旧快照写回服务器。

**B12. `useEnhancedImage` 绕过单例 WS 管理器自建第二个 STOMP 客户端**
- `composables/useEnhancedImage.ts:204-218`：自己 `new Client`+SockJS——(a) 阅读器打开期间存在第二条 `/ws` 连接；(b) **不带 connectHeaders**，认证开启时 STOMP CONNECT 必失败并无限重试；(c) 固定 `reconnectDelay:5000` 而非契约的指数退避。应走 `useWebSocket` 的 `subscribeProcessing`。

**B13. SW 路由顺序使图片缓存失效**
- `public/sw.js:116-126`：规则 2（`/api/` → `networkFirstApi`）先于规则 3（`cacheFirstImage`）匹配 → 同源阅读器图片进 API_CACHE（无 TTL），IMAGE_CACHE/配额淘汰逻辑永远只服务跨源缩略图；且 `networkFirstApi` 缓存 `ok` 响应无 TTL → 过期 API 数据离线无限期提供。

**B14. 阅读器增强图热替换失败：已加载页被整页替换为错误覆盖层**
- `PageMode.vue:350-352`：原图已显示后增强图失败，`onImageError` 置 error 并整页替换为 "Failed to load page N"（websocket-protocol §3.3 要求静默保留原图）。

### Android

**B15. 删除永不传播：收藏删除/历史清除被下次同步撤销**
- `WebUiSyncEngine.java:80-125`：`buildPush` 全量推送且 `deleted=false` 硬编码（:93,:104），从不发 tombstone；`applyFavorites`（:116-122）按并集把服务端仍存在的已删收藏**重新插入本地**。
- 后果：删除收藏 → 服务端仍持有 → 下次 pull 复活；清空历史 → 重装或首次同步（since=0）全部复活（`EhDB.applySyncedHistory` 是 upsert，`EhDB.java:895-917`）。

**B16. WebUiGalleryProvider 退出崩溃风险**
- `WebUiGalleryProvider.java:81-86,116-118,132`：`stop()` 调 `shutdownNow()`，而渲染线程的 `onRequest`/`onForceRequest` 无守卫直接 `mExecutor.execute()` → 退出瞬间的迟到请求抛 `RejectedExecutionException` 在 GL 渲染线程未捕获 → 崩溃。`EhGalleryProvider` 用 null 守卫+延迟释放规避了同类问题。

### Mock 服务器

**B17. mock 与真后端/frontend 三个协议级不一致**
- **WS legacy topic 断链**（`ws/progress.mjs:234,244`）：契约要求 `/topic/download/{gid}` 与 `/topic/download/all` 发**裸 DTO**；mock 在 `/{galleryId}` 发**信封**、从不发 `/all` → 前端下载进度对 mock 永不更新。pong 订阅 id 取 `keys().next()` 首项（:154），非匹配项 → 错位丢帧。
- **下载 state 枚举**（`fixtures/downloads.mjs:2`）：mock 用 `0=pending,1=downloading,2=paused...`；真后端是 Android 语义 `0=NONE,1=WAIT,2=DOWNLOADING,3=FINISHED,4=FAILED` → 对 mock 暂停显示为下载中、start 拒收已完成项等全部错位。
- **分类位掩码反转**（`fixtures/galleries.mjs:4-15` 等）：mock 用 `1=Doujinshi...`；真 `EhConfig.java:9-18` 是 `MISC=1,DOUJINSHI=2,...`（前端 `types/components.ts:118-129` 与真一致）→ mock 下分类标签全错、`search?category=` 位与结果错。`sync-schemas.json:40` 注释也重复了这个错误映射。
- **`/api/v1/proxy/test` 挂错路由**（`routes/settings.mjs:73`）：挂在 `/api/v1/settings` 下变成 `/api/v1/settings/proxy/test`；真后端（`ProxyController.kt`）与前端（`AdminProxy.vue:188`）都用 `/api/v1/proxy/test` → 管理页"测试连接"对 mock 404。
- **`/api/v1/preferences` 缺失**：真后端 `PreferenceController.kt` 有 GET/PUT，前端设置页调用（`api/preferences.ts:46,50`），mock 404；该路径在 openapi.yaml 也是缺口。
- **sync 端点用陈旧 openapi 平铺结构**（`routes/sync.mjs`），而真后端与 sync-schemas.json 是 `{entities, deviceId, timestamp}` + `bookmarks/filters/downloadLabels/preferences` → mock 无法测 Android 同步流。

---

## C. 🟡 MEDIUM（选列）

1. **后端**：token 缺失返回 403 而非契约 401（`AuthTokenFilter`+无 entryPoint）→ 客户端 401 重登录逻辑永不触发；登录/配对无速率限制（6 位码 32^6，10 分钟 TTL，局域网可爆）；BCrypt 72 字节截断无长度约束。
2. **后端**：`GalleryService.searchGallery` 是内存历史过滤（全量加载+contains），非 EH 搜索；大数据下退化；`getHistory` 同模式。
3. **后端**：`DownloadItem.downloadDir` 向客户端泄露服务器绝对路径；`/metrics` 形状与契约不符；`HealthComponent.details` Map vs string；SMB state 枚举 `idle/syncing/...` vs 契约 `IDLE/RUNNING/...`（前端与真后端一致，契约陈旧）。
4. **后端**：`CommentService` 全部以 `anonymous` 上传；`SpaWebConfig` 只转发 2 段深链（`/gallery/123/page/4` 404）；`EhSessionManager.requireValidSession` 死代码；内存 Map 无界（tasks/pairCodes/comments）。
5. **前端**：WS 管理器认证失败后状态永挂 `connecting`、`lastError` 只报 transport 错误（`useWebSocket.ts:274-299`），契约 §1.5.6 页面隐藏暂停重连未实现；`commitSearch` 对 `keyword:null` 崩溃（`SearchView.vue:434`）；HomeView 去重后死循环空拉页（`HomeView.vue:200-204`）；`GalleryDetailView.vue:452` 无 gid watch；多个 snack/flash 定时器与 FastScroller 的 window 指针监听器未在卸载时清理；AdminDownload/AdminProcessing 600ms 防抖在卸载时丢弃未保存修改。
6. **Android**：fastjson 1.2.83（EOL，CVE-2022-25845 系）未开 safeMode 且 2 处无类型 parseObject（`WebUiApiClient.java:155,198`）；`WebUiCredentialStore` 在主线程做 KeyStore 操作，FBE 设备首解锁前打开设置页即崩（`WebUiCredentialStore.java:52-69`）；同步无单飞保护（双点"立即同步"并发跑 push→pull→apply，高水位竞态回退，`WebUiSyncFragment.java:542-593`）；`applySyncedHistory` 只拷 12/20+ 字段且用 `time` 而非 `lastModified` 比较；偏好同步用 `since=0` 全量重拉且 `lastModified:0`，`preferences` 实体不在冻结契约内（`PreferenceSyncHelper.java:54,543-588`）；OkHttp 3.14.7（CVE-2021-0341 需 3.14.9+）。
7. **mock**：`/metrics`、`/health` 形状与真后端不符；`/cache/clear` 是 mock-only 端点；无 401 语义（`requireAuth` 被忽略）→ 认证流程无法演练；缩略图指向不存在的 `ehgt.org/t/mock/...`（开发环境裂图）；token 16 位 hex vs 真实 10 位。

---

## D. 🔵 LOW（摘要）

- **死代码/未用**：后端 `EncryptionService`、`workerPool`、`requireValidSession`、`ArchiveService`/`TorrentService` stub；前端 6 个 composables（`useVirtualScroll/useInfiniteScroll/useLazyLoad/useTheme/useImagePreload/useResponsiveColumns`）、3 个 store（`ws/gallery/download`——其中 download store 的 state 编号 0=active/1=done/2=failed 与前后端全都不一致，复活即坑）、2 个组件（`AppHeader/SyncProgress`）、`GalleryDetailView.copy.spec.ts` 重复 spec、`types/index.ts` 遗留 CATEGORY_MAP 与 `CATEGORY_BIT_VALUES` 矛盾。
- **重复实现**：`GalleryCard.vue` vs `AppCard.vue` 两套画廊卡；全仓 12 处原生 `<select>`、多套手写输入框/分段（UI 审查 UX-14 已知）。
- **Android**：`INTERNET` 权限声明两次（`AndroidManifest.xml:5-6`）；`webui_sync_settings.xml:48-58` 重复 preference 键；`clearConfig` 不调用服务端 logout；host/port 无校验；`getGalleryPages` javadoc 与行为不符；`WebUiGalleryProvider` 无 startPage 恢复、save 按钮永远失败。
- **契约陈旧（三方已一致、文档落后）**：openapi.yaml 的 Sync DTO（平铺 vs entities）、DownloadItem.state、SMB state 枚举、`AuthStatusResponse` 缺 `authRequired/ehSessionValid/ehSessionExpired`、`/preferences` 路径缺失、`websocket-protocol.md` 的信封/裸 DTO 段落与实作分歧。**文档先行的"契约冻结"纪律已被打破，需一次集中校准。**
- `test.key` 被 git 跟踪（仓库根，git ls-files 确认）——遗留密钥文件，应删除/轮换。
- `GalleryController.addToHistory` 接受无校验 Map body；`MetricsController` 占位零值；`WebSocketConfig` 允许 `5173`（dev server 实为 3000）。
- 阅读器 SW 场景：iOS A2HS 图标需在线安装（已知边界，PWA.md 已记录）。

---

## E. 建议修复顺序（按风险/收益）

1. **A1 阅读器端点 + A2 认证/CORS 默认值** —— 一个功能级灾难、一个安全级灾难，必先修。
2. **A3 Android 明文网络 + B15 删除传播** —— WebUI 特性在 Android 上"默认不可用"+"删除语义损坏"。
3. **B1 WS 认证与生产 origin、B3 token 过期、B4 登出/注册** —— 认证模型整体加固。
4. **B7 处理管线 + B8 下载并发/竞态 + B9 收藏分页** —— 三个核心功能正确性。
5. **B11-B14 前端竞态/WS/SW 系列** —— 用户可见错误。
6. **B17 mock 对齐（state/分类位/WS 主题/proxy 路由/preferences/sync 结构）** —— mock 是契约的守门员，必须先校准才能谈契约冻结。
7. **契约文档集中校准**（openapi.yaml + websocket-protocol.md + sync-schemas.json 注释 vs 实作三方一致）。
8. 死代码清理（D 组）——低优先但降低长期漂移风险。

---

## F. 复核记录

以下为 lead 逐条打开源码复核过的关键项：A1（ImageProxyController 3 端点 vs PageMode.vue:100）、A2（WebConfig.kt:11-14 / SecurityConfig.kt:29 / ServerConfigService.kt:41）、A3（network_security_config.xml:28 / WebUiConfig 默认 http）、B1（WebSocketConfig.kt:19）、B8 起点（DownloadService.kt:31 workerPool 未用）、B17 中 proxy 挂载（settings.mjs:73）、`/api/v1/preferences` 缺失（mock 无 routes/preferences.mjs）、DownloadController 全端点清单。其余为子代理报告、未逐行复核，落地前建议再验。

*工具：4×general 子代理（后端/前端/Android/契约+mock）+ lead 复核 ｜ 交付格式：Markdown*
