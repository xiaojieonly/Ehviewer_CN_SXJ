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
