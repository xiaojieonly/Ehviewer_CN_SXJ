# 阅读进度同步 + WebUI 性能修复 — 实施方案

日期：2026-09-02 · 分支：BiLi_PC_Gamer · 状态：待实施（本文档为唯一实现依据，交给实现模型按序执行）

> 文档分两部分：**Part A（§1-10）阅读进度记忆 + 浏览记录多端同步**；**Part B（§11-14）WebUI 性能修复**。两部分无耦合，可分别实施；文件域互不重叠。

## 1. 背景与问题

WebUI（浏览器端）本地阅读**不记忆阅读进度**：重开画廊永远从第 1 页开始。审查结论是三层同时缺失：

1. **数据层**：服务端 `history_info` 表（`anotherviewer-web/src/main/java/com/hippo/anotherviewer/web/entity/HistoryInfoEntity.kt`）没有任何进度列；整个 web 服务端不认识 Android 的 `.anotherviewer`（SpiderInfo/.spm）进度文件。
2. **写路径**：`web-frontend/src/views/ReaderView.vue` 的 `pushHistory()`（≈L476）只 POST `{token, title}`，不带页码；降级态（无 detail token，纯本地导入画廊）完全不回写。
3. **读路径**：`ReaderView.vue` `load()`（≈L560）初始页固定取 `route.params.page` 否则 0；详情页入口（`GalleryDetailView.vue` `read()` ≈L409）永不带 page。设置页"显示阅读进度"开关（`GeneralSettings.vue` ≈L52）无任何消费方，是死设置。

对照：Android 端机制完整——`GalleryActivity.onUpdateCurrentIndex`（app/src/main/java/com/hippo/anotherviewer/ui/GalleryActivity.java:863）每翻页调 `putStartPage(index)` 写 `.spm`；打开时（同文件 :416）`getStartPage()` 恢复，双源（下载目录+缓存）取 max（`SpiderQueen.java:819`）。

## 2. 目标

1. 浏览器阅读记忆进度：重开从上次位置继续；纯本地（无 EH 元数据/降级态）画廊也有会话记忆。
2. 进度随浏览记录在 **App ↔ 服务器 ↔ 浏览器** 多端流转。
3. 复活"显示阅读进度"设置：卡片显示进度角标。

## 3. 核心设计决策（已拍板，不再讨论）

**D1 权威存储**：服务端 `history_info` 新增 `page` 列（Int，0 起页索引，语义对齐 Android `SpiderInfo.startPage`）。进度 = 历史记录的一个字段，搭既有同步链路，不新增表/文件格式/端点。

**D2 各端读写规则**：

| 端 | 恢复（打开画廊） | 写入（翻页） |
|---|---|---|
| 浏览器 | `detail.readProgress`（唯一来源；降级态用 localStorage 兜底） | REST `POST /gallery/history/{gid}` 直写 `page`（含 0，支持重读） |
| App | `max(本地 .spm, HistoryInfo.page)` | 既有 `.spm` 照写 + 桥接更新 `HistoryInfo.page` **并刷新 time** |
| 服务器 sync 合并 | — | 行按既有 LWW/策略序定胜负；**行胜时** `applyHistoryFields` 内 page 取 `max(existing, incoming)` |

**D3 sync 合并选方案 A（行胜才 max）**：私有部署个人场景，不为极端并发冲突增加协议复杂度；管线简单可跑通优先。配套措施：App 桥接 `updateHistoryPage` 同时刷新 `HistoryInfo.time`——正在阅读的设备时间戳永远最新，几乎不可能在合并中落败，把竞态窗口缩到"两台设备同时读同一本"（可接受，下个会话自愈）。**明确不做**：行负时单独抬 page（会引入 lastModified bump/echo/墓碑例外三处复杂度）。

**D4 已实施的 3 个服务端文件保留**（见 §5.1 ✅ 项，与方案一字不差）。

**D5 REST 直写 vs sync max 的理由**：旧 App push 不带 page（fastjson 默认 0），若按普通全字段覆盖会清零已存进度——max 保证只进不退；浏览器 REST 写代表当前活跃阅读位置（包括有意跳回 0 重读），必须原样落库。

## 4. 数据流

```
App 阅读 → onUpdateCurrentIndex → .spm（现状） + SiteDB.updateHistoryPage(gid, index)（新增，刷 page+time）
        → 下个同步周期 push（ledger 以 time 判变，time 刷新 ⇒ 必重推）→ 服务器 mergeHistory：
            行胜 → applyHistoryFields（page = max）；行负 → 整行忽略（D3）
        → 其他设备 pull → applyHistory → HistoryInfo.page → 打开时 max(.spm, page)

浏览器阅读 → 防抖回写 REST（带 page）→ 服务器直写（upsert，page 原样）
浏览器打开 → GET /gallery/{gid} → detail.readProgress（= history 行 page）→ 初始页
App 进度 → 服务器 → 浏览器 detail.readProgress；浏览器进度 → 服务器 → App pull → max 恢复
降级态（无 token）→ localStorage `readProgress:{gid}` 读写，不碰服务器
```

## 5. 改动清单

> 行号为 2026-09-02 核验值，实现时以锚点描述为准。

### 5.1 服务端 `anotherviewer-web`

| # | 文件 | 改动 | 状态 |
|---|---|---|---|
| S1 | `entity/HistoryInfoEntity.kt` | + `@Column(nullable=false) var page: Int = 0`（mode 列之后）。ddl-auto: update 自动补列，无需迁移脚本 | ✅ 已完成 |
| S2 | `dto/HistoryDto.kt` | `HistoryItem` + `page: Int = 0`（time 之前）；`AddHistoryRequest` + `page: Int = 0`（缺省 0 = 不改写已存进度） | ✅ 已完成 |
| S3 | `service/HistoryService.kt` | `addHistory` 增加 `page: Int? = null` 参数（null → 保持已存值；非 null → `coerceAtLeast(0)` 写入），旧签名委托新重载；`toItem()` 带出 `page` | ✅ 已完成 |
| S4 | `dto/GalleryDto.kt` | `GalleryItemDto`（≈L15）+ `readProgress: Int? = null`；`GalleryDetailDto`（≈L68）+ `readProgress: Int? = null` | 待做 |
| S5 | `service/GalleryService.kt` | ① `addToHistory`（≈L503）加 `page: Int? = null`，语义同 S3；② 新增私有 `readProgressOf(gid) = historyRepository.findByGid(gid)?.page ?: 0`；③ `downloadDetailDto`（≈L397）与 `favoriteDetailDto`（≈L360）填 `readProgress = readProgressOf(gid)`；④ `enrichHistoryDetail`（≈L452）直接 `readProgress = history.page`（行已在手，勿重复查询）；⑤ 上游拉取路径（getGalleryDetail ≈L340）**不改**——该路径只在无历史行时走到，进度恒 0，DTO 缺省 null 即可；⑥ `getHistory`（≈L522）`paged.map { it.toDto().copy(readProgress = it.page) }`；⑦ `getLocalFavorites`（≈L532）批量查历史行填 readProgress（见 S7） | 待做 |
| S6 | `api/GalleryController.kt` | `addToHistory`（≈L109）透传 `body.page` | 待做 |
| S7 | `repository/HistoryInfoRepository.kt` | + `fun findByGidIn(gids: Collection<Long>): List<HistoryInfoEntity>`（S5⑦ 与 S9 批量用，避免 N+1） | 待做 |
| S8 | `dto/SyncDto.kt` | `SyncHistoryDto` + `page: Int = 0`（mode 之后） | 待做 |
| S9 | `service/SyncService.kt` | `toHistoryEntity`（≈L874）设 page；`applyHistoryFields`（≈L879）page 行 = `entity.page = maxOf(entity.page, dto.page)`（**唯一**的 max 点，D3；墓碑路径不经过此函数，无需处理）；`toSyncHistoryDto`（≈L1052）带出 page | 待做 |
| S10 | `dto/DownloadDto.kt` + `service/DownloadService.kt` | 下载列表 item DTO + `readProgress: Int? = null`；`listDownloads`（≈L112）对最终 `rows`（含 regexPage 路径）经 `findByGidIn` 批量填 readProgress（构造器注入 HistoryInfoRepository） | 待做 |

### 5.2 契约 `contracts/`

| # | 文件 | 改动 |
|---|---|---|
| C1 | `openapi.yaml` | `HistoryItem`（≈L3237）+ `page`（integer，optional）；`GalleryItemDto`（≈L2930）/`GalleryDetailDto`（≈L3030）+ `readProgress`（integer，nullable/optional）；`/api/v1/gallery/history/{gid}`（≈L621）请求体 + `page`（integer ≥0，optional，默认 0=不改写）。全部 additive |
| C2 | `sync-schemas.json` | `syncHistory`（≈L158）properties + `page`（integer，minimum 0，描述注明"0 起页索引；行胜合并时取 max 防旧端清零"）。**不得加入 required**（旧 App push 不带该字段） |

### 5.3 Web 前端 `web-frontend/`

| # | 文件 | 改动 |
|---|---|---|
| W1 | `types/index.ts` | `GalleryInfo` + `readProgress?: number`（GalleryDetail extends GalleryInfo 自动获得） |
| W2 | `api/history.ts` | `HistoryItem` + `page: number` |
| W3 | `api/gallery.ts` | `AddHistoryPayload`（≈L140）+ `page?: number`，注释更新 |
| W4 | `views/ReaderView.vue` | ① `load()` 成功路径（≈L560）：初始页 = 深链 `route.params.page` 优先，否则 `detail.readProgress ?? 0`，钳 `[0, pages-1]`；② unknownPageCounts 路径（≈L576）：取 readProgress 不做上界钳制（页数未知）；③ `pushHistory()`（≈L476）：payload + `page: currentPage.value`；④ 降级态（≈L587 / pushHistory 的 degraded 分支）：localStorage 读写 `readProgress:{gid}`（数字），恢复时同 ① 优先级，flush/leave 时写入；⑤ 防抖策略（10 页/30 秒 + pagehide flush）不变 |
| W5 | `components/gallery/GalleryCard.vue` | 偏好 `showReadProgress` 开启且 `gallery.readProgress > 0` 时显示角标：`{readProgress+1}/{pages}P`（pages>0），否则 `{readProgress+1}P`。样式对齐 Android `GalleryAdapterNew`（app/.../GalleryAdapterNew.java:241-245） |
| W6 | `views/HistoryView.vue` | `toGalleryInfo`（≈L231）+ `readProgress: item.page` |
| W7 | `views/DownloadView.vue` | DTO→GalleryInfo 映射透传 readProgress |

### 5.4 Android App `app/`

| # | 文件 | 改动 |
|---|---|---|
| A1 | `dao/HistoryInfo.java` | + `public int page;` + getter/setter；全参构造器（:38）**末尾**追加 `int page` 参数；`writeToParcel`（:248）+ `dest.writeInt(this.page)`，`HistoryInfo(Parcel)`（:254）+ `this.page = in.readInt()`。`HistoryInfo(GalleryInfo)` 拷贝构造不拷 page（新行 page=0，正确）。**全参构造器唯一调用方是 HistoryDao.readEntity:225**（已核验），一并更新 |
| A2 | `dao/HistoryDao.java` | `Properties.Page = Property(22, int.class, "page", false, "PAGE")`（:46 后追加，索引 22）；`createTable`（:59）末尾（FAVORITE_NAME 之后）追加 `"PAGE" INTEGER NOT NULL ,`；两处 `bindValues`（:93/:156）末尾 `stmt.bindLong(23, entity.getPage())`；`readEntity`（:224）构造调用追加 `cursor.getInt(offset + 22)`；`readEntity`（:253）+ `entity.setPage(cursor.getInt(offset + 22))`。**新列必须追加在最后**——既有 bind/read 全是位置索引，中途插入会错位 |
| A3 | `dao/DaoMaster.java` | `SCHEMA_VERSION` 8 → 9（:20） |
| A4 | `SiteDB.java` | `upgradeDB` 加 `case 8:` → `addColumn(db, "HISTORY", "PAGE", "INTEGER NOT NULL DEFAULT 0")`（容错 helper 已有，:110；紧随 case 7 风格，:193）；+ 新方法 `loadHistoryInfo(long gid)`（`sDaoSession.getHistoryDao().load(gid)`，目前无单行读接口，已核验）；+ 新方法 `updateHistoryPage(long gid, int page)`：行存在则 `page=..., time=System.currentTimeMillis(), dao.update()`，不存在则跳过（历史行由详情页访问创建，阅读时行必已存在）；`applySyncedHistory`（:974）字段复制清单 + `existing.page = incoming.page;` |
| A5 | `ui/GalleryActivity.java` | ① 打开恢复（:416）：`mPage >= 0 ? mPage : Math.max(mGalleryProvider.getStartPage(), SiteDB.loadHistoryInfo(mGalleryInfo.gid) != null ? ...page : 0)`（同步读，与 GalleryDetailScene 直接调 SiteDB 的既有风格一致）；② `onUpdateCurrentIndex`（:863）：`putStartPage(index)` 后追加异步 `SiteDB.updateHistoryPage(mGalleryInfo.gid, index)`（投递 IoThreadPoolExecutor，参照 SpiderQueen.putStartPage 的 AsyncTask 模式）。此为**单一咽喉点**，覆盖全部 Provider（下载/在线/Archive/WebUI 代理） |
| A6 | `webui/WebUiSyncModels.java` | `SyncHistory`（:67）+ `public int page;`（fastjson 按字段名映射，未知字段静默忽略——双向兼容已论证） |
| A7 | `webui/WebUiSyncEngine.java` | `fillHistory`（≈:870-890）push + `hist.page = hi.page;`；`applyHistory`（≈:1098）pull + `info.page = hist.page;`（applySyncedHistory 的"新者胜整行替换"语义不变：本地新 → 下轮重推；服务端 max → 不回退，收敛性成立） |

## 6. 兼容性矩阵（四个方向全部安全，可灰度滚动）

| 组合 | 机制 |
|---|---|
| 旧 App → 新服务器 push（无 page） | Kotlin 默认 `page=0` → `maxOf(existing, 0)` = 存量不丢（D5） |
| 新 App → 旧服务器 push（带 page） | Spring Boot 默认忽略未知 JSON 属性 |
| 旧 App ← 新服务器 pull（带 page） | fastjson 忽略未知字段 |
| 旧 Web ← 新服务器（带 readProgress） | TS 运行时忽略多余字段 |
| App DB 升级 | greendao v9 `addColumn` 容错（已有 v6→7、v7→8 先例）；`.spm` 不动，本地进度无迁移需求 |
| 服务器 DB | ddl-auto: update 自动补列（mode 列同款先例，S1 注释已写明） |

## 7. 边界场景决策记录

| 场景 | 行为 |
|---|---|
| 浏览器有意跳回第 0 页重读 | REST 直写 0 → 各浏览器恢复 0 ✓ |
| App 有意重读 | 本设备 `.spm` last-write 归 0，本设备从 0 读；跨设备保留最远进度（max 的已知取舍，与 Android 双源 max 一致） |
| 旧 App push 不带 page | max(存量, 0)，进度不丢 |
| 两设备同时读同一本、高页数方 push 落败 | 该次进度暂不落服务器，下个会话自愈（D3 接受） |
| 浏览器降级态（无 token） | localStorage 兜底，不碰服务器 |
| 历史行删除/清空（墓碑/clearHistory） | 进度随行消失，重读从 0 |
| App 端翻页刷 time 的副作用 | 阅读中的画廊停留在历史列表顶部（"最近在看"语义，符合直觉）；sync ledger 以 time 判变 ⇒ 阅读中行必重推（正是所需） |

## 8. 测试计划

**服务端**（扩既有 `HistoryServiceTest` / `HistoryModePassthroughTest` / `SyncServiceTest` / `SyncStrategyMatrixTest`）：
- add-history 带/不带 page 的 upsert（不带不清零、带 0 可重读）；
- mergeHistory：incoming page 更低且行胜 → page 取 max；行负 → 整行不动；
- detail 四路构建器 readProgress（download/favorite 查行、history 行内值、上游路径 null）；
- 列表（history/favorites/downloads）readProgress 填充。

**前端**（vitest，扩 `ReaderView.spec.ts` / `HistoryView.spec.ts`，卡片按既有 spec 目录）：
- 初始页恢复（readProgress 有/无、深链优先、未知页数不钳上界）；
- 防抖回写 payload 含 page；降级态 localStorage 读写；
- GalleryCard 角标随 showReadProgress 开关与 readProgress 值显隐。

**App**（JVM，扩 `WebUiSyncClientMatrixTest` / `WebUiSyncStrategyTest`，用 `InMemorySyncServer`）：
- page push/pull 往返；applySyncedHistory 携带 page；
- HistoryDao 读写含 page 列（新装/升级两路）。

**契约**：若有 openapi/sync-schemas 一致性测试，随 C1/C2 更新。

## 9. 实施顺序与验收

顺序：S4-S10（服务端）→ C1-C2（契约）→ W1-W7（前端）→ A1-A7（App，greendao 最谨慎，单独验证 `importDB` 与升级路径）→ 全量测试 → FAQ/CONTEXT 术语补"阅读进度"。

验收标准：
1. 浏览器读到 N 页关掉，重开同一画廊从 N 继续纯本地导入（降级态）画廊同样成立；
2. App 读到 N 页，等一个同步周期，浏览器打开同画廊从 N 继续；反向同样成立；
3. 旧格式 push（模拟不带 page）不清掉已存进度；
4. 设置页"显示阅读进度"开启后，历史/收藏/下载卡片显示 `N/MP` 角标；
5. 三端既有测试全绿，新增测试覆盖上述路径。

## 10. 实现者注意事项（核验中发现的坑）

1. **greendao 位置索引**：HistoryDao 的 bindValues/readEntity 全部按列位置偏移，`PAGE` 必须追加为第 22 列（最后），否则全表错位。
2. **全参构造器调用方唯一**（HistoryDao.readEntity:225，已核验无其他调用），加参后只需同步这一处。
3. `applyHistoryFields` 是行胜路径的**唯一**字段复制点（D3：max 只加在这里；墓碑/行负路径都不要碰 page）。
4. `GalleryService.addToHistory` 与 `HistoryService.addHistory` 是两套平行实现（历史原因），本次**不合**，仅同步加 page 语义。
5. REST `AddHistoryRequest.page` 缺省 0 语义 = "不改写"；显式传 0 = 重读。测试必须覆盖这两个 0 的区别。
6. `sync-schemas.json` 的 `page` **不加 required**；注意 ≈L247 已有的 `"page"` 属于 syncBookmark，勿混淆。
7. 前端 `unknownPageCounts` 模式恢复进度时不要钳上界（totalPages=0 时 `Math.max(0, totalPages-1)` 会把进度压成 0）。
8. App 侧 DB 操作勿在 UI 线程做写（参照 SpiderQueen 的 IoThreadPoolExecutor 模式）；打开时的同步读与 GalleryDetailScene 既有风格一致，可接受。

---

# Part B：WebUI 性能修复（§11-14）

## 11. 背景与审查结论（2026-09-02）

用户主诉：点击各模块有较长时间等待。部署硬件 1265LV3（4C8T）**不是瓶颈**——根因全部在请求路径上的"同步上游 EH 往返 + 零缓存 + 每请求 fork curl 进程"：

- **F1 详情页串行上游拉取（最严重）**：`GalleryService.enrichHistoryDetail`（anotherviewer-web/.../service/GalleryService.kt ≈L452）EH 可达时每次点击同步执行 `SiteEngine.getGalleryDetail`（EH 详情页全量 + 评论解析），**无缓存**，读超时 30s。`GalleryLookupService.detailCache`（10 分钟 Caffeine，GalleryLookupService.kt ≈L47）现成未用。
- **F2 toplist/搜索零缓存**：`topListFeed`（≈L186）、`searchGallery` 每次上游拉取；空关键词本地历史快路径不受影响。
- **F3 缩略图首开风暴**：`/api/v1/image/proxy`（ImageProxyController.kt ≈L60）cache miss 同步 curl 上游且**无 in-flight 合并**——对比页图路径有 `pageFetchers` 去重 + 每画廊信号量（≈L71）；首页 25 卡首开 = 25 并发 curl 进程 + 75 临时文件。缓存命中后（max-age=86400）即快，症状为"第一次慢"。
- **F4 超时过长**：`CurlSiteExecutor` 每请求 `waitFor(60s)` + OkHttp 30s；熔断只覆盖"完全不可达"，EH **可达但慢**时非关键路径挂满超时。
- **排除项（确认无问题，勿动）**：收藏/历史全表内存过滤（个人库规模毫秒级）；`DownloadDirIndex`（启动构建，请求路径零扫描）；前端缩略图已有 `loading="lazy"`；Tomcat/Hikari 默认配置。

底层放大器：所有站点流量经 `CurlSiteExecutor` 走系统 curl（绕 Cloudflare JA3，架构上必要）——每请求 3 个临时文件 + fork/exec + 读写 + 清理，单次 10-50ms，被 F1-F3 的频次放大。

## 12. 性能改动清单（P1-P4，均在 anotherviewer-web）

| # | 文件 | 改动 | 状态 |
|---|---|---|---|
| P1 | `service/GalleryLookupService.kt` + `service/GalleryService.kt` | GalleryLookupService 增加公开方法 `getDetailCached(gid: Long, token: String?): GalleryDetail?`：查 `detailCache` → miss 时 `SiteEngine.getGalleryDetail` 并 put（10min TTL 沿用）；`enrichHistoryDetail` 的上游补强改走该方法。本地 DTO 先行的结构不变，评论随 detail 带回 | 待做 |
| P2 | `service/GalleryService.kt` | ① `topListFeed` 结果 Caffeine 缓存（expireAfterWrite **5min**，key 固定）；② `searchGallery` 站点结果缓存（expireAfterWrite **2min**，key=构建后的 URL）。两者：**缓存查询放在 `availability.isBlocked()` 之前**（DOWN 期间命中缓存照常返回陈旧内容，改善断网体验）；只缓存 `success=true` 且非空的结果；`ehBlockedListResponse` 语义不变 | 待做 |
| P3 | `api/ImageProxyController.kt` | `/proxy` 增加与页图同款的 in-flight 合并：`ConcurrentHashMap<String, CompletableFuture<ResponseEntity<*>>>` 按 url 去重共享一次上游 fetch；全局 `Semaphore(6)` 限并发 curl（25 卡首开 → 6 并发批次）；失败路径 completeExceptionally + 移除 map 条目（参照页图 `pageFetchers` 实现模式） | 待做 |
| P4 | `service/CurlSiteExecutor.kt` + 调用方 | 每请求超时覆盖：executor 读 request tag（OkHttp `Request.tag()`，不进线路无需剥离）取 per-request max-time（钳 1..60s），无 tag 默认 60s（阅读器页图不动）；缩略图 fetch（ImageProxyController）打 **10s** 标。P1/P2 的 SiteEngine 调用若 core 内部构建 Request 打标不可达，则该项只落 curl 侧机制 + 缩略图应用，enrich/toplist 靠缓存降频即可（不强求） | 待做 |

## 13. 性能测试计划

- `GalleryLookupService`：缓存命中只打一次上游（fake fetcher 计数）。
- `GalleryService`：toplist/search 二次调用不触网；`availability.isBlocked()` 时缓存命中仍返回陈旧数据；空结果不缓存。
- `ImageProxyController`：同 URL 并发请求只触发一次上游（参照页图去重既有测试模式）；超 6 并发被信号量串行化。
- `CurlSiteExecutor`：带 tag 请求按 tag 超时；无 tag 默认 60s。

## 14. 性能验收标准

1. 二次点击同一画廊详情 < 200ms（缓存命中，零上游请求）。
2. toplist 二次进入 < 200ms。
3. 首开 25 卡：并发 curl ≤ 6，同 URL 无重复上游请求。
4. 模拟上游延迟 > 10s 时，缩略图（及可达时的 enrich）10s 快速失败，不再挂 30-60s。
5. 服务端既有 + 新增测试全绿。
