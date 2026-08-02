# 同步引擎全 7 实体 + 包名迁移 + 分发/备份 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** App↔WebUI 全 7 实体双向同步、applicationId 迁移 `com.pf.anotherviewer`、data-dir 统一、备份/还原/迁移、zip 分发预适配

**Architecture:** 扩展 app 端 WebUiSyncEngine（服务器 SyncService 已支持 7 实体）；applicationId 参数化（legacy 产物支持数据迁移）；`--data-dir` 权威派生固定目录结构；BackupService 固定结构打包 + 可插拔加密后端

**Tech Stack:** Java/Kotlin、GreenDAO、Spring Boot 3、SQLite、fastjson、OkHttp、Apache Commons Compress、Vue 3

---

## 执行模式

- 每任务一个 fresh subagent（implementer），完成后 spec-reviewer → code-quality-reviewer 两阶段审查，TodoWrite 跟踪，频繁 commit
- 波次并行（dispatching-parallel-agents）：同波次任务文件互不重叠，契约由本表锁定

```
Wave 1（5 并行）: T1 EhDB ∥ T2 包名 ∥ T3 SyncServiceTest ∥ T4 SyncControllerTest ∥ T5 mock 核对
Wave 2（3 并行）: T6 WebUiSyncEngine ∥ T7 Fragment UI ∥ T10 data-dir+持久化
Wave 3（2 并行）: T11 Backup 后端 ∥ T13 packaging 骨架
Wave 4（2 并行）: T12 AdminBackup.vue（依赖 T11 契约）∥ T14 版本号
Wave 5（串行）  : T15 deployment.md → T9 构建+平板两轮验证
```

**契约锁定**（跨任务接口，以本表为准）：
- T1 产出 EhDB 签名 → T6 消费
- T6 的 `Result` 新字段 → T7 消费
- T10 的 `DataDirResolver` → T11 消费
- T11 的 REST 契约 → T12 消费

---

## Grill 决策集成（2026-08-02，grill-with-docs 会话产出）

- **单用户模型**（ADR-0001）：同步/部署/备份只服务单一用户；多设备=同用户，LWW 覆盖即完全同步。Filter 无时间戳 push 恒 `now` **接受**（不用 SharedPreferences 缓存）；preferences always-overwrite 成立
- **备份分卷 = 独立 7z 分片**（ADR-0002）：分片可单独传输/校验/异地备份（NAS/U 盘）；浏览器下载 zip 包装；GPG/AES 经 BackupEncryptor 接口预留
- **restore 防护**：Bearer token + `.bak` 保留 + **前端二次确认（输入 RESTORE 确认词）**
- **纯手动同步**：不做启动自动 pull、不做定时任务、不做 WebSocket 推送
- **超时与故障判断**：`WebUiApiClient` readTimeout 30s→**120s**（首轮全量 pull 余量）；同步失败高水位不推进（已有）；错误 toast 区分"网络/超时"与"服务器拒绝"（T16）
- **文件随迁移走**：备份 includeDownloads 可选含下载内容；同设备包名迁移 = 同步元数据 + 新包名 app 重新授权 SAF 目录复用原文件（T9 验证覆盖）

## T16: WebUiApiClient 超时 + 错误信息（新增，小任务）

**Files:**
- Modify: `app/src/main/java/com/hippo/ehviewer/webui/WebUiApiClient.java:70`（readTimeout 30s→120s）
- Modify: `app/src/main/java/com/hippo/ehviewer/webui/WebUiSyncEngine.java`（push 失败错误信息区分类型）

- [ ] **Step 1**: readTimeout `30, TimeUnit.SECONDS` → `120, TimeUnit.SECONDS`
- [ ] **Step 2**: `WebUiSyncEngine` push/pull 的 IOException 文案区分：`SocketTimeoutException`→"同步超时"、HTTP 非 2xx→"服务器拒绝 (HTTP xxx)"（保持现有 throws IOException 结构，只优化 message）
- [ ] **Step 3**: 编译 + commit：`git commit -m "fix(app): sync timeout budget + distinct error messages"`

## T1: EhDB 辅助方法（bookmark 封装 + filter 复合 key 查删）

**Files:**
- Modify: `app/src/main/java/com/hippo/ehviewer/EhDB.java`（新增 ~60 行，位于 Filter 方法区 line 972-987 附近）
- Verify: `app/src/main/java/com/hippo/ehviewer/dao/BookmarkInfo.java`（确认 gid 为唯一键，`BookmarksBao` 用法参照现有 `DownloadDao` 调用）

- [ ] **Step 1: 新增 import**（`com.hippo.ehviewer.dao.BookmarkInfo`）
- [ ] **Step 2: 实现 5 个方法**（签名锁定，T6 依赖）

```java
public static synchronized List<BookmarkInfo> getAllBookmark() {
    return sDaoSession.getBookmarksBao().queryBuilder().list();
}
public static synchronized void putBookmark(BookmarkInfo bookmark) {
    sDaoSession.getBookmarksBao().insertOrReplace(bookmark);
}
public static synchronized void removeBookmarkByGid(long gid) {
    sDaoSession.getBookmarksBao().deleteByKey(gid);
}
public static synchronized Filter findFilterByKey(int mode, String text) {
    return sDaoSession.getFilterDao().queryBuilder()
            .where(FilterDao.Properties.Mode.eq(mode), FilterDao.Properties.Text.eq(text))
            .unique();
}
public static synchronized void deleteFilterByKey(int mode, String text) {
    Filter f = findFilterByKey(mode, text);
    if (f != null) sDaoSession.getFilterDao().delete(f);
}
```

- [ ] **Step 3: 编译验证**：`./gradlew :app:compileDebugJavaWithJavac` 期望 BUILD SUCCESSFUL
- [ ] **Step 4: Commit**：`git commit -m "feat(app): EhDB bookmark & filter key helpers for sync"`

> ⚠️ 若 `FilterDao.Properties` 字段名不同（Mode/Text），以 GreenDAO 生成类为准调整；`BookmarksBao` 若主键非 gid 需改用 `queryBuilder().where(...).unique()` + delete 模式。T6 使用的方法签名以上为契约。

## T2: applicationId 参数化 + 外部配置排查

**Files:**
- Modify: `app/build.gradle:29`（applicationId）
- Verify: `google-services.json`（不存在则跳过）、`fastlane/`、`.github/workflows/fastlane.yml`、`README.md`、`FAQ.md` 中 `com.xjs.ehviewer` 引用

- [ ] **Step 1: applicationId 参数化**

```groovy
defaultConfig {
    applicationId project.findProperty("applicationId") ?: "com.pf.anotherviewer"
    ...
}
```

- [ ] **Step 2: 全仓 grep** `com.xjs.ehviewer` / `com.pf.anotherviewer`，确认仅有 build.gradle 一处；外部配置（fastlane/CI 签名路径）如引用包名则同步更新或记录（不阻塞）
- [ ] **Step 3: 两种构建验证**
```bash
./gradlew :app:assembleDebug                       # 产物 applicationId = com.pf.anotherviewer.debug
./gradlew :app:assembleDebug -PapplicationId=com.xjs.ehviewer   # legacy 产物 = com.xjs.ehviewer.debug
```
用 `aapt dump badging` 验证两个 APK 的 package 名
- [ ] **Step 4: Commit**：`git commit -m "chore(app): parameterize applicationId for pf rebrand + legacy builds"`

> ⚠️ 不动 namespace `com.hippo.ehviewer`、不动 `FILE_PROVIDER_AUTHORITY` buildConfigField（manifest 已 `${applicationId}.fileprovider` 动态）。

## T3: SyncServiceTest.kt（7 实体 merge 单测）

**Files:**
- Create: `ehviewer-web/src/test/java/com/hippo/ehviewer/web/service/SyncServiceTest.kt`
- Reference: `PairingFlowTest.kt`、`TestMatchers.kt`（Mockito matcher 风格）、`contracts/sync-conflict-rules.md`

- [ ] **Step 1: 写失败测试**（Mockito mock 7 个 repository + preferenceService，经 `SyncService.push()` 测 merge）：
  - mergeDownload：新 gid 入库 / `deleted=true` 保留 tombstone（不删）/ tombstone 复活 / LWW（`lastModified` 超 ±5s skew）/ **union 语义：incoming tombstone 不删 existing alive**
  - mergeFilter：按 `(mode,text)` key 去重 / tombstone / additive bias（同时间戳 enabled=true 胜）/ LWW
  - mergeBookmark：hard-delete（incoming deleted → 删除行）/ LWW / skew 内取更大 page
  - mergeQuickSearch / mergeDownloadLabel：按 name/label key、tombstone、LWW
  - per-user 隔离：A 用户的 push 不覆盖 B 用户行（`ownedBy` 逻辑）
  - adoptNullOwnership：legacy null-username 行被首个 push 用户认领
- [ ] **Step 2: 运行确认失败**：`./gradlew :ehviewer-web:test --tests "com.hippo.ehviewer.web.service.SyncServiceTest"` 期望 FAIL（类不存在）
- [ ] **Step 3: 实现**：按 SyncService.kt 行为写断言（merge 逻辑已是服务器既有实现，测试锁定契约防回归）
- [ ] **Step 4: 运行确认通过**（全绿）
- [ ] **Step 5: Commit**：`git commit -m "test(web-be): SyncService merge tests for all 7 entities"`

## T4: SyncControllerTest.kt（push/pull 集成）

**Files:**
- Create: `ehviewer-web/src/test/java/com/hippo/ehviewer/web/api/SyncControllerTest.kt`
- Reference: `AuthControllerTest.kt`（MockMvc 基建）

- [ ] **Step 1: 写测试**：push 7 实体 → pull `since=0` 全量往返；`since` 增量过滤（推送后 `since=serverTimestamp` 第二次 pull 为空）；`deviceId` 更新 lastSeen；未认证 401（`SecurityConfig` 语义）
- [ ] **Step 2-4: 红→绿循环**（同上）
- [ ] **Step 5: Commit**：`git commit -m "test(web-be): SyncController push/pull integration"`

## T5: mock-server 一致性核对

**Files:**
- Verify: `mock-server/routes/sync.mjs`、`mock-server/fixtures/sync.mjs`

- [ ] **Step 1: 核对** downloads/filters merge 与 `SyncService.kt` 语义一致（union/tombstone/LWW/skew/adoptNullOwnership/per-user）
- [ ] **Step 2: 如不一致**，修正 sync.mjs 并补 fixture；运行 `node --check` + 冒烟请求
- [ ] **Step 3: Commit**：`git commit -m "fix(mock): align downloads/filters merge with backend"`（无差异则 skip）

## T6: WebUiSyncEngine 扩展（核心，全 7 实体）

**Files:**
- Modify: `app/src/main/java/com/hippo/ehviewer/webui/WebUiSyncEngine.java`
- 依赖: T1 的 EhDB 签名（契约见 T1）

- [ ] **Step 1: 新增常量与 Result 字段**

```java
private static final String SUFFIX_SNAPSHOT_DOWNLOADS = ".snapshot.downloads";
private static final String SUFFIX_PENDING_DOWNLOADS = ".pending.downloads";
private static final String SUFFIX_SNAPSHOT_BOOKMARKS = ".snapshot.bookmarks";
private static final String SUFFIX_PENDING_BOOKMARKS = ".pending.bookmarks";
private static final String SUFFIX_SNAPSHOT_FILTERS = ".snapshot.filters";
private static final String SUFFIX_PENDING_FILTERS = ".pending.filters";
private static final String SUFFIX_SNAPSHOT_QUICK_SEARCHES = ".snapshot.quickSearches";
private static final String SUFFIX_PENDING_QUICK_SEARCHES = ".pending.quickSearches";
private static final String SUFFIX_SNAPSHOT_DOWNLOAD_LABELS = ".snapshot.downloadLabels";
private static final String SUFFIX_PENDING_DOWNLOAD_LABELS = ".pending.downloadLabels";
private static final int PUSH_BATCH_SIZE = 500;

// Result 新增：
public int pushedDownloads, pulledDownloads, pushedBookmarks, pulledBookmarks;
public int pushedFilters, pulledFilters, pushedQuickSearches, pulledQuickSearches;
public int pushedDownloadLabels, pulledDownloadLabels;
```

- [ ] **Step 2: key 集合收集**：gid 型（downloads/bookmarks 用 `Set<Long>`）；复合型 `Set<String>`：filters=`mode + "|" + text`、quickSearches=name、downloadLabels=label。复用现有 `loadKeySet/saveKeySet`（String 版）并新增 `loadKeySet/saveKeySet(String)` 泛化
- [ ] **Step 3: 分批 push 重构**：`buildPush` → 返回 `List<PushRequest>`（每批 ≤500 downloads；其余实体并入首批；tombstone 单独成批亦可）
- [ ] **Step 4: 新增 build 填充**（映射契约）：
  - `SyncDownload`：`copyGalleryToDto` + `state=info.state, legacy, time=info.time, label=info.label, total, finished, downloaded`；`lastModified=info.time`
  - `SyncBookmark`：`page`（BookmarkInfo 的页码字段，以 dao 为准）、`time`
  - `SyncFilter`：`mode, text, enabled=enable, lastModified=now`（本地 Filter 无时间戳，恒 now——文档化：push 侧恒新）
  - `SyncQuickSearch`：`name, mode, category, keyword, advanceSearch, minRating, pageFrom, pageTo, time, lastModified=time`
  - `SyncDownloadLabel`：`label, time, lastModified=time`
  - tombstone：filters/quickSearches/downloadLabels 软删（deleted=true），bookmarks 硬删语义（deleted=true 服务器删行）
- [ ] **Step 5: 新增 apply 方法**（完全服从服务器，无条件落库）：
  - `applyDownloads`：tombstone→`removeDownloadInfo(gid)`；无→`putDownloadInfo(DownloadInfo from dto)`；有→更新 state/进度字段（`putDownloadInfo` 覆盖）
  - `applyBookmarks`：tombstone→`removeBookmarkByGid`；无→`putBookmark`；有→LWW 用 dto.lastModified 对比本地 time
  - `applyFilters`：tombstone→`deleteFilterByKey(mode,text)`；无→`addFilter(新 Filter)`；有→按 lastModified 更新 enable（`triggerFilter`）
  - `applyQuickSearches`：tombstone→按 name 删；无→`insertQuickSearch`；有→`updateQuickSearch`
  - `applyDownloadLabels`：tombstone→按 label 删；无→`addDownloadLabel`；有→`updateDownloadLabel`
  - dto→实体需要新增 `copyDtoToDownload`（含 state 等字段）
- [ ] **Step 6: sync() 主流程接线**：5 个实体各自 load snapshot/pending → detectDeletions（String key 版重载）→ 并入 push 列表 → apply 后保存 snapshot、清空 pending
- [ ] **Step 7: 编译**：`./gradlew :app:compileDebugJavaWithJavac`
- [ ] **Step 8: Commit**：`git commit -m "feat(app): sync all 7 entities in WebUiSyncEngine"`

## T7: Fragment toast + strings.xml 更新

**Files:**
- Modify: `app/src/main/java/com/hippo/ehviewer/ui/fragment/WebUiSyncFragment.java:590-597`
- Modify: `app/src/main/res/values/strings.xml`（`settings_webui_sync_done`）

- [ ] **Step 1: 更新 toast 文案**（新增下载/过滤计数，保持可读）：
```java
fragment.getString(R.string.settings_webui_sync_done,
    result.pushedFavorites, result.pulledFavorites,
    result.pushedDownloads, result.pulledDownloads,
    result.pushedFilters, result.pulledFilters)
```
- [ ] **Step 2: 更新 strings.xml** 对应 format（`%1$d/%2$d 收藏、%3$d/%4$d 下载、%5$d/%6$d 过滤`，zh 为主；其余语言 fallback）
- [ ] **Step 3: 编译 + Commit**：`git commit -m "feat(app): sync summary toast includes downloads & filters"`

## T10: data-dir 统一 + 下载路径持久化

**Files:**
- Modify: `ehviewer-web/src/main/resources/application.yml`
- Modify: `ehviewer-web/src/main/java/com/hippo/ehviewer/web/config/EhCoreConfigProperties.kt`
- Modify: `ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/SettingsService.kt`
- Modify: `ehviewer-web/src/main/java/com/hippo/ehviewer/web/processing/ImageProcessingService.kt:33`
- Reference: `ServerConfigService`（KV 持久化 API）

- [ ] **Step 1: application.yml 统一占位符**：`${EHVIEWER_DATA_DIR:./data}` 派生 db URL / `ehviewer.download.path` / `cache-path` / `encryption-key-path`
- [ ] **Step 2: EhCoreConfigProperties**：新增 `var dataDir: String = "./data"`；download.path/cachePath/encryptionKeyPath 默认改为 `$dataDir/...` 派生（`@PostConstruct` 或初始化块：未被显式覆盖时）
- [ ] **Step 3: 持久化**：`ServerConfigService` 新增 KEY `download.path`/`cache.path`；`SettingsService.updateSettings` 写入；启动合并（`EhCoreConfigProperties` 初始化时 ServerConfig 值优先于默认派生值）
- [ ] **Step 4: ImageProcessingService** 改用 `EhCoreConfigProperties.cachePath`（注入 bean 替代 `@Value`）
- [ ] **Step 5: 测试**：现有 SettingsController 相关测试全绿；`./gradlew :ehviewer-web:test`
- [ ] **Step 6: Commit**：`git commit -m "feat(web-be): unify data-dir resolution + persist download paths"`

## T11: BackupService + BackupController

**Files:**
- Create: `ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/BackupService.kt`
- Create: `ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/BackupEncryptor.kt`（可插拔接口）
- Create: `ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/NoopBackupEncryptor.kt`（默认实现）
- Create: `ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/BackupController.kt`
- Create: `ehviewer-web/src/test/java/com/hippo/ehviewer/web/service/BackupServiceTest.kt`
- Modify: `ehviewer-web/build.gradle`（+`org.apache.commons:commons-compress` 7z）
- 依赖: T10（`EhCoreConfigProperties.dataDir` 派生 `backups/` 落点）

- [ ] **Step 1: 接口设计**
```kotlin
interface BackupEncryptor {
    fun name(): String              // "none" | "aes" | "gpg"
    fun encrypt(in: InputStream, out: OutputStream)  // 预留 GPG/PGP（BouncyCastle）
    fun decrypt(in: InputStream, out: OutputStream)
}
```
> GPG 仅接口 + 文档（contracts/backup-format.md），YAGNI 不引 BouncyCastle 依赖，Noop 为默认。
- [ ] **Step 2: BackupService.export**：固定结构打包 → 数据分片（db/config/可选 downloads+cache，线程池并行压缩）：db 用 SQLite `VACUUM INTO` 一致性快照到临时文件；每片 `SevenZOutputFile` 固实压缩（分卷命名 `.7z.001` 顺序）；`manifest.json`（版本、时间、分片清单、SHA-256、是否含下载内容）；输出到 `backups/` 或直接响应流
- [ ] **Step 3: BackupService.restore**：校验 manifest + 哈希 → 解压到临时目录 → 停写（`@Transactional` + 服务内简单锁/提示）→ 旧文件改名保留（`.bak`）→ 替换 → 返回需重启提示
- [ ] **Step 4: BackupController**：`GET /api/v1/backup/export?includeDownloads=false`（StreamingResponseBody）、`POST /api/v1/backup/restore`（MultipartFile）；鉴权与现有 admin 一致
- [ ] **Step 5: BackupServiceTest**：导出→还原→db 计数一致；manifest 校验；缺片/哈希不符拒绝
- [ ] **Step 6: 全量测试 + Commit**：`git commit -m "feat(web-be): backup/restore with parallel 7z slices + encryptor SPI"`

## T13: packaging 骨架（zip 发布 + 包管理器预适配）

**Files:**
- Create: `scripts/package.sh`（参数化：`-v <version>`、`-o <outdir>`、`--no-data`）
- Create: `packaging/systemd/anotherviewer.service.tpl`
- Create: `packaging/ospackage.gradle`（nebula.ospackage 骨架，默认不激活，注释说明）
- Create: `packaging/README.md`（包管理器预适配说明：安装路径/数据路径抽象）
- Reference: `start.sh`/`stop.sh`

- [ ] **Step 1: package.sh**：读取 `gradle.properties` 版本 → 组装 zip（`lib/app.jar`、`bin/start.sh`、`bin/stop.sh`、`data/` 模板含 README 目录说明、`README.txt` 安装说明）；验证 jar 存在
- [ ] **Step 2: systemd 模板**：`ExecStart=/usr/bin/java -jar /opt/anotherviewer/lib/app.jar --data-dir=/var/lib/anotherviewer`（体现 `--data-dir` 语义 = zip 内 `data/` 的同一抽象）
- [ ] **Step 3: ospackage.gradle 骨架**：配置结构就位（osPackage { ... }），顶部注释"备用：激活后 `./gradlew :ehviewer-web:buildDeb buildRpm`"，依赖 systemd 模板
- [ ] **Step 4: 冒烟**：`bash scripts/package.sh -v 1.1.0 -o /tmp/release` 产出 zip；`unzip -l` 校验结构
- [ ] **Step 5: Commit**：`git commit -m "feat(scripts): zip release packaging + distro-package preadaptation"`

## T14: 版本号单一来源

**Files:**
- Modify: `gradle.properties`（+`webVersion`）
- Modify: `ehviewer-web/build.gradle`（jar version ← `webVersion`）
- Modify: `web-frontend/package.json`、`AdminAbout.vue:62`（构建注入或与发布脚本同步说明）
- Verify: `docs/deployment.md`、`FAQ.md`

- [ ] **Step 1**: gradle.properties 加 `webVersion=1.1.0`；jar `version = webVersion`
- [ ] **Step 2**: 前端版本改为构建时从 `webVersion` 注入（package.json script 或 vite define；保持 AdminAbout 显示正确）
- [ ] **Step 3**: 构建验证 jar manifest 版本 + 前端显示
- [ ] **Step 4: Commit**：`git commit -m "chore: unify version source across backend/frontend"`

## T12: AdminBackup.vue

**Files:**
- Create: `web-frontend/src/views/admin/AdminBackup.vue`
- Modify: `web-frontend/src/router`（或 AdminLayout 菜单）、`web-frontend/src/api/`（+backup.ts）
- 依赖: T11 REST 契约

- [ ] **Step 1**: 导出卡片：`GET /api/v1/backup/export?includeDownloads=` 勾选（默认否）→ 触发浏览器下载 + 进度提示
- [ ] **Step 2**: 还原卡片：文件选择（manifest 校验提示）→ `POST /api/v1/backup/restore` → 结果提示（含"需重启生效"）
- [ ] **Step 3**: 风格对齐 AdminServer.vue（SectionHeader/PrefRow）；接入路由与菜单
- [ ] **Step 4**: `npm run build` 通过 + 现有 e2e 不回归
- [ ] **Step 5: Commit**：`git commit -m "feat(web-fe): AdminBackup export/restore view"`

## T15: 文档更新（对齐 T11 实际实现）

**Files:**
- Modify: `docs/deployment.md`（zip 安装、data-dir 语义、迁移=备份还原/拷目录、**大备份还原边界**）
- Create: `contracts/backup-format.md`（固定结构、manifest、分卷命名、AES/GPG 约定）

- [ ] **Step 1**: backup-format.md 按 T11 实际实现写：分片 `slice-NN.7z` + `manifest.json`（formatVersion/exportedAt/appVersion/slices[{name,sha256,sizeBytes}]/includesDownloads）；VACUUM INTO 一致性快照；**config 还原 = 回写 server_config 表（非文件）**；加密 SPI 约定（先压缩后加密）
- [ ] **Step 2**: deployment.md：zip 安装；`--data-dir` 语义；迁移 = 备份还原（WebUI，<50MB 元数据）或手动拷 data-dir（含下载内容的 GB 级备份）；同设备包名迁移 = 同步元数据 + SAF 重新授权
- [ ] **Step 3**: 记录 50MB 边界（restore multipart 限制，面向元数据备份）
- [ ] **Step 4: Commit**：`git commit -m "docs: zip deployment + backup format contracts"`

## T9: 构建 + 平板两轮验证（串行，最后执行）

**前置:** T2/T6/T7/T16 完成、后端 bootJar（T10/T11 不阻塞）

- [ ] **Step 1**: `./gradlew :app:assembleDebug -PapplicationId=com.xjs.ehviewer` → legacy APK；`adb install -r` 覆盖平板（保数据）
- [ ] **Step 2**: 后端 `java -jar ehviewer-web/build/libs/*.jar --server.port=8081` 启动；`adb reverse tcp:8081 tcp:8081`
- [ ] **Step 3**: 后端生成配对码（admin API）→ 平板 WebUiSyncFragment 配 `http://127.0.0.1:8081` + 码 → **手动点立即同步** → 服务器 SQLite 断言：downloads=8999 / filters=388 / history=100 / 其余 0
- [ ] **Step 4**: `./gradlew :app:assembleDebug`（新包名）→ `adb install`（并存）→ 配对 → since=0 拉全量 → 平板 `eh.db` run-as 断言计数一致
- [ ] **Step 5**: **新包名 app 重新授权 SAF 目录** `/storage/4A21-0000/Eh/`（image_path 设置）→ 下载记录与文件对上
- [ ] **Step 6**: 反向：前端/API 增删收藏+过滤+书签 → 平板 pull 落地验证；卸载旧包
- [ ] **Step 7**: 结果记录到 session 总结（不 commit 设备数据）

---

## 验证总览

| 关卡 | 命令 | 预期 |
|---|---|---|
| app 编译 | `./gradlew :app:compileDebugJavaWithJavac` | SUCCESSFUL |
| 后端测试 | `./gradlew :ehviewer-web:test` | 全绿（含新增 3 个测试类） |
| 前端 | `npm run build` + e2e | 通过 |
| 打包 | `bash scripts/package.sh -v 1.1.0` | zip 结构正确 |
| 平板 | 两轮验证 | 8999/388/100 双向往返一致 |

## 已知瑕疵（记录不修）
`SyncDownloadDto.label: String` vs `DownloadInfoEntity.label: Int` → 下载标签不上同步（统一契约另立任务）
