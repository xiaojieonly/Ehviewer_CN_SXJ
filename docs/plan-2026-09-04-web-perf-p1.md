# 方案：WebUI 性能 P1 修复（目录索引 / feed 缓存 / 详情缓存 / 探针超时）

日期：2026-09-04
来源：对 192.168.6.141:8081 运行实例的性能评估（实测数据见文末附录）。EH 不可达已定性为代理节点失效（环境问题），**不在本方案范围内**。
基线：2026-09-03 三端测试全绿（服务端 894 / 前端 1015 / App 292）；工作树有未提交改动，本方案改动与其无文件冲突。

## 改动总览（4 项，按文件域拆 3 个并行实现单元）

| # | 任务 | 文件域 | 目标 |
|---|---|---|---|
| A | DownloadDirIndex 消除每请求全量列目录 | `DownloadDirIndex.kt` + 测试 | 页图 pushed 路径 250ms → <20ms |
| B | feed 缓存 + 直接开详情走 detailCache | `GalleryService.kt` + 测试 | EH 恢复后首页/详情不再每次付上游 RTT |
| C | 探针超时 5s → 12s | `application.yml`、`EhAvailabilityService.kt` + 测试 | 上游慢而不死时减少误熔断 |

三个单元文件互不重叠，可并行。`ImageProxyController.kt` 不动（250ms 的根因全部在 DownloadDirIndex 内部）。

---

## 任务 A：DownloadDirIndex 每请求 3 次全量列目录

文件：`anotherviewer-web/src/main/java/com/hippo/anotherviewer/web/service/DownloadDirIndex.kt`

### 问题（实测）
downloads/ 根下有 **9309 个目录**。走 pushed-file 路径的页图请求（`ImageProxyController.findPushedPageFile` → `findPage` + `dirFor`）每请求做 3 次根目录 `listFiles()`：
- `findPage()` L154 → `ensureFresh()` L122（一次 `root.listFiles()` 指纹检查）
- `dirFor()` L212 → `ensureFresh()`（再一次）
- `lookup()` L195 与 `dirFor()` 内部的 `findDir()` L219（再各一次 `root.listFiles()`）

实测单张页图 ~250ms（缓存命中路径仅 7ms）；阅读器 srcset 1x/2x 会放大。代码注释"无变化零开销"不成立——指纹比较本身就要求全量列目录。

### 改法
1. **`DirEntry`（L48-56）增加 `val dir: File` 字段**：`scanDir()`（L166）构造时已有 `dir` 参数，直接带上。
2. **`lookup()`（L195）改为对 `entry.dir` 自验**：索引命中时用 `entry.dir.isDirectory && entry.dir.lastModified() == entry.dirMtime` 判定是否仍有效；有效直接返回 entry。仅当索引 miss 或自验失败时才走 `findDir()`（rename/删除自愈路径保留：`entry.dir` 不存在 → `findDir()` 重找 → `scanDir()` 重建）。
3. **`dirFor()`（L212）索引直读**：`ensureFresh()` 后 `index[gid]?.dir?.takeIf { it.isDirectory } ?: findDir(gid).takeIf { it.isDirectory }`。注意保留现语义：返回 null 表示目录不存在。
4. **`ensureFresh()`（L107-133）加节流 TTL**：新增 `@Volatile var lastCheckAtMs` 与可配置间隔 `anotherviewer.download.dir-index-refresh-ms`（构造器 `@Value("${anotherviewer.download.dir-index-refresh-ms:30000}")`，默认 30_000ms）。距上次检查超过间隔才做指纹 `listFiles()`；到点检查发现变化仍走原 synchronized 双检 + `refresh()`。**间隔 ≤0 表示每次都检（保持现行为，测试用）**。
5. 类头注释（L20-33 区间）同步更新：per-request 描述改为「索引命中零磁盘遍历；根指纹检查按 TTL 节流」。

### 决策记录（被否选项）
- ❌ Java WatchService/inotify 监听 9309 目录树：复杂度高，单用户场景过度设计。
- ❌ 去掉自动感知、只靠下载生命周期事件刷新：丢失 2026-08-31 上线的"复制缓存目录即用、无需重启"能力（commit fc0965b2）。
- ❌ 只做 TTL 不做 DirEntry.dir 直读：`findDir()` 每次 `listFiles()` 的 250ms 主体仍在，不解决问题。

### 兼容性
- `PageRef`、`refresh()`、`invalidate()`、`pageCount()` 对外签名不变。
- `ensureFresh` TTL 只影响"外部改动多快被感知"：从实时退化为 ≤30s（新目录复制进来后首次请求最坏多等一个 TTL；有 TTL 到点后的下一次请求兜底）。可接受，实现者注意在 KDoc 写明。
- 启动 `loadAll()`/`refresh()` 行为不变。

### 测试
- 既有 `DownloadDirIndexTest` 全部保持绿（构造时注入间隔 0 可保持旧语义的测试不变）。
- 新增：(1) 索引命中后 `dirFor`/`findPage` 返回与重构前一致；(2) 重命名 gid 目录后自愈（`entry.dir` 失效 → `findDir` 重找）；(3) TTL=0 时外部新建目录立即可见；TTL>0 时未到间隔不感知、到点后感知；(4) `invalidate` 后 `findPage` 重新扫描。

---

## 任务 B：GalleryService 两处缓存补齐

文件：`anotherviewer-web/src/main/java/com/hippo/anotherviewer/web/service/GalleryService.kt`

### B1 feed 缓存
`feedGallery()` L191-210（popular/subscription 分支，即 `MODE_WHATS_HOT`/`MODE_SUBSCRIPTION`）目前无任何缓存（toplist 有 5min、search 有 2min，唯独 feed 每次全额上游往返，实测热 1.4s / 冷 8.4s）。

改法（完全对齐 `searchCache` 既有模式，参照 L128-139/L156-173）：
1. 新增字段（放在 `searchCache` L62-63 旁）：
   ```kotlin
   private val feedCache: Cache<String, GalleryListResponse> = Caffeine.newBuilder()
       .expireAfterWrite(2, TimeUnit.MINUTES)
       .maximumSize(64)
       .build()
   ```
2. `feedGallery()` 内：**缓存查询放在 `availability.isBlocked()` 之前**（topListFeed L217 的 P2 先例——DOWN 期间命中缓存照常返回陈旧内容），key = `"$mode:$page:$pageSize"`；仅 `success=true && data.isNotEmpty()` 才 `put`；`isBlocked()` 的 `ehBlockedListResponse()` 兜底保留在 miss 路径。

已知取舍（记录，不另做设计）：缓存的是**富化后的** `GalleryListResponse`（含 favoriteName/readProgress），2 分钟内收藏/进度角标变化不 reflected——与 searchCache 的既有取舍完全一致，不引入新的不一致类别。

### B2 直接开详情走 detailCache
`getGalleryDetail()` L359-382 的第 3 分支（token 非空且站点可达 → 上游直取）目前直调 `SiteEngine.getGalleryDetail`，无缓存；这是"列表→点开详情"最常用路径，每次付上游 RTT 1.4-2.4s（冷 8.4s）。

改法：该分支改用 `galleryLookupService.getDetailCached(gid, token)`（L131-146，gid 键 detailCache 10min TTL，miss 拉上游并回填，异常吞掉返回 null——与 `enrichHistoryDetail` L525 既有用法一致）：

```kotlin
if (!token.isNullOrBlank() && !availability.isBlocked()) {
    val detail = galleryLookupService.getDetailCached(gid, token) ?: run {
        logger.warn("Gallery Site detail fetch failed for gid={}", gid)
        return null
    }
    addToHistory(gid, token, detail.title, 0)
    return detail.toDetailDto()
}
```

语义变化（可接受）：失败原因不再细分记日志（getDetailCached 吞异常）；历史补强与直接开详情共享同一缓存条目（收益：同 gid 两路径只打一次上游）。`addToHistory` 与 `toDetailDto()` 时序保持原样。

### 测试
- `GalleryServiceTest` 新增：(1) feed 两次调用只触发一次上游（fake/existing stub 手段与 searchCache 测试一致）；(2) DOWN 且缓存命中时返回陈旧数据、miss 时返回 EH_UNAVAILABLE envelope；(3) 失败/空结果不进缓存；(4) 直接开详情两次调用只打一次上游、且与 enrichHistoryDetail 共享缓存条目；(5) token 为空/历史行/下载行/收藏行四条短路路径行为不变。
- 既有测试全绿。

---

## 任务 C：可用性探针超时 5s → 12s

文件：`anotherviewer-web/src/main/resources/application.yml` L41、`anotherviewer-web/src/main/java/com/hippo/anotherviewer/web/service/EhAvailabilityService.kt` L45 附近 `@Value("\${anotherviewer.availability.probe-timeout-ms:5000}")`。

### 问题
实测上游经代理 TTFB 1.4~8.4s（暖 1.4s、冷 8.4s），探针 connect/read/write 各 5s——上游"慢而不死"时探针先超时，存在误熔断窗口（本次 EH 断线是真断，与此无关，但 07:01-07:03 的 UP↔DOWN 抖动说明临界状态真实存在）。

### 改法
- `application.yml`：`probe-timeout-ms: 5000` → `12000`。
- `EhAvailabilityService` 的 `@Value` 默认值 `:5000` → `:12000`（两处保持一致，yml 为主、注解默认兜底）。
- KDoc 里"5s"相关描述若有则同步。

### 决策记录
- ❌ 自动恢复/TTL 后台扫描：plan-2026-08-30-eh-circuit-breaker.md §3.1 的手动语义是既定设计，不动。
- ❌ 更长（30s+）：探针阻塞的是用户点击"重新连接"的请求线程，12s 已是可接受上限（三超时合围，最坏 ~36s 的场景不应出现——连接/读任一先超即返）。

### 测试
- 显式注入该属性的既有测试不受影响；若有断言默认值的测试同步改 12000。

---

## 不修但记录

- EH DOWN 期间前端每 ~70s 自动重探一次（journal 规律出现），与探针"纯手动语义"的代码注释表述不一致。流量可忽略（每次一个 5-12s HEAD），实际是不错的自动恢复感知 UX。本次不改；若将来要改，改的是注释/文档而非前端。

---

## 验收标准（主会话汇合后执行）

1. `./gradlew :anotherviewer-web:test` 全绿（新增测试 + 既有全部）。
2. `./build.sh` 构建成功。
3. 代码 review：任务 A 的 `lookup`/`dirFor` 在索引命中路径上零 `root.listFiles()`（grep 可验：`findDir` 只应出现在 miss/自愈分支）。
4. 部署后（另行指令）复测：已下载画廊页图 pushed 路径 <20ms；EH 恢复后首页二次加载 <200ms、详情二次打开 <100ms。

## 实现者注意事项

- 工作树有 40+ 未提交文件（2026-09-03 部署的代码），**只改本方案列出的文件**，不要顺手格式化/重构其他文件。
- Kotlin 服务在 `anotherviewer-web` 模块；跑单模块测试 `./gradlew :anotherviewer-web:test --tests "*DownloadDirIndex*"` 这类过滤即可。
- `DownloadDirs.parseGid`/`isOursDir`、`PageRef` 的语义见 `DownloadDirIndex.kt` 头注释与 `GalleryService` 对 `countPushedPages` 的用法，别改变其行为。
- GalleryService 测试里上游交互如何 fake：参照 `searchCache`/`topListFeed` 相关既有测试的 stub 手法（SiteEngine 为静态门面，项目已有 fake 模式）。
- `getDetailCached` 在 `GalleryLookupService` L131-146，已存在，勿重复实现。

## 附录：评估实测数据（2026-09-04，为什么修）

- 页图 pushed 路径 223-260ms/张 vs 缓存命中 7-11ms；downloads/ 9309 目录。
- feed popular：冷 8.4s、暖 1.43s（无缓存，暖=上游 RTT）；详情直开：冷 2.35s、暖 1.44s（无缓存）。
- 对照健康值：本地列表 API 15-90ms；12 路并发全 200（19-47ms）；热缩略图 5-6ms。
- 服务器：journal 24h 零 ERROR/WARN；JVM 峰值 797MB/15G；磁盘剩 1.4T。
