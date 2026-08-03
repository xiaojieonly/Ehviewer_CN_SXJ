# 代码库审计 TODO（供后续 session 执行）

**来源**: `docs/audit-2026-08-02.md`（6 并行 agent 全面审计）+ 人工复核
**复核日期**: 2026-08-02
**状态说明**: ✅ = 已对照代码验证属实 · ⚠️ = 事实属实但严重度/定性有保留 · 已修 = 已完成

---

## 已修复（无需再处理）

| ID | 项目 | 说明 |
|---|---|---|
| L-9 | `.dockerignore` 引用旧模块名 | 已修复（`anotherviewer-core/build`、`anotherviewer-web/build`） |
| H-3 部分 | 同步 pull `since=0` 边界（lastModified=0 记录漏拉） | 后端已修（含注释）；**全表扫描本体未修，见 H-3** |

---

## P0 — 安全/数据完整性（建议立即处理）

### C-1 CI 硬编码签名密码
- **位置**: `azure-pipelines.yml:29-31`（`jarsignerKeystorePassword: '123456'`）
- **问题**: 签名密钥密码明文提交仓库
- **方案**: 改 Azure DevOps secret variable / secure file 注入；密码轮换
- **验收**: `grep -rn "123456" azure-pipelines.yml` 为空；CI 用变量引用
- **复核**: ✅ 已验证属实

### H-3 同步 pull 全表扫描 ×7 实体
- **位置**: `anotherviewer-web/.../web/service/SyncService.kt` pull 方法
- **问题**: `findAll().filter{username== && lastModified>since}` × 7 实体；数据量增大后每次拉取全表读
- **方案**: 为 lastModified/username 建索引 + 派生查询（如 `findByUsernameAndLastModifiedGreaterThan`）；注意保留 `since==0` 特判（lastModified=0 合法记录）
- **验收**: pull 查询不再 `findAll()`；100 万行量级同步耗时 < 1s（可放宽）
- **复核**: ✅ 已验证属实；`since=0` 部分已修，勿回归

### M-2 登录无速率限制
- **位置**: `AuthController.kt`（login）
- **问题**: 无暴力破解防护
- **方案**: 内存计数器 + 指数退避（IP/用户名维度），阈值可配
- **验收**: 连续 5 次失败后锁定 ≥ 60s；配置项可关闭
- **复核**: ✅ 属实（未逐行验证实现缺失，属常见默认）

### M-8 AES 密钥派生用截断/零填充代替 KDF
- **位置**: `anotherviewer-web/.../web/service/EncryptionService.kt` `deriveKey()`
- **问题**: `key.toByteArray()` 截断/补零当密钥，弱于 KDF
- **方案**: PBKDF2WithHmacSHA256（迭代 ≥ 100k）+ 随机 salt；**兼容迁移**：旧密文需能解密（保留旧派生路径或一键重加密）
- **验收**: 新密文含 salt/迭代参数；旧数据可迁移
- **复核**: ✅ 已验证属实（`deriveKey` 截断/填充）

### L-6 `isAllowedArchiveHost` 重言式
- **位置**: `ArchiveController.kt`（约 64-67 行）
- **问题**: `host == "gallery.test" || host == "gallery.test"` 同一判断两次
- **方案**: 修为一个判断（并审视白名单是否应为 allowlist 配置而非常量）
- **验收**: 代码无重复条件；编译通过
- **复核**: ✅ 已验证属实（真 bug）

### L-7 mock-server 缺 `since===0` 保护
- **位置**: `mock-server/routes/sync.mjs:155`
- **问题**: `lastModified > since` 无 `since==0` 特判，首次全量拉取会漏 lastModified=0 的记录，与后端行为不一致
- **方案**: 对齐后端 `include()` 逻辑
- **验收**: mock 与真实后端对 since=0 的返回一致（用 test-ehentai 同款断言补充 sync 用例）
- **复核**: ✅ 已验证属实

### L-5 版本号双处硬编码
- **位置**: `HealthController.kt:45`、`MetricsController.kt:27`（各写 `"1.0.0-SNAPSHOT"`）
- **问题**: 与 `gradle.properties` 的 `webVersion=1.1.0` 脱节
- **方案**: 统一从构建元数据注入（Manifest 属性或 buildConfigField 等价物）
- **验收**: 改 `webVersion` 后两个端点返回值同步变化
- **复核**: ✅ 已验证属实

---

## P1 — 近期待办（架构/可维护性/安全加固）

### C-2 引入数据库迁移工具（可选项，评估后定）
- **位置**: `application.yml:11`（`ddl-auto: update`）
- **问题**: schema 变更无版本控制/回滚
- **方案**: Flyway 或 Liquibase 生成 baseline（SQLite 支持需验证）；或**维持现状并文档化**"改 schema 必须人工验证"
- **验收（若做）**: 新列变更走迁移文件而非 ddl-auto
- **复核**: ✅ 属实；对单用户 SQLite 自托管收益有限，**建议评估后降级为文档约定**

### H-1 默认无认证
- **位置**: `ServerConfigService.kt:82`（`security.require_auth` 默认 false）
- **问题**: LAN 内所有 API（含备份导出/恢复）无认证开放
- **方案**: 默认改为 true（破坏性变更，需配套设置引导）；或文档明确"部署在可信网络"并加一次性引导提示
- **验收**: 默认配置下 API 返回 401；配对流程仍可用
- **复核**: ✅ 已验证属实

### M-1 开放注册
- **位置**: `SiteAuthService.kt` / `AuthController.kt`
- **问题**: auth 开启时任何人可注册账号
- **方案**: 环境变量控制是否开放注册（默认关闭/仅首设备）
- **验收**: 默认仅配对/预设凭据可登录
- **复核**: ⚠️ 依赖 H-1 语境，LAN 单用户模型下影响小，但顺手加固成本低

### M-3 CORS 默认 `*` + credentials
- **位置**: `WebConfig.kt`（默认 `ANOTHERVIEWER_CORS_ORIGINS=*`）
- **问题**: 宽松 CORS；注释说明 LAN 有意为之
- **方案**: 默认收紧为 `http://localhost:*` + 文档引导配置实际前端 origin；或保持现状并注明
- **验收**: 默认配置非 `*`；README 说明如何配置
- **复核**: ✅ 属实（有注释背书）；与 H-1 联动评估

### M-4 应用层无安全响应头
- **位置**: `SecurityConfig.kt`
- **问题**: 无 CSP/X-Frame-Options/HSTS（仅 Caddy 部署路径有）
- **方案**: Spring 过滤器统一加头（CSP 需适配 SPA + 内联样式场景）
- **验收**: 响应头含 CSP/X-Frame-Options；前端功能不受影响
- **复核**: ⚠️ 未逐项验证缺失，按报告采纳

### M-5 输入验证稀疏
- **位置**: 多个 Controller（仅 `AuthDto`、`HistoryDto` 有约束）
- **问题**: 多数 `@RequestBody` 无 `@Valid`；`GalleryController.search` 的 `pageSize` 无上限
- **方案**: 补 `@Valid` + DTO 约束；pageSize clamp（如 1..200）
- **验收**: 非法输入统一 400；超限 pageSize 被 clamp 而非异常
- **复核**: ⚠️ 按报告采纳（抽查过 Controller 层，模式属实）

### M-6 错误响应格式不统一
- **位置**: 全局（报告称至少 5 种 envelope）
- **问题**: Boolean / AuthResponse / Result / 裸 ResponseEntity 混用
- **方案**: 定义 `ApiError` envelope + 全局 `@RestControllerAdvice`
- **验收**: 所有错误路径同一 JSON 结构（含 traceId/errorCode）
- **复核**: ⚠️ 未逐项验证，按报告采纳

### M-7 DownloadService 并发写入无事务/乐观锁
- **位置**: `DownloadService.kt`
- **问题**: 多 worker 并发写行存在覆盖风险
- **方案**: `@Version` 乐观锁或事务化状态更新；或分析后确认单写者模型安全并文档化
- **验收**: 并发压测（3 worker × 100 任务）无状态丢失
- **复核**: ⚠️ 报告称"取消逻辑精良"（AtomicBoolean+CountDownLatch），并发写风险需实测确认

### M-13 History `mode` 字段跨设备同步丢失
- **位置**: `HistoryInfoEntity.kt`（服务端无 mode 列）vs `HistoryInfo.java:14`（Android 有）
- **问题**: 搜索模式上下文不同步
- **方案**: 服务端补列 + 同步 schema（contracts/sync-schemas.json 同步更新）
- **验收**: 两端字段对齐；schema 测试通过
- **复核**: ✅ 已验证属实

### M-14 Download `label` 类型不一致（String vs Int）
- **位置**: `DownloadInfoEntity.kt:41`（Int）vs wire/Android（String 标签名）
- **问题**: 服务端 Entity 存 ID、wire 传名字；映射依赖独立 downloadLabels 实体
- **方案**: 确认转换链路完整（toDto/merge 两端都做 label↔id 转换），补缺漏 + 契约文档注明该抽象
- **验收**: 下载标签跨设备同步功能测试通过（新建标签→同步→另一设备可见）
- **复核**: ⚠️ 属设计选择，重点是**验证转换链路无缺漏**而非强制对齐类型

### M-9 Android 全局明文流量 + 信任用户 CA
- **位置**: `network_security_config.xml`
- **问题**: 全局 cleartext + user CA；为 LAN/WebUI 场景放开
- **方案**: 域白名单化（仅局域网段/配置的 host 放行明文）；user CA 保留（WebUI 自签场景需要，文档注明）
- **验收**: 非白名单域名明文请求被拒；WebUI 功能不回归
- **复核**: ✅ 已验证属实（LAN 场景有注释背书，属权衡而非疏忽）

### M-10 exported Activity 无权限保护
- **位置**: `AndroidManifest.xml`
- **问题**: 多个 exported 组件（如 WiFi 服务、文件选择类）可被外部拉起
- **方案**: 无跨应用用途的组件 `exported=false`；确实需要的加权限校验/签名校验
- **验收**: 清单审计后无冗余 exported；外部拉起被拒
- **复核**: ⚠️ 按报告采纳，需逐个组件确认用途

### M-11 WebView 启用 JS 无域名白名单
- **位置**: `WebViewSignInScene.kt`、`MyTagsActivity.java`、`UConfigActivity.java`
- **问题**: WebView JS 无白名单
- **方案**: 加载前校验 URL host 属于 `gallery.test`/已知站域；或降级 JS 策略
- **验收**: 非白名单 URL 拒绝加载或提示
- **复核**: ⚠️ 按报告采纳（登录页场景 WebView 风险较低）

### M-12 Docker 容器以 root 运行
- **位置**: `Dockerfile`
- **问题**: 无 `USER` 指令
- **方案**: `USER 1000` + 卷权限调整（data 目录 chown）
- **验收**: `docker run` 后进程为 non-root；挂载卷可写
- **复核**: ✅ 属实（无 USER 指令）

---

## P2 — 技术债（择机处理）

| ID | 项目 | 位置 | 说明 |
|---|---|---|---|
| H-2 | OkHttp 3.14.x EOL | web `build.gradle.kts:18`（3.14.7）、app `build.gradle`（3.14.9） | 升级到 4.12+ 修复 CVE-2023-3635；⚠️ **CVE-2021-0341 归因错误**（Android 平台 CVE，与 OkHttp 无关）；App 端注释"旧版稳定好用"需评估破坏面 |
| H-4 | targetSdk 30 过期 | `app/build.gradle:32` | 非 Play Store 分发，影响有限；升级 34+ 需适配 scoped storage/前台服务 |
| M-1 | 开放注册 | `SiteAuthService.kt` | 见 P1（可提前） |
| L-1 | 配对码端点无速率限制 | `AuthController.kt` | 90bit 熵 + 10min TTL + 一次性，暴力可行概率极低；加固：失败计数 |
| L-2 | `WebUiConfig.validate()` 死代码 | `WebUiConfig.java:88` | 删除或接入配对对话框 |
| L-3 | greenDAO 迁移 raw ALTER TABLE | `SiteDB.java`（约 424 行） | 迁移流程脆弱；升级策略需评估（greenDAO 已停止维护） |
| L-4 | `BookmarksBao` 命名错误 | `dao/BookmarksBao.java` | 应改名 `BookmarksDao`（牵连引用较多） |
| L-8 | 分页基数不一致 | Gallery（0-based）/ Favorites（1-based） | 统一约定并文档化；或保留并注明 |
| L-10 | 构建脚本跳过测试（`-x test`） | `build.sh` | 生产构建跑测试或单独 CI 任务覆盖 |
| — | Android 同步引擎零测试 | `webui/WebUiSyncEngine.java` | 最关键的跨系统逻辑，补 push/pull/tombstone/复活单测 |
| — | 前端 reader 组件 6/8 无测试 + WebSocket composable 无测试 | `web-frontend/src/components/reader/`、`src/composables/` | 阅读器是核心功能 |
| — | app ↔ core client/ 包并行复制 | `app/` vs `anotherviewer-core/` | ~22 解析器 + ~25 数据模型重复；让 app 依赖 core（大工程，高风险，先做依赖可行性评估） |
| — | 无 i18n | `web-frontend/` | 中英文硬编码混杂 |
| — | 前端大文件拆分 | `SmbBackupView.vue`（1542 行）、`SearchView.vue`（1047 行） | 拆分组件 |
| — | 前端无数据缓存层 | `web-frontend/` | 画廊/下载/历史每次导航重新请求 |
| — | WebView/AVD 相关 CAMERA 权限理由可疑 | `AndroidManifest.xml` | 核实用途后移除或改注释 |

---

## 执行建议

1. **一次 session 范围**: P0 全部（8 项）+ P1 中 C-2/H-1 二选一
2. **P0 顺序**: C-1 → L-6 → L-7 → L-5 → M-8 → H-3 → M-2（先易后难，均有明确验收）
3. **验证手段**: 每个改动后跑对应模块测试：`node --test mock-server/test-gallery.mjs`、`./gradlew :anotherviewer-web:test :app:assembleDebug`
4. **契约变更（M-13/M-14）**: 改代码前先改 `contracts/sync-schemas.json` + `contracts/openapi.yaml`，再同步两端实现与 mock
5. **本文件与 `docs/audit-2026-08-02.md` 配套使用**；完成一项即勾掉一项并注明 commit

---

## 端到端运行时验证（2026-08-02，本 session 追加）

> 本节由一次「构建当前源码 → 启动真实后端 + 内嵌前端 → 浏览器(computer_use)走查 + curl 探针 + SQLite 直查」的端到端测试产出。**本 session 未改任何代码**；为测试「当前版本」仅做了构建（`./gradlew :anotherviewer-web:bootJar`，纯构建非改码），并以一次性数据目录 `/tmp/av-e2e/data` 在端口 `8090` 运行（**未触碰用户真实 `./data/ehviewer.db`**）。
>
> **关键前提（与人工对齐）**：当前代码已「neutralize」画廊域名为 `gallery.test`（`4edf742b`），该域 `NXDOMAIN` 不可达，故**画廊浏览/搜索/委托下载/阅读器取图的「失败」属预期**，本节目录时**不作为缺陷**，只校验「失败时系统行为是否正确/优雅」。下文仅把**与画廊可达性无关的逻辑/数据缺陷**与**优雅降级是否到位**列为问题。

### 测试基线说明（避免误判）
- 仓库自带的可执行 jar `ehviewer-web-1.1.0.jar`（12:04 构建）**早于** neutralize/重命名提交，是**陈旧产物**：它上报 `ehentaiApi`、用 `ehviewer.db`、且能连真实画廊。直接跑它会测到旧代码。**必须重建**才测到当前版本；重建后产物为 `anotherviewer-web-1.1.0.jar`（与 `start.sh` 的 `find ... -name "anotherviewer-web-*.jar"` 匹配，故 `start.sh` 对当前代码**正常**，先前「脚本找不到 jar」只是陈旧产物假象）。
- 主类/包名仍为 `com.hippo.ehviewer.web.EhWebApplicationKt`，与模块名 `anotherviewer-web` 不一致 → 重命名未收尾（见 E2E-2，清理项，非运行时缺陷）。
- `ANOTHERVIEWER_DATA_DIR` 在当前代码**正确生效**（DB→`/tmp/av-e2e/data/anotherviewer.db`，cache/downloads 同目录派生，admin「下载路径」亦显示该目录）。先前观察到的「data-dir 不生效」系陈旧 jar 所致，**非缺陷**。

### A. 运行时已验证「行为正确」的功能（前后端打通）
| 功能 | 证据 | 结论 |
|---|---|---|
| 健康/指标 | `GET /health`→`DEGRADED`（`galleryApi:DOWN` 因 dummy 不可达，required 组件 UP，整体降级**正确**）；`GET /metrics` 正常 | ✅ 降级语义正确 |
| 认证状态/配对 | `/auth/status`→`authRequired:false`；`POST /auth/pair`→6 位码 `C4AEFP`+过期时间 | ✅ |
| 收藏（前后端+同步合并） | sync push 的 1001/5555 与本地 `/favorite/add` 的 7777 合并入同表；UI「收藏」显示「3 galleries」+10 个收藏夹 tab，评分/分类正确 | ✅ |
| 历史列表 | `/history/list` 与首页 feed 正确展示同步历史 | ✅（`mode` 字段除外，见 B） |
| 下载管理 | `/download/list` + UI「下载」正确展示条目、进度、**Failed 状态带清晰错误串**「Download incomplete: 0 of 30 pages completed」（dummy 下取图失败的**正确处理**）；标签 tab 由 `downloadLabels` 生成 | ✅ 失败处理正确（标签关联除外，见 B） |
| 同步 push/pull/status/devices | 全量 push→pull 回环正确；**增量更新在 lastModified 差 > 5s skew 时正确生效**（`conflicts:1`）；`since=0` 边界正确返回 `lastModified=0` 记录（H-3 的 since=0 修复**已验证生效**）；**硬删实体（history/bookmark）push `deleted=true` 正确硬删行**（与 favorite 的 union 软删形成正确对照）；`/sync/devices` 列表正确 | ✅（skew/union 语义见 E2E-7） |
| 设置读写 | `GET/PUT /settings` 全量回环正确（`workerCount` 3→7 持久化）；admin「下载设置」展示并绑定 data-dir | ✅ |
| 偏好（干净数据时） | 重置 `user_preference` 行后 `GET /preferences` 返回 200 默认嵌套结构（general/reader/privacy） | ✅（被异构同步串污染时见 E2E-1） |
| 备份导出/还原 | `GET /backup/export` 产出合法 zip（`manifest.json`+`slice-01.7z`，manifest 含 `sha256`/`sizeBytes`/`appVersion:"1.1.0"`）；UI「备份」含导出开关+按钮、还原文件选择+`RESTORE` 确认词+未确认禁用还原按钮，**与契约一致** | ✅ |
| 代理测试 | `POST /proxy/test` 对 dummy 域返回 `{success:false, latencyMs, error:"gallery.test: nodename nor servname..."}`——**正确的失败上报**（非崩溃） | ✅ |
| UI 渲染/导航 | Safari 走查 首页/收藏/详情/下载/管理面板(下载设置+备份) 均正常渲染、暗色主题、图标正常、无崩溃 | ✅ |
| 详情优雅降级 | dummy 下详情页用**本地元数据**渲染（标题/页数/分类/READ·DOWNLOAD·FAVORITE·SHARE），标签/评论显示「No tags/No comments」，**不崩溃** | ✅ 降级正确 |

### B. 运行时确认的缺陷（与画廊可达性无关，真实 bug）

#### E2E-1【P0/数据完整性+健壮性，新发现】preferences 同步与读取 schema 不一致 → `/preferences` 直接 500
- **现象**：sync push 携带 `preferences.preferences = "{\"theme\":\"dark\"}"` 后，`user_preference` 表原样存入该字符串；随后 `GET /api/v1/preferences` 与 `PUT /api/v1/preferences` 均返回 **HTTP 500**。
- **根因**（日志坐实）：`UserPreferenceService.get`(`UserPreferenceService.kt:16`) 用严格 Jackson 把存储串反序列化为 `PreferenceResponse`，抛 `UnrecognizedPropertyException: Unrecognized field "theme" (... 3 known properties: "general","reader","privacy")`。**写入路径(`replace`)不校验 schema，读取路径不容忍未知字段**，二者不一致。
- **影响**：任意设备（如 App）推一次「非 web schema」的偏好串，即**永久打挂 web 端「设置」页**（加载偏好即 500），直到该行被修复；且 500 走 Spring 默认 envelope（无 message，仅日志栈）。
- **方案**：读取侧 `FAIL_ON_UNKNOWN_PROPERTIES=false` + 缺省填充；和/或 sync `replace` 写入前按 `PreferenceResponse` 校验/归一；全局 `@RestControllerAdvice` 兜底避免裸 500。
- **验收**：推送 `{theme:...}` 等异构串后，`GET/PUT /preferences` 仍 200 且 web 设置页可加载。

#### E2E-2【P2/一致性，新发现】ehviewer→anotherviewer 重命名未收尾 + 陈旧产物
- 主类/包仍 `com.hippo.ehviewer.web.*`；仓库残留陈旧 `ehviewer-web-*.jar`。当前代码构建产物已正确为 `anotherviewer-web-1.1.0.jar`，`start.sh` 对当前代码正常。
- **方案**：清理 `build/libs` 陈旧 jar；评估是否将 web 主类/包名一并改名（注意 `start.sh`/Docker/文档联动）。非运行时缺陷。

#### M-13【已运行时坐实，升级】history `mode` 同步丢失
- 证据：push `mode=9`/`mode=5` → pull 与 `/history/list` 均回 `mode=0`；`SyncService.applyHistoryFields` **无** `entity.mode=` 赋值，`HistoryInfoEntity` 无 `mode` 列（DTO 有该字段，纯丢失）。
- 影响：搜索模式上下文跨设备丢失。**确认数据丢失**。

#### M-14【已运行时坐实，升级】download↔label 关联同步丢失
- 证据：push download `label="MyLabel"`/`"FreshLabel"`，且 `downloadLabels` 表确有 `MyLabel=id1`/`FreshLabel=id2`，但 `download` 行 `label=0`（`applyDownloadFields` **未写任何 label 字段**，name→id 映射缺失）；pull 回 `label:null`。
- **用户可见后果**：UI「下载」的标签 tab（来自 `downloadLabels`）存在，但对应下载**不会归入其标签 tab**（仅出现在 All）。
- 附带：`downloaded`(已下载字节) 字段同样未持久化（push 10→0）；`finished` 正常。若 `downloaded` 本就不需同步请文档化。

#### L-5【已运行时坐实，升级】版本号三处不一致
- 证据：`/health` 的 `version="1.0.0-SNAPSHOT"`（`HealthController.VERSION` 硬编码）≠ 备份 manifest `appVersion="1.1.0"` ≠ jar `1.1.0`（`gradle webVersion`）。同一进程内版本自相矛盾。

#### M-6【已运行时坐实，升级】响应/错误 envelope 至少 7 种
- 观察到：`{success,message}`、`{success,data,total}`、`{favorites,totalPages,currentPage}`、`{history,total}`、`{downloads,labels}`、`{comments}`、`{success,latencyMs,error}`(proxy)、以及未捕获异常/校验失败时的 **Spring 默认** `{timestamp,status,error,path}`（`PUT /settings` 部分体→400、`/preferences`→500 均为此形，且无业务 message）。注意 `/image/proxy` dummy 失败返回**裸 404 无 body**。
- 影响：前端无法用统一逻辑处理错误；裸 500/404 无业务 message。建议统一 `ApiError` + `@RestControllerAdvice`。

#### H-1【已运行时坐实】默认无认证
- `authRequired:false`，全部 API（含 `/backup/export|restore`、`/sync/*`）匿名可访问；匿名主体名 `default`。LAN 单用户模型下属权衡，但备份/还原匿名可达风险高。

### C. 对既有审计条目的复核更正
- **M-1（开放注册）→ 现状已缓解**：`POST /auth/register` 默认返回 **403 "Registration is disabled on this server"**（`isRegistrationAllowed()` 默认关）。审计「任何人可注册」的定性**偏重**；建议把 M-1 降为「确认默认关闭 + 文档化开启条件」，而非待修缺陷。
- **L-6（`isAllowedArchiveHost` 重言式）→ neutralize 产物，非原始逻辑 bug**：`host=="gallery.test" || host=="gallery.test"` 系 find/replace 把两个真实画廊域都改成 `gallery.test` 所致；dummy 环境下**无可观测影响**（唯一域即 gallery.test）。**de-neutralize 后需复核**第二分支是否恢复为另一真实域；当前不必当产品缺陷修。
- **H-3**：`SyncService.pull` 仍 `findAll().filter{username&&include(lastModified)}` ×7（全表扫描，性能债保留 P1）；其 `since=0` 边界修复**已运行时验证生效**（`lastModified=0` 的 5555 被返回）。
- **L-7**（mock-server `since=0`）：mock 为开发桩，本 session 未复测，维持原条目。
- **M-2**（登录限速）：默认注册关闭+无用户，未直接触发；维持原条目。

### D. 新增低优先级/UX 观察（非阻塞）
- **E2E-3【UX/小】** 缺缩略图时 `<img>` 的 alt(=标题) 漏进灰色占位框，且**不一致**（部分卡片占位框内显示标题、部分空白）。建议占位组件统一不渲染 alt 文本。
- **E2E-4【契约/小】** `category` 类型不一致：`/favorite/list`、`/history/list` 返回**字符串** `"2"`，sync DTO 为 **Int**。
- **E2E-5【i18n/小】** Bean 校验消息为中文「不能为空」，其余 API 文案为英文；与「无 i18n」条目同源。
- **E2E-6【UX 提示，dummy 下非缺陷】** 画廊搜索失败返回 **HTTP 200 `{success:false}`**，UI 表现为「空结果」而非错误提示——dummy 下可接受，但真实环境**间歇性不可达会被静默吞掉**。建议失败与「真·空结果」区分（错误码或 toast）。详情页降级、下载 Failed 文案、proxy/test 错误串均已正确，见 A。
- **E2E-7【设计确认，请核对契约】** sync 合并：① `SKEW_TOLERANCE=5000ms`，lastModified 差 ≤5s 的更新被**静默丢弃**且 push 仍 `success:true conflicts:0`（一度误判为「只插不更」，实测差 >5s 即正确更新）；② union 语义下「单端删除」不会移除服务端仍存活的**软删**记录（favorite 的 tombstone 被忽略，符合 `contracts/sync-conflict-rules.md`）；而**硬删**实体（history/bookmark）`deleted=true` 会真删行（已验证）。**需确认 App 端 lastModified 用真实墙钟**，否则正常编辑可能落入 5s skew 而被丢。非 bug，属契约核对项。

### E. 本 session 环境收尾
- 测试后端运行于 `:8090`（一次性 `/tmp/av-e2e/data`，**含本 session 注入的假数据**：假收藏/历史/下载/标签；偏好行已重置为空）。若供 Android 客户端做「干净同步」测试，**应改用全新空数据目录重启**，否则 App 会拉入这些假数据。
- 端口 `:8081` 的 `ehviewer-web` 残留进程与 `mock-server`(`node server.mjs`) 为**先前已存在**，本 session 未触碰；其中 `:8081` 为陈旧代码，不应作为当前版本测试目标。
- 重申：本节仅为记录，**未修改任何源码/配置**；构建产物 `anotherviewer-web-1.1.0.jar` 为测试当前版本所必需的重建结果。

---

## 端到端运行时验证 · 第二轮（2026-08-02 深夜：杀旧→单入口重启 + WebUI `computer_use` 全走查 + Android 平板 ADB）

> 本节由「杀陈旧/旧实例 → 以隔离数据目录重启**单一**当前版本服务器 → Safari `computer_use` 逐屏走查 + curl 探针 + SQLite 直查 → 平板（先 WiFi ADB 后 USB）`adb` 驱动已装 app 走查 + 与服务器交叉核验」产出。**本 session 未改任何源码/配置**（未重建 jar/apk；平板 IME 临时切到 Gboard 后已还原；app 存储的服务器配置因 connect/pair 均未成功而**未被改动**，仍为 `127.0.0.1:8081`；用户真实 `./data/ehviewer.db` 与 app 本地书库均未触碰）。

### 测试基线 / harness
- **进程清理 + 单入口**：杀掉陈旧 `ehviewer-web-1.1.0.jar`(:8081, PID 6535, 旧代码) 与上轮测试 `anotherviewer-web`(:8090, PID 24826)；以 `anotherviewer-web-1.1.0.jar --server.port=8080` 启**单一**当前版本实例。`mock-server`(`node server.mjs`) 为既有开发桩，未触碰（非生产入口）。
- **数据隔离**：`ANOTHERVIEWER_DATA_DIR=/tmp/av-e2e2/data`（全新空目录）；DB→`/tmp/av-e2e2/data/anotherviewer.db`，downloads/cache 同目录派生（admin「下载路径」显示 `/tmp/av-e2e2/data/downloads` 佐证）。**未碰**用户真实 `./data/ehviewer.db`。
- **web jar 新鲜度**：`find anotherviewer-web/src -newer <jar>` 为空 → jar 即当前源码，未重建。
- **平板**：Lenovo TB322FC / Android 16。WiFi ADB `192.168.6.95:42451` 中途掉线（端口轮换/锁屏，`adb connect` Connection refused、mDNS 不再广播）→ 按用户指示**切 USB**（serial `HA24NCCE`）。Mac LAN IP `192.168.6.69`（en0，与平板同 /24）。**TCP 平板→Mac:8080 已验证可达**（`nc -w3` 退出码 0；Mac 应用防火墙 `disabled`）。
- **已装 app**：`com.pf.anotherviewer.debug` versionName `2.0.2.2`/versionCode 111（与 `app/build.gradle` 一致），今天 11:59 安装，**早于** neutralize 提交 `4edf742b`(16:26) → 已装 app 为 **pre-neutralize**（画廊域仍为真实域、偏好 wire 键仍为 EH 命名，见 D）。
- **驱动手段**：WebUI = Safari + `computer_use`（AX 索引点击 + 截图）；Android = `adb shell input tap/text/keyevent` + `uiautomator dump`（取像素 bounds，规避坐标猜测）+ `screencap` 拉 PNG 阅读。设备分辨率 1904×3040。
- **画廊不可达 caveat（同第一轮）**：neutralize 域 `gallery.test` NXDOMAIN；app 端画廊首页空态「什么都没有找到」与 web 首页画廊 feed 空态同属**预期**，不计缺陷，只校验失败时行为是否优雅。

### A. WebUI 走查「行为正确」（computer_use 截图坐实）
| 屏/功能 | 证据 | 结论 |
|---|---|---|
| 首页空态 | 熊猫图 + 「还没有画廊数据 去搜索/登录后开始浏览」 | ✅ 空态正确（画廊 feed 因 dummy 不可达） |
| 收藏 | 标题「Favorites **2 galleries**」+ 10 个分类 tab；默认 Favorites-0 空（播种 category=2/5） | ✅ 同步数据**对 UI 可见** |
| 管理面板·下载设置 | 完整渲染；下载路径=`/tmp/av-e2e2/data/downloads`；并发线程数步进器 1–10 | ✅ 走 `/settings`，不受 E2E-1 影响 |
| 管理面板·备份 | 导出（含下载内容 toggle + 导出按钮）+ 还原（选择文件 ≤50MB + 确认词 `RESTORE` 启用还原 + 还原默认禁用 + `.bak` 提示） | ✅ 与 `contracts/backup-format.md` 一致 |
| 导航/主题 | 侧栏 9 项 + 暗色主题 + 图标全正常，无崩溃 | ✅ |

### B. WebUI 新发现 / 既有条目升级
#### E2E-9【P1/UI，新发现】下载（疑含收藏）卡片网格「DOM 在、不绘制」
- **现象**：下载「All」tab 的 AX 树含**完整**卡片（`Dl Alpha`: 30/30 pages、100%、Fetching…、进度条、Downloading、Pause/Stop/Delete；`Dl Delta`: 0/10、0%、Idle、Start/Delete；外加 Start all/Pause all/New label，元素 [18]–[38]），但**两次** vision 截图内容区**全空白**；切到「MyLabel」tab 则正确显示「No downloads」空态（证明列表组件能渲染空态，却对「非空 All」渲染不出任何卡片）。
- **对照**：管理面板列表/表单正常绘制 → 缺陷**局限于画廊/下载卡片网格组件**。本 session 未见到任何**收藏卡片**被绘制（默认 Favorites-0 为空），故空缩略图下收藏卡片是否同样空白**待复核**。
- **疑似根因**：卡片可见性/入场动画绑定缩略图 `<img>` 的 load/error，`thumb:null` 时无 `<img>` → 卡在不可见；或该网格的层合成/裁剪问题。
- **方案**：卡片不依赖缩略图加载完成即可见（占位框兜底，与收藏占位逻辑统一）；补「无缩略图下载/收藏」的渲染单测/快照。
- **验收**：`thumb=null` 的下载/收藏在「All」可见标题+进度+按钮。

#### E2E-8【P1/同步正确性，新发现】push 时 `lastModified` 跨实体处理不一致
- **证据**（SQLite 直查）：实体行**保留**客户端 `lastModified`（history 3000/4000、download 5000/6000、download_label 7000/8000），**唯独** `user_preference` 被服务器**重打戳**为 serverTimestamp。
- **影响**：preferences 的高水位/增量语义与实体不一致；客户端对 preferences 的 high-water 记账可能与服务器重打戳值错位 → 潜在漏拉/循环。
- **方案**：统一策略（要么全保留客户端值、要么全由服务器权威打戳并文档化），并与 `contracts/sync-conflict-rules.md` 的 skew 规则对齐。
- **验收**：push 后各实体（含 preferences）的 `lastModified` 来源一致且符合契约。

#### E2E-1【升级：UI 表现 + 真实默认触发路径，见 D】
- UI 表现更正：右窗格有 `[31] "无法加载设置"`（截图低对比未显，AX 坐实）→ UI 对 500 做了**有限降级**，但**四个分区全不可用**、无重试/详情。
- 真实触发：**普通 app 配对+同步即触发**（不再是「对抗性输入」），根因见 D（neutralize 偏好键漂移 + 严格反序列化）。严重度维持 **P0**。

#### M-13 / M-14【升级：schema + UI 双层坐实】
- **M-13**：`history_info` 表**无 `mode` 列**（PRAGMA 坐实）→ push `mode=9/5` 无处落库；pull 与 `/history/list` 回 `mode=0`。schema 级缺口。
- **M-14**：`download_info.label` 为 INTEGER 存 `0`（name→id 从未映射）；`download_info` **无 `downloaded` 列**（字节数物理不可持久化）；`done`(完成页数) 正常。UI 层：命名标签 tab（MyLabel/FreshLabel）存在但**为空**（下载未归入其标签），「All」又因 E2E-9 不绘制 → 用户视角标签功能失效。

#### M-5【升级：服务端无 clamp】PUT `/settings` 扁平 `{"workerCount":99999}` 被**静默忽略**却返回 200 裸 `true`（真实字段在嵌套 `download` 下；UI 步进器客户端 clamp 1–10，**服务端 API 无 clamp/无未知键校验**）。读回顶层 keys=`[download,cache,smb,security,processing,proxy]` 无顶层 workerCount 佐证。
#### M-6【升级：≥10 种 envelope + 裸 404/500】观测到 push/pull/list(fav|hist|dl)/pair/register/proxy/preferences-OK/settings-PUT(裸 `true`)/revoke 各异，外加未捕获异常的 Spring 默认 `{timestamp,status,error,path}`（`/preferences`→500）、`/image/proxy` dummy 失败→**裸 404 无 body**。
#### E2E-4【坐实】`/favorite/list` 的 `category` 为**字符串** `"2"/"5"`，sync DTO 为 Int（mapper 层不一致）。
#### E2E-6【坐实】`GET /gallery/search` 失败回 **200** `{"success":false,"data":[],"total":0}`，与真空结果同形，无错误码/文案。
#### E2E-10【UX/小，待复核】下载页选中 MyLabel 后 teal 下划线仍停在「All」而内容已切到 MyLabel 空态 → 疑 tab 激活指示器未跟随（侧栏亦见「双高亮」样式怪癖，可能同源）。
#### 复核更正：M-1 默认已缓解（`POST /auth/register`→403「Registration is disabled」）；H-1 备份/还原匿名可达（admin 面板无认证）。

### C. Android 走查：发现
#### E2E-11【P2/隐私·一致性，新发现】启动即外联更新检查 + 版本源不一致
- **现象**：app 启动弹「新版发布 **2.0.2.3** 建军节快乐~」+ 更新日志；状态栏下载速率 746kB/s 佐证启动时**外联**拉取了更新元数据。
- **不一致**：已装/仓库 = `2.0.2.2`，更新源却报 `2.0.2.3`（其 changelog 含 setPreferencesFromResource / drawable NPE / 图像解码修复，即发布版领先于本仓库 checkout）。
- **问题**：对「LAN 自托管/可离线」定位的工具，启动即访问外部更新源属意外外联；离线部署下该检查会失败（需确认优雅降级）；更新源与仓库版本脱节。
- **方案**：fork/自托管构建应禁用或改指自有更新源；更新检查失败须静默降级；版本源与 `app/build.gradle` 对齐。
- **验收**：离线启动无外联/无报错弹窗；更新源版本==仓库版本或明确禁用。

#### E2E-13【P0/互操作·UX，新发现】默认(auth-off)配置下「配置服务器→连接并保存」是死路
- **根因**（`WebUiSyncFragment.java:399` `ConnectTask`）：无条件先 `login(username,password)`；auth 关闭且**无用户**时 `login("default","")`→服务器 400→抛错→「连接失败」**一闪 toast**、对话框不关、**配置不保存**。对话框还**预填 username=`default`**，把用户引向死路。
- **影响**：文档默认配置（`security.require_auth=false`，见 H-1）下，最直观的「填服务器→连接」按钮**永远失败**，且无任何引导改用配对；唯一可行路径是「配对服务器」(`PairTask`→`POST /auth/pair/complete`，permitAll)。
- **方案**：`/auth/status` 报 `authRequired=false` 时跳过 login、仅做可达性检查即保存；或重命名/引导；不预填假用户名。
- **验收**：auth-off 服务器下「连接并保存」可成功保存配置；失败时 toast 指明应改用配对。

#### E2E-12【P1/同步正确性，新发现·待复核】高水位/快照未绑定服务器身份
- **现象**：app 存配置 `127.0.0.1:8081` 且「上次同步 2026-08-02 12:51:45」（对已杀的陈旧服务器）。切服务器时 high-water/snapshot 似未随 baseUrl 重置/隔离。
- **风险**：切到新服务器后，新服务器 seeded 记录的小 `lastModified` < 陈旧 high-water → pull 漏拉；或 snapshot 已含全部 key → push no-op。
- **方案**：high-water+snapshot 按 `baseUrl` 分片，或 (re)pair 时重置；并文档化。
- **验收**：换服务器后首次同步能完整拉取新服务器数据。
- **注**：本 session 因 E2E-13 + 自动化输入阻塞（见 E）未能完整验证此项，列为待复核。

#### 服务器 POST→405 观察（归因不明 = 可观测性缺口，关联 M-6）
- 每次「连接并保存/配对」点击，服务器都记 `HttpRequestMethodNotSupportedException: POST not supported`（GET-only 路由）。
- 已排除：app webui 客户端（login=POST/login 存在、status=GET、pair/complete=POST 存在、push=POST 存在）与 web 前端 api 层（`auth.ts` 用 GET /auth/status，无任何 POST 到 GET-only 路由）**均不** POST 到 GET-only 端点 → 来源未定（疑 SockJS/STOMP 传输或无关客户端）。
- **可观测性缺口**：服务器 405 的 WARN 日志**不含请求 URI 与客户端 IP** → 无法归因。
- **方案**：开 Tomcat access log，或在 `MethodNotSupportedException` 处理处记录 URI+method+remoteAddr。

#### 自动化输入 caveat（**非产品缺陷**，仅记录）
- `adb shell input text` 在该平板 + Gboard 组合下，**大写字母/末位字符常被 IME 组合缓冲吞掉或自动纠错搞乱**（host 变 `192..1686.69`、码丢末位 `KAY86`、纯大写码只录首数字 `9`）→ 我的配对码无法经自动化正确录入。
- 配对码字段 inputType = `TEXT|CAP_CHARACTERS|NO_SUGGESTIONS`（`WebUiSyncFragment.java:278`，**接受字母**）→ **真人用软键盘可正常输入字母码**，故配对功能对用户**并非不可用**；此为自动化伪影，不计产品缺陷。

#### 画廊首页空态（预期 caveat，同基线）：app 首页「什么都没有找到」= 画廊不可达，非缺陷。

### D. 跨端静态坐实（无需实机同步即可断言 —— app 用相同端点 `POST /sync/push`、`GET /sync/pull`）
- **M-13/M-14 同样作用于 app**：服务器 `history_info` 无 `mode` 列、`download_info` 无 `downloaded` 列且 label 不映射 → app push 的 mode/downloaded/label **必丢**，pull 回 0/null。已用 curl+SQLite 坐实（端点与 app 完全相同）。
- **E2E-1 的真实默认触发**：`PreferenceSyncHelper` push 顶层 `{general,reader,privacy}`（与 web 同构，顶层不触发）；但**已装 pre-neutralize app** 的 `general` 携带 pre-neutralize 键 `showEhEvents/showEhLimits`，而 neutralize 后的 web `GeneralPreferences`（`PreferenceDto.kt`）期望 `showSiteEvents/showSiteLimits` → 严格 Jackson 对**嵌套**对象同样 `FAIL_ON_UNKNOWN_PROPERTIES` → 抛 `UnrecognizedPropertyException` → `/preferences` 500 → web「无法加载设置」。且 `PairTask` 配对成功后**自动** `pullPreferences()`、同步引擎 push 偏好 → **配对即自动打挂 web 设置**。（当前 app **源码**已把这两个串值 neutralize 为 showSite*，故重建 app 可避开**此特定**失配；但严格/无容忍设计使**任何**跨端键漂移都会 brick 对方，且已装 app 其它键漂移亦可能。）
- **结论**：E2E-1 不是边角 case，而是 neutralize(`4edf742b`) 在**偏好 wire 键**上跨端不一致 + 严格反序列化共同造成的**现役互操作中断**。

### E. 未能完成项与原因（诚实记录）
- **完整 app↔server 双向数据同步未在 session 内跑通**，原因有二：① 唯一可行的连接路径「配对」无法经自动化完成（IME 输入伪影，见 C caveat）；② E2E-13 使直观 connect 路径为死路。**这两者本身即本轮 Android 头条发现**，而非测试疏漏。
- 故 **app 侧数据流（pull 后 app 是否显示服务器收藏/历史、push 后服务器是否收到 app 新建项）的实机验证被上述缺陷部分阻塞**；app 所依赖的服务器行为已用**相同端点**经 curl+SQLite 直验（B/D）。
- 若后续需实机跑通：建议先修 E2E-13（或人工在平板软键盘手输配对码完成配对），再验双向数据流 + E2E-12。

### F. 本 session 环境收尾（第二轮）
- 单一当前版本服务器运行于 `:8080`，数据目录 `/tmp/av-e2e2/data`（含本 session 注入假数据：2 收藏/2 历史/2 下载/2 标签；`user_preference` 曾重置为空以做 app 偏好测试，后因未实机同步仍为空/测试态）。**供后续干净测试应换全新空目录重启**。
- 平板 app 存储配置**未变**（仍 `127.0.0.1:8081`，配对未成功故未写回）；app 本地书库未触碰；IME 已还原为原 Gboard。
- 浏览器留有 `localhost:8080` 标签（无害）。
- 重申：本节仅记录，**未改任何源码/配置**。

---

## 多子代理并行执行规则（供后续执行本 TODO 的模型 —— 大胆并行，但守以下护栏）

> 目标读者 = 一个**能力强、善开子代理、善用工具**的执行模型。下列规则旨在把本 TODO 拆成可**高并发**执行的单元，同时杜绝并行写冲突与共享资源竞态。**核心思想**：能并行的全并行（用 git worktree 隔离每个 agent 的工作树），不能并行的（共享契约/共享构建/共享设备/集成 E2E）显式串行化为「门」。

### 0. 总原则
1. **leader/coordinator** 负责：建任务图、分配 disjoint 写域、拥有共享文件、跑门、合并 worktree、跑集成 E2E。**不**亲自写业务代码（除非补漏）。
2. **worker agent** 只在自己拥有的文件集合内改码 + 写/改自己模块的测试；改完跑**自己模块**的验证门，再向 leader 报「done + 证据」。
3. 把 agent 输出当**证据**而非真理：leader 在合并/集成门复核。
4. 并行度大胆：Wave-1 直接 **6–8 路**；用 worktree 让 8 个 agent 同时编辑互不冲突。

### 1. 依赖分层（Waves）
- **Wave-0（leader，串行，开工前）**：冻结契约基线 —— 快照 `contracts/openapi.yaml`、`contracts/sync-schemas.json`、`contracts/sync-conflict-rules.md`、`contracts/backup-format.md`；确认 neutralize 当前状态（grep `gallery.test`/`showSite*`/`showEh*` 在 app/web/mock/contracts 四处的分布）作为全局不变量基线。
- **Wave-1（并行 6–8）**：互不相交的 P0 修复（见 §2）。
- **Wave-2（契约波，串行→再并行）**：跨端 schema/契约变更（M-13/M-14/E2E-8/偏好键），见 §3。
- **Wave-3（并行，按模块分片）**：P1 加固，见 §4。
- **Wave-final（单 agent，串行）**：集成 + 全量 E2E 复跑，见 §5。

### 2. Wave-1：P0 disjoint 修复（每项标 owned files，确保同波无重叠）
| Agent | 条目 | owned files（独占） | 验证门 |
|---|---|---|---|
| W1a | C-1 | `azure-pipelines.yml` | `grep -rn 123456 azure-pipelines.yml` 空 |
| W1b | L-5 | `HealthController.kt`、`MetricsController.kt`；**向 leader 申请**构建元数据注入（共享 build 文件由 leader 落） | 改 `webVersion` 后两端点同步变 |
| W1c | L-6 | `ArchiveController.kt`（de-neutralize 复核第二分支） | 编译 + 单测 |
| W1d | L-7 | `mock-server/routes/sync.mjs` + `mock-server/test-*.mjs` | `node --test mock-server/*` |
| W1e | M-2 | `AuthController.kt` + **新建**限速组件 + 向 leader 申请 `application.yml` 配置键 | 5 次失败锁 ≥60s；可配关闭 |
| W1f | M-8 | `EncryptionService.kt` + **新建**迁移 helper | 新密文含 salt/迭代；旧密文可解 |
| W1g | E2E-1 | `UserPreferenceService.kt` + Jackson `ObjectMapper` 配置 + `PreferenceController` 兜底 | push 异构串后 GET/PUT /preferences 仍 200 |
| W1h | M-6（advice-first） | **新建** `@RestControllerAdvice` + `ApiError`；**本波不改**各 controller 的 ad-hoc envelope（留到 Wave-3 收尾，避免与 W1e/W1g 撞文件） | 未捕获异常/校验失败统一 envelope |

> 注意 M-6 与 E2E-1/M-2 在「错误处理」上潜在重叠 → 故 M-6 本波**只加全局 advice**，per-controller 清理**延后**，这是为保 disjoint 而做的刻意排序。

### 3. Wave-2：契约波（串行改契约 → 并行改实现）
- **3a（leader 或单一契约 agent，串行）**：先改 `contracts/sync-schemas.json` + `contracts/openapi.yaml` + `contracts/sync-conflict-rules.md`：① history 增 `mode`、download 明确 `label`(name↔id 抽象) + 是否同步 `downloaded`、② 统一 `lastModified` 来源规则(E2E-8)、③ 偏好 wire 键命名定档（`showSite*` 或 `showEh*` 二选一并全端统一）、④ `category` 类型定档(E2E-4)。
- **3b（并行）**：契约冻结后，app-agent 与 web-agent **并行**按新契约改各自实现 + mock-agent 改 mock；三者 owned 域不重叠（app/ vs anotherviewer-web/ vs mock-server/）。
- **3c（leader 门）**：契约一致性 grep（app/web/mock/contracts 四端字段名/类型对齐）+ schema 测试。

### 4. Wave-3：P1 加固（按模块分片并行）
- 后端安全/校验片：H-1（默认 auth + 一次性引导）、M-3（CORS）、M-4（响应头）、M-5（`@Valid`+clamp，**含** Wave-1 延后的 per-controller envelope 清理）。
- 后端运维片：M-12（Docker `USER`）、C-2（迁移工具评估/文档约定）。
- Android 片：M-9（net-sec 域白名单）、M-10（exported 审计）、M-11（WebView 白名单）、H-2（OkHttp 升级评估）、H-4（targetSdk 评估）、E2E-11（更新检查）、E2E-13（connect 死路）、E2E-12（high-water 分片）。
- 前端片：E2E-9（卡片网格渲染）、E2E-10（tab 指示器）、E2E-3/E2E-5/E2E-6、大文件拆分、缓存层、i18n。
- 每片内部仍可再拆子 agent，但**同片内** owned files 须 disjoint。

### 5. Wave-final：集成 + 全量 E2E 复跑（单 agent，串行）
- 合并所有 worktree/分支 → 跑 `./gradlew :anotherviewer-web:test :app:assembleDebug` + `node --test mock-server/*`。
- 重跑**本文件两轮 E2E 的验收项**：WebUI 用 `computer_use` 走查（重点 E2E-1 设置页可用、E2E-9 卡片可见、M-14 标签 tab 归位、M-6 统一 envelope）；Android 用 `adb`（**人工软键盘输配对码**或 E2E-13 修好后自动化）跑配对→双向同步→交叉核验（重点 M-13 mode 回环、M-14 label 回环、E2E-1 app 同步不再 brick web、E2E-12 换服务器首同步完整）。

### 6. 共享资源串行化规则（硬锁，违反即竞态）
- **Gradle**：同一时刻**只一个** agent 跑 `./gradlew`（构建/测试是全局锁）。每波验证门由 leader 串行调度，或 agent 仅跑「编译自己模块」且 leader 统一跑全量 test。
- **单一 SQLite / 单一服务器 / 单一平板**：集成 E2E（Wave-final）**单 agent 串行**；并行 agent **不得**各自起服务器/连平板/写同一 DB。需要时各自用**独立临时 data-dir + 独立端口**做单元级 HTTP 自验，但**不得**用于跨端互验（互验只在 Wave-final 的单服务器上做）。
- **mock-server**：改动串行（W1d 独占）。

### 7. 冲突避免的具体规则
- **r1**：同波内**无两 agent 编辑同一文件**；上表 owned files 即合约。
- **r2**：共享文件（`build.gradle*`、`gradle.properties`、`contracts/*`、`AndroidManifest.xml`、`application.yml`、`settings.gradle`）**归 leader 独占**；worker 以「patch 说明」提交意图，leader 应用。
- **r3**：DB schema 变更（M-13 加列、M-14）由**单一迁移 owner**（与 C-2 决策合并）执行，且**同时**更新 sync-schemas.json + 两端 entity/mapper + mock，缺一不可。
- **r4**：每个 worker **必须**为自己的改动加/改测试；「绿 build」≠ done，须含针对性测试 +（如适用）curl/契约自验。
- **r5**：commit 粒度 = 每 agent 每修复**一个**逻辑 commit（遵循用户「多逻辑 commit 优于单大 commit」偏好）；leader **不**跨 agent squash。
- **r6**：agent 报 done 须附：改动文件清单、跑过的命令+输出摘要、对应验收项的实测结果；leader 据此核销 TODO 行。

### 8. 用 worktree 隔离实现「真并行文件编辑」
- 每个 Wave-1/3 worker 以 **`isolation: "worktree"`** 启动 → 各自独立 git worktree/分支，8 路同时编辑**零工作树冲突**。
- leader 在波末按依赖序合并 worktree（先契约、后实现；同波 disjoint 故应无文本冲突，有冲突即说明 owned 域划错，回炉重分）。
- 这是把「并行度上限」从「共享工作树的串行」抬到「CPU/IO 真并发」的关键手段 —— **大胆用**。

### 9. neutralize 全局不变量（跨 agent 红线）
- neutralize(`4edf742b`) 是**临时**全局状态，触及画廊域串或偏好 wire 键的**任何** agent，改完必须跑跨端一致性 grep（app/web/mock/contracts 四处的 `gallery.test`/真实域、`showSite*`/`showEh*`），并在 done 证据中附 grep 结果。de-neutralize 应作为**单独串行波**，由 leader 统一翻回并复跑全 E2E（L-6 的「第二分支复核」并入此波）。

### 10. 给执行模型的「挑战上限」提示
- 不要保守地一路串行：Wave-1 直接 8 路 worktree 并发；Wave-3 三片并发、片内再 2–3 路。
- 但**严守 §6 硬锁**：并行的前提是 disjoint 写域 + 共享资源串行门；做不到 disjoint 就**降并发**而非冒险竞态。
- 每个 agent 的 prompt 须**自包含**（含本文件相关条目原文 + owned files + 验证门命令 + neutralize 红线），因为它们看不到本对话。

---

## 子代理深度代码分析补充（两只读 agent 返回，已 grep 复核；file:line 精确）

> 两个后台只读 agent 在「第二轮 E2E」主体写完后返回，带回 file:line 级发现与对既有条目的精确化。下列**经本 session grep 复核**后纳入；少数标 *impl-verify* 的请在实现时再核函数名/属性表。多用户相关项按本项目「单用户 LAN 自托管」设计**降为 latent/low（超范围）**。

### 新增 HIGH
- **N-1 硬删（history/bookmark）不随增量 pull 传播**【数据完整性】`SyncService.kt:181` `historyRepository.delete(existing)`、`:231` `bookmarkRepository.delete(...)` 把行真删；但 history/bookmark 的 toSync DTO **从不带 `deleted`**（默认 false），且行已删 → `pull(since>0)` 的 `include()`(`:57`) 取不到 → 设备 A 删一条历史，设备 B 增量 pull **永远学不到**，残留脏行（仅 `since=0` 全量才靠「缺席=删除」生效，而客户端增量不做全量）。软删实体无此问题（tombstone 带新 lastModified）。*验收*：硬删 push 后，第二台设备下一次**增量** pull 删除本地对应行。
- **D-1 契约自相矛盾：`sync-schemas.json` 的 `syncEntityCollection` 设 `additionalProperties:false` 却无 `preferences` 属性**【契约】`contracts/sync-schemas.json:363`/`:405`；而代码 `SyncDto.kt:174` 与 `openapi.yaml`(`SyncEntityCollection.preferences`) **含** preferences → 严格校验 pull 载荷的客户端会**拒收**服务器 pull。*验收*：两契约文件对 collection 字段集一致；含 preferences 且 `additionalProperties` 放行或显式列出。

### 新增 MEDIUM
- **N-3 preferences 同步无 LWW + 写不校验**【同步正确性，E2E-1 写侧根因】`SyncService.kt:41-44`→`UserPreferenceService.replace` 无条件覆盖，**从不比较** `SyncPreferencesDto.lastModified`（与契约 §1.2/§3 的 LWW+skew 矛盾）；叠加 E2E-1 的写不校验 → 旧设备后推可覆盖新偏好，且可写入打挂 `GET /preferences` 的串。*验收*：旧 lastModified 的 push 不覆盖；任意 push 的偏好串能经 `GET` 往返不 500。
- **N-4 `requireAuth` 默认值跨调用方不一致**【配置，已复核】无 DB 行且无 env 时：`AuthTokenFilter.kt:24` 与 `ServerConfigService.kt:82` 用 **false**（实际不鉴权），但 `SiteAuthService.kt:151`、`SettingsService.kt:31`、`WsAuthChannelInterceptor.kt:31` 用 **true** → `/auth/status` 与 `/settings` 报 `authRequired=true` 而过滤器实际放行。*验收*：无行无 env 时，`/settings` 的 `security.requireAuth` == 过滤器实际执行值。
- **N-5 `FavoriteService.addFavorite` 把 category 写进 favoriteSlot**【字段映射，已复核】`FavoriteService.kt:49` `this.favoriteSlot = category`；category 是位掩码（最大 512），favoriteSlot 约束 -2..9 → 写 512 越界并破坏 `listFavorites` 的 slot 过滤（`:19`）。*验收*：`POST /favorite/add` 永不写越界 favoriteSlot；slot 与 category 独立入参。（注：本 session 观察到「收藏总数=2 但默认 tab 空」的 UI 现象，其与 slot 映射的确切关系 *impl-verify*——`:19` 的 `slot<=0` 分支返回 all，与「tab0 空」表面矛盾，需复核 tab↔slot 约定，勿据此误判。）
- **B1 下载进度计数从不往返**【app 侧，已复核】`dao/DownloadsDao.java` **无** downloaded/finished/total 列（grep 仅 archiveUri 命中）→ DB 载入的 DownloadInfo 这三项=0 → push 恒发 0；pull 设进内存后被 `dao.update/insert` 丢弃。*验收*：设备 A 的 finished/total/downloaded 在设备 B pull 后非 0 且持久化。
- **B2 `lastModified = 创建时间` 破坏 LWW；下载无条件跟服务器**【同步正确性，已复核】`WebUiSyncEngine.java:328` `dto.lastModified = info.time`（`:300` 历史同），`time` 是创建戳、进度/元数据变更**不推进** → 服务器 skew/LWW 退化为「后创建者恒赢」；且 `:494`「Downloads follow the server unconditionally」→ 早创建设备上**正在下载**的进度，每次 pull 被晚创建设备的陈旧态覆盖。*验收*：设备 A 的活跃进度/状态不被设备 B 的「创建更晚但进度更旧」记录重置。
- **B3 进行中的下载 state 在 push 前被压成 NONE**【app】`SiteDB.getAllDownloadInfo()`「Fix state」把 WAIT/DOWNLOAD→NONE → 活跃下载在其它设备显示为 NONE。*验收*：STATE_DOWNLOAD 在设备 B 显示为下载中。
- **B4 四个画廊实体大部分 galleryInfoBase 字段为「幽灵」**【app】rated/pages/simpleTags/thumbW·H/span*/favoriteSlot/favoriteName 被 copy*ToDto 映射但**非 greenDAO 列** → push 发默认值、pull 设进内存后被 dao 丢弃 → 跨设备丢标签/页数/缩略图尺寸/收藏夹 slot·name。（与 N-5 叠加：app 端 favoriteSlot 本就不持久化。）*验收*：带 pages/simpleTags/favoriteSlot 的收藏跨设备保留。

### 新增 LOW / 契约漂移
- **B5/D-2**：契约定义 `fileSize`、`archiveUri`，app DTO 两者皆无（archiveUri 故意本地保留；fileSize 未实现）。**D-6**：`simpleTags` 分隔符 openapi(空格) vs sync-schemas(分号) 自相矛盾。**D-7**：版本三处不一（=L-5，openapi `1.0.0` 为第三处）。**D-8**：`deviceId` schema 要求 minLength1，服务端接受空串无校验。**B6**：契约 §1.2 单调守卫未实现（app 发 lastModified=time，无 per-entity 高水位）。**B9**：push 为全量非增量（O(库) 带宽，downloads 以 500 分批缓解）。**B7/B8/B11/B12**：triggerFilter 空 enable NPE 风险 / setFilterEnabled 不对称 / 配对码校验松(length≥4) 且 validate() 未接线 / 无全局 sync 锁（pref push 可与 sync() 并发）。
- **已解开放项**：app 时钟源 = 真实墙钟 `System.currentTimeMillis()`（解第一轮 E2E-7 的「确认 app 用墙钟」）——但叠加 B2（lastModified=创建时间不推进），墙钟并不如预期「 reassuring」。

### 对既有条目的精确化/更正
- **E2E-1 触发序列更正**：偏好**仅**经显式「同步配置」按钮 push（`PreferenceSyncHelper.pushToServer`）；主 `sync()` **不含**偏好；配对后仅自动 **pull**（不 push）。故 brick 序列 = 某设备**显式 push** 了异构偏好串。已装 pre-neutralize app 的 `general` 几乎确定携带 `showEhEvents/showEhLimits`（neutralize `4edf742b` 在源码把它们改成 `showSite*`；*impl-verify* 2.0.2.2 构建/源 tag），与 neutralize 后 web `GeneralPreferences`(`PreferenceDto.kt` 的 showSiteEvents/showSiteLimits) 冲突 → 严格 Jackson 对**嵌套**同样抛错 → web `/preferences` 500。当前**源码** app 已 neutralize 该串值，重建可避此**特定**失配；但严格/无容忍设计 + N-3 使**任何**跨端键漂移都会 brick。
- **M-14 `downloaded` 语义更正**：契约 `downloaded` = 「本会话已下载图/页数」(**计数**，非字节)；两端均不持久化（app 见 B1、server 无列）→ 往返恒 0。字节大小是另一字段 `fileSize`（未实现，见 D-2/B5）。
- **M-13 责任界定**：app **正确**双向同步并持久化 mode（`HistoryDao` 有 mode 列；fillHistory 发 mode；applyHistory 写 mode）；**唯一**丢失点是 server（无 mode 列 + applyHistoryFields 漏 + toSync 发 0 + REST 硬编 0）。故 M-13 纯 server 侧。
- **范围校准**：agent 的 **N-2**（REST 服务未按 username 隔离，`clearHistory` 删全用户）与 **N-7**（自然键全局唯一，静默丢第二用户记录）属**多用户**问题，按本项目单用户设计**超范围**，降为 latent/low，仅当 H-1 翻转且多账户时才相关。

---

## mock-server 补全 + 画廊语料（百炼生成）+ 接线 —— 开发规格（供执行模型实现；本 session 不写产品代码）

> **架构定性（务必先读）**：mock-server 的职责 = **模拟远端画廊站点**（临时脚手架，开发/测试完即弃、切真站）。**WebUI 后端 API 是另一套逻辑**（即我们跑的真实后端，**不应**被 mock 冒充）。当前 `mock-server/server.mjs` 把 `/api/v1/*`（后端 API 模拟）与画廊站点模拟（`gallery.mjs`）**混在同一进程**，是关注点混淆 → 本规格要求厘清/剥离（见 M-0）。
> **本 session 边界**：本 session **只**写本规格 + 生成图片语料（百炼，见 C-0）；**产品代码**（拦截器/mock 路由/解析器对齐/重建/重装/全量复跑）由执行模型按本规格实现；**跑完我们再复测**。顺序：写完本 TODO → 我们装百炼+生图(C-0) → 执行模型跑 C-1..C-3 → 我们复测。

### C-0 画廊语料（本 session 产出，执行模型视为「已提供」）
- **图像来源 = 阿里云百炼 CLI**（按 `https://bailian.aliyun.com/cli/install.md`：`npm install -g bailian-cli`，Node≥18.17 本机 v22 满足；鉴权 `bl auth login --api-key <KEY>` 或 `bl auth login --console`；**KEY 取自控制台，绝不硬编码/提交**；已装 `bl 1.13.0`；文生图 = **`bl image generate`**（Qwen-Image / wan2.x，具体 flags 生成时 `bl image generate --help` 取，含尺寸/模型/输出路径）；二进制在 `$(npm prefix -g)/bin/bl`（**该 bin 未在默认 PATH**，调用用全路径或先 `export PATH="$(npm prefix -g)/bin:$PATH"`）；凭证存 `~/.bailian/config.json`，**绝不提交**；`npx skills add ...` 非必需）。
- **种子集（内容不限）**：生成少量图，存 `mock-server/assets/seed/`：~6 张「页」图（竖版，prompt 各异/颜色各异）+ ~2 张「封面/缩略」图；附 `mock-server/assets/seed/MANIFEST.json` 记 prompts + 模型 id + 尺寸（可复现）。**提交种子**（小、即「留存」的素材）。
- **`mock-server/scripts/gen-corpus.mjs`**（本 session 写+跑）：读 `gallery-fixtures.mjs`，对每个 (gid,page) 复制 `seed[(gid+page) % N]` → `mock-server/assets/image/{gid}/{page}.jpg`；封面 → `assets/t/{gid}/cover.jpg`；每页缩略 → `assets/t/{gid}/{i}.jpg`。使 mock 需要的**每个 URL 路径都有真实文件**。本地跑通；per-path 文件**默认不提交**（由生成器复现；提交策略见 r-asset）。
- **数量**：fixtures 现 7 画廊、页 5/6/7/4/8/3/4 = 37 页 + 7 封面 + 37 缩略 ≈ 81 路径，由 ~8 张种子复用覆盖（内容不限故可复用）。
- *备选*：若需「肉眼核对页码」的测试，保留 `gallery-images.mjs` 的确定性带标签生成器作 fallback（规格注明二选一/并存）。

### M-0 厘清 mock 关注点
- 剥离/隔离 `server.mjs` 的 `/api/v1/*` 后端 API 模拟（它冒充我们的真实后端，与「模拟远端站点」职责冲突）：要么**删除** `/api/v1/*` 路由与 `fixtures/galleries.mjs`（推荐，前端单独开发若需要再单列），要么移到独立 entry/进程并文档化「非远端站点模拟」。mock-server 对外**只**呈现画廊站点（`gallery.mjs` 的 `/g /s /image /t /` + 静态语料）。

### M-1 mock 代码任务（执行模型）
- **修 `server.mjs` 重复 import 致命 bug**：`galleryRoutes` 被 `./routes/gallery.mjs` 与 `./gallery.mjs` 各 import 一次 → 同名标识符 SyntaxError → 当前文件 `node` 起不来（在跑 PID 是陈旧进程）。重命名画廊站点那个 import（如 `gallerySiteRoutes`）。
- **静态托管语料**：`express.static('assets')` 挂根，使 `/image/{gid}/{page}.jpg`、`/t/{gid}/cover.jpg`、`/t/{gid}/{i}.jpg` 解析到 `assets/...`；**支持 Range**（`Accept-Ranges: bytes` + 206，利于下载/断点/reader 校验）+ 正确 content-type/length。**退役** `gallery.mjs` 里 on-the-fly 的 `/image`、`/t` makeImage 路由（或降为 fallback）。
- **`gallery.mjs` HTML 对齐 core 解析器**：对照黄金 HTML `app/src/test/resources/com/hippo/anotherviewer/client/parser/{GalleryDetail.html, GalleryPageParserTest.html, GalleryListParser*.html}` 校验 detail/page/list 形态（`var gid/token` 脚本、`#gnd`、`#gdd` 的 `Length: N pages`、`.gm` 封面、`#gdt` 预览、`#taglist`、`#cdiv`、image 页 `<img id="img" src=...>`+showkey、列表 `table.itg`/`gtr`/`glthumb`/`glname`）。**EXH_BASE 保持 `https://gallery.test`**（已复核 `gallery-fixtures.mjs`，与 neutralize 客户端一致 → 内嵌链可被拦截器改写）。
- **夹具**：`gallery-fixtures.mjs` 现含 1001→1002→1003、3001→3002 版本链 + 2001/2002 独立，足以验版本折叠；若需「全量」类别覆盖，**append-only** 增 gid（勿改 1001-1003，保 `test-gallery.mjs` 绿），或同步更新该测试。
- **测试**：`node --test mock-server/test-gallery.mjs` 全绿 + 新增「静态托管/Range/格式分支/解析器契约」用例。

### W-1 接线：Web（执行模型）
- `anotherviewer-web/.../service/SiteSessionManager.kt:41` 的 `OkHttpClient.Builder()` 加**拦截器** + 新配置 `anotherviewer.gallery.mock-base-url`（默认空=关）。拦截器：当该配置非空且请求 host ∈ {`gallery.test`, `*.gallery.test`} 时，改写 scheme+host+port → 该 base（如 `http://127.0.0.1:4100`），**保留** path/query 与 `Host`/`Referer` 头（`SiteCallPatternConsistencyTest` 显示 web 发 `Host: gallery.test`、`Referer: https://gallery.test/`，解析/mock 可能依赖）。同一 client 覆盖 `ImageProxyController` → 图链一并改写。默认关 → 生产零影响、可逆。

### A-1 接线：App（执行模型，debug-only）
- app 用 `SiteHosts` 把 `gallery.test` 钉到 IP（OkHttp 自定义 Dns）+ `https://gallery.test`，**无法**靠 /etc/hosts 指向 Mac → 在 app 站点 OkHttpClient 加 **debug 门控**拦截器，键名 **`BuildConfig.MOCK_EH_BASE_URL`**（`gallery.mjs` 注释已用此名，沿用），非空时改写 `gallery.test`→该 base；release 该字段空、拦截器不生效。注意改写后 `SiteHosts` Dns 钉死不应干扰（host 已变为 IP）；`network_security_config` 已放行明文（M-9）→ http 可达。

### 端口 / 部署
- mock 跑 **:4100**（`PORT=4100`，避与真实后端 8080 撞车）。真实后端 :8080 用隔离 data-dir。

### 验证（执行模型各门 + 复测）
- mock 门：`node --test mock-server/test-gallery.mjs`+新用例；curl：list/detail/page=200 html、`/image/../*.jpg`=200 且 magic bytes、`Range`→206、`/t/..` 200。
- web 门：web 单测 + 一个 mock 指向的集成 smoke（search 返回 mock 画廊；image proxy 流出 mock 字节；置 mock-base-url 后 `/health` 的 `galleryApi=UP`）。
- app 门：`./gradlew :app:assembleDebug` + lint；adb smoke 留 C-3。
- **C-3 全量复测（我们做）**：web `computer_use` 搜索网格(带 mock 缩略图)→详情(标签/评论/READ/DOWNLOAD)→阅读器(看到百炼图)→下载 N 页(核对文件数/大小)；app adb 同走通 + 与 web 交叉核验同步。

### 弃用说明
- mock 为临时脚手架；W-1/A-1 均**门控**（默认关）→ 切真站 = 不设 mock-base-url / `MOCK_EH_BASE_URL` 留空，无需改代码。

---

## 上述 mock/语料/接线 工作的多子代理并行执行规则

> 目标读者 = 能力强、善开子代理的执行模型。本工作可并行度受「disjoint 代码树」约束，**安全上限 = 3 路**（mock ∥ web ∥ app，各占一棵树，worktree 隔离）；再拆需同树子 worktree+合并，收益递减，**不建议**为凑并发而冒险竞态。

### Waves
- **C-0（本 session 已做/视为已提供）**：百炼种子 + `gen-corpus` + 本地物化。执行模型开工前先**校验** `mock-server/assets/seed/` 与 `scripts/gen-corpus.mjs` 存在且可重跑；缺则按 C-0 规格补。
- **C-1（3 路 worktree 并行）**：
  - **Agent-mock**（own `mock-server/**`，**不含** `assets/seed/`[已提供]）：M-0 + M-1 全部 + 测试。
  - **Agent-web**（own `anotherviewer-web/**`，含其 `application.yml`）：W-1。
  - **Agent-app**（own `app/**`，含其 `build.gradle`/`AndroidManifest`/`BuildConfig`）：A-1。
  - 三棵树互不相交 → worktree 真并发零冲突。
- **C-2（leader 串行）**：合并 worktree；跑 neutralize/EXH_BASE 一致性 grep（`gallery.test`/`showSite*`/`showEh*`/`EXH_BASE` 跨 mock·web·app·contracts）；**串行**重建 web jar + debug APK（gradle 全局锁，见下）。
- **C-3（单 agent 串行，我们复测）**：USB 重装 debug APK（**清 app 数据做干净同步须先经用户确认**——会删平板本地书库）；跑全量 E2E（见验证 C-3）；结果回填本 TODO。

### 硬锁（违反即竞态）
- **gradle**：任一时刻仅一个 agent 跑 `./gradlew`。C-1 内**仅** Agent-app 在其门跑 `assembleDebug`，故 Agent-app 的 gradle 须由 leader 与任何其它 gradle 串行调度；Agent-mock/web **不得**跑全量 gradle。
- **mock 端口 4100**：Agent-mock 独占；C-3 复测时由复测者起。
- **单一平板 / 单一真实后端**：仅 C-3 使用；C-1 各 agent **不得**连平板/写同一 DB（自验用各自临时 data-dir+端口的单元/HTTP smoke，**不**用于跨端互验）。

### 冲突规则
- **r1** 同波无两 agent 编辑同文件；C-1 的 owned 树即合约。
- **r2** 跨树共享文件（`contracts/*`、根 `gradle.properties`、`settings.gradle`）leader 独占；本波**不改 sync 契约**（mock 是外部站点模拟器，sync 契约不变）→ 简化。`application.yml` 归 Agent-web（在其树内）、app 的 manifest/BuildConfig 归 Agent-app（在其树内）→ 仍 disjoint。
- **r3** 无契约变更 → 无需契约波。
- **r4** 每 agent 必加/改测试（见各门）；绿 build ≠ done。
- **r5** commit 粒度 = 每 agent 每改动一逻辑 commit；leader 不跨 agent squash。
- **r6** done 证据 = 改动文件清单 + 命令+输出 + 验收实测。
- **r-asset** 语料提交策略：提交 `assets/seed/`+`scripts/gen-corpus.mjs`（小、可复现）；per-path 物化文件**默认不提交**（CI/setup 时生成，或 git-lfs），避免仓库膨胀。
- **neutralize/EXH_BASE 门**：触碰画廊 host 串的 agent 必跑跨树 grep；EXH_BASE 须 == neutralize host（`gallery.test`）；mock-base-url/`MOCK_EH_BASE_URL` 只改**传输目标**，不改解析器看到的 Host 头（拦截器保留 `Host: gallery.test`）。

### 挑战上限的说明
- C-1 直接 3 路 worktree 并发即本工作的安全上限；**勿**为「更多并行」把 Agent-mock 再拆（同树子 worktree+合并=负收益）。真正的并发杠杆在「disjoint 树 + worktree」，已用满。
- 每个 agent 的 prompt 自包含（本规格相关条目原文 + owned 树 + 门命令 + neutralize/EXH_BASE 红线 + 「mock 是临时远端站点模拟器、`/api/v1` 模拟须剥离」的架构定性）。

---

## 执行记录（2026-08-03 多 Agent 并行 session · 未 commit，按用户指示）

> 执行方式：主线程（leader）侦察 + 两批并行子 Agent（第一批 8 路：M-2/M-8/同步核心(E2E-1+N-3+N-1)/M-6/L-5/N-4/N-5/M-13；第二批 5 路：同步域第二波(H-3+M-14+E2E-8)/控制器加固(M-5+M-6收尾)/运维加固(M-3+M-4+M-12+C-2+H-1文档)/前端(E2E-9/10/3/6)/契约(D-7+E2E-4+mock对齐)）。**未 commit**（用户指示：有并行 Agent 在工作，勿提交）。所有 Agent 遵守：不改 `com.hippo.*` 包名/类名（许可证红线）、不引入 showEh/真实画廊域名（neutralize 红线）。

### 已完成并验证（验收 = 运行时冒烟实测 + 单测全绿）
| ID | 状态 | 验证方式 |
|---|---|---|
| C-1 | 已修 | 密码改 secret 变量引用；`rg "123456" azure-pipelines.yml` 空 ✓ |
| L-6 | 已修 | ArchiveController + ArchiveService 重言式删为单判断 ✓ |
| L-7 | 已修 | mock sync since=0 特判对齐后端 include()；`node --test` 23/23 ✓ |
| L-5 | 已修 | webVersion 注入 version.properties；`/health` 实测 version=1.1.0 ✓；D-7 由契约 Agent 同步（openapi 1.1.0） |
| M-2 | 已修 | LoginRateLimiter（IP+用户名、5 次锁 60s、指数退避、可配关闭）；实测 5×400→6th 429 ✓ |
| M-8 | 已修 | PBKDF2WithHmacSHA256 100k 迭代 + salt + AVK2 魔数；旧密文兼容解密；错误 key 拒绝 ✓ |
| E2E-1 | 已修 | UserPreferenceService 容错反序列化 + 归一；异构串 push 后 GET/PUT /preferences 200 ✓ |
| N-3 | 已修 | preferences LWW（incoming > stored+5s 才覆盖）✓ |
| N-1 | 已修 | history/bookmark 墓碑化（软删行 + bump lastModified）；增量 pull 传播删除 ✓（实测 pull since>0 回 deleted=true）；契约文档同步 |
| M-6 | 已修 | ApiError + GlobalExceptionHandler 统一 envelope；per-controller 清理；实测 404/400/429 统一 `{error:{code,message,traceId,status}}` ✓ |
| N-4 | 已修 | requireAuth 默认统一 false；实测 /auth/status authRequired=false ✓ |
| N-5 | 已修 | addFavorite slot 独立入参 clamp -2..9；category 不再写 favoriteSlot ✓ |
| M-13 | 已修 | server 加 mode 列 + sync/REST 双路径；实测 push mode=9 → pull mode=9、/history/list mode=5 ✓ |
| M-14 | 已修 | label name↔id 双向映射 + 未知标签自动补建；downloaded 文档化为非同步字段；实测 label 回环 ✓ |
| E2E-8 | 已修 | preferences 保留客户端 lastModified；实测回环 ✓ |
| D-1 | 已修 | sync-schemas.json syncEntityCollection 补 preferences ✓ |
| D-7 | 已修 | openapi.yaml version 1.1.0 ✓ |
| E2E-4 | 已修 | category 全链路 Int（契约+DTO+两个 Service+前端类型）；实测 /history/list category=2 ✓ |
| H-3 | 已修 | 7 Repository 派生查询（findByUsername / findByUsernameAndLastModifiedGreaterThan），since=0 边界保留；测试 verify 无 findAll；**@Index 未落地**（需 schema 迁移 owner 统一加，见遗留） |
| M-5 | 已修 | 全量 @Valid + DTO 约束；pageSize clamp 1..200 ✓ |
| M-3 | 已修 | CORS 默认收紧 localhost/127.0.0.1，env 可覆盖 ✓ |
| M-4 | 已修 | CSP/X-Frame-Options/nosniff/HSTS 头；前端功能待 Safari 复验 |
| M-12 | 已修 | Docker USER 1000（setpriv 降权 + 条件 chown）；**本机无 docker，需 docker 环境实测** |
| C-2 | 已修 | 降级为文档约定（README「数据库 schema 变更约定」） |
| H-1 | 文档化 | 默认不翻转，README 部署节写明可信网络 + ANOTHERVIEWER_REQUIRE_AUTH |
| E2E-9 | 已修 | 卡片可见性脱离缩略图加载 + 显式 `to {opacity:1}` 关键帧；Playwright 实测 thumb=null 卡片可见 ✓（Safari 待复验） |
| E2E-10 | 已修 | 侧栏双抽屉修复；下载 tab 指示器 Chromium 实测 ✓（Safari 待复验） |
| E2E-3 | 已修 | 占位框统一图标、不渲染 alt 文本 ✓ |
| E2E-6 | 已修 | 搜索失败区分错误态+重试（HTTP 200 {success:false} 被拒为错误）✓ |
| E2E-2 部分 | 已清理 | build/libs 陈旧 ehviewer-web-*.jar 已删；**包名未动**（用户红线：com.hippo.* 继承命名保持） |

### 验证门结果（全绿）
- `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :anotherviewer-web:test` → BUILD SUCCESSFUL（含新增 ~150 用例；修复了 SyncControllerTest 7 个假仓库未 stub 新派生方法的回归 + M-14 标签数断言）
- `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :anotherviewer-web:bootJar` → SUCCESS（anotherviewer-web-1.1.0.jar）
- `node --test mock-server/test-sync.mjs mock-server/test-gallery.mjs` → 23/23
- `web-frontend`: `npm run typecheck` clean + `npm test` 420/420 + `npm run build` ✓
- 运行时冒烟（一次性 /tmp/av-smoke/data + :8191）：/health version=1.1.0、/auth/status authRequired=false、异构偏好 GET /preferences 200、统一 error envelope、mode 9→pull 9→/history/list 5、墓碑增量传播、label 回环、5 失败→429、preferences lastModified 回环 —— 全部符合验收

### 遗留（需后续处理）
1. **@Index 未落地**（H-3 只做了派生查询）：7 实体 `@Table(indexes=[username, lastModified])` 需 schema 迁移 owner 统一加。
2. **E2E-9/E2E-10 需 Safari computer_use 复验**（前端 Agent 用 Chromium+Playwright 实测通过；原现象仅 Safari 可复现）。
3. **M-12 需 docker 环境实测**（本机无 docker）。
4. **M-4 CSP 前端回归需 E2E 复验**（SPA 内联样式场景）。
5. **未处理（P1/P2/Android 域）**：H-1 默认翻转（保留文档化）、M-1（已默认关闭，维持）、M-7（并发写压测）、M-9/M-10/M-11、M-15 无、H-2/H-4、E2E-11/E2E-12/E2E-13、B1-B4、前端缓存层/i18n/大文件拆分、mock M-0 剥离、`contracts/observability.md` 等 1.0.0 残留。
6. **待定契约张力**：sync-conflict-rules.md §3.3 与 sync-schemas.json downloaded 描述已统一（本 session 合并）。

---

## 第三轮复验（2026-08-03 测试 session · 运行时验收 + Safari computer_use 走查）

> 范围：复验 08-03 并行 session 的全部修复（工作树未 commit）。方式：三层测试门 + 隔离 data-dir（`/tmp/av-e2e3/data`）curl/SQLite 运行时验收 + Safari 实机走查。**本 session 重建了前端 dist + bootJar**（原因见 T-2），未改任何产品源码。结论：API 层修复**全部通过**；发现 1 个 Safari 渲染回归（T-1，交开发模型）、1 个构建流水线问题（T-2，已修）、1 个低优先级缺陷（T-3）。

### 测试门（全绿）
- `./gradlew :anotherviewer-web:test` UP-TO-DATE（源码自上次全绿后未变，缓存有效）；`node --test mock-server/test-sync.mjs test-gallery.mjs` 23/23；`web-frontend` typecheck clean + 420/420。
- 环境注意：本机默认 `java` 为 1.8，直接 `java -jar` 起不了（UnsupportedClassVersionError）；`start.sh` 自带 17+ 探测（Homebrew openjdk@21 优先），**不受影响**，非缺陷。

### 运行时验收（curl + SQLite，全过）
| 条目 | 结果 | 备注 |
|---|---|---|
| L-5 | ✅ | `/health` version=1.1.0；`/metrics/dashboard` summary.version=1.1.0。**注意** `/metrics` 根响应本无 version 字段（契约如此），版本在 dashboard，勿误判 |
| N-4 | ✅ | `/auth/status` authRequired=false 与匿名实际放行一致 |
| M-3 | ✅ | localhost origin 反射 ACAO；非白名单 origin 不反射 |
| M-4 | ✅ | CSP/X-Frame-Options/X-Content-Type-Options 头存在；Safari 下 SPA 样式/功能无回归 |
| M-6 | ✅ | 404/405/400 统一 `{error:{code,message,traceId,status}}` |
| M-2 | ✅ | 5×400 → 第 6 次 429。**注意** 429 body 为 `AuthResponse{success,message}` 形——AuthController 刻意保留 auth 契约形（代码注释背书，app 兼容），非统一 envelope 漏网 |
| M-13 | ✅ | push mode=9 → pull 9、`/history/list` 9；`history_info` 含 mode 列 |
| N-1 | ✅ | history/bookmark 墓碑后增量 pull(since>0) 回 `deleted=true`；list 不再含该行 |
| M-14 | ✅ | `download_info.label` 存 label 表 id（name↔id 双向）；未知标签自动补建；finished 回环 |
| E2E-4 | ✅ | `/favorite/list` category 为 Int |
| N-5 | ✅ | slot=99 clamp 进 -2..9（DB `favorite_slot`=9）；category=512 独立存储不串 slot |
| H-3 since=0 | ✅ | lastModified=0 记录被 since=0 返回 |
| E2E-1 | ✅ | 异构顶层 `{theme:dark}` 与嵌套 `showEh*` push 后 GET/PUT `/preferences` 均 200；UI 设置页完整渲染（见下） |
| E2E-8 | ✅ | sync push 保留客户端 lastModified（存 `updated_at`）；**注意** REST PUT 路径用服务器戳、sync replace 路径用客户端戳，二者设计不同，测试须用墙钟量级时间戳（小合成戳会被 LWW 正确拒绝——曾误判为失败） |
| N-3 | ✅ | LWW 三段：新覆盖/陈旧拒/更新再覆盖；pull 回环客户端戳 |
| M-5 | ✅ | pageSize=99999 clamp 不报错；@Valid 400 统一 envelope |
| D-1 | ✅ | sync-schemas.json `syncEntityCollection` 含 preferences 属性（与 openapi 一致） |
| 备份导出 | ✅ | zip+manifest（appVersion=1.1.0、sha256、sizeBytes） |

### Safari computer_use 走查
- ✅ **E2E-1 UI**：设置页四分区完整渲染（当时 DB 尚存 showEh* 异构串，端到端坐实）。
- ✅ **M-4**：CSP 下暗色主题/开关/下拉全正常。
- ✅ 下载卡片（DownloadItem）与搜索**网格**卡片可见；下载 All/MyLabel tab 正常、LabelTest 归入 MyLabel（M-14 UI）、指示器跟随（E2E-10）。
- ⚠️ **E2E-6**：Safari 未复验（搜索输入框无 AX 文本角色，自动化输入受限）；维持 Chromium Playwright + 单测结论。
- ❌ **T-1【P1/新回归】Safari 收藏列表卡片不可见**：`/favorites` count=「2 galleries」且 AX 树含 CatTest/SlotNine 按钮，但视觉全空白（等入场动画完成后再截图仍空白；系统 reduceMotion=off）。首页 feed 同疑（未完全坐实）。根因锁定 `web-frontend/src/views/FavoriteView.vue:359-362` `.gallery-list__row { animation: item-in 240ms var(--ease-decelerate-quart) both }` 在 Safari 卡在 opacity 0（fill both + from opacity 0）。**对照**：网格模式 `.app-card`（`GalleryCard.vue:179`，`gallery-card-enter … backwards`）与 DownloadItem 在 Safari 正常 → 作用面 = 列表行入场动画。Chromium 正常（前端 agent 仅 Chromium 复验的盲区）。**交开发模型**：Safari 实机复验入场动画（或列表行去 fill-both/改 transition），补 Safari 回归测试手段。
- 🔧 **T-2【构建流水线，已修】jar 内嵌陈旧前端 dist**：工作树 `FavoriteView.vue:123` 已有 categoryBit number guard，但旧 jar 内嵌 bundle 为 `e.trim()` 旧版 → 服务端 Int category 触发 `TypeError: e.trim is not a function` → 收藏页 error 态（access log 200 + 提取 jar 内 bundle 比对坐实；私密窗口无 SW/无旧状态复现排除环境因素）。已 `npm run build` + `bootJar` 重建。**建议**：`build.sh`/CI 保证 frontend build 先于 bootJar（或加 dist-freshness 门），杜绝「测试全绿但产物陈旧」。
- 🐛 **T-3【低/新】backup export ASYNC dispatch 403 日志**：导出请求响应已 committed（客户端 200 拿到 zip）后，Tomcat async dispatch 再过安全链时 SecurityContext 不跨 ASYNC dispatch → `AuthorizationDeniedException` ERROR 日志（「response already committed」）。客户端无感但日志噪音；若存在未 committed 的 async 端点会表面化为 401。建议：async dispatch 传播 SecurityContext（或 `dispatcherType(ASYNC).permitAll` 于已认证请求）并补日志 URI（关联既有可观测性缺口）。

### 环境注记
- 本轮中途 :8080 被**非本 session 启动**的进程接管（PID 81975，data-dir `/tmp/av-e2e-dummy`，疑用户并行任务）——未杀、未触碰；本 session 验证移至 **:8082**（自有实例，`/tmp/av-e2e3/data`，含夹具：2 收藏/1 下载/1 标签/1 历史/异构偏好串），留供用户复查；`./stop.sh` 不适用（非 pidfile 启动），需 `kill` 对应 PID。
- 此前「下载/收藏 list 空」一度疑为产品 bug，实为 :8080 实例串扰（dummy 空库）——8082 单实例复测数据齐全，**非缺陷**。

---

## 执行记录（2026-08-03 晚 · Android 清理 + 第三轮复验修复 · 已按波提交）

> 用户红线：`com.hippo.*` 包名/类名（原项目继承部分）一律不改；`gallery.test`/`showSite*` neutralize 态保持至接真实站点波。commit 策略 = 每波完成后提交（用户批准）。

### Android 清理波（已提交）
| commit | 内容 |
|---|---|
| `50105801` Wave-1a | MOCK_EH_BASE_URL 支持 `-PmockEhBaseUrl` 注入；删死代码（NewsScene/TestThread/trashtest/requestOverride.js/空 libs fileTree/FILE_PROVIDER_AUTHORITY/重复 SpinKit）；testNamespace 修正；mock server.mjs 重复 import 修复 |
| `78c88e44` Wave-1b | Manifest：重复 host/无效 autoVerify/CAMERA 注释/15+3 组件显式 exported=false；activity_blick_list→black_list；getmSiteCookieStore→getSiteCookieStore；isMockSiteHost 去重 |
| `885d5382` Wave-1c | 删 ehtracker.org 豁免；WebView 白名单×2 + 删 file-scheme cookie + 删 LoginWebViewClientSNI 死代码；SiteHosts 删 raw.githubusercontent.com 硬编码 IP；自动更新默认关；UpdateDialog 跳 Releases 对齐 |
| `bd3bc3bd` Wave-2 | **schema v8**：4 表 +B4 十列 + DOWNLOADS +finished/total/fileSize/lastModified（downloaded 契约保持会话计数）；SiteDB case7→8 迁移（逐列 try/catch）；移除 getAllDownloadInfo Fix-state 副作用（B3）；DownloadManager 状态变更打 lastModified（B2）；BookmarksBao→BookmarksDao（L-4）；生成器 VERSION=8；WebUiSyncEngine 实例化重构 + Store/Transport 接缝；SiteDbSchemaV8Test + WebUiSyncEngineTest（11 用例） |
| `0524768f` | TorrentParser 正则换行容忍（neutralize 起基线红，修绿） |
| `d1ae9039` Wave-3c | E2E-12 高水位按 baseUrl 分片（旧键不泄漏）；E2E-13 ConnectTask 先探测 /auth/status（auth-off 跳过 login 直接保存；失败引导配对；去 default 预填）；L-2 validate 接入两对话框；WebUiSettingsTest/WebUiConfigTest |
| `50b2735c` Wave-3d | 5 处硬编码中文提 string 资源；GalleryListScene legacy requestCode==1001 分支删除 |

### 第三轮复验发现修复（已提交）
| ID | commit | 说明 |
|---|---|---|
| T-1 | `993ce1fe` | **Safari 列表行卡 opacity 0**：全部 `animation-fill-mode: both` 改 `backwards`+基础 `opacity:1`（11 处规则：列表行/详情段/对话框/scrim/toast/登录卡/hero）；`entranceAnimation.spec.ts` 30 条静态回归断言。**待 Safari 实机复验** |
| T-2 | `92ed4a1c` | build.sh 前端先于 bootJar + dist 新鲜度门（防陈旧前端打进 jar） |
| T-3 | `839e6c1e` | SecurityConfig `dispatcherTypeMatchers(ASYNC).permitAll()`；accessDeniedHandler 与 405 日志补 method/uri/remoteAddr；规则级测试守 ASYNC 放行（删规则测试即红已验证）；本地起服 export 后 0 条 403 日志 |

### 上一轮（08-03 白昼）已验证工作的补提交（第三轮复验确认后落库）
- `bf942f37` web 同步域：E2E-1/N-1/N-3/H-3/M-13/M-14/E2E-8/N-5/E2E-4
- `839e6c1e` web 加固域：M-2/M-5/M-6/M-8/N-4/M-3/M-4/L-5/L-6/T-3
- `92ed4a1c` 契约运维域：D-1/D-7/E2E-4/L-7/C-2/H-1/M-12/T-2
- `993ce1fe` 前端域：E2E-9/10/3/6 + category Int + T-1
- `c2e30a96` mock 语料：seed corpus + gen-corpus.mjs（r-asset：per-path 产物 gitignore）

### 验证门（提交前全绿）
- `:app:testAppReleaseDebugUnitTest` 全绿（含 W2 迁移/引擎测试、A3c 分片/校验测试）
- `:anotherviewer-web:test` 309/0（含 T-3 四个新用例）
- `web-frontend` typecheck clean + 450/450
- `node --test mock-server/test-sync.mjs test-gallery.mjs` 23/23

### 遗留
1. **E2E 基线待跑**（平板未在线，用户已同意押后）：dummy/mock 全流程真机 E2E → 接真实站点复测。
2. **Safari 实机复验** T-1（收藏/首页/下载列表行 + 详情 hero/段动画）。
3. **@Index 未落地**（H-3 派生查询已修，实体索引待 schema owner 统一）。
4. **M-12 docker 实测**（本机无 docker）。
5. **push 全量→增量**（B9）、OkHttp 升级（H-2）、targetSdk（H-4）、app↔core 去重：均冻结后另行评估。
