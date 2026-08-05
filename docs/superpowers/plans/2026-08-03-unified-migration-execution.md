# 统一执行规范：接入真实 E-Hentai（de-neutralize + mock 移除）+ 原版 EhViewer 备份迁移

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development（推荐）。执行主体为强能力 AI（善于开子代理、并行处理、善用工具）。本规范定义两条独立工作线、多 subagent 并行编排规则、文件所有权矩阵与阶段门（gates）。步骤用 `- [ ]` checkbox 跟踪。
>
> **前提**：本项目处于"中立化（neutralize）"状态——站点 URL 均为 `gallery.test` 占位、mock-server 脚手架已随 E2E 提交入库（commit 93981bf3 之后）。本规范把主流程"接入真实 E-Hentai"与扩展任务"原版 EhViewer 备份迁移"合并为一份可执行计划，统一执行。

**Goal:** 移除 dummy 脚手架、全链路域名回切真实 E-Hentai（e-hentai.org / exhentai.org / ehgt.org）、恢复网络与安全语义；另提供从原版 EhViewer 导出 `.db` 迁移到本系统的能力（legacy 路径 + WebUI 导入端点）。

**架构:** 域名常量集中在 `SiteUrl`/`SiteHosts`/`SiteEngine`（app 与 anotherviewer-core 各一份）；站点主机判定谓词分散在 app 代理拦截器与 web SSRF 白名单；同步 wire 键 `showSite*` 需全端原子翻回 `showEh*`。备份迁移复用既有 legacy 包路径（零新端点）或新增 WebUI 导入端点。

---

## 0. 两条工作线总览（文件交集为零）

- **线 A（de-neutralize + mock 移除）**：域名回切、删除 mock 脚手架、SSRF 白名单翻回、深链/文案翻回、测试与文档清理。tickets A1–A9。
- **线 B（EhViewer 备份迁移）**：legacy 迁移路径文档化、importDB 书签修补、WebUI 导入端点 + 前端入口 + 测试。tickets B1–B5。

两线文件集合无重叠；可全程并行，仅共享最终验证 gate。

## 1. 范围（做 / 不做）

**做**：
- 域名常量回切（E 站 / EX 站 / H@H / forums / upld / s / lofi / repo），依据 git 历史 `4705badc` 的确切值（见 §3 契约锁定表）。
- 删除所有 mock 脚手架：`-PmockEhBaseUrl` buildConfigField、`SiteApplication.createMockSiteInterceptor`、`WebUiTier2ProxyInterceptor.isMockDebugActive`、`SpiderQueen` 的 mock 豁免、web `SiteSessionManager.mockRewriteInterceptor`、整个 `mock-server/` 目录。
- 恢复反劫持校验为发布形态（仅保留 Tier-2 浏览代理豁免）。
- web 端 SSRF 白名单 / 健康探测 / 代理探测翻回真实域。
- web 前端 `SITE_ASSET_DOMAIN` 翻回；`showSite*` → `showEh*` 全端原子翻回。
- 原版 EhViewer 备份迁移：B2 文档化既有 legacy 路径；B3 WebUI 导入端点（表驱动扫描 + 可选登录授权导入）。

**登录授权（cookies）迁移——关键事实与路径**：
- 事实：`/sdcard/EhViewer/data/*.db` 导出文件**只含 8 张 greenDAO 业务表，不含登录授权**（无 cookie 表；`strings` 无 `ipb_*`/`igneous` 命中）。登录态在 app 内部数据目录：`databases/okhttp3-cookie.db`（表 `OK_HTTP_3_COOKIE`，列 `_id, NAME, VALUE, EXPIRES_AT, DOMAIN, PATH, SECURE, HTTP_ONLY, PERSISTENT, HOST_ONLY`）+ `shared_prefs/`。**因此登录授权不走导出文件。**
- 路径 1（登录迁移主通道，零代码）：legacy 包覆盖安装（applicationId 参数化）→ app 内部数据目录原样保留 → `okhttp3-cookie.db`（含 `ipb_member_id`/`ipb_pass_hash`/`igneous`）+ shared_prefs 自动随包迁移。B1 文档化此路径。
- 路径 2（WebUI 导入端点可选段）：B3 支持可选上传 cookies（来自 okhttp3-cookie.db 或 JSON），按站点域写入 WebUI `SiteSessionManager.cookieStore`，使 WebUI 代理可带登录态抓取 EX 站。登录态**不进 sync 实体**（凭据安全 + 每设备独立）。
- 登录授权导入的安全约束：仅管理员鉴权端点接受；WebUI cookieStore 为会话级（进程内），重启丢失——B3 需明确"会话级导入"语义或增加持久化（另议，见 B3 风险）。

**所有记录（表驱动扫描）**：
- B3 的 `EhImportService` 按"表驱动扫描"实现：枚举 db 中实际存在的 EhViewer 表（非硬编码 8 张），对存在的表逐表映射；`PRAGMA table_info` 自适应缺列（v7 缺列落默认值）。覆盖：8 张核心业务表 → sync 实体；`Black_List` → WebUI `black_list`；`Gallery_Tags` → 拍平 `(gid, tag, namespace)` → WebUI `gallery_tags`；`DOWNLOAD_DIRNAME` → `download_dirname`。如此未来任何更复杂的导出文件都能被吸收。

**不做**：
- 偏好数据迁移（源 DB 无偏好表，无从迁移；shared_prefs 仅经 legacy 覆盖安装路径随包迁移，不进导入端点）。
- `Gallery_Tags`、`Black_List` 纳入**同步**（sync 实体保持 8 类）；但**导入**端点收容这两表（见上，导入 ≠ 同步）。
- 把 v7 裸 `.db` 直接替换 `anotherviewer.db`（Hibernate `ddl-auto:update` 对 NOT NULL 新列不可靠）。
- `cf_clearance` / Cloudflare 挑战处理（超出本规范，记为已知风险）。
- `DOWNLOAD_DIRNAME` 纳入 sync 实体（C1 列为可选后续，本期不做；仅导入端点收容）。

## 2. 多 subagent 并行编排规则（核心）

### 2.1 执行模型

- **coordinator**（执行主体大模型）：任务分发、文件所有权裁决、阶段门（Gate 1–3）编译/测试、跨模块集成冲突解决、聚合提交。
- **worker**（subagent）：每个 ticket 一个 worker，`implement` 语义——只改分配文件、本地验证、交回 `git diff + 验证证据 + 变更说明`。coordinator 审查后再进入下一阶段。
- **并行度上限**：Wave 1 最多 **9 路并行**；Wave 2 最多 **5 路并行**。单 worker 仅可串行处理其 ticket 内步骤，不得越权领取其他 ticket 文件。

### 2.2 文件所有权矩阵（单文件单 worker，冲突规避的硬规则）

| worker | 文件集合（唯一所有权） |
|---|---|
| A1 | `app/src/main/java/com/hippo/anotherviewer/client/SiteUrl.java`、`SiteHosts.java`、`SiteEngine.java`；`anotherviewer-core/src/main/java/com/hippo/anotherviewer/client/SiteUrl.java`、`SiteEngine.java`、`SiteUtils.java` |
| A2 | `app/build.gradle`；`app/src/main/java/com/hippo/anotherviewer/SiteApplication.java`；`app/src/main/java/com/hippo/anotherviewer/webui/WebUiTier2ProxyInterceptor.java`；`app/src/main/java/com/hippo/anotherviewer/spider/SpiderQueen.java`；`app/src/test/java/com/hippo/anotherviewer/webui/WebUiTier2ProxyInterceptorTest.java` |
| A3 | `anotherviewer-web/src/main/java/com/hippo/anotherviewer/web/service/SiteSessionManager.kt`、`ArchiveService.kt`、`TorrentService.kt`；`web/api/SiteProxyController.kt`、`ArchiveController.kt`、`HealthController.kt`、`ProxyController.kt` |
| A4 | `web-frontend/src/utils/siteAsset.ts`；`contracts/observability.md`；`web-frontend/e2e/playwright.drawer.config.ts` |
| A5 | `app/src/main/AndroidManifest.xml`；`app/src/main/res/values/strings.xml`；`app/src/main/res/values-ko/strings.xml` |
| A6 | `app/src/main/java/com/hippo/anotherviewer/webui/PreferenceSyncHelper.java`；`anotherviewer-web/src/main/java/com/hippo/anotherviewer/web/dto/PreferenceDto.kt`；`web-frontend/src/api/preferences.ts`；`web-frontend/src/views/settings/GeneralSettings.vue`；`contracts/openapi.yaml`（整文件） |
| A7 | `app/src/test/**`（除 A2 独占的 `WebUiTier2ProxyInterceptorTest.java`）——parser URL 测试 + 36 个 fixture 的域名翻回 + FavoritesParserTest 注释 |
| A8 | `anotherviewer-web/src/test/**`；`scripts/api-test.sh` |
| A9 | `mock-server/`（整目录删除）；`docs/MASTER-2026-08-03.md`、`docs/TODO-audit-2026-08-02.md`、`docs/audit-2026-08-02.md`、`docs/adr/0003-three-party-model-sync-policy.md` 的 mock 段清理 |
| B1 | `docs/deployment.md` |
| B2 | `app/src/main/java/com/hippo/anotherviewer/SiteDB.java`（仅 importDB 书签 TODO） |
| B3 | `anotherviewer-web/.../api/BackupController.kt`（或新 `api/ImportController.kt`）、新 `service/EhImportService.kt`、新 `dto/EhImportDto.kt`（表驱动扫描 + 可选 cookies 段） |
| B4 | 新增 web 测试（B3 配套）；`contracts/backup-format.md`（若存在）或 `contracts/openapi.yaml`（**仅在 A6 完成后再动 openapi.yaml**，二者串行，见 2.3） |
| B5 | `web-frontend/src/views/.../AdminBackup.vue`（导入入口；依赖 B3 REST 契约） |

> 例外声明：`contracts/openapi.yaml` 由 A6 独占；B4 如需改它，必须排在 A6 完成之后（串行段）。除此无重叠。

### 2.3 依赖图（DAG）与阻塞边

```
Gate 0 ─ 基线快照（当前全绿状态：bootJar + assembleDebug + app unit + web test + frontend + mock node test）
  │
Wave 1（9 路并行）: A1 ∥ A2 ∥ A3 ∥ A4 ∥ A5 ∥ A6 ∥ B1 ∥ B2 ∥ B3
  │   阻塞边：B3 的 REST 契约先锁定（供 B4/B5）；A2 内生产代码与测试原子完成（见 2.4 破坏态）
  ▼
Gate 1 ─ coordinator 统一编译：bootJar + assembleDebug + frontend build + web 编译（不跑 test）
  │
Wave 2（5 路并行）: A7 ∥ A8 ∥ A9 ∥ B4 ∥ B5
  │   依赖：A7←A1/A2；A8←A3；A9←A1..A6 定稿；B4/B5←B3 契约
  ▼
Gate 2 ─ 全量测试：app unit + web test + frontend 单测 + scripts/api-test.sh（web 需真站或代理可达，见 §6）
  │
Wave 3（coordinator 亲自，不委托）: 集成审查、真机 E2E（连通性依赖环境）、聚合提交、docs 执行记录
```

### 2.4 Wave 1 内的"破坏态"管理

- A2 删除 `BuildConfig.MOCK_EH_BASE_URL` 后，`WebUiTier2ProxyInterceptorTest` 的 `testMockDebugYieldGate` 会编译失败。**A2 必须在同一 ticket 内原子删除该方法**（该测试文件归 A2 独占，见 2.2）。
- Gate 1 不编译 test 源码，因此 Wave 1 内 `app/src/test` 的 URL 断言保持 `gallery.test` 属预期中间态，由 A7（Wave 2）统一翻回。
- A3 删除 web mock 拦截器后，`SiteSessionManagerTest` 若引用该拦截器需同步处理（归 A8）。

### 2.5 质量守则（worker 硬性约束）

1. **只改分配文件**；越界即视为失败交付。
2. 域名值**必须**取自 §3 契约锁定表，禁止臆造（如拿不准，worker 报"需核对"，不得自编）。
3. 每 worker 交付：`git diff --stat` + 该模块编译/单测证据 + 一句话变更说明。
4. 禁止改动 `CONTEXT.md`、`README.md`、根构建脚本、`.github/`、`azure-pipelines.yml`（不在任何所有权内）。
5. 翻译文案改动限 `values/strings.xml` 与 `values-ko/strings.xml`（既有语言，不新增语言）。
6. 注释/文档中 `gallery.test` 一并按相同映射处理，不留残值（`rg -n gallery.test` 最终应为 0 命中，除 git 历史外）。
7. 交叉审查：Wave 2 的 A7 负责核对 A1/A2 的实际 diff 与契约表一致（测试即审查）；A8 核对 A3。

### 2.6 提交策略

- 每阶段 gate 通过后由 coordinator 聚合提交，一个 ticket 一个 commit（message 含 ticket 号与波次）。
- 提交前 `git status` 只允许出现本波 worker 文件；异常文件一律不提交。
- Wave 3 最终提交后工作树应干净（除 session 导出等用户自留文件）。

## 3. 契约锁定表（域名回切唯一事实来源，来自 git 4705badc）

### 3.1 `SiteUrl.java`（app 与 anotherviewer-core 同款）

| 常量 | 当前（占位） | 翻回 |
|---|---|---|
| `DOMAIN_EX` | `"gallery.test"` | `"exhentai.org"` |
| `DOMAIN_E` | `"gallery.test"` | `"e-hentai.org"` |
| `DOMAIN_LOFI` | `"lofi.gallery.test"` | `"lofi.e-hentai.org"` |
| `API_SIGN_IN` | `https://forums.gallery.test/index.php?act=Login&CODE=01` | `https://forums.e-hentai.org/index.php?act=Login&CODE=01` |
| `URL_POPULAR_E` | `https://gallery.test/popular` | `https://e-hentai.org/popular` |
| `URL_POPULAR_EX` | `https://gallery.test/popular` | `https://exhentai.org/popular` |
| `URL_IMAGE_SEARCH_E` | `https://upld.gallery.test/image_lookup.php` | `https://upld.e-hentai.org/image_lookup.php` |
| `URL_IMAGE_SEARCH_EX` | `https://upld.gallery.test/upld/image_lookup.php` | `https://upld.exhentai.org/upld/image_lookup.php` |
| `URL_SIGN_IN` | `https://forums.gallery.test/index.php?act=Login` | `https://forums.e-hentai.org/index.php?act=Login` |
| `URL_REGISTER` | `https://forums.gallery.test/index.php?act=Reg&CODE=00` | `https://forums.e-hentai.org/index.php?act=Reg&CODE=00` |
| `DOMAIN_FORUMS` | `"forums.gallery.test"` | `"forums.e-hentai.org"` |
| `URL_FORUMS` | `https://forums.gallery.test/` | `https://forums.e-hentai.org/` |
| `URL_PREFIX_THUMB_E` | `https://gallery.test/` | `https://ehgt.org/` |
| `URL_PREFIX_THUMB_EX` | `https://gallery.test/t/` | `https://exhentai.org/t/` |

### 3.2 `SiteHosts.java`（内置 DNS 键恢复；IP 值不变）

`gallery.test`(E 站 CF IP) → `e-hentai.org`；`repo.gallery.test` → `repo.e-hentai.org`；`forums.gallery.test` → `forums.e-hentai.org`；`upld.gallery.test`(89.149/95.211) → `upld.e-hentai.org`；`gallery.test`(ehgt IP 折叠键) → `ehgt.org`；`gallery.test`(EX IP 折叠键) → `exhentai.org`；`upld.gallery.test`(178.175) → `upld.exhentai.org`；`s.gallery.test` → `s.exhentai.org`。另恢复缺失键 `raw.githubusercontent.com`（更新检查用，4705badc 行 80）。

### 3.3 `SiteEngine.java`（app 与 core 同款）

`KOKOMADE_URL` → `https://exhentai.org/img/kokomade.jpg`；sign-in 流程 `referer = https://forums.e-hentai.org/index.php?act=Login&CODE=00`、`origin = https://forums.e-hentai.org`；igneous 判定 `url.equals("https://exhentai.org/")`。`SiteUtils.java:16` 的 `replace("gallery.test","gallery.test")` 改回 `replace("ehgt.org","ehgt.org")`。

### 3.4 app 运行时

- `SiteApplication.createMockSiteInterceptor`/`isMockSiteHost`/`rewriteMockSiteUrl` 整段删除，`:478/:569` 两处 `.addInterceptor(...)` 注册删除。
- `WebUiTier2ProxyInterceptor.isMockDebugActive` 删除；路由 gate 改为仅 `isGallerySiteHost(host) && clientTier>=2`。`isGallerySiteHost` 谓词翻回为：`host.equals("exhentai.org")||host.equals("e-hentai.org")||host.equals("lofi.e-hentai.org")||host.equals("ehgt.org")||host.endsWith(".exhentai.org")||host.endsWith(".e-hentai.org")||host.endsWith(".ehgt.org")`。
- `SpiderQueen.java:1421` 反劫持恢复为 `if (!WebUiTier2ProxyInterceptor.isRoutingActive(mWebUiSettings) && !targetImageUrl.equals(responseUrl))`（删除 `BuildConfig.MOCK_EH_BASE_URL.isEmpty() &&`）。
- `app/build.gradle`：删除 release `:64-66` 与 debug `:71-73` 的 `MOCK_EH_BASE_URL` buildConfigField 及其注释（`-PmockEhBaseUrl` 通路消失）。
- `AndroidManifest.xml:151-155` VIEW intent-filter 主机 → `exhentai.org` / `e-hentai.org` / `g.e-hentai.org` / `lofi.e-hentai.org`。
- `strings.xml:64` `site_ex` → `"exhentai"`；`:217` `select_scene_explain` → `"exhentai: ..."`（values-ko `:156` 同款）。

### 3.5 web 服务端

- `SiteSessionManager.kt`：删除 `@Value("${anotherviewer.gallery.mock-base-url:}")` 字段、`mockRewriteInterceptor()`、`addInterceptor` 注册。
- `SiteProxyController.kt:67-68` SSRF 白名单 → 与 3.4 同款谓词（含 lofi/ehgt 子域）；`:44` 错误文案改真实域名族。
- `ArchiveService.kt:136-138`、`ArchiveController.kt:63-65` → `normalized == "e-hentai.org" || normalized == "exhentai.org"`。
- `TorrentService.kt:85-90` → `"ehtracker.org" || "e-hentai.org" || "exhentai.org"`。
- `HealthController.kt:177` 探测 → `URI.create("https://e-hentai.org")`。
- `ProxyController.kt:46` → `https://e-hentai.org/`。

### 3.6 web 前端 + wire 键

- `siteAsset.ts:18` `SITE_ASSET_DOMAIN = 'gallery.test'` → 真实站点域（E 站：`e-hentai.org`；`isSiteAssetUrl` 谓词同步，覆盖 e-hentai/exhentai/ehgt 子域，与 3.4 谓词一致）。
- **wire 键决策**：`showSiteEvents`/`showSiteLimits` → `showEhEvents`/`showEhLimits`，全端原子翻回（`PreferenceSyncHelper.java`、`PreferenceDto.kt`、`preferences.ts`、`GeneralSettings.vue`、`openapi.yaml`）。任一处遗漏即严格 Jackson 反序列化 500——A6 必须一次提交完成五处。

### 3.7 测试翻回规则

- app 测试 fixture 域名映射（A7）：`lofi.gallery.test→lofi.e-hentai.org`、`upld.gallery.test→upld.e-hentai.org|upld.exhentai.org`（按 fixture 上下文）、`g.gallery.test→g.e-hentai.org`、`s.gallery.test→s.exhentai.org`、裸 `gallery.test` → 站内页（`/g/`、`/popular` 等）映射 `e-hentai.org`（E 站 fixture）或 `exhentai.org`（EX 站 fixture）；缩略图（path `/t/` 或 `/w/`）按 3.1 `URL_PREFIX_THUMB_E/EX` 映射。
- A7/A8 验证手段：改完即跑对应 parser/控制器测试；挂则按测试约束微调映射，不得为过测试而放宽断言。
- 目标：全仓 `rg -n gallery.test` 命中归零（测试、资源、文档全算）。

## 4. 线 A tickets（de-neutralize + mock 移除）

### A1: 核心域名常量回切

**Files:** §2.2 A1 行。**依赖:** 无。
- [ ] 按 §3.1/3.2/3.3 翻回 app 与 core 两份 `SiteUrl`/`SiteHosts`/`SiteEngine`/`SiteUtils`。
- [ ] 验证：两模块编译通过；`rg -n gallery.test` 在 app/src/main、anotherviewer-core/src/main 归零。
- [ ] commit：`fix(site): restore real E-Hentai host constants (de-neutralize)`。

### A2: app mock 脚手架移除 + 反劫持恢复

**Files:** §2.2 A2 行（含测试文件原子处理）。**依赖:** 无（值取契约表）。
- [ ] build.gradle 删两个 buildConfigField；SiteApplication 删拦截器方法及两处注册；WebUiTier2ProxyInterceptor 删 mock gate、翻回谓词；SpiderQueen 删 mock 豁免；WebUiTier2ProxyInterceptorTest 删 `testMockDebugYieldGate` 并翻回 URL 断言。
- [ ] 验证：`assembleDebug`（无 -P 参数）+ 该测试文件编译；确认 `BuildConfig.MOCK_EH_BASE_URL` 全仓引用归零。
- [ ] commit：`fix(app): remove mock scaffolding, restore anti-hijack check`。

### A3: web 服务端翻回

**Files:** §2.2 A3 行。**依赖:** 无。
- [ ] 删 `SiteSessionManager` mock 拦截器；四个 SSRF 白名单 + Health/Proxy 探测按 §3.5 翻回。
- [ ] 验证：`bootJar` 编译通过；`SiteSessionManagerTest`/`SiteProxyControllerTest` 等本模块测试暂以 gallery.test 断言者可保留至 A8，但 mock 拦截器引用必须删除。
- [ ] commit：`fix(web): restore real-site SSRF allowlists and probes`。

### A4: 前端资产域 + 契约文档

**Files:** §2.2 A4 行。**依赖:** 无。
- [ ] `siteAsset.ts` 按 §3.6 翻回；`observability.md:313`、playwright config 注释同步。
- [ ] 验证：frontend 单测（siteAsset.spec.ts）通过。
- [ ] commit：`fix(web-frontend): restore site asset domain`。

### A5: 深链 + 文案

**Files:** §2.2 A5 行。**依赖:** 无。
- [ ] Manifest VIEW hosts + strings.xml/values-ko 按 §3.4 翻回。
- [ ] 验证：`assembleDebug` 编译通过（Manifest 变更不引入新依赖）。
- [ ] commit：`fix(app): restore deep-link hosts and site labels`。

### A6: wire 键 showSite* → showEh*

**Files:** §2.2 A6 行（五处原子）。**依赖:** 无。
- [ ] 一次提交改完五处；验证前后端各自编译 + frontend settings 单测 + web 反序列化测试。
- [ ] commit：`fix(sync): restore showEh* wire keys across stack`。

### A7: app 测试与 fixture 翻回

**Files:** §2.2 A7 行。**依赖:** A1、A2。
- [ ] 按 §3.7 映射翻回 parser URL 测试与 36 个 fixture（约 3502 处）；FavoritesParserTest 注释改为"冻结历史抓取快照"。
- [ ] 验证：`./gradlew :app:testDebugUnitTest` 全绿。
- [ ] commit：`test(app): align parser fixtures with real hosts`。

### A8: web 测试与 api-test 翻回

**Files:** §2.2 A8 行。**依赖:** A3。
- [ ] anotherviewer-web/src/test 的 gallery.test 断言翻回；`scripts/api-test.sh:199-201` proxy/test 用例改为真域名断言（成功/失败语义随 ProxyController 目标调整，见 §6 网络前提）。
- [ ] 验证：web test 全绿；`scripts/api-test.sh` 在 web 可达真站时通过（不可达则按 §6 记录）。
- [ ] commit：`test(web): align specs with real hosts`。

### A9: mock 目录删除 + 文档清理

**Files:** §2.2 A9 行。**依赖:** A1..A6 定稿。
- [ ] `git rm -r mock-server`；清理 docs 中 mock 启动命令/门/承诺（MASTER、TODO-audit、audit、adr/0003）。
- [ ] 验证：`rg -n "mock-server|mockEhBaseUrl|MOCK_EH_BASE_URL|4100" docs contracts` 归零（除 git 历史）。
- [ ] commit：`chore(mock): remove mock-server and stale docs`。

## 5. 线 B tickets（EhViewer 备份迁移）

> 迁移数据源（真实用户数据，用户自备）：`/var/folders/19/.../opencode/ehviewer-backup/2026-08-02-11-36-05-662.db`（v7；DOWNLOADS 8999、DOWNLOAD_DIRNAME 23577、HISTORY 100、FILTER 388、其余空）。结构与本 app 的 v8 表同构（exportDB 机制产物）。

### B1: legacy 迁移路径文档化

**Files:** `docs/deployment.md`。**依赖:** 无。
- [ ] 在包名迁移小节（约 :217）后新增"从原版 EhViewer 迁移"小节：旧 app 导出 .db → legacy 包（`-PapplicationId=com.xjs.anotherviewer`）覆盖安装 → 设置→高级→导入数据 → 手动同步推 → 新包名配对拉全量。注明：importDB 自动 v7→v8 升级、下载进度落 0（STATE 保留）、书签依赖 B2、偏好不迁移。
- [ ] **登录授权迁移**：在同一小节写明——登录态（`okhttp3-cookie.db` 的 `OK_HTTP_3_COOKIE` 表 + shared_prefs）**不随导出文件走**，但覆盖安装 legacy 包时随 app 内部数据目录原样保留，登录态自动迁移；导入 .db 只迁业务表，不会清空/覆盖已保留的登录态。
- [ ] commit：`docs: legacy EhViewer data migration path (incl. sign-in cookies)`。

### B2: importDB 书签 TODO

**Files:** `app/src/main/java/com/hippo/anotherviewer/SiteDB.java`（约 :1235）。**依赖:** 无。
- [ ] 补实现 importDB 中书签导入（参照 `copyDao` 对 BOOKMARKS DAO 的拷贝循环，与其余 7 类一致）。
- [ ] 验证：`app/src/test` 中如有 importDB 相关测试则更新/新增一条"含书签的 v8 db 导入"用例。
- [ ] commit：`fix(app): import bookmarks in importDB`.

### B3: WebUI 导入端点（新，表驱动扫描 + 可选登录授权）

**Files:** §2.2 B3 行。**依赖:** 无（契约先锁定供 B4/B5）。
- [ ] 新增 `POST /api/v1/backup/import-ehviewer`：multipart 上传 `.db`（可选第二字段 `cookies`，见下）；鉴权同 backup/restore（Bearer + 管理员）。契约（锁定，供 B4/B5 消费）：
  - Request: multipart `file`（SQLite db）；可选 multipart `cookies`（SQLite cookie db，即 okhttp3-cookie.db，或 JSON 数组 `[{name,value,expiresAt,domain,path,secure,httpOnly,persistent,hostOnly}]`）。
  - Response 200: `{ "success": true, "imported": { "downloads": n, "history": n, "filters": n, "quickSearches": n, "labels": n, "bookmarks": n, "favorites": n, "dirnames": n, "blackList": n, "galleryTags": n }, "cookies": { "imported": n, "siteDomain": n }, "skipped": m }`；`skipped` = gid 冲突跳过的下载/历史数。
  - 参数 `?force=true` 时 gid 冲突 upsert（默认跳过）。
- [ ] 实现 `EhImportService` **表驱动扫描**：JDBC（复用 sqlite-jdbc）→ `PRAGMA table_info` 感知列集 → 对 db 实际存在的表逐表映射（8 核心表 → sync 实体；`Black_List` → `black_list`；`Gallery_Tags` → 拍平 `(gid, tag, namespace)` → `gallery_tags`；`DOWNLOAD_DIRNAME` 含孤儿 → `download_dirname`）→ 事务写入；缺列落默认值；进度字段落 0，lastModified 用 `TIME` 列；用户归属绑定当前认证用户。
- [ ] **登录授权导入**（cookies 段）：解析 `OK_HTTP_3_COOKIE` 表/JSON → 仅保留站点域（`.e-hentai.org` / `.exhentai.org` / `.ehgt.org` / `.forums.e-hentai.net` 及子域，与 §3.4 谓词一致）→ 写入 `SiteSessionManager.cookieStore`（会话级；若 web 端有 cookie 持久化则一并落库，否则明确标注重启失效）。忽略非站点域 cookie。
- [ ] 验证：`bootJar` 编译；用真实 v7 db + 构造的 cookie 场景本地实测端点（见 §6）。
- [ ] commit：`feat(web): import legacy EhViewer db endpoint (all records + sign-in cookies)`。

### B4: 导入端点测试 + 契约

**Files:** 新增 web 测试；`contracts/openapi.yaml`（**须在 A6 之后**，否则与 A6 冲突）。**依赖:** B3、A6（若动 openapi）。
- [ ] `EhImportServiceTest`：临时 db 构造 v7 样张（下载+过滤+dirname 孤儿+Black_List+Gallery_Tags）断言映射正确性、缺列默认值、冲突跳过/upsert、事务回滚。
- [ ] 登录授权导入用例：构造 `OK_HTTP_3_COOKIE` 样张（含站点域与非站点域 cookie）断言只导入站点域、写入 cookieStore。
- [ ] openapi.yaml 增加 import-ehviewer 定义（若 A6 已提交）。
- [ ] commit：`test(web): ehviewer import endpoint (records + cookies)`。

### B5: 前端导入入口

**Files:** `web-frontend/src/views/.../AdminBackup.vue`（备份/恢复页）。**依赖:** B3 契约。
- [ ] 增加"导入 EhViewer 备份"控件：选 `.db`（+ 可选 cookie 文件）→ POST → 展示 imported/skipped/cookies 计数。
- [ ] 验证：frontend 构建 + 手动用例（可降级为 curl 验证）。
- [ ] commit：`feat(web-frontend): ehviewer import entry (records + cookies)`。

## 6. 统一验证方案与网络前提

**代码正确性验证**（与网络无关，必过）：
- `./gradlew :app:testDebugUnitTest`（A7 后全绿）
- `./gradlew :anotherviewer-web:test`（A8 后全绿）
- `./gradlew :app:assembleDebug`（不带 -PmockEhBaseUrl）与 `:anotherviewer-web:bootJar`
- frontend 单测 + 构建
- `rg -n "gallery.test|MOCK_EH_BASE_URL|mockEhBaseUrl|4100" app anotherviewer-core anotherviewer-web web-frontend contracts docs` 命中归零

**真实站点连通性（不阻塞决策，2026-08-03 实测更新）**：
- 直连不可达（TCP 超时）；经用户代理 `http://127.0.0.1:10808` 实测 e-hentai.org 451、exhentai.org 302、ehgt.org 403、forums 403——受 Cloudflare 地域策略限制，当前环境无法稳定抓取。**网络验证不阻塞本计划的架构搭建与代码正确性验证。**
- **决策**：不再部署 dummy/mock 测试环境；正确性验证统一走编译 + 单测 + 静态检查（`rg` 归零）+ 契约测试。真机/真实站点的端到端验证在网络可用时补充执行（用户可在可达环境或经可用代理进行），原版 EhViewer 访问管线（UA/cookie/反劫持）继承自上游，代码层正确性由测试保证。
- 因此 Wave 3 的"真站 E2E"降级为"可选补充验证"，不再作为验收门；`scripts/api-test.sh` 的 proxy/test 用例改为断言"端点契约正确"而非"真实站点可达"。

**迁移端点实测**（不需真站）：
- 本地起 `bootJar`（临时 data-dir）→ curl 上传真实 v7 db → 断言 imported 计数（downloads 8999、filters 388、history 100、dirnames 14578 孤儿）→ `sqlite3 anotherviewer.db` 抽查 `download_info`/`filter_info`/`download_dirname` 行。
- 登录授权场景：构造含站点域 + 非站点域 cookie 的样张上传 → 断言只导入站点域、`SiteSessionManager.cookieStore` 可见 `ipb_member_id`/`ipb_pass_hash`/`igneous`（会话级，进程内验证）。

## 7. 已知风险

1. **网络不可达**（最大外部风险）：站点抓取/登录/健康探测在无代理网络下必然失败；代码正确性不受影响，但端到端"接真实站"验收依赖用户环境。
2. **fixture 域名映射歧义**：A7 的 3502 处替换中缩略图/E 站-EX 站归类需按 fixture 上下文判断，映射错误会挂 parser 测试——以测试结果反向修正。
3. **openapi.yaml 双写**：A6 与 B4 都可能改它，串行约束见 2.3；coordinator 须在 Gate 2 前核对无覆盖。
4. **cf_clearance / Cloudflare 挑战**：仓库无处理逻辑，真实登录可能被 CF 拦（超出本期范围）。
5. **importDB 导入覆盖**：B2 补书签后，旧库书签可能与现有冲突——采用 insertOrReplace（greenDAO 默认），与现有导入行为一致。

## 8. 执行顺序建议（统一执行时）

1. coordinator 跑 **Gate 0** 基线，固化当前 commit 与全绿证据。
2. 依次派发 **Wave 1（9 路并行）**；每 worker 返回后审查，必要时让 worker 自证（编译/测试命令输出）。
3. **Gate 1** 统一编译；**Wave 2（5 路并行）**。
4. **Gate 2** 全量测试（排除网络依赖项）；**Wave 3** coordinator 亲验迁移端点 + 真机（网络可用时）+ 聚合提交 + 更新 `docs/MASTER-2026-08-03.md` 执行记录。
5. 最终交付：干净工作树 + 每 ticket 一个 commit + 验证报告。
