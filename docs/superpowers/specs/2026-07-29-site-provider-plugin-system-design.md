# AnotherViewer 站点插件系统 — 设计规格文档

> 状态：v3.0 — Tachiyomi 风格多源聚合 + Profile 用户态隔离（现行方案，§35 起）
> 创建：2026-07-29（v1.0）／ 评审：2026-07-29（v1.1）／ 重构多源：2026-07-29（v2.0）／
>       讨论本体/模块责任：2026-07-29（v3.0 现行）
> 分支：BiLi_PC_Gamer
> 关联：`docs/webui-progress.md` §I5 运维阻断事件
>
> ── v3.0 摘要（与 v2.0 关键差异）──
> v2.0 是"Site 单源、单一 Provider 实现"的过渡模型。v3.0 重新定位 AnotherViewer：
> • **不再是 E-Hentai 客户端，而是 Tachiyomi 风格的多源聚合器**——本体不实现任何站点逻辑；
> • **站点逻辑以 Source Extension APK 形式分发**（独立 apk，Bound Service + AIDL 跨进程调用）；
> • **数据契约收敛为三段** Manga → Chapter → Page（继承 Tachiyomi 习惯）；
> • **Profile 用户态完全隔离**（DB/Cookies/缓存/下载按 profile 切分），R18 Profile 仅隐藏入口、无认证；
> • SFW 默认 Profile 下根本看不到 R18 Source 列表（安全屏障 = 不可见，而非不可达）。
>
> v1.0 §1～§12 / v1.1 §13～§22 / v2.0 §23～§34 保留为历史沿革；现行方案以 v3.0 §35 起为准，
> 与上文冲突一律以 v3.0 为准。决策记录见 §46。

---

## 1. 背景与动机

### 1.1 问题陈述

当前项目代码库中，E-Hentai 平台相关代码（EhEngine、EhUrl、解析器、数据模型等）与通用代码（工具类、网络层、下载引擎）**混合在 `ehviewer-core` 模块中**。这导致：

1. **LLM 内容审核阻塞** — Qwen/DeepSeek 等模型在开发其他组件（前端 UI、同步模块、后端服务）时，加载项目上下文会触及 E-Hentai adult 内容代码，触发 `data_inspection_failed` 审核拦截（实例参见 `docs/webui-progress.md` §I5 运维阻断事件）。

2. **站点替换困难** — 更换接入站点（如切换到其他画廊平台）需要修改核心代码而非插件式添加。

3. **命名混乱** — 包名仍为 `com.hippo.ehviewer`，模块名为 `ehviewer-core`，与实际项目名 AnotherViewer 不一致。

### 1.2 目标

- **将 E-Hentai 内容敏感代码隔离到独立 Gradle 模块**，LLM 开发其他组件时可不加载该模块
- **定义 `SiteProvider` 插件接口**，后续更换站点只需实现新 Provider
- **全项目改名 AnotherViewer**（包名 `com.pegionfish.anotherviewer`）
- **平台特定 UI（如 E-Hentai 设置页）不嵌入主 UI**，通过设置页的"平台"Tab 动态加载

### 1.3 非目标

- 本 Stage 不实现 OSGi 级别的热加载插件系统
- 不引入新的站点后端（仅完成架构改造）
- 不改变现有前端功能逻辑，仅增加设置入口

---

## 2. 设计原则

| 原则 | 说明 |
|------|------|
| **渐进迁移** | 移动代码不改逻辑，零行为回归 |
| **一石多鸟** | 模块拆分与包名重命名合并执行 |
| **命名区分** | E-Hentai 兼容代码保留 `Eh*` 前缀，通用复用代码去除该前缀 |
| **最小侵入** | 新增 SPI 接口不影响现有编译 |
| **隔离优先** | LLM 可只加载 `anotherviewer-core` 而不触发内容审核 |

---

## 3. 模块架构

```
anotherviewer/                              (rootProject.name)

├── anotherviewer-core/                     ★ LLM 安全模块
│   package: com.pegionfish.anotherviewer
│   依赖: okhttp3, jsoup
│   无 E-Hentai 内容
│   ├── site/           SPI 接口 (SiteProvider, ProviderContext, ProviderSettingsPage)
│   ├── model/          通用数据模型 (UnifiedGalleryInfo, UnifiedSearchQuery 等)
│   ├── util/           通用工具 (TextUtil, Pair, TagTranslationUtil)
│   ├── network/        通用网络层 (CookieStore, 原 EhCookieStore)
│   ├── dao/            数据库对象 (DownloadInfo, QuickSearch)
│   └── spider/         通用下载引擎 (SpiderQueen — 后续改为接收 SiteProvider)
│
├── anotherviewer-provider-ehentai/         ★ E-Hentai 插件（内容隔离）
│   package: com.pegionfish.anotherviewer.provider.ehentai
│   依赖: anotherviewer-core
│   ├── EhentaiSiteProvider.kt       SiteProvider 实现（适配器模式包装 EhEngine）
│   ├── EhentaiSettingsPage.kt       平台设置页定义
│   ├── engine/                      EhEngine.java (1430行), EhUrl.java (318行)
│   │   EhRequestBuilder, EhConfig, EhFilter, EhUtils,
│   │   EhCacheKeyFactory, EhTagDatabase, EhClient (Task接口)
│   ├── parser/                      22 HTML 解析器
│   │   GalleryListParser, GalleryDetailParser, GalleryPageParser,
│   │   GalleryPageApiParser, FavoritesParser, SignInParser,
│   │   ArchiveParser, TorrentParser, TopListParser, EhHomeParser,
│   │   ProfileParser, RateGalleryParser, VoteCommentParser,
│   │   GalleryTokenApiParser, GalleryApiParser, GalleryDetailUrlParser,
│   │   GalleryListUrlParser, GalleryPageUrlParser, MyTagLitParser,
│   │   ForumsParser, EhEventParse, ParserUtils
│   ├── data/                        23 E-Hentai 数据 POJO
│   │   GalleryInfo, GalleryDetail, GalleryComment, GalleryCommentList,
│   │   GalleryPreview, GalleryTagGroup, Tag, ArchiverData, TorrentInfo,
│   │   ListUrlBuilder, FavListUrlBuilder, PreviewSet, NormalPreviewSet,
│   │   LargePreviewSet, GalleryApiInfo, ...
│   └── exception/                   E-Hentai 异常
│       EhException, ParseException, CancelledException, NoHAtHClientException
│
├── anotherviewer-web/                     Spring Boot 后端
│   package: com.pegionfish.anotherviewer.web
│   编译: compileOnly anotherviewer-core (仅 SPI 接口)
│   运行: runtimeOnly anotherviewer-provider-ehentai (默认插件)
│   ├── api/         REST Controllers
│   ├── service/     业务逻辑 (通过 SiteProviderRegistry.getActive() 访问站点)
│   ├── config/      注入 Provider 实例
│   └── dto/         响应 DTO
│
├── app/                                   Android 端
│   applicationId: com.xjs.anotherviewer
│   依赖: anotherviewer-core + anotherviewer-provider-ehentai
│   改动: 全局 import 路径调整为 .provider.ehentai
│
└── web-frontend/                          Vue 3 前端（不变）
    改动: SettingsView.vue 新增"平台"Tab
```

---

## 4. SiteProvider SPI 接口定义

### 4.1 核心接口

```kotlin
// 文件: anotherviewer-core/src/main/java/com/pegionfish/anotherviewer/site/SiteProvider.kt
package com.pegionfish.anotherviewer.site

interface SiteProvider {
    /** 唯一标识符，如 "e-hentai" */
    val siteId: String

    /** 人类可读名称 */
    val displayName: String

    /** 平台设置页定义（设置 → 平台 → E-Hentai 设置） */
    val settingsPage: ProviderSettingsPage

    // ── 认证 ──
    suspend fun authenticate(input: Map<String, String>): AuthResult
    suspend fun validateSession(session: String): Boolean
    suspend fun revokeSession(session: String)

    // ── 画廊 ──
    suspend fun search(query: UnifiedSearchQuery): UnifiedSearchResult
    suspend fun getDetail(gid: Long, token: String): UnifiedGalleryDetail
    suspend fun getImageUrl(gid: Long, token: String, page: Int): String
    suspend fun getGalleryPages(gid: Long, token: String): List<PageInfo>
    suspend fun getPopular(page: Int): UnifiedSearchResult
    suspend fun getTopList(page: Int): UnifiedSearchResult
    suspend fun getHome(page: Int): UnifiedSearchResult

    // ── 收藏 ──
    suspend fun getFavorites(slot: Int, page: Int): List<UnifiedFavorite>
    suspend fun addFavorite(gid: Long, token: String, slot: Int): Boolean
    suspend fun removeFavorite(gid: Long, token: String, slot: Int): Boolean

    // ── 评论 ──
    suspend fun getComments(gid: Long): List<UnifiedComment>
    suspend fun postComment(gid: Long, text: String): Boolean
    suspend fun voteComment(gid: Long, commentId: Long, vote: Int): Boolean

    // ── 下载/种子/归档 ──
    suspend fun getTorrents(gid: Long, token: String): List<UnifiedTorrent>
    suspend fun getArchives(gid: Long, token: String): List<UnifiedArchive>
    suspend fun downloadArchive(gid: Long, token: String, archiver: String): java.io.File

    // ── 个人 ──
    suspend fun getProfile(): UnifiedProfile
    suspend fun getMyTags(): List<UnifiedUserTag>
}
```

### 4.2 注册表

```kotlin
// SiteProviderRegistry.kt
package com.pegionfish.anotherviewer.site

object SiteProviderRegistry {
    private val providers = ConcurrentHashMap<String, SiteProvider>()
    private val active = AtomicReference<String>()

    fun register(provider: SiteProvider)
    fun discover()                           // ServiceLoader 自动发现
    fun get(siteId: String): SiteProvider?
    fun getActive(): SiteProvider
    fun getAll(): Collection<SiteProvider>
    fun setActive(siteId: String)
}
```

### 4.3 运行上下文

```kotlin
// ProviderContext.kt
data class ProviderContext(
    val httpClient: OkHttpClient,
    val cacheDir: Path,
    val configStore: ProviderConfigStore,  // 键值持久化存储
)
```

### 4.4 平台设置页模型

```kotlin
// ProviderSettingsPage.kt
data class ProviderSettingsPage(
    val pageId: String,
    val pageTitle: String,                  // 如 "E-Hentai 设置"
    val icon: String,                       // 图标标识
    val sections: List<SettingsSection>,    // 设置分区
)

data class SettingsSection(
    val sectionTitle: String,
    val fields: List<SettingField>,
)

data class SettingField(
    val key: String,                        // 配置键
    val label: String,                      // 显示标签
    val type: SettingType,                  // TEXT / PASSWORD / TEXTAREA / SELECT / SWITCH
    val defaultValue: String?,
    val options: List<String>?,             // SELECT 类型的选项
    val required: Boolean = false,
)
```

### 4.5 通用数据模型

```kotlin
// model/UnifiedSearchQuery.kt
data class UnifiedSearchQuery(
    val keyword: String?,
    val category: Int = 0,
    val page: Int = 0,
    val pageSize: Int = 20,
    val advancedFilters: Map<String, String> = emptyMap(),
)

// model/UnifiedSearchResult.kt
data class UnifiedSearchResult(
    val galleries: List<UnifiedGalleryInfo>,
    val total: Int,
    val currentPage: Int,
)

// model/UnifiedGalleryInfo.kt
data class UnifiedGalleryInfo(
    val gid: Long,
    val token: String,
    val title: String,
    val titleJpn: String?,
    val thumb: String,
    val category: Int,
    val posted: String,
    val uploader: String,
    val rating: Float,
    val pages: Int,
)

// model/UnifiedGalleryDetail.kt
data class UnifiedGalleryDetail(
    val info: UnifiedGalleryInfo,
    val tags: List<TagGroup>,
    val imageUrl: String,
    val favoriteSlot: Int,                  // -1 表示未收藏
)

// model/AuthResult.kt
data class AuthResult(
    val success: Boolean,
    val session: String?,                   // session/cookie token
    val error: String?,
)
```

> 注：完整模型（UnifiedComment, UnifiedFavorite, UnifiedTorrent, UnifiedArchive, UnifiedProfile 等）定义见实施时的具体文件。

---

## 5. 命名规则

### 5.1 包名替换

| 项 | 旧值 | 新值 |
|----|------|------|
| Java/Kotlin 基础包 | `com.hippo.ehviewer` | `com.pegionfish.anotherviewer` |
| Provider 子包 | — | `com.pegionfish.anotherviewer.provider.ehentai` |
| Android applicationId | `com.xjs.ehviewer` | `com.xjs.anotherviewer` |
| Gradle rootProject | `ehviewer` | `anotherviewer` |
| Gradle 模块名 | `ehviewer-core` | `anotherviewer-core` |
| Gradle 模块名 | `ehviewer-web` | `anotherviewer-web` |
| Web 前端项目名 | `ehviewer-web-frontend` | 保持不变 |

### 5.2 类名规则

| 规则 | 适用范围 | 示例 |
|------|---------|------|
| **`Eh*` 前缀保留** | `anotherviewer-provider-ehentai/` 内所有文件 | `EhEngine`, `EhUrl`, `EhConfig`, `EhFilter` |
| **通用代码去 `Eh*`** | `anotherviewer-core/` 中非 E-Hentai 特有的文件 | `EhCookieStore`→`CookieStore`，`EhCoreConfig`→`CoreConfig` |
| **新增 SPI 用 AnotherViewer 语义** | `site/` 包内 | `SiteProvider`, `ProviderContext`, `ProviderSettingsPage` |

---

## 6. 文件迁移清单

### 6.1 从 core 迁移至 provider-ehentai

```
源目录: ehviewer-core/.../com/hippo/ehviewer/client/
目标目录: provider-ehentai/.../com/pegionfish/anotherviewer/provider/ehentai/

engine/ (原 client/ 主目录)
├── EhEngine.java                1430 行 — 核心 API 引擎
├── EhUrl.java                    318 行 — URL 构建
├── EhRequestBuilder.java         HTTP 请求构建
├── EhConfig.java                 分类/评分常量
├── EhFilter.java                 画廊过滤逻辑
├── EhUtils.java                  工具方法
├── EhCacheKeyFactory.java        缓存键
├── EhTagDatabase.java            标签数据库
└── EhClient.java                 Task 接口定义

parser/ (22 文件)
├── GalleryListParser.java
├── GalleryDetailParser.java
├── GalleryPageParser.java
├── GalleryPageApiParser.java
├── FavoritesParser.java
├── SignInParser.java
├── ArchiveParser.java
├── TorrentParser.java
├── TopListParser.java
├── EhHomeParser.java
├── ProfileParser.java
├── RateGalleryParser.java
├── VoteCommentParser.java
├── GalleryTokenApiParser.java
├── GalleryApiParser.java
├── GalleryDetailUrlParser.java
├── GalleryListUrlParser.java
├── GalleryPageUrlParser.java
├── MyTagLitParser.java
├── ForumsParser.java
├── EhEventParse.java
└── ParserUtils.java

data/ (23 文件)
├── GalleryInfo.java
├── GalleryDetail.java
├── GalleryComment.java
├── GalleryCommentList.java
├── GalleryPreview.java
├── GalleryTagGroup.java
├── Tag.java
├── ArchiverData.java
├── TorrentInfo.java
├── TorrentDownloadMessage.kt
├── ListUrlBuilder.java
├── FavListUrlBuilder.java
├── PreviewSet.java
├── NormalPreviewSet.java
├── LargePreviewSet.java
├── GalleryApiInfo.java
├── EhTopListDetail.java
├── EhTopListInfo.java
├── HomeDetail.java
├── EhNewsDetail.java
├── NewVersion.java
├── userTag/  (UserTag, UserTagList, TagPushParam)
└── wifi/  (WiFiDataHand, ...)

exception/
├── EhException.java
├── ParseException.java
├── CancelledException.java
└── NoHAtHClientException.java
```

### 6.2 留在 core 并改名

| 旧文件 | 新文件 | 改名理由 |
|--------|--------|---------|
| `EhCoreConfig.java` | `CoreConfig.kt` | 通用配置 |
| `EhCookieStore.java` | `CookieStore.kt` | 通用 Cookie 管理（OkHttp 级别，与站点无关） |
| `EhDB.java` | `DataStore.kt` | 数据辅助（拆出 E-Hentai 特定方法移入 provider） |
| `Settings.java` | `Settings.kt` | 通用设置（拆出 E-Hentai 特有常量到 provider） |
| `AppConfig.java` | `AppConfig.kt` | 应用级配置 |
| `Analytics.java` | `Analytics.kt` | 分析（如果包含 E-Hentai 特定指标则拆分） |
| `GetText.java` | `GetText.kt` | 文本工具（Android R 资源引用需适配） |
| `EhApplication.java` | 移入 app/ 或提取基类 | Android 特定，不属于纯 core 库 |
| `R.java` | `R.kt` | 保留非 E-Hentai 资源引用 |

### 6.3 SpiderQueen 适配

`SpiderQueen.java` 位于 `app/src/main/java/.../spider/`（不在 core），它直接调用 `EhEngine.doGetGalleryPage()` 和 `EhEngine.doGetImageUrl()`。

**改动方案：** SpiderQueen 接收 `SiteProvider` 或 `SiteProviderRegistry.getActive()` 引用，通过接口获取下载链接，不再直接调用 EhEngine 静态方法。

---

## 7. 后端适配 (anotherviewer-web)

### 7.1 依赖变更

```kotlin
// anotherviewer-web/build.gradle.kts
dependencies {
    compileOnly(project(":anotherviewer-core"))           // SPI 接口（编译可见）
    runtimeOnly(project(":anotherviewer-provider-ehentai")) // 默认插件（运行时类路径）
    // ... 其他依赖不变
}
```

`compileOnly` 确保编译期后端代码只依赖 `SiteProvider` 接口，不依赖 E-Hentai 具体实现。

### 7.2 Provider 注入

```kotlin
// 新增 config/ProviderConfig.kt
@Configuration
class ProviderConfig {
    @Bean
    fun siteProviderRegistry(): SiteProviderRegistry {
        val registry = SiteProviderRegistry
        registry.register(EhentaiSiteProvider())
        registry.setActive("e-hentai")
        return registry
    }
}
```

### 7.3 Service 层改造

| 文件 | 现有调用 | 改为 |
|------|---------|------|
| `DownloadService.kt` | `EhEngine.getGalleryPage()` → 获取下载 URL | `SiteProviderRegistry.getActive().getImageUrl()` |
| `EhSessionManager.kt` | `EhEngine.signIn()` → E-Hentai 登录 | `SiteProviderRegistry.getActive().authenticate()` |
| `EhAuthService.kt` | 本地用户认证（JWT），不涉及 E-Hentai | **不动** |
| `GalleryService.kt` | 数据库仓库查询 | **不动**（未来可接入 provider） |
| `CommentService.kt` | Mock 实现（ConcurrentHashMap） | 可选接入 provider |
| `FavoriteService.kt` | — | 改为 `SiteProviderRegistry.getActive().getFavorites()` |
| `TorrentService.kt` | — | 改为 provider |
| `ArchiveService.kt` | — | 改为 provider |

### 7.4 新增 Provider REST API

```kotlin
// 新增 api/ProviderController.kt
@RestController
@RequestMapping("/api/v1/providers")
class ProviderController {
    @GetMapping
    fun listProviders(): List<ProviderInfo>

    @GetMapping("/{siteId}/settings")
    fun getSettings(@PathVariable siteId: String): ProviderSettingsPage

    @PutMapping("/{siteId}/settings")
    fun updateSettings(
        @PathVariable siteId: String,
        @RequestBody values: Map<String, String>
    ): Boolean
}
```

---

## 8. 前端适配 (web-frontend)

### 8.1 新增 API 客户端

```typescript
// src/api/provider.ts
export interface ProviderInfo {
    siteId: string
    displayName: string
    settingsPage: ProviderSettingsPage
}

export const providerApi = {
    async list(): Promise<ProviderInfo[]>,
    async getSettings(siteId: string): Promise<ProviderSettingsPage>,
    async updateSettings(siteId: string, values: Record<string, string>): Promise<boolean>,
}
```

### 8.2 设置页改造

`SettingsView.vue` 的 Tab 导航增加"平台"项，路由绑定：

```
设置
├── 通用    (现有)
├── 下载    (现有)
├── 缓存    (现有)
└── ★ 平台  (新增)
    └── E-Hentai 设置 (由 ProviderSettingsPage 定义，动态渲染)
        ├── ipb_member_id (文本框)
        ├── ipb_pass_hash (密码框)
        └── ...
```

平台设置页**不嵌入主 UI 布局**，仅作为设置内的一个 Tab 存在。每个 Provider 定义自己的 `settingsPage`，前端据此动态渲染表单控件（TEXT / PASSWORD / SELECT / SWITCH 等类型）。

### 8.3 前端改动清单

| 文件 | 改动 |
|------|------|
| `src/api/provider.ts` | 新增 — API 客户端 |
| `src/views/SettingsView.vue` | 新增"平台"Tab + ProviderSettingsPanel 组件 |
| `src/components/settings/ProviderSettingsPanel.vue` | 新增 — 动态渲染设置表单 |
| `src/stores/provider.ts` | 新增 — Provider 列表状态 |

---

## 9. 实施阶段

### Phase 1: SPI 接口定义 ⭐ 纯新增

- 新建 `anotherviewer-core/src/main/java/com/pegionfish/anotherviewer/site/` 包
- 新增 SiteProvider.kt、SiteProviderRegistry.kt、ProviderContext.kt、ProviderSettingsPage.kt
- 新增 model/ 下的通用数据模型
- **零影响现有代码**

**验证:** `./gradlew :anotherviewer-core:compileJava` PASS

### Phase 2: Provider 模块创建 + E-Hentai 代码迁移

- 新建 Gradle 模块 `anotherviewer-provider-ehentai`
- 实现 `EhentaiSiteProvider.kt`（适配器模式，内部委托给 EhEngine 静态方法）
- 从 ehviewer-core 移动所有 E-Hentai 相关代码到 provider 模块
- 调整 provider 模块内所有 import 包名
- 实现 `EhentaiSettingsPage.kt`（定义 E-Hentai 特有的配置项）

**验证:** `./gradlew :anotherviewer-provider-ehentai:compileJava` PASS

### Phase 3: Core 模块清理

- 修改包名 `com.hippo.ehviewer` → `com.pegionfish.anotherviewer`
- 重命名通用类（EhCoreConfig→CoreConfig, EhCookieStore→CookieStore 等）
- 清理移除 E-Hentai 代码后残留的引用
- 调整 SpiderQueen 使其接收 SiteProvider

**验证:** `./gradlew :anotherviewer-core:compileJava` PASS

### Phase 4: 后端适配

- `anotherviewer-web` 改依赖声明为 compileOnly + runtimeOnly
- ProviderConfig 注入 SiteProviderRegistry
- 改造 DownloadService、EhSessionManager 等为通过 provider 调用
- 新增 ProviderController
- 全局 import 包名替换

**验证:** `./gradlew :anotherviewer-web:test` 40 PASS

### Phase 5: 前端适配

- 新增 `provider.ts` API 客户端
- SettingsView.vue 新增"平台"Tab
- 新增 ProviderSettingsPanel 组件

**验证:** `vue-tsc --noEmit` 0 errors, `vitest` 200/200

### Phase 6: Android 适配

- `app/build.gradle` 改依赖为 `anotherviewer-core` + `anotherviewer-provider-ehentai`
- 全局 import 包名替换：`com.hippo.ehviewer.client` → `com.pegionfish.anotherviewer.provider.ehentai.engine`
- AndroidManifest 更新 namespace/applicationId

**验证:** `./gradlew :app:compileAppReleaseDebugJavaWithJavac` PASS

---

## 10. 验证清单

### 编译验证

| 命令 | 阶段 |
|------|------|
| `./gradlew :anotherviewer-core:compileJava` | P1, P3 |
| `./gradlew :anotherviewer-provider-ehentai:compileJava` | P2 |
| `./gradlew :anotherviewer-web:compileKotlin` | P4 |
| `./gradlew :anotherviewer-web:test` | P4 |
| `./gradlew :app:compileAppReleaseDebugJavaWithJavac` | P6 |
| `cd web-frontend && npm run typecheck` | P5 |
| `cd web-frontend && npm run test` | P5 |
| `cd web-frontend && npx vite build` | P5 |

### LLM 安全验证

- `anotherviewer-core/` 目录树中不包含 E-Hentai 特定 URL（`e-hentai.org`, `exhentai.org`）
- `anotherviewer-core/` 文件内容不含 adult 相关关键字
- 模拟：仅加载 `anotherviewer-core` 给 Qwen 模型，确认无内容审核阻塞

### 功能验收

- [ ] 后端通过 SiteProvider 接口访问站点，无直接 EhEngine import
- [ ] 后端 REST API `/api/v1/providers` 可返回 Provider 列表
- [ ] 后端 REST API `/api/v1/providers/{siteId}/settings` 可读写
- [ ] 前端设置页 → "平台" Tab → 显示 E-Hentai 设置表单
- [ ] 前端其他功能（画廊浏览、下载、收藏）不受影响

---

## 11. 风险与缓解

| 风险 | 严重度 | 缓解措施 |
|------|--------|---------|
| 包名变更导致大规模 import 错误 | 中 | Phase 3 使用批量搜索替换（Perl/sed），Phase 6 全量编译验证 |
| SpiderQueen 直接调用 EhEngine 静态方法，迁移需改调用方式 | 中 | Phase 3 将 SpiderQueen 改为接收 SiteProvider 引用 |
| `Settings.java` 同时包含通用和 E-Hentai 常量，拆分易遗漏 | 中 | 审计所有 `Settings.getXxx()` 调用，将 E-Hentai 特有常量移入 provider 配置 |
| `EhDB.java` 包含 E-Hentai 特有查询逻辑 | 中 | 拆分通用 DB 辅助 + E-Hentai 查询为单独 DAO |
| Android app/ 有 100+ 文件引用了旧包名，需逐文件确认 | 低 | 批量替换后编译即验证 |
| `R.java` 中的资源 ID 引用了 E-Hentai 特有字符串 | 低 | 与 E-Hentai 相关的 R 常量移入 provider 模块或通过接口注入 |
| 现有 `web-frontend/package.json` 项目名包含 ehviewer | 极低 | 前端项目名保持不变（与后端解耦） |

---

## 12. 后续扩展

- 实现 `anotherviewer-provider-exhentai`（ExHentai 插件）
- SPI 接口版本化机制
- 插件独立打包与类路径隔离
- 前端直连 Provider 模式（Service Worker 代理）

---

## 附录 A: 关键文件索引

| 文件 | 行数 | 迁移目标 |
|------|------|---------|
| `client/EhEngine.java` | 1430 | `provider-ehentai/engine/` |
| `client/EhUrl.java` | 318 | `provider-ehentai/engine/` |
| `client/parser/GalleryListParser.java` | — | `provider-ehentai/parser/` |
| `client/parser/GalleryDetailParser.java` | — | `provider-ehentai/parser/` |
| `client/data/GalleryInfo.java` | — | `provider-ehentai/data/` |
| `client/data/GalleryDetail.java` | — | `provider-ehentai/data/` |
| `app/.../spider/SpiderQueen.java` | — | 改为接收 SiteProvider |
| `ehviewer-web/.../service/DownloadService.kt` | 313 | 改为调用 provider |
| `ehviewer-web/.../service/EhSessionManager.kt` | — | 改为调用 provider |

---

## 附录 B: EhentaiSiteProvider 实现草图

```kotlin
// anotherviewer-provider-ehentai/.../EhentaiSiteProvider.kt
package com.pegionfish.anotherviewer.provider.ehentai

import com.pegionfish.anotherviewer.site.SiteProvider
import com.pegionfish.anotherviewer.site.model.*

class EhentaiSiteProvider(
    private val context: ProviderContext,
) : SiteProvider {

    override val siteId = "e-hentai"
    override val displayName = "E-Hentai"

    override val settingsPage = ProviderSettingsPage(
        pageId = "ehentai-auth",
        pageTitle = "E-Hentai 设置",
        icon = "cookie-brown",
        sections = listOf(
            SettingsSection("认证信息", listOf(
                SettingField("cookie_ipb_member_id", "Member ID", SettingType.TEXT),
                SettingField("cookie_ipb_pass_hash", "Pass Hash", SettingType.PASSWORD),
            )),
            SettingsSection("站点偏好", listOf(
                SettingField("site_variant", "站点", SettingType.SELECT,
                    defaultValue = "e-hentai",
                    options = listOf("e-hentai", "exhentai")),
            )),
        )
    )

    // 适配器模式：委托给 EhEngine 静态方法
    override suspend fun search(query: UnifiedSearchQuery): UnifiedSearchResult {
        // 1. 转换 UnifiedSearchQuery → EhEngine 参数
        // 2. 调用 EhEngine.getGalleryList(...)
        // 3. 转换 GalleryListParser.Result → UnifiedSearchResult
    }

    // ... 其余方法类似
}
```

---

> 文档评审后进入 `/mode act` 执行 Phase 1。本文档不包含实际代码实现——实现细节由实施者（人或 LLM）在编写代码时决定。

> ⚠ v1.1 起以此节为准。下面的 §13～§22 含可执行细节。如与上文 §1～§12 冲突，以本节为准。

---

## 13. 评审纪要（v1.1）

### 13.1 关键缺陷与代码现实对比

| # | v1.0 论述 | 代码现实（证据） | 影响 |
|---|-----------|-----------------|------|
| **F1** | LLM 触发审核的原因是"`ehviewer-core` 与通用代码混合"，拆 core 即可隔离 | 真正触发 `data_inspection_failed` 的是子代理探索 **`app/`**（84 工具调用、~4.9M token，见 `webui-progress.md` §6 / §I5）。`app/` 含平行的 13 个 Eh\* 文件 + 36 个直接 `import com.hippo.ehviewer.client.*` 的文件，远比 core 复杂。 | **方案不解决其原始动机。** 仅重命名 core 后，app/ 仍是 LLM 不可用的敏感高密度块。 |
| **F2** | §6.1 列出迁移自 `ehviewer-core/.../client/` 的 9 engine + 22 parser + 23 data + 4 exception | 实测：core 内为 **9 engine + 22 parser + 21 data + 9 exception**（不是 23/4）。9 exception 多出 5 个：`EmptyGalleryException`、`GalleryUnavailableException`、`Image509Exception`、`OffensiveException`、`PiningException`——其中 `Image509Exception` 已被 SpiderQueen 显式 catch，迁移遗漏会编译失败。 | 数据清单须以实测为准，迁移清单补齐 5 个异常类。 |
| **F3** | "迁移 core 的 E-Hentai 代码至 provider-ehentai" 即可 | `app/.../client/EhEngine.java`（1429 行）与 core 同名但内容不同：app 版引用 `android.util.Log`、`android.text.TextUtils`、`androidx.annotation.Nullable`；core 版引用 `org.slf4j.Logger`、`com.hippo.ehviewer.util.TextUtil`。`app/.../client/EhUrl.java`、`EhClient.java`、`EhConfig.java`、`EhCookieStore.java`、`EhFilter.java`、`EhCacheKeyFactory.java`、`EhRequestBuilder.java`、`EhTagDatabase.java` 也都是平行副本。`app/build.gradle` 不依赖 `:ehviewer-core`（已 grep 确认，`grep "project(" app/build.gradle` 返回空）。 | 必须先决定 app/ 与 provider 的关系，否则 Phase 6 的"全局 import 改成 provider 那一套"会让 app 引用到一个 SLF4J 风格的 EhEngine（与 Android Log 不兼容），编译失败。决策见 §18。 |
| **F4** | §7.3 把 `FavoriteService`/`TorrentService`/`ArchiveService`/`CommentService` 都"改为调用 provider" | 实测后端仅 **2 处** 真正调用 Eh\* API：`DownloadService.kt`（`EhEngine.getGalleryDetail/getGalleryPage` + `EhUrl.getGalleryDetailUrl/getHost/getReferer` + `EhRequestBuilder`）、`EhSessionManager.kt`（`EhEngine.signIn` + `EhCookieStore`）。其余三服务全是 stub/local DB：`CommentService`（`ConcurrentHashMap` 内存 mock）、`FavoriteService`（本地 `LocalFavoriteInfoRepository`）、`ArchiveService`/`TorrentService` 均 16 行且直接 return `emptyList()`/`false`。**把这些改为 provider 即新增远端调用功能**，违反 §1.3 非目标。 | §7.3 范围缩为「仅 DownloadService + EhSessionManager」，其它服务标注"不动（本地化）"。见 §17。 |

### 13.2 次要/可改进点

- **F5 SPI 太宽泛、半未经现实校准**：`getFavorites(slot, page) → List<UnifiedFavorite>` 无分页返回总数。EhEngine `getFavorites` 实际含 favcat 0..9 + always + total。建议改用分页 wrapper。
- **F6 搜索模型欠表达力**：`UnifiedSearchQuery(base keyword, category, page, pageSize, advancedFilters: Map)` 无法表达 EhEngine `ListUrlBuilder` 的 mode（NORMAL/IMAGE_SEARCH/TOPLIST/SUBSCRIPTION）、namespace tag（`artist:`、`female:`）、最低评分、页面范围。建议引入 `mode` 枚举 + 类型化 `advancedFilters`（或退化为字符串透传 `query` 让 Provider 各自解析）。
- **F7 错误模型缺失**：core 9 个异常类（含 `Image509Exception`/`OffensiveException`/`GalleryUnavailableException`）通讯含义不同，后端映射 HTTP 状态也不同（503→限流、410→已删、451→offensive）。SPI 应提供 `sealed SiteException`。
- **F8 会话失败信号缺失**：现有 `EhSessionExpiredException` 用于注入 401， convertido a Provider 后须在 SPI 暴露 `class SessionExpiredException : SiteException`，由 `AuthExceptionMapper` 捕获并返回 401。
- **F9 ProviderContext 不完整**：仅含 `httpClient`/`cacheDir`/`configStore`。但 `EhSessionManager` 自建 OkHttpClient 并注入 `cookieStore`。应显式区分「Provider 接收外部注入的共享 OkHttpClient（携带 cookieJar）」与「Provider 自建 client」。
- **F10 i18n 缺失**：`pageTitle = "E-Hentai 设置"` 是中文硬编码，前端应通过 i18n 键由前端字典本地化（`labelKey` 字段）。
- **F11 ServiceLoader 元文件约束缺失**：`SiteProviderRegistry.discover()` 走 `java.util.ServiceLoader` 须在 provider 模块 `src/main/resources/META-INF/services/com.pegionfish.anotherviewer.site.SiteProvider` 写入实现类全名——v1.0 未提，Phase 2 必漏。
- **F12 `compileOnly(project(":core"))` 对 model 类脆弱**：data class 字段须在编译期可见，`compileOnly` 不可用；须 `implementation(project(":core"))` 或 `api`。runtimeOnly(provider) 通过 provider 对 core 的 `api/implementation` 传递依赖保证运行时类路径完整。
- **F13 `R.java` 跨模块反向依赖**：core/EhEngine.java 用 `GetText.getString(R.string.kokomade_tip)`，core/R.java 是 7 行的 stub。FavoritesParser.java 也 `import com.hippo.ehviewer.R`。EhEngine 与所有 parser 一并迁入 provider 时，`R.java` 必须同步迁入 provider（否则 provider 编译缺 R），但 §6.2 明示 `R.java` 留 core。**冲突**——须修正：把 core 中 E-Hentai 专用的 `R.string` 项（7 个）随 parser 迁入 provider，core 不留 R.java。
- **F14 命名空间 token 拼写**：`com.pegionfish.anotherviewer` 中 `pegion` 疑为 `pigeon`（鸽鱼）之笔误。grep 全仓 0 命中 `pegionfish`/`pigeonfish`——新名空间未引入过任何历史。**须用户确认最终拼写**（见 §21.1 决策 D1）。
- **F15 `applicationId` vs `namespace` 混淆**：当前 app `namespace='com.hippo.ehviewer'`、`applicationId='com.xjs.ehviewer'`，二者本就分离。v1.0 §5.1「applicationId 改为 `com.xjs.anotherviewer`」与「包名改为 `com.pegionfish.anotherviewer`」共生——技术上可行（android 允许 namespace ≠ applicationId），但需显式写清二者映射，避免实施混淆。
- **F16 阶段顺序问题**：Phase 2（创建 provider）必须 import 已改名的 core SPI 包（`com.pegionfish.anotherviewer.site.*`），但 core 改名在 Phase 3 —— 时序矛盾。修正：包名重命名须提前为 **Phase 0**（先改名、不删 E-Hentai 文件），随后 Phase 1 建 SPI、Phase 2 建 provider、Phase 3 搬迁。
- **F17 验证命令遗漏关键门**：`./gradlew :anotherviewer-core:compileJava` 仅验证 Java；若新增 model 类用 Kotlin（spec 暗示 `.kt`），须 `compileKotlin`。P5 缺 `npm run lint`。无架构不变量级断言（如 grep `e-hentai.org` 在 core/*.java 命中应为 0）。

---

## 14. SPI 接口修订（v1.1）

本节替换 §4.1 / §4.2 / §4.3 / §4.5 的 SPI 定义。修订原则：
1) 接口最小可测——不引入运行期未用方法。当前只后端用 4 个方法：authenticate, getDetail, getImageUrl, getGalleryPages(隐含)；本 Stage 仅签名范畴化它们 + 预留方法以 sealed `UnsupportedOperationException` 占位。
2) 错误模型类型化（` SiteException`），替代 `java.lang.Exception` 抛无类型。
3) i18n key 而非 pre-baked 中文字面量。
4) ServiceLoader 元文件契约一并列。

### 14.1 修订版 SiteProvider

```kotlin
// anotherviewer-core/src/main/kotlin/com/pegionfish/anotherviewer/site/SiteProvider.kt
package com.pegionfish.anotherviewer.site

interface SiteProvider {
    val siteId: String                      // "e-hentai" / "exhentai"
    val displayNameKey: String              // i18n key，如 "provider.ehentai.name"
    val settingsPage: ProviderSettingsPage

    // —— 认证 —— 由 EhSessionManager 注入共享 OkHttpClient（cookieJar 内置）
    suspend fun authenticate(input: AuthInput): AuthResult
    suspend fun validateSession(): Boolean  // 检查 cookieJar 是否仍含身份 cookie
    suspend fun revokeSession()

    // —— 画廊（v1.1 仅后端真正使用的子集）——
    suspend fun getDetail(gid: Long, token: String): UnifiedGalleryDetail
    suspend fun getGalleryPages(gid: Long, token: String): List<PageInfo>          // DownloadService.fetchPageCount
    suspend fun getImageUrl(gid: Long, token: String, page: Int): ImagePageResult  // DownloadService.fetchImageUrl

    // —— 占位（v1.1 抛 UnsupportedOperationException）——
    // search / getPopular / getTopList / getHome / getFavorites /
    // addFavorite / removeFavorite / getComments / postComment / voteComment /
    // getTorrents / getArchives / downloadArchive / getProfile / getMyTags
    //  → 仅声明，由 followup Stage 实现。本 Stage 不做。
}

data class AuthInput(
    val username: String? = null,           // 可选——若用 cookie 直接登录则 null
    val password: String? = null,
    val cookies: Map<String, String> = emptyMap(),   // 制导式：可绕过账密 pre-fill
)

data class ImagePageResult(
    val imageUrl: String,
    val previewUrl: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val skipReason: String? = null,         // 509/限定/offensive 时 imageUrl 留空 + 此字段
)
```

**只签名 6 个真正会用到的 suspend 方法 + 14 个 placeholder**。Placeholder 在本 Stage 显式 `throw UnsupportedOperationException("planned for followup Stage")`，前端 LLM 不会受诱惑去实现它们。

### 14.2 修订版错误模型

```kotlin
// anotherviewer-core/.../site/SiteException.kt
sealed class SiteException(message: String, cause: Throwable? = null)
    : RuntimeException(message, cause) {
    class SessionExpired : SiteException("session expired")
    class ContentRemoved(gid: Long) : SiteException("gallery $gid removed (410)")
    class ContentOffensive(gid: Long) : SiteException("gallery $gid offensive (451)")
    class RateLimited(host: String) : SiteException("rate limited by $host (503)")
    class ImageUnavailable(message: String) : SiteException(message)
    class ParseException(message: String, cause: Throwable? = null) : SiteException(message, cause)
    class Cancelled : SiteException("cancelled") { override fun fillInStackTrace() = this }
    class Network(code: Int, url: String) : SiteException("HTTP $code at $url")
    class Other(message: String, cause: Throwable? = null) : SiteException(message, cause)
}
```

- Provider 实现把 EhEngine 抛的 9 个具体异常映射到上述 sealed 类。
- Web 端 `AuthExceptionMapper`/全局 `@ControllerAdvice` 把 `SessionExpired → 401`、`ContentRemoved → 410`、`ContentOffensive → 451`、`RateLimited → 503`、`Other → 500`。

### 14.3 修订版注册表与上下文（抵消 F9 / F11）

```kotlin
// anotherviewer-core/.../site/SiteProviderRegistry.kt
object SiteProviderRegistry {
    private val providers = ConcurrentHashMap<String, SiteProvider>()
    private val active = AtomicReference<String?>(null)

    /** 显式注册——替代 ServiceLoader 探测（v1.1 不启用 ServiceLoader，
     *  避免 provider 模块多一个 META-INF/services 维护点；待多 provider 再启用） */
    fun register(provider: SiteProvider) = providers.put(provider.siteId, provider)
    fun get(siteId: String): SiteProvider? = providers[siteId]
    fun getActive(): SiteProvider =
        providers[active.get()] ?: error("no active SiteProvider; call setActive() first")
    fun setActive(siteId: String) { require(providers[siteId] != null) { "unknown provider $siteId" }; active.set(siteId) }
    fun all(): Collection<SiteProvider> = providers.values
}

// anotherviewer-core/.../site/ProviderContext.kt
data class ProviderContext(
    /** 由 EhSessionManager 构建并注入——OkHttpClient 自带 cookieJar
     *  因此 provider 无需再开 cookie store（避免 EhCookieStore 与 ProviderContext.httpClient 不一致） */
    val httpClient: OkHttpClient,
    val cacheDir: java.nio.file.Path,
    val configStore: ProviderConfigStore,
    val logger: org.slf4j.Logger,
)
```

### 14.4 修订版设置页（i18n）

```kotlin
data class ProviderSettingsPage(
    val pageId: String,
    val titleKey: String,                   // i18n key 替代 pageTitle 字面量
    val iconSvg: String? = null,            // SVG/PNG base64，避免 android R 耦合
    val sections: List<SettingsSection>,
)
data class SettingsSection(val titleKey: String, val fields: List<SettingField>)
data class SettingField(
    val key: String, val labelKey: String,
    val type: SettingType,                  // TEXT / PASSWORD / TEXTAREA / SELECT / SWITCH
    val defaultValue: String? = null,
    val options: List<String>? = null,
    val required: Boolean = false,
    val placeholderKey: String? = null,
)
```

### 14.5 分页搜索结果（修正 F5）

```kotlin
data class UnifiedSearchResult(
    val items: List<UnifiedGalleryInfo>,
    val total: Int,                         // 站点已知总数（未知 = -1）
    val currentPage: Int,
    val pageSize: Int,
)
```

---

## 15. 修订后模块拓扑图

```
anotherviewer/ (rootProject.name)

├─ anotherviewer-core/                  ★ LLM 安全模块（包名先改：com.pegionfish.anotherviewer）
│   ── 通用：site/、model/、util/、network/(CookieStore 去前缀)、dao/、spider/(空)
│   ── 此模块中 grep "e-hentai.org|exhentai.org|api://e-hentai" 命中必须 = 0
│
├─ anotherviewer-provider-ehentai/       ★ E-Hentai 插件（敏感内容隔离）
│   package：com.pegionfish.anotherviewer.provider.ehentai
│   compileOnly anotherviewer-core 之后改 implementation(project(":anotherviewer-core"))
│   ── engine/：9 Eh* java（从 app 移来，绑定 android.util.Log 抽象出来或下沉）
│   ── parser/：22 java + R 移至 provider（追加 7 个 stub string resource）
│   ── data/：21 + 9 exception（迁移漏的 5 个补齐）
│   ── EhentaiSiteProvider.kt + EhentaiSettingsPage.kt（i18n key 版）
│
├─ anotherviewer-web/                    ★ 后期适配（compileOnly/core → runtimeOnly/provider）
│   仅 DownloadService + EhSessionManager 真接 EhEngine，改 SiteProvider 调用
│
├─ app/                                  ★ Android（参见 §18：保留平行副本 vs 收敛 provider 的决策 D2）
│   applicationId：com.xjs.anotherviewer
│   namespace：com.pegionfish.anotherviewer
│
└─ web-frontend/                         ★ Vue（仅新增「平台」Tab，不动其它）
```

---

## 16. 修订后阶段编排（Phase 0..6 可执行）

每阶段含 **(1) 目标 (2) 文件操作 (3) 构建门 (4) 失败回滚**。
顺序与原 §9 不同：**包名先改（Phase 0）**——理由见 F16。

### Phase 0 — 包名与模块名替换（不改逻辑）

- **目标**：rootProject / Gradle 模块 / Java-Kotlin 源码包名一步改全，但**不删任何 E-Hentai 代码**；改完仍能编译现有所有模块。
- **文件操作**（顺序强相关，按步走）：
  1. `settings.gradle`：`rootProject.name = "anotherviewer"`；模块路径照旧。
  2. `settings.gradle`：把 `include ':ehviewer-core'`、`include ':ehviewer-web'` 保留（模块路径不变；可后续重命名 Gradle 模块，本阶段保持物理目录名 = `ehviewer-*` 减少机械改动）。
  3. 重命名**物理目录** `ehviewer-core/` → `anotherviewer-core/`、`ehviewer-web/` → `anotherviewer-web/`（让模块名清晰）。同步 `settings.gradle` 中两行 `include` 与 `project(':anotherviewer-core').projectDir = ...`（如不改 include 名）。
  4. 全仓机械替换字符串（使用 ripgrep 检查后 perl 进行）：
     - `com.hippo.ehviewer` → `com.pegionfish.anotherviewer`（Java/Kotlin 源 + Gradle）
     - `com.hippo.widget` → `com.pegionfish.anotherviewer.widget`
     - `com.hippo.util` → `com.pegionfish.anotherviewer.util`
     - `com.hippo.network` → `com.pegionfish.anotherviewer.network`
     - `com.hippo.lib.yorozuya` → `com.pegionfish.anotherviewer.util.yorozuya`
     - `app/.../com/hippo/ehviewer/` → `app/.../com/pegionfish/anotherviewer/`（含物理目录 mv）
     - `app/build.gradle`：`namespace 'com.pegionfish.anotherviewer'`、`applicationId "com.xjs.anotherviewer"`、testNamespace、FileProvider authority 同步
  5. **不动**：`daogenerator`、`app/src/main/cpp/`、AndroidManifest 引用资源名（资源名改动风险高、本阶段不碰）。
- **构建门**（一次通过；失败回滚 = `git checkout` 整体；分级回滚见下）：
  ```bash
  ./gradlew :anotherviewer-core:compileJava :anotherviewer-web:compileKotlin
  cd web-frontend && npm run typecheck && npx vitest run
  ```
  > Android 的 `:app:compileAppReleaseDebugJavaWithJavac` 本阶段**不强求**（参考 §18 D2；若决定 app 暂留平行副本则跑过 App 编译即可证明包改没破坏 Android）。
- **回滚**：`git stash` + 重新分小步替换；包改属纯机械，无逻辑回归。

### Phase 1 — SPI 接口与数据模型写入 anotherviewer-core

- **目标**：按 §14 定义 SPI；core 内**完全不含** E-Hentai 字串（grep 校验）。
- **新增文件**（路径已带新包名）：
  ```
  anotherviewer-core/src/main/kotlin/com/pegionfish/anotherviewer/site/
      SiteProvider.kt
      SiteProviderRegistry.kt
      ProviderContext.kt
      SiteException.kt
      model/AuthResult.kt
      model/AuthInput.kt
      model/ImagePageResult.kt
      model/PageInfo.kt
      model/UnifiedGalleryInfo.kt
      model/UnifiedGalleryDetail.kt
      model/UnifiedSearchQuery.kt
      model/UnifiedSearchResult.kt
      model/TagGroup.kt
      settings/ProviderSettingsPage.kt
      settings/SettingsSection.kt
      settings/SettingField.kt
      settings/SettingType.kt
  ```
- **不动**：原 `ehviewer-core/.../client/` 下 E-Hentai java（这些将在 Phase 3 迁出）。
- **构建门**：
  ```bash
  ./gradlew :anotherviewer-core:compileKotlin :anotherviewer-core:compileJava :anotherviewer-core:test
  # 架构不变量（必须为 0）
  ! rg -i "e-hentai\.org|exhentai\.org|ehtracker|api\.e-hentai" anotherviewer-core/src/main
  ```
- **回滚**：删 site/ 包，core 立即恢复到 Phase 0 状态。

### Phase 2 — provider 模块骨架

- **目标**：新建 Gradle 模块，建包，**只**写 `EhentaiSiteProvider.kt`（含 14 个占位 `UnsupportedOperationException` 的方法）+ `EhentaiSettingsPage.kt`（i18n 版），**不**移任何 E-Hentai 代码。
- **build.gradle.kts**：
  ```kotlin
  plugins { `java-library` ; kotlin("jvm") }
  java { sourceCompatibility = JavaVersion.VERSION_21 }
  dependencies {
      implementation(project(":anotherviewer-core"))            // 取代 §7.1 提议的 compileOnly
      implementation("com.squareup.okhttp3:okhttp:3.14.7")
      implementation("org.jsoup:jsoup:1.15.4")
      implementation("org.ccil.cowan.tagsoup:tagsoup:1.2.1")
      implementation("com.alibaba:fastjson:1.2.83")
      compileOnly("org.slf4j:slf4j-api:2.0.9")
      testImplementation("junit:junit:4.13.2") ; testImplementation("org.slf4j:slf4j-simple:2.0.9")
  }
  ```
  > 注：§7.1 中 web 用 `compileOnly(project(":anotherviewer-core"))` 错（F12须 `implementation`，data class 字段编译期可见性）；本 provider 端 `implementation` 正确。
- **注册表初始化**：默认 Provider 由 Phase 4 web 端 `ProviderConfig` 显式 `register(EhentaiSiteProvider(...))` / `setActive("e-hentai")`。
- **不引** `java.util.ServiceLoader` —— F11 退化为显式 register（v1.1 单 provider 简化）。
- **构建门**：
  ```bash
  ./gradlew :anotherviewer-provider-ehentai:compileKotlin
  ```
  注：只要 `EhentaiSiteProvider` 14 个 placeholder 抛 `UnsupportedOperationException` 即可通过——验证 SPI 编译连接，未触逻辑迁移。
- **回滚**：删模块 + `settings.gradle` 中移除 `include`。

### Phase 3 — E-Hentai 代码迁移至 provider（含 core 清理）

- **目标**：把 anotherviewer-core 中所有 E-Hentai 文件**移物理位置**至 provider 模块，**改包名前缀**至 `provider.ehentai.*`，core 退为「LLM 安全」净模块。
- **物理 mv 清单（按 §6 实测精确数量）**：
  ```
  anotherviewer-core/src/main/java/com/pegionfish/anotherviewer/client/
      engine/EhEngine.java, EhUrl.java, EhRequestBuilder.java, EhConfig.java,
             EhFilter.java, EhUtils.java, EhCacheKeyFactory.java,
             EhTagDatabase.java, EhClient.java
      parser/<22 个 .java>
      data/<21 + TorrentDownloadMessage.kt>
      data/topList/, data/userTag/, data/wifi/
      exception/<9 个 .java>
      R.java (含 7 个 stub string，迁出 core)            # F13 修正：不留 core
      EhApplication.java, GetText.java                  # 与 EhEngine 紧耦合，迁 provider
  ↓
  anotherviewer-provider-ehentai/src/main/java/com/pegionfish/anotherviewer/provider/ehentai/{engine,parser,data,exception,util}/
  ```
- **包名**：`com.pegionfish.anotherviewer.client.*` → `com.pegionfish.anotherviewer.provider.ehentai.<sub>.*`
- **core 保留**：`util/`(Pair, TextUtil, TagTranslationUtil)、`network/CookieStore`(原 EhCookieStore→改名)、`dao/`(DownloadInfo, QuickSearch)、`spider/`(空，原 SpiderQueen 在 app/，本阶段不动)、`widget/`(去 Eh 前缀)、`lib/yorozuya/`(改名后路径)。
- **必须解决**：`EhEngine.java` 中 `GetText.getString(R.string.kokomade_tip)` 等 4 处 → `R.java` 一并迁 provider，包名随之。
- **构建门**：
  ```bash
  ./gradlew :anotherviewer-provider-ehentai:compileJava :anotherviewer-core:compileJava
  # core 净空校验（必须为空）
  ! rg "Eh|gallery|hentai| Favorite|Torrent" anotherviewer-core/src/main --type java --type kotlin
  ```
  注：第二行 rg 是粗校验，最终以 `:anotherviewer-core:compileJava` + 单测通过为准。
- **回滚**：`git mv` 反向或重 git revert。建议**分步提交**：每移一个 sub-package 单 commit，编译过即续下，失败定位即时。

### Phase 4 — 后端适配 anotherviewer-web（最小改动）

- **目标**：仅改 2 个 service + 新增 2 个 config/controller；其他 service 显式不动（修正 §7.3 / F4）。
- **改动文件**：
  1. `anotherviewer-web/build.gradle.kts`：
     ```kotlin
     dependencies {
         implementation(project(":anotherviewer-core"))
         runtimeOnly(project(":anotherviewer-provider-ehentai"))     // 新增：默认 provider 装配
         // 其它依赖不动
     }
     ```
     > 注：web 仍用 `implementation`（不是 §7.1 提议的 `compileOnly`）——core 中 model data class 字段在 web 编译期可见（F12）。
  2. **新增** `config/SiteProviderConfig.kt`：
     ```kotlin
     @Configuration class SiteProviderConfig {
         @Bean fun siteProviderRegistry(sessionManager: EhSessionManager): SiteProviderRegistry {
             val ctx = ProviderContext(
                 httpClient = sessionManager.okHttpClient,
                 cacheDir = Paths.get(config.cacheDir),
                 configStore = SpringProviderConfigStore(...),
                 logger = LoggerFactory.getLogger("site-provider"),
             )
             val registry = SiteProviderRegistry
             registry.register(EhentaiSiteProvider(ctx))
             registry.setActive("e-hentai")
             return registry
         }
     }
     ```
     注：让 **EhSessionManager 不再直接调 EhEngine**——把 `signIn` 委托给 `SiteProviderRegistry.getActive().authenticate(...)`，session 状态机保持 in sessionManager。
  3. **改** `service/DownloadService.kt`：
     - 删除 `import com.pegionfish.anotherviewer.provider.ehentai.engine.EhEngine / EhUrl / EhRequestBuilder` 三行。
     - `fetchPageCount` → `runBlocking { SiteProviderRegistry.getActive().getGalleryPages(gid, token).size }`，它们的 size 失败默认 1（保留原行为）。
     - `fetchImageUrl` → `runBlocking { SiteProviderRegistry.getActive().getImageUrl(gid, token, page).imageUrl }`，保留 logger warn 行为（provider 抛 SiteException 时 map 到返回 null）。
     - `downloadImage` 中 `EhUrl.getReferer()` → `SiteProviderRegistry.getActive().getImageUrl(...).previewUrl ?: ""` 或新增 `getReferer()` SPI，本 Phase 简化为硬编码 `https://e-hentai.org/`（与本 Stage 等价行为；服务调用 image host 时 EhEngine 内部会自重建 referer）。
  4. **改** `service/EhSessionManager.kt`：
     - 删除 `import ...EhEngine`、`import ...EhCookieStore`（后者已经在 Phase 3 改名成 core/CookieStore）。
     - `signIn(username, password)` → `runBlocking { registry.authenticate(AuthInput(username, password)).let { ... } }`，server 端 cookieJar 注入到 context.httpClient。
     - 增加 `@Bean` 注入 `SiteProviderRegistry`（替代直接 new）。
  5. **新增** `api/ProviderController.kt`（同 §7.4，但路由用 `/api/v1/providers`）。
  6. **全局验证**：`grep -rn "import com.pegionfish.anotherviewer.provider.ehentai" anotherviewer-web/src/main` 应 = 0（这是隔离目标，否则编译期把 provider 拉进 web 编译类路径，破坏「web 只看见 SPI」）。
- **明示不动**：`FavoriteService.kt`(本地 DB)、`CommentService.kt`(内存 mock)、`ArchiveService.kt`/`TorrentService.kt`(空 stub)、`GalleryService.kt`、`HistoryService.kt`(纯 DB)、`SyncService.kt`、`EhAuthService.kt`、`SettingsService.kt`、`ImageCacheService.kt`、`SmbBackupService.kt`、`EncryptionService.kt`。
- **构建门**：
  ```bash
  ./gradlew :anotherviewer-web:compileKotlin :anotherviewer-web:test
  ! rg "import com.pegionfish.anotherviewer.provider.ehentai" anotherviewer-web/src/main
  ```
  注：原 §9 P4 写「40 PASS」须以实跑为准；如新增 `ProviderControllerTest`、`DownloadServiceProviderTest` 增量 2-4 测试。
- **回滚**：service-level 单测盖 + `git revert` 一 commit。

### Phase 5 — 前端「平台」Tab

- **目标**：仅新增一个 Tab 完成 §8；与既有视口零回归。
- **改动文件**（按 §8.3）：
  ```
  web-frontend/src/api/provider.ts                          新增
  web-frontend/src/components/settings/ProviderSettingsPanel.vue 新增
  web-frontend/src/stores/provider.ts                       新增（pinia）
  web-frontend/src/views/SettingsView.vue                   修改加 Tab
  web-frontend/src/locales/zh-CN.ts + en-US.ts              新增 i18n 键（取代 §4.4 中文 pageTitle）
  web-frontend/src/router/index.ts                          大概率不动（Tab 走 SettingsView 内路由）
  ```
- **构建门**：
  ```bash
  cd web-frontend
  npm run lint && npm run typecheck && npx vitest run && npx vite build
  npx playwright test --grep=visual                         # 若 §H1 视觉套件已建
  ```
- **回滚**：rm 新增文件 + git checkout SettingsView.vue。

### Phase 6 — Android app/ 适配（决策 §18 D2 后才能定）

- 此 Phase **依赖 §18 D2 决策**。本节默认按 **D2-A「app 暂留平行副本，Phase 6 仅做包名 import 替换；让 app 继续编译跑」**——见 §18 论证。
- **改动文件**（按 A 方案）：
  - `app/build.gradle`：`namespace 'com.pegionfish.anotherviewer'`、`applicationId "com.xjs.anotherviewer"`、`FileProvider` authority 字段同步、testNamespace。
  - 全仓的 `app/src/main/java/com/hippo/ehviewer/` 物理目录 mv 到 `app/src/main/java/com/pegionfish/anotherviewer/` 包路径（包名替换已在 Phase 0 完成的字符串部分，但包内 sub-directory 物理位移此处完成）。
  - **不引** `:anotherviewer-provider-ehentai`（app 保留 `client/EhEngine.java`）—— 若选 D2-B 会反过来：删 app/ 平行副本、依靠 provider 模块的 EhEngine，但 EhEngine app 版用 `android.util.Log`/`androidx.annotation.Nullable`/`android.text.TextUtils`，provider 版用 SLF4J+`TextUtil`，移植工作量 ≥ 1 个 spinoff Stage；**本 Stage 不做**。
- **构建门**：
  ```bash
  ./gradlew :app:assembleAppReleaseDebug        # 或具体 variant 名
  adb shell pm install -t app/build/outputs/apk/appRelease/debug/app-appRelease-debug.apk   # 真机烟测可选
  ```
- **回滚**：`git revert` + 包名回滚到 `com.hippo.ehviewer`。

---

## 17. 服务改造范围（v1.1 修正）

| 服务 | v1.0 主张 | v1.1 修正（实测后） |
|------|----------|--------------------|
| `DownloadService.kt` | 改 provider.getImageUrl | ✅ 范围正确；也改 fetchPageCount / downloadImage 的 EhUrl.getReferer 引用 |
| `EhSessionManager.kt` | 改 provider.authenticate | ✅ 范围正确；注意 session 状态机保留 in EhSessionManager |
| `EhAuthService.kt` | 不动 | ✅ 不动（本地 JWT，非 E-Hentai） |
| `GalleryService.kt` | "未来可接入 provider" | ❌ 本 Stage 不动（DB 仓库，无 E-Hentai 调用） |
| `CommentService.kt` | "可选接入 provider" | ❌ **不动**（mock 是当前设计；接入 provider 等于新增远端评论，违反非目标 §1.3） |
| `FavoriteService.kt` | 改 provider.getFavorites | ❌ **不动**（本地收藏 DB；改远端收藏是新功能，违反非目标） |
| `TorrentService.kt` | 改 provider | ❌ **不动**（空 stub；接入是新功能） |
| `ArchiveService.kt` | 改 provider | ❌ **不动**（空 stub；同上） |
| `HistoryService.kt`/`SyncService.kt`/`ImageCacheService.kt`/`SmbBackupService.kt`/`EncryptionService.kt`/`SettingsService.kt` | 未列 | ✅ 均不动 |

**净改动**：2 个 service + 新增 2 个（`ProviderController` + `SiteProviderConfig`/`SpringProviderConfigStore`），共 ~5 文件 + 1 行 build.gradle。

---

## 18. app/ 平行副本的决策表（D2）

**背景**（F3）：app/ 有 13 个与 core 同名 Eh\* 文件，与 core 版差异为 Android API 依赖（`android.util.Log`、`android.text.TextUtils`、`androidx.annotation.Nullable`）。两套长期并存 = 重复维护、LLM 改其一另一边漏。

| 选项 | 操作 | 代价 | v1.1 采纳 |
|------|------|------|-----------|
| **D2-A 暂留平行副本** | Phase 6 app 仅做包名/import 替换，仍引用 app 本地副本 | 维护两套，敏感代码仍在 app/，未达 F1 目标 | ★ **本 Stage 默认** |
| D2-B 收敛到 provider | 删 app/ 平行副本，app 改 `implementation(project(":anotherviewer-provider-ehentai"))`；provider 的 EhEngine 改用 SLF4J-bridge 接 `android.util.Log`（引入 `slf4j-android` 或注入 logger 抽象） | 多 1 个 sub-Stage（移植 android-flavored EhEngine），失败风险高 | 后续 Stage |
| D2-C 抽 EhEngine port 接口 | 让 provider 只暴露 SiteProvider SPI，app 引用 provider 但通过 SPI 调用（绕开 EhEngine） | Android-side 大量场景（SpiderQueen/DownloadService 内）需要 EhEngine 内部 API（如 `doGetImageUrl`，回值比 SPI 更详），SPI 接不下须扩 | 后续 Stage 多 provider 时评估 |

**结论**：本 Stage 走 D2-A；F1 中"LLM 在探索 app/ 时被审核"问题，由 **followup Stage「app/ 隔离」** 配合（先把 app/ Eh\* 文件按 sub-package `provider/ehentai/` 类似聚合可跳过，这本质就是 D2-B）。

---

## 19. 硬性验证门（机器可执行，每阶段必过）

```bash
# Phase 0
./gradlew :anotherviewer-core:compileJava :anotherviewer-web:compileKotlin
cd web-frontend && npm run typecheck && npx vitest run

# Phase 1
./gradlew :anotherviewer-core:compileKotlin :anotherviewer-core:compileJava :anotherviewer-core:test
! rg -i "e-hentai\.org|exhentai\.org|ehtracker|api\.e-hentai" anotherviewer-core/src/main

# Phase 2
./gradlew :anotherviewer-provider-ehentai:compileKotlin

# Phase 3
./gradlew :anotherviewer-provider-ehentai:compileJava :anotherviewer-core:compileJava :anotherviewer-core:compileKotlin

# Phase 4
./gradlew :anotherviewer-web:compileKotlin :anotherviewer-web:test
! rg "import com\.pegionfish\.anotherviewer\.provider\.ehentai" anotherviewer-web/src/main   # 隔离断言

# Phase 5
cd web-frontend && npm run lint && npm run typecheck && npx vitest run && npx vite build

# Phase 6
./gradlew :app:assembleAppReleaseDebug
```

**LLM 安全断言（Phase 3 之后必过）**：
```bash
ls anotherviewer-core/src/main/java/com/pegionfish/anotherviewer/client 2>&1   # → No such file
rg -i "hentai|gallery|favorite|torrent|archiver" anotherviewer-core/src/main  # → 无命中；除通用术语 Page 等按需 ignore
```

---

## 20. 风险修订（增订 §11 表）

| 风险 | 严重度 | v1.0 缓解 | v1.1 增订 |
|------|--------|----------|-----------|
| app/ 平行副本与 core 副本长期存续，维护两套 (F3) | **高** | 未提 | §18 D2-A 决策 + followup「app/ 隔离」Stage |
| Service 改造范围过宽，引入未授权远端功能 (F4) | 中 | §7.3 列 7 服务 | §17 缩为 2 服务 + 2 新增 |
| SPI 错误类型_STRONG_缺失（F7） | 中 | 未提 | §14.2 sealed SiteException + 9 异常映射 |
| R.java 跨模块反向依赖（F13） | 高 | §6.2 说留 core | §16 Phase 3 明示 R.java 随 parser 迁 provider |
| 包名时序矛盾（Phase 2 import 已改名 core SPI，但 Phase 3 才改名）(F16) | 高 | 未提 | §16 引入 Phase 0 包名先改 |
| compileOnly 误用（F12） | 中 | §7.1 | §16 Phase 4 改 `implementation(project(":anotherviewer-core"))` |
| ServiceLoader META-INF 缺文件（F11） | 低 | 未提 | §14.3 v1.1 不启用 ServiceLoader，显式 register |
| SPI 14 个占位方法被 LLM 误实现 | 低 | 未提 | §14.1 placeholder 显式 throw UnsupportedOperationException + 注释 |
| i18n pageTitle 中文硬编码 | 低 | §4.4 「pageTitle: "E-Hentai 设置"」 | §14.4 titleKey 增 i18n |
| 包名 `pegionfish` 笔误怀疑 (F14) | 中 | §5.1 假定 | §21.1 决策 D1 — **执行前必须用户确认** |

---

## 21. 命名与决策记录（须用户确认）

### 21.1 决策 D1 — `pegionfish` 拼写确认

`pegion` 在英文中为非词，疑为 `pigeon` 之误。grep 全仓 0 命中确认此 token 从未出现过。

须用户最终敲定以下其一：
- (a) `com.pigeonfish.anotherviewer`（假设本意是 pigeon fish 鸽鱼）
- (b) `com.pegionfish.anotherviewer`（继续使用，视为自定义品牌构词）
- (c) 其他

**默认实施采用 (b) `pegionfish`** 以不擅自改文案，但本文档强烈建议执行 Phase 0 前向作者核对。

### 21.2 决策汇总

| ID | 决策项 | 取值 | 出处 |
|----|--------|-----|------|
| D1 | 包名 token | 待确认（默认 `pegionfish`） | §21.1 |
| D2 | app/ 平行副本处理 | D2-A 暂留 | §18 |
| D3 | SPI 多 provider 发现机制 | 显式 register（不引 ServiceLoader） | §14.3 / F11 |
| D4 | SPI 错误模型 | sealed SiteException + 9 子类 | §14.2 |
| D5 | SPI 「画像」「搜索」等占位方法 | placeholder 抛异常 | §14.1 |
| D6 | SettingsService 等本地服务 | 不接入 provider | §17 |
| D7 | web → core 依赖类型 | `implementation`（非 `compileOnly`） | §16 Phase 4 |
| D8 | R.java | 迁 provider（不留 core） | §16 Phase 3 |

---

## 22. 一次性 Todo 列表（实施者执行 Phase 0 时的最小起步）

```
[ ] D1 用户确认包名 token
[ ] D2 用户确认 Phase 6 走 D2-A（暂留 app 平行副本）
[ ] Phase 0 / 1 / 2 准备工作：建 WIP 分支 `feature/site-provider`
[ ] Phase 0：跑 §16 Phase 0 五个机械步骤 → 构建门过 → commit
[ ] Phase 1：建 SPI 包 → core 净空校验 → commit
[ ] Phase 2：建 provider 模块骨架 → commit
[ ] Phase 3：分 sub-package 分 commit 移动 E-Hentai 文件（先 exception → data → parser → engine → R/GetText），每步 compileJava 过
[ ] Phase 4：改 web 端 2 service + 新增 2 类 → web:test 过 → commit
[ ] Phase 5：前端新增 + i18n 键 → vitest 过 → commit
[ ] Phase 6：app/ 包名 import + AndroidManifest 同步 → assembleAppReleaseDebug 过 → commit
[ ] 收尾：跑 §19 全量门；合 PR；webui-progress.md 增 §I5»SiteProvider 完成 记录
```

---

## 附：v1.0 → v1.1 修订摘要（一行版）

> 包名先改（Phase 0）｜SPI 收敛为 6 真用 + 14 占位｜sealed 错误模型｜ServiceLoader 暂不启用｜R.java 迁 provider｜Service 改造缩为 2 个｜web → core 改 implementation｜app 平行副本 D2-A 暂留｜i18n key 替代中文字面量｜9 异常类全数迁移｜包名 token `pegionfish` 待用户确认。

---

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Part B：v2.0 — 完整多源拆分（现行方案，§23 起）
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

## 23. v2.0 目标重述

### 23.1 v2.0 顶层目标

1. **完全物理隔离**：E-Hentai **所有功能**（含 app/ 平行副本的 13 engine + 22 parser + 21 data + 9 exception + wifi + 多 R）下沉到 **唯一**可分离的 Gradle 模块 `anotherviewer-provider-ehentai`；core / app / web 中**不再有任何** E-Hentai 特定文件或 import。
2. **provider 纯 JVM**：provider 不依赖 `android.*` / `androidx.*`——能被 web 后端（Spring Boot 纯 JVM）与 Android app 两端共用。Android API 替换为抽象（§24）。
3. **单一 SiteProvider SPI 通用化**：app 与 web 一并通过 `SiteProviderRegistry.getActive()` 访问站点。SPI 覆盖 v1 EhEngine 全部 33 个公共方法 + SharedPreferences/DAO 等本地快照层（§25）。
4. **多源扩展 ready**：新增一个站点 = 新建一个 provider 模块实现 `SiteProvider`。core/app/web 完全 0 改动。

### 23.2 v1.1 → v2.0 决策差异

| 项 | v1.1 | v2.0 | 理由 |
|----|------|------|------|
| **app/ 平行副本** | D2-A 暂留 | **删除**，全部用 provider | 用户目标要求"所有 E-Hentai 功能拆出" |
| **SPI 完整度** | 6 真用 + 14 占位 | **33 方法全部声明 + 实现** | app 全功能要能跑 |
| **provider 性质** | 未约束 Android | **强制纯 JVM** | 跨平台共用 |
| **Parcelable 处理** | 未答 | **POJO 化 + app 端 Parcelize 桥接** | 上层耦合 Android 序列化 |
| **app 改造策略** | 仅 import | **核心场景全走 SPI** | 真正多源切换 |

### 23.3 v2.0 实测证据基线（不可推翻）

源自本次评审的代码勘察，决定方案拓扑：

- **app/.../client/** 含 13 engine + 22 parser + ~30 data + 9 exception + 5 wifi 文件。
  EhEngine.java（1429 行）依赖 `android.util.Log` / `android.text.TextUtils` / `androidx.annotation.Nullable` / `android.util.Pair`。
  数据类 23 个 `implements Parcelable` + 写死 Parcel 字段（如 GalleryInfo.java 第 233 行 `writeToParcel(Parcel dest, int flags)`）。
  parser 7 个用 `android.text.TextUtils` / `android.util.Log` / `android.util.Pair`。
- **app 中 import `com.hippo.ehviewer.client.*` 的文件 126 个**：UI/scene（17）/widget（7）/download（4）/spider（3）/dao（4）/sync（4）/preference（4）/dialog（3）/fragment（2）/wifi（2）/topList（2）/各单文件（30+）。
- **app 实测使用 28 个 EhEngine 公共方法**（不是 33，因为 v1 死代码 5 个）+ `EhUrl.*` 36 次分布 + `GalleryInfo` 字段访问 253 处 + `GalleryDetail` 字段访问 11 处 + 6 个 Parser.Result 内部类型显式引用。
- **Package token 已确认**：`pegionfish` 拼写无误（用户网名）——§21.1 D1 关闭。

---

## 24. Android 依赖抽象层（provider 纯 JVM 的关键）

### 24.1 待替换的 Android API 清单

| Android API | 用量 | 替换方案 | 备注 |
|-------------|------|---------|------|
| `android.util.Log` | EhEngine + 7 parser | `org.slf4j.Logger`（已在 core 用） | 直接替换 `Log.d(TAG, msg)` → `logger.debug(msg)` 等 |
| `android.text.TextUtils` | EhEngine + 3 parser | provider 内 `Strings.isEmpty(CharSequence?)` 单文件工具 | 与 core `TextUtil.isEmpty` 区分以免互相 import |
| `android.util.Pair<F,S>` | EhEngine + 2 parser | `kotlin.Pair<A,B>`（EhEngine 是 .java 但 Pair 字段简单可换） | 不引第三方库 |
| `androidx.annotation.Nullable` | EhEngine 大部分方法签名 | 改 `org.jetbrains.annotations.Nullable`（jar 30kb，已在 core 可用）或删除注解 | provider 不引 androidx |
| `android.os.Parcel*` / `Parcelable` | 23 data 类 | **POJO 化**：删 `implements Parcelable`，删 `writeToParcel` / `createFromParcel` / `CREATOR` 静态字段 | 唯一强侵入改动，影响 app Parcelize 调用点 ~20 处 |
| `android.text.TextUtils.isEmpty` 等 | WiFi 等 5 文件 | 同上 Strings 单文件 | wifi 5 文件一并迁 provider |

### 24.2 Parcelable 桥接（app 侧 Parcelize）

为何需要：app 通过 `Intent` 传递 GalleryInfo/Detail（如 `GalleryDetailScene` → `GalleryPreviewsScene`）依赖 `putParcelable` / `getParcelableExtra`。删 Parcelable 后 app 无法跨 Activity/Fragment 传值。

**解决**：在 app 中新建 `app/.../parcel/` 包，作为 E-Hentai data 模型的 **app-side Parcelize 适配**：

```kotlin
// app/src/main/java/com/pegionfish/anotherviewer/parcel/GalleryInfoParcel.kt
@Parcelize
data class GalleryInfoParcel(
    val gid: Long,
    val token: String,
    val title: String,
    val titleJpn: String?,
    val thumb: String,
    val category: Int,
    val posted: String,
    val uploader: String,
    val rating: Float,
    val pages: Int,
    val simpleLanguage: String?,
    val simpleTags: List<String>?,
    val favoriteSlot: Int,
) : Parcelable {
    fun toDomain() = GalleryInfo(gid, token, title, titleJpn, thumb, category,
        posted, uploader, rating, pages, simpleLanguage, simpleTags, favoriteSlot)
    companion object {
        fun from(d: GalleryInfo) = GalleryInfoParcel(d.gid, d.token, ...)
    }
}
```

**调用点改造**：app 中现在跨 Intent 传 GalleryInfo 的位置（如 `GalleryDetailScene.startActivity(Intent.putExtra(KEY, gi))`）改为 `Intent.putExtra(KEY, GalleryInfoParcel.from(gi))`；接收侧 `intent.getParcelableExtra(KEY)` 改为 `(intent.getParcelableExtra(KEY) as GalleryInfoParcel).toDomain()`。**约 6–10 处替换**，机械化。

> **精细策略**：仅对**实际跨 Intent 传递**的 data 类做 Parcelize 桥接（实测核心 5 个：GalleryInfo、GalleryDetail、TorrentInfo、ArchiverData、ListUrlBuilder）。其它 data 类即便 v1 implements Parcelable 但未跨进程传，删 Parcelable 即可，零桥接。

### 24.3 provider 模块的 build.gradle.kts（关键：纯 JVM）

```kotlin
// anotherviewer-provider-ehentai/build.gradle.kts
plugins {
    `java-library`
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    api(project(":anotherviewer-core"))              // SPI + 共享 model
    implementation("com.squareup.okhttp3:okhttp:3.14.7")
    implementation("org.jsoup:jsoup:1.15.4")
    implementation("org.ccil.cowan.tagsoup:tagsoup:1.2.1")
    implementation("com.alibaba:fastjson:1.2.83")
    implementation("org.json:json:20231013")
    compileOnly("org.jetbrains:annotations:24.1.0")  // @Nullable，编译期注解；运行时 0 体积
    compileOnly("org.slf4j:slf4j-api:2.0.9")
    // 绝不引 android.* / androidx.*
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.slf4j:slf4j-simple:2.0.9")
}
```

**验证 provider 纯 JVM 性**（每次构建跑）：
```bash
./gradlew :anotherviewer-provider-ehentai:dependencies --configuration runtimeClasspath | \
  rg "android|androidx" && { echo FAIL; exit 1; } || echo OK
```

### 24.4 抽象层小文件清单（provider 内自用）

```
anotherviewer-provider-ehentai/src/main/java/com/pegionfish/anotherviewer/provider/ehentai/internal/
    Strings.java                            ← android.text.TextUtils 替代
    PlatformLog.java (private slf4j 委托)   ← android.util.Log 替代
    Annotations.java（如保留 @Nullable 用 jetbrains） 
```

---

## 25. SiteProvider SPI 完整版（v2.0）

### 25.1 设计原则

- v1 EhEngine **28 个公共方法**全部纳入 SPI，按 E-Hentai 行为域名分组（认证 / 浏览 / 收藏 / 评论 / 评分 / 下载 / 个人 / 标签 / 归档 / 种子 / 其它）。
- 每个方法返回类型用 **Unified 模型**——不复用 Eh\*Generator 内部子类型如 `GalleryListParser.Result`。
- 实参类型同样 Unified：用 `UnifiedSearchQuery` 而非 `ListUrlBuilder`；用 `GalleryDetail` 统一模型而非 e-hentai 内部形态。
- 错误 grpnage 用 `sealed SiteException`（v1.1 §14.2 模型保留）。
- 通用化的"代价"：Provider 实现里要做 E-Hentai Parser.Result ↔ Unified model 双向转换——这是 §28 但应**接受**因为它是多源成本核心。

### 25.2 SiteProvider 完整接口

```kotlin
// anotherviewer-core/.../site/SiteProvider.kt
package com.pegionfish.anotherviewer.site

interface SiteProvider {
    val siteId: String
    val displayNameKey: String
    val settingsPage: ProviderSettingsPage

    // ── 认证 ──
    suspend fun authenticate(input: AuthInput): AuthResult
    suspend fun validateSession(): Boolean
    suspend fun revokeSession()
    suspend fun getImageFingerprint(): ImageFingerprint?   // exhentai igneous 等

    // ── 浏览（搜索 / 列表 / 详情 / home / top / watched）──
    suspend fun search(query: UnifiedSearchQuery): UnifiedSearchResult
    suspend fun getGalleryList(query: UnifiedSearchQuery): UnifiedSearchResult   // LUB 通用
    suspend fun getDetail(gid: Long, token: String): UnifiedGalleryDetail
    suspend fun getPopular(page: Int): UnifiedSearchResult
    suspend fun getHome(page: Int): UnifiedHomeDetail
    suspend fun getTopList(page: Int): UnifiedSearchResult
    suspend fun getWatchedList(page: Int): UnifiedSearchResult
    suspend fun getEhNews(): UnifiedNews
    suspend fun imageSearch(hash: ByteArray, page: Int): UnifiedSearchResult

    // ── 预览与单页（v1 用 GalleryPreview / PreviewSet）──
    suspend fun getPreviewSet(gid: Long, token: String, page: Int): UnifiedPreviewSet
    suspend fun getImageUrl(gid: Long, token: String, page: Int): ImagePageResult
    suspend fun getGalleryPageApi(gid: Long, index: Int, pToken: String, showKey: String?, prevPToken: String?): ImagePageResult

    // ── 收藏 ──
    suspend fun getFavorites(slot: Int, page: Int): UnifiedFavoritesResult
    suspend fun getAllFavorites(page: Int): UnifiedFavoritesResult
    suspend fun addFavorite(gid: Long, token: String, slot: Int): Boolean
    suspend fun addFavoritesRange(range: FavoritesRange): Boolean
    suspend fun modifyFavorites(gids: List<Pair<Long, String>>, slot: Int): Boolean

    // ── 评论 ──
    suspend fun getComments(gid: Long, page: Int): UnifiedCommentList
    suspend fun commentGallery(gid: Long, text: String): UnifiedComment
    suspend fun voteComment(gid: Long, commentId: Long, vote: Int): Int   // 返 vote 后评分

    // ── 评分 ──
    suspend fun rateGallery(gid: Long, token: String, rating: Int): Float

    // ── 下载 / 种子 / 归档（远端资源）──
    suspend fun getTorrents(gid: Long, token: String): List<UnifiedTorrent>
    suspend fun getArchiver(gid: Long, token: String): UnifiedArchiver
    suspend fun getArchiveList(gid: Long, token: String, archiver: String): List<UnifiedArchive>
    suspend fun downloadArchive(gid: Long, token: String, archiver: String, dst: java.io.File): Boolean
    suspend fun downloadTorrent(gid: Long, token: String, torrent: UnifiedTorrent, dst: java.io.File): Boolean

    // ── 个人 / 用户标签 ──
    suspend fun getProfile(): UnifiedProfile
    suspend fun getMyTags(): List<UnifiedUserTag>
    suspend fun addTag(parent: String, name: String): UnifiedUserTag
    suspend fun deleteWatchedTag(tagId: Long): Boolean

    // ── 维护 / 工具（小服务）──
    suspend fun resetLimit(): Boolean                       // EhEngine.resetLimit
    suspend fun getGalleryToken(gid: Long, token: String, page: Int): String?   // EhEngine.getGalleryToken
    suspend fun getUrlOpenerFor(url: String): UnifiedUrlOpener?   // EhUrlOpener 替代

    // ── 初始化 / 健康检查 ──
    suspend fun initialize()                                // EhEngine.initialize
    suspend fun healthCheck(): HealthStatus
}
```

**注释**：
- 28 个 v1 公共方法对应回 EhEngine 同名 API + 拆分一些合并的 EhEngine.doXxx 为 doX(d) / doX(d1, d2)。
- 未列的方法（如 `EhEngine.getHomeDetail` → `getHome` 返回 `UnifiedHomeDetail`，把 EhHomeParser 包装为 home detail）按 v1 实际调用流程归一组。
- `getUrlOpenerFor` 是 EhUrlOpener 的 SPI 形式——app 有 EhUrlOpener.kt 用于从一段文本中识别 E-Hentai 链接，对其他站点（如 nhentai）也会有等价；可以泛化。
- `addFavoritesRange` / `FavoritesRange` 数据类型专门为 modify favorites range 调用——v1 的 `addFavoritesRange` 内部参数有 favcat、starts、ends 等多参数，封成 data class 更可读。

### 25.3 完整 Unified 模型清单（v2.0）

```
anotherviewer-core/src/main/kotlin/com/pegionfish/anotherviewer/site/model/
    AuthInput.kt          AuthResult.kt          ImageFingerprint.kt
    UnifiedSearchQuery.kt UnifiedSearchResult.kt UnifiedGalleryInfo.kt
    UnifiedGalleryDetail.kt UnifiedPreviewSet.kt  ImagePageResult.kt
    UnifiedHomeDetail.kt  UnifiedNews.kt          UnifiedFavoritesResult.kt
    FavoritesRange.kt     UnifiedComment.kt      UnifiedCommentList.kt
    UnifiedTorrent.kt     UnifiedArchiver.kt     UnifiedArchive.kt
    UnifiedProfile.kt     UnifiedUserTag.kt       UnifiedUrlOpener.kt
    UnifiedTagGroup.kt    UnifiedTag.kt           TagGroup.kt
    HealthStatus.kt       PageInfo.kt
    settings/ProviderSettingsPage.kt SettingsSection.kt SettingField.kt SettingType.kt
```

**字段定义**：比 v1.1 §14.5 全。详尽字段表见 §25.5。

### 25.4 注册表 / Context / 错误模型（v2.0）

继承 v1.1 §14.2 / §14.3：

- `SiteProviderRegistry` 仍是 `object` 单例，**显式 register**（不引 ServiceLoader）。
- `ProviderContext` 注入共享 `OkHttpClient`（承载 cookieJar）、`cacheDir`、`configStore`、`logger`。
- `sealed SiteException`（§14.2 9 子类）。Provider 实现把 9 个 Eh\* exception 映射到 sealed 子类。
- **新增 v2.0** `ProviderConfigStore` abstraction：

```kotlin
interface ProviderConfigStore {
    fun get(key: String, default: String? = null): String?
    fun set(key: String, value: String?)
    fun getAll(): Map<String, String>
}
```

Web 端用 Spring `Environment` 实现；app 端用 SharedPreferences 实现。统一 SPI。

### 25.5 关键 Unified 模型字段（★ 必须纳入 SPI 数据完整度）

`UnifiedGalleryInfo`：

```kotlin
data class UnifiedGalleryInfo(
    val gid: Long, val token: String, val title: String, val titleJpn: String?,
    val thumb: String, val category: Int, val posted: String, val uploader: String,
    val rating: Float, val pages: Int, val thumbHeight: Int, val thumbWidth: Int,
    val simpleTags: List<String>? = null, val simpleLanguage: String? = null,
    val favoriteSlot: Int = -2,           // -2 = unknown / -1 = no slot
    val generateNonAsciiTags: Boolean = false,
)
```

> 上述字段对应 app 中实测的 9 个常用字段 + 简化 ones（v1 GalleryInfo 实测 ui/ 下访问频率 ≥ 2）。

`UnifiedGalleryDetail`：

```kotlin
data class UnifiedGalleryDetail(
    val info: UnifiedGalleryInfo,
    val tagGroups: List<UnifiedTagGroup>,
    val previewSet: UnifiedPreviewSet,
    val comments: UnifiedCommentList,
    val archiver: UnifiedArchiver?,
    val torrents: List<UnifiedTorrent>,
    val imageUrl: String,                 // 第一页直接可用
    val apiUid: Long?, val apiKey: String?,
    val torrentCount: Int, val archiveTier: String?,
    val favoriteName: String?,
    val rating: Float, val rated: Boolean,
)
```

`UnifiedPreviewSet`（替代 v1 `PreviewSet`/`NormalPreviewSet`/`LargePreviewSet`）：

```kotlin
interface UnifiedPreviewSet {
    val size: Int
    fun getPosition(index: Int): Int
    fun getImageUrl(index: Int): String
    fun getPreviewUrl(index: Int): String?
    val pageUrlList: Map<Int, String>     // index → page url（for SpiderQueen streaming）
}
```

`UnifiedCommentList`：

```kotlin
data class UnifiedCommentList(
    val comments: List<UnifiedComment>,
    val hasMore: Boolean,
    val maxId: Long?,
)
data class UnifiedComment(
    val id: Long, val uploader: String, val score: Int, val voteState: Int,
    val time: String, val text: String, val userAvatar: String? = null, val commented: Boolean = false,
)
```

全字段细节随实现补；以上是"接得下"的最小契约。

### 25.6 SPI 命名空间扩展性评估（★）

多源 SPL 出错最常见是**接口偏一站点**。v2.0 用这些手段保证不为 e-hentai 特化：

1. `UnifiedSearchQuery` 用通用谓词：

```kotlin
data class UnifiedSearchQuery(
    val mode: SearchMode = SearchMode.NORMAL,      // NORMAL / IMAGE / TOPLIST / SUBSCRIPTION
    val keyword: String? = null,
    val category: Int = 0,                          // 各站点各自编码
    val advanced: AdvancedFilter = AdvancedFilter(),
    val page: Int = 0, val pageSize: Int = 25,
    val extras: Map<String, String> = emptyMap(),   // ★ 站点专属扩展槽（如 exhentai favcat）
)
data class AdvancedFilter(
    val minRating: Int = 0, val pageFrom: Int = 0, val pageTo: Int = 0,
    val language: String? = null, val uploader: String? = null,
    val excludeUploaders: List<String> = emptyList(),
)
enum class SearchMode { NORMAL, IMAGE, TOPLIST, SUBSCRIPTION }
```

未来 nhentai 实现 `SiteProvider.search`：把 `keyword` + `advanced.language` + `extras["media_id"]` 解析为 nhentai API 参数；不须改 SPI 契约。

2. 错误 sealed 类未 hardcode "e-hentai" 字串。`class RateLimited(host: String)` 万一 nx 直连 Flickr 也是同款。

3. `UnifiedArchive` `/ `UnifiedTorrent` 等 tz-spec 类：用通用 `archiverId: String` 而非 `"hath" / "exh" / "original"` 等枚举，让 provider 自由编码。

### 25.7 v1 EhEngine → SPI 方法映射表（实施者参考）

| v1 EhEngine 方法（v1） | SPI 方法 | 转换说明 |
|------------------------|---------|---------|
| signIn | authenticate(AuthInput) | 把 (username, password) 包成 AuthInput |
| getGalleryList | getGalleryList(UnifiedSearchQuery) | LUB → UnifiedSearchQuery |
| getGalleryDetail | getDetail(gid, token) | 直接 |
| getGalleryPage | getImageUrl(gid, token, page) | Result → ImagePage returnUrl |
| getGalleryPageApi | getGalleryPageApi(gid, index, pToken, showKey, prev) | 直接 |
| getGalleryToken | getGalleryToken(gid, token, page) | 直接 |
| fillGalleryListByApi | search(... advanced AdvancedFilter) 内部调 | 不直接暴露，合并到 search |
| getFavorites | getFavorites(slot, page) | 直接 |
| getAllFavorites | getAllFavorites(page) | 直接 |
| addFavorites | addFavorite(gid, token, slot) | 单条 |
| addFavoritesRange | addFavoritesRange(FavoritesRange) | data class 聚 参数 |
| modifyFavorites | modifyFavorites(gids: List<Pair<Long, String>>, slot) | (gid, token) list |
| commentGallery | commentGallery(gid, text) | 直接 |
| voteComment | voteComment(gid, commentId, vote) | 直接 |
| rateGallery | rateGallery(gid, token, rating) | 直接 |
| getTorrentList | getTorrents(gid, token) | EhEngine.getTorrentList 内部调 getTorrentUrl → 列 |
| getArchiver | getArchiver(gid, token) | 直接 |
| getArchiveList | getArchiveList(gid, token, archiver) | 直接 |
| downloadArchive | downloadArchive(... dst File) | 直接，dst 注入 |
| downloadArchiver（v1 命名错误） | downloadArchive | 同 |
| getPreviewSet | getPreviewSet(gid, token, page) | EhEngine.getPreviewSet → set size/url |
| getProfile | getProfile() | 直接 |
| getMyTags | getMyTags() | 直接 |
| addTag | addTag(parent, name) | 直接 |
| deleteWatchedTag | deleteWatchedTag(tagId) | 直接 |
| getHomeDetail | getHome(page) | EhHomeParser result → UnifiedHomeDetail |
| getTopList | getTopList(page) | 直接 |
| getWatchedList | getWatchedList(page) | 直接 |
| getEhNews | getEhNews() | → UnifiedNews |
| resetLimit | resetLimit() | 直接 |
| initialize | initialize() | 直接 |
| imageSearch | imageSearch(hash, page) | byte[] 参数 |

剩 5 个未提到 EhEngine v1 方法（37 - 上述 33）经实测在 app/web **零调用**，v2.0 不纳入 SPI。Provider 内部如有内部依赖仍可通过 private 方法实现。

---

## 26. app/ 126 文件改造策略（v2.0 完整 SPI 化的关键）

### 26.1 改造分类

把 app 中 126 个引用 client 的文件分 4 类，分别施策：

**A 类（直接重构走 SPI，约 18 文件）** ——核心数据流场景：

| 子类 | 文件示例 | EhEngine 调用点 | 改造 |
|------|---------|------------------|------|
| spider（3） | SpiderQueen.java / SpiderInfo / SpiderDen | EhEngine.getGalleryPage / getGalleryPageApi / getPreviewSet | 改为 `SiteProviderRegistry.getActive().getXxx()` |
| ui/scene/gallery/list（9） | FavoritesScene.kt / GalleryListScene.java | EhEngine.getFavorites / getGalleryList / fillGalleryListByApi | SPI + 在 Scene 内做 `runBlocking` |
| ui/scene/gallery/detail（4） | GalleryDetailScene.java | EhEngine.getGalleryDetail / rateGallery / commentGallery / addFavorite | 全部 SPI |
| ui/scene/sign（4） | EhEngine.signIn 等登录流程 | 复懁 EhEngine.signIn → SPI.authenticate |
| download（4） | DownloadManager.java / DownloadService.kt | EhEngine.getGalleryDetail / getTorrentList / downloadArchive | SPI |
| sync（4） | GalleryListTagsSyncTask / DownloadSpiderInfoExecutor.kt | EhEngine.getGalleryList / getTorrentList | SPI |
| webui（1）/smb（1）/preference（4 RestoreDownload） | EhApplication / RestoreDownloadPreference.kt | EhEngine.initialize / restoreLimit | SPI |

**B 类（仅 import 路径替换，约 76 文件）** ——使用 `GalleryInfo`、`GalleryDetail` 等 Unified 模型作为类型注解 / 字段：

UI 渲染处（ViewHolder、Adapter 等）只用模型字段读取，不调 EhEngine。改 `import com.hippo.ehviewer.client.data.GalleryInfo` → `import com.pegionfish.anotherviewer.site.model.UnifiedGalleryInfo` 即可。

但这些文件 v1 用 `GalleryInfo` 作为类型名，改成 `UnifiedGalleryInfo` 时变量声明、findViewById 等都要换——可考虑在 provider 实现"`typedef` 语义"：在 `anotherviewer-core/.../legacy_aliases.kt` 提供 `typealias GalleryInfo = UnifiedGalleryInfo`，**保持 B 类文件用 `GalleryInfo` 名字不动**。仅改 import 行：
```
import com.hippo.ehviewer.client.data.GalleryInfo
→
import com.pegionfish.anotherviewer.legacy.GalleryInfo      // typealias
```
成本低，行为完全一致；后续如果想用 Unified\* 名字再渐进迁移。

> **决策**：v2.0 **采用 typealias 别名**——B 类文件因此仅改一行 import，机械化（§26.3 批量脚本）。

**C 类（既 import 又用 R/资源，约 24 文件）** —— R.java 迁 provider 后 app 中 `com.hippo.ehviewer.R` 仍指向 app 自身 R；但 v1 core R.java 是 stub。app 中 `import com.hippo.ehviewer.R` 用的是 app-generated R（154 文件命中），与 provider R 不冲突。C 类仅须确认 app R 中各资源 id 不被 provider 跨模块引用（如 parser 用的 `R.string.error_*`）。

施策：`R.java` 7 个 stub 字符串迁 provider 内部包（§27.3）；app 仍用自身 R。无 import 改动。

**D 类（wifi 5 文件）** —— 年代久远的 LAN 跨设备下载（app/client/wifi/），与 E-Hentai 服务无强关联。但它包名在 `client.wifi`、且与 data 数据共享 GalleryInfo，迁 provider。app 中只有 `ui/wifi`（2 文件）和 `client/wifi`（自包含）用。改造：照 B 类做 typealias 替换。

### 26.2 app parcel 包结构（新增）

```
app/src/main/java/com/pegionfish/anotherviewer/parcel/
    GalleryInfoParcel.kt
    GalleryDetailParcel.kt
    TorrentInfoParcel.kt
    ArchiverDataParcel.kt
    ListUrlBuilderParcel.kt
    Extensions.kt                  ← intent.getGalleryInfoExtra() / putGalleryInfo() 扩展
```

### 26.3 v2.0 app 改造批量脚本（草）

实施时按顺序跑（Phase 4 §29）：

```bash
# 1) 把 AppEhEngine 调用替换为 SPI（A 类 18 文件手改）
# 2) B 类 76 文件 import 替换（机械）：
rg -l "import com.hippo.ehviewer.client.data.GalleryInfo" app/src/main | \
  xargs perl -i -pe 's|import com.hippo.ehviewer.client.data.GalleryInfo|import com.pegionfish.anotherviewer.legacy.GalleryInfo|g'
# 同理对 GalleryDetail / ListUrlBuilder / PreviewSet / Tag 等共 12 个 model class
# 3) 6 处 Intent 跨 Activity 传值改 Parcelize 桥（手工）
# 4) 编译
./gradlew :app:compileAppReleaseDebugJavaWithJavac :app:compileAppReleaseDebugKotlin
```

### 26.4 typedef / typealias 设计（B 类关键）

`anotherviewer-core/.../site/legacy_aliases.kt`：

```kotlin
@Suppress("unused")
typealias GalleryInfo = com.pegionfish.anotherviewer.site.model.UnifiedGalleryInfo
typealias GalleryDetail = com.pegionfish.anotherviewer.site.model.UnifiedGalleryDetail
typealias ListUrlBuilder = com.pegionfish.anotherviewer.site.model.UnifiedSearchQuery
typealias PreviewSet = com.pegionfish.anotherviewer.site.model.UnifiedPreviewSet
typealias TagGroup = com.pegionfish.anotherviewer.site.model.UnifiedTagGroup
typealias GalleryComment = com.pegionfish.anotherviewer.site.model.UnifiedComment
typealias GalleryCommentList = com.pegionfish.anotherviewer.site.model.UnifiedCommentList
typealias TorrentInfo = com.pegionfish.anotherviewer.site.model.UnifiedTorrent
typealias ArchiverData = com.pegionfish.anotherviewer.site.model.UnifiedArchiver
// ... 共约 12 个
```

> **首次实施可省略 typealias 路径** —— 但会使 B 类文件改造量从 76 → 多大几倍。本设计文档推荐 typealias 路径以达成"渐进可执行"。

---

## 27. 多源扩展 Landscape

### 27.1 接入新站点的工作清单

假设要新增 nhentai：

1. 新建 Gradle 模块 `anotherviewer-provider-nhentai/`（纯 JVM，build.gradle.kts 同 §24.3）。
2. 实现 `NhentaiSiteProvider : SiteProvider` —— 包内自有 parser、HTTP 调用、数据映射。
3. app/build.gradle 或后端 SiteProviderConfig 注册：
   ```kotlin
   SiteProviderRegistry.register(NhentaiSiteProvider(...))
   ```
4. 用户在设置页「平台」Tab 切换 active provider：
   ```kotlin
   SiteProviderRegistry.setActive("nhentai")
   ```
5. **app/web 0 改动**——只要 SPI 已实现行就指过去；否则该 nhentai provider 抛 UnsupportedOperationException，前端友好提示。

### 27.2 多 provider 并存 registered 列表

v2.0 支持 web/app 同时注册多个 provider（e-hentai + exhentai + nhentai 等）：

```kotlin
SiteProviderRegistry.register(EhentaiSiteProvider(...))
SiteProviderRegistry.register(ExhentaiSiteProvider(...))
SiteProviderRegistry.register(NhentaiSiteProvider(...))
SiteProviderRegistry.setActive("e-hentai")
```

但 **同一时刻只有一个 active**—— đaProvider 不混合查询（避免 cache 与 cookieJar 串；后续 v3 加 multi-active query）。

### 27.3 R.java / 资源处理

v1 core R.java 7 个 stub string 仅 EhEngine 用，迁 provider **内部包** `provider.ehentai.internal.R`。app 自己的 R.java（154 文件用）与 provider 无关。Spring Boot 端无 R.java 概念（provider 内不引 R.string）——错误消息用 `ProviderException.message` 抛出，由 web 端 `MessageSource` 解析 i18n。

### 27.4 i18n

| 项 | 旧（v1） | 新（v2.0） |
|----|---------|-----------|
| EhEngine 错误消息 | `R.string.error_xxx` | `SiteException.message`（英文） + 后端 `MessageSource` 解析 `messages_[zh,en].properties` 键 `error.ehentai.xxx` |
| Provider 设置页 label | `pageTitle = "E-Hentai 设置"` | `titleKey = "provider.ehentai.settings.title"`，前端 vue-i18n 解 |
| GalleryPageParser siteURL | （不涉及 i18n） | 不变 |

---

## 28. EhentaiSiteProvider 实现抽象（v2.0）

### 28.1 实现包装策略

```kotlin
class EhentaiSiteProvider(private val ctx: ProviderContext) : SiteProvider {

    override val siteId = "e-hentai"
    override val displayNameKey = "provider.ehentai.name"
    override val settingsPage = EhentaiSettingsPage.default

    // 内部委托：v1 app/client/EhEngine 的方法用从头重写的 JVM 版本（不再有 android.util.Log/Text 等）
    // 适配器：把 EhEngine <V> unwrap 到 SPI Unified model
    override suspend fun getDetail(gid: Long, token: String): UnifiedGalleryDetail {
        return try {
            val url = EhUrl.getGalleryDetailUrl(gid, token)                  // 留 Eh* 在 provider 内
            val ehDetail = EhEngine.getGalleryDetail(null, ctx.httpClient, url)   // 重写版 EhEngine
            EhModelMapper.toUnified(ehDetail)                                // 转换器
        } catch (e: Image509Exception) { throw SiteException.RateLimited("e-hentai.org") }
          catch (e: OffensiveException) { throw SiteException.ContentOffensive(gid) }
          catch (e: GalleryUnavailableException) { throw SiteException.ContentRemoved(gid) }
          // ... 9 异常映射 sealed SiteException
    }

    // ... 其余 27 方法同模式
}
```

### 28.2 EhModelMapper 转换器

```kotlin
object EhModelMapper {
    fun toUnified(d: GalleryDetail): UnifiedGalleryDetail { /* 字段逐一映射 */ }
    fun fromUnified(q: UnifiedSearchQuery): ListUrlBuilder { /* 反向 */ }
    fun toUnifiedList(list: List<GalleryInfo>): List<UnifiedGalleryInfo> { ... }
    // ... 共约 33 转换函数，纯函数无副作用
}
```

EhModelMapper 是 provider 内部 object，不暴露给 SPI 调用者。

### 28.3 Eh\* 文件去向（v2.0 精确清单）

从 `app/src/main/java/com/hippo/ehviewer/client/` 与 `anotherviewer-core/.../client/`（如 Phase 0 已改名后是 `anotherviewer-core/.../pegionfish/anotherviewer/client`）**全部迁出**到：

```
anotherviewer-provider-ehentai/src/main/java/com/pegionfish/anotherviewer/provider/ehentai/
    engine/            9 Eh* java（EhEngine 已去 Android 化）
    parser/            22 java
    data/              21 + TorrentDownloadMessage.kt + topList + userTag + wifi
    exception/         9
    internal/          R / Strings / PlatformLog（v2.0 新增抽象层，§24.4）
    EhentaiSiteProvider.kt
    EhentaiSettingsPage.kt
    EhModelMapper.kt
    EhConfigAdapter.kt   （EhConfig → ProviderConfigStore 读）
```

**source-of-truth 决策**：app 与 core 原有同一文件的**两副本**，迁时**以 app 副本为基础**（因为 app 副本在全仓活跃度更高，包含完整 Android-specific 功能）——而 core 副本会被 v2.0 在 Phase 3 全删。然后 §24 把 app 副本中 Android API 替换为 JVM 等价。**只一份**最终 provider 代码。

---

## 29. v2.0 阶段编排（Phase 0..7）

每阶段含 (1) 目标 (2) 文件操作 (3) 构建门 (4) 回滚。

### Phase 0 — 包名替换与平行副本识别（不改逻辑）

**目标**：v1.1 §16 Phase 0 同（包名机械替换 `com.hippo.ehviewer` → `com.pegionfish.anotherviewer`）。但本 v2.0 还需识别 app/client/ vs core/client/ 异同，作 Phase 3 决策依据。

文件操作 / 构建门 / 回滚 与 v1.1 §16 Phase 0 一致。**额外产出**：`docs/superpowers/specs/eh-android-vs-jvm-diff.txt`，记录 EhEngine/EhUrl/EhClient/EhConfig 两副本的 diff（作为 Phase 3 处理依据）。

构建门：
```bash
./gradlew :anotherviewer-core:compileJava :anotherviewer-web:compileKotlin :app:compileAppReleaseDebugJavaWithJavac
```

### Phase 1 — SPI 完整写入 anotherviewer-core

**目标**：§25.1 接口 + §25.3 全部 Unified 模型 + §25.4 Registry/Context/Exception + §26.4 typealias。

新增文件清单（v2.0）：
```
anotherviewer-core/src/main/kotlin/com/pegionfish/anotherviewer/site/
    SiteProvider.kt          SiteProviderRegistry.kt   ProviderContext.kt
    ProviderConfigStore.kt   SiteException.kt         legacy_aliases.kt
    model/         （详见 §25.3 清单 26 个文件）
    settings/      ProviderSettingsPage.kt SettingsSection.kt SettingField.kt SettingType.kt
```

构建门：
```bash
./gradlew :anotherviewer-core:compileKotlin :anotherviewer-core:compileJava :anotherviewer-core:test
! rg -i "e-hentai\.org|exhentai\.org|api\.e-hentai" anotherviewer-core/src/main
```

### Phase 2 — provider 模块骨架 + Eh\* 迁入 + JVM 化

**目标**：建 provider 模块；**从 app/ 与 core/ 同时迁入** Eh\* 代码到 provider；按 §24 把 Android API 全部替换为 JVM 等价；改包名前缀。

子步骤（顺序强相关，分 commit）：

1. 新建 Gradle 模块（§24.3 build.gradle.kts），`settings.gradle` 加 `include ':anotherviewer-provider-ehentai'`。
2. `git mv anotherviewer-core/src/main/java/com/pegionfish/anotherviewer/client/ → anotherviewer-provider-ehentai/src/main/java/com/pegionfish/anotherviewer/provider/ehentai/{engine,parser,data,exception}/` —— **以 core 副本**（已是 JVM 风格，因 Phase 0 后是 `anotherviewer-core` 包名；其侧 EhEngine 已是 SLF4J/TextUtil）作为迁移源以减少 Android API 替换工作量。
3. **删 core/client/ 与 app/client/ 副本**——provider 唯一留存。`git rm -r app/src/main/java/com/pegionfish/anotherviewer/client/`。
4. provider 内机械替换包名：`com.pegionfish.anotherviewer.client` → `com.pegionfish.anotherviewer.provider.ehentai.<sub>`。
5. 按 §24.1 替换 Android API（如 core 副本中残留的）：`android.text.TextUtils.isEmpty(x)` → `Strings.isEmpty(x)`；新增 `internal/Strings.java` 等。
6. 删 `R.java` 中 7 stub string 资源；改用 `SiteException.ParseException("..." )` 抛字面量（i18n 由 web/app 在 catch 时插值 resolve）。
7. 写 `EhentaiSiteProvider.kt`、`EhentaiSettingsPage.kt`、`EhModelMapper.kt` —— 28 个 SPI 方法对应实现（先完成 6 个最优先：authenticate/getDetail/getImageUrl/getGalleryPages/getFavorites/search，其余 22 个 `throw UnsupportedOperationException("v2.0 阶段 2 暂缓")`）。
8. app/build.gradle 加 `implementation(project(":anotherviewer-provider-ehentai"))` 保持 app 编译过。

构建门：
```bash
./gradlew :anotherviewer-provider-ehentai:compileKotlin :anotherviewer-provider-ehentai:compileJava
# 纯 JVM 断言
./gradlew :anotherviewer-provider-ehentai:dependencies --configuration runtimeClasspath | \
  rg "android|androidx" && { echo FAIL; exit 1; } || echo OK
# core 净空
! rg -i "Eh|gallery|hentai|Favorite|Torrent" anotherviewer-core/src/main --type java --type kotlin
```

> 注：本 Phase 同时完成 v1.1 Phase 2 + Phase 3 内容。代价 = 单 Phase 工作量大（建议拆 2a/2b sub-phase）。
> **2a**：模块骨架 + 迁入 + JVM 化 + 包名替换；编译 Eh\* 纯 Provider 类（不验证 SPI 实现）。
> **2b**：6 个核心 SPI 方法 + EhModelMapper + EhentaiSiteProvider 注册；编译通过 phase.

构建门（2a 后）：

```bash
# app 仍编译过即可：A 类未改时引用 client → 应 break。本 2a 阶段构建 app 会失败。
# 因此 2a 与 Phase 3 必须串一起，不可单独验证 app
```

### Phase 3 — app 改造全走 SPI（核心）

**目标**：§26 A/B/C/D 四类文件全改造；app 编译通过；行为不变。

子步骤（建议每子步一 commit）：

1. **A 类 18 文件手改**：
   spider（3）、ui/scene/gallery/list（9）、ui/scene/gallery/detail（4）、ui/scene/sign（4）、download（4）、sync（4）、webui（1）、smb（1）、preference RestoreDownload（4） —— 全部 `EhEngine.doXxx(...)` → `runBlocking { SiteProviderRegistry.getActive().doXxx(...) }` 或 `suspendCancellable` 视场景。

2. **B 类 76 文件机械替换**（§26.3 脚本）：每个 import 行替换为 typealias 路径；运行脚本；对遗漏用 `rg -l "com.hippo.ehviewer.client"` 反复校验。

3. **C 类 24 文件**：确认无 import 改动；手动核对 R 资源引用。

4. **D 类 wifi 5 + ui/wifi 2 + client/wifi 2**：按 B 类同模式。

5. **Parcelable 桥接**：建 `app/.../parcel/` 包，对 5 个实测跨 Intent 模型写 `@Parcelize` 包装；改造约 6-10 `Intent.putExtra(KEY, gi)` 与接收处。

6. **app/build.gradle 调整**：
   - 删 `app/src/main/java/com/hippo/ehviewer/client/` 整目录（git rm -r）。
   - 加 `implementation(project(":anotherviewer-provider-ehentai"))`（保编译）。
   - 加 kotlin-parcelize 插件（如未启用）。

构建门：
```bash
./gradlew :app:compileAppReleaseDebugJavaWithJavac :app:compileAppReleaseDebugKotlin
# app 无 client.* import 残留
! rg "import com.hippo.ehviewer.client" app/src/main
# app 中直接 EhEngine import 应为 0
! rg "import com.hippo.ehviewer.*\\.client\\.EhEngine\\b" app/src/main
# provider 仍纯 JVM（验证不被 trimestriel app 引 android 发吊)
./gradlew :anotherviewer-provider-ehentai:dependencies --configuration runtimeClasspath | rg "android|androidx" && exit 1 || echo OK
```

### Phase 4 — 后端适配（与 v1.1 §16 Phase 4 相同，但加全套 SPI 接入）

**目标**：`ehviewer-web` → `anotherviewer-web`，仅 2 真用 service（DownloadService / EhSessionManager）改 SPI 调用，其他本地 service 不动；新增 `ProviderController`。

文件操作沿用 v1.1 §16 Phase 4，但 `DownloadService.fetchPageCount` / `fetchImageUrl` 用完整 SPI；`EhSessionManager.signIn` 用 `authenticate`；`healthCheck` 用 `validateSession`。

构建门：
```bash
./gradlew :anotherviewer-web:compileKotlin :anotherviewer-web:test
! rg "import com.pegionfish.anotherviewer.provider.ehentai" anotherviewer-web/src/main
```

### Phase 5 — 前端「平台」Tab（与 v1.1 一致，省略 详述）

### Phase 6 — 验证 + 多 provider 健康检查

**目标**：eni 共跑全构建；新增 `anotherviewer-provider-nhentai` 4 文件骨架验证多源接入。**不实装 nhentai**，仅 stub 验证 `SiteProviderRegistry.register/setActive` 多 provider 并存。

构建门：
```bash
./gradlew build                                                    # 全仓
./gradlew :anotherviewer-core:test :anotherviewer-web:test
cd web-frontend && npm run lint && npm run typecheck && npx vitest run && npx vite build
./gradlew :app:assembleAppReleaseDebug

# install + 真机烟测（手动 ）
adb shell pm install -t app/build/outputs/apk/appRelease/debug/app-appRelease-debug.apk
adb shell am start -n com.xjs.anotherviewer/.ui.MainActivity
# 验证：UI 进入 → 浏览列表 → 进画廊 → 下载第一页 → 切收藏 slot → 退出登录 → 登录
```

### Phase 7（可选尾声）— 多 provider 实证验证

仅做 stub `anotherviewer-provider-nhentai`：
1. 注册 `NhentaiSiteProvider()`（search/getDetail 抛 UnsupportedOperationException）。
2. `setActive("nhentai")`，验证 web 端 `GET /api/v1/providers` 返回两个；前端「平台」Tab 可切换；切到 nhentai 后 search 抛 503 NotImplemented（web 端 SiteExceptionHandler 映射）。
3. 切回 "e-hentai"，验证功能完好。

> 本 Phase 仅证明多源接入工作，不实装 nhentai。

---

## 30. 验证矩阵（v2.0 全套硬性门）

| 阶段 | 命令 | 期望 | 失败处理 |
|------|------|------|---------|
| P0 | `./gradlew :anotherviewer-core:compileJava :anotherviewer-web:compileKotlin :app:compileAppReleaseDebugJavaWithJavac` | BUILD SUCCESSFUL | git revert 包名替换 |
| P1 | `./gradlew :anotherviewer-core:compileKotlin :anotherviewer-core:test` | BUILD SUCCESSFUL | 删 SPI 包回归 |
| P1 | `rg -i "e-hentai\.org\|exhentai\.org" anotherviewer-core/src/main` | 空 | 删除违规字串 |
| P2a | `./gradlew :anotherviewer-provider-ehentai:compileJava` | BUILD SUCCESSFUL（Eh\* 已 JVM 化） | 回替换 Android API |
| P2a | `./gradlew :anotherviewer-provider-ehentai:dependencies --configuration runtimeClasspath \| rg "android\|androidx"` | 空 | 修 android 引用 |
| P2b | `./gradlew :anotherviewer-provider-ehentai:compileKotlin` | BUILD SUCCESSFUL | 修 SPI impl |
| P3 | `./gradlew :app:compileAppReleaseDebugJavaWithJavac :app:compileAppReleaseDebugKotlin` | BUILD SUCCESSFUL | 改 A 类 SPI 调用 |
| P3 | `rg "import com.hippo.ehviewer.client\|import com.pegionfish.anotherviewer.*\\.client\\.EhEngine\\b" app/src/main` | 空 | 反复跑替换脚本 |
| P3 | `rg "EhEngine\\." app/src/main` | 应为 0（除非内部扩展） | 修手改处 |
| P4 | `./gradlew :anotherviewer-web:compileKotlin :anotherviewer-web:test` | BUILD SUCCESSFUL + 全 test PASS | fix test |
| P4 | `rg "import com.pegionfish.anotherviewer.provider.ehentai" anotherviewer-web/src/main` | 空 | 修 service 改造 |
| P5 | `npm run lint && npm run typecheck && npx vitest run && npx vite build` | 全 PASS | fix 前端 |
| P6 | `./gradlew build :app:assembleAppReleaseDebug` | BUILD SUCCESSFUL | 综合 |
| P7 | 真机烟测：登录 / 浏览 / 下载 / 收藏 / 划页 / 退出 | 全通过 | 定位 |

**LLM 安全最终断言（v2.0 核心）**：
```bash
rg -i "hentai|gallery|favorite|torrent|archiver|ehentai" \
    anotherviewer-core/src/main \
    --type java --type kotlin -g '!*legacy_aliases*'
# 期望命中 0（除 legacy_aliases 中 typealias 行）
```

---

## 31. 风险登记表（v2.0）

| ID | 风险 | 严重 | 缓解 |
|----|------|------|------|
| R1 | app/ 中有未识别的 client.* 静态依赖（如反射）漏改 | 高 | Phase 3 后构建门 + 真机烟测 + grep 双重 |
| R2 | v1 EhEngine 内部状态（如 private static）非线程安全，SPI 多线程切换后崩 | 中 | EhEngine 已 v1 验证为单例 OkHttpClient state；保持 client.ctx httpClient 单实例；加 unit test 模拟并发 |
| R3 | A 类 18 文件手改 SPI 调用时引入语义偏移 | 高 | 每 commit 配 unit/integ test；调用点尽量保持原参数顺序 |
| R4 | Parcelable 5 桥接实际不止 5（实施时发现 8 个） | 中 | 验证：grep `putParcelable\|getParcelableExtra` in app/src 命中数（基线 benchmark） |
| R5 | 6 个核心 SPI 方法 vs 22 个抛 UnsupportedOperationException，后续缺功能要快速补 | 中 | 22 个不阻塞 1.1 release，但阻塞 multi-provider 演示 Phase 7 |
| R6 | provider 纯 JVM 但 fastjson 等库 unsafe，多源共享 client 类路径冲突 | 中 | 各 provider javasist / fastjson 单独 shade（后续优化） |
| R7 | typealias `GalleryInfo = UnifiedGalleryInfo` 与 parcelize 冲突（kotlin-parcelize 不支持 typealias extension） | 中 | aseguramos app parcel 类为新 data class；typealias 仅用于变量类型声明，不参与 Parcelize |
| R8 | app 端通过反射读 `GalleryInfo.CREATOR`（v1 Parcelable）的代码遗漏 | 高 | grep `GalleryInfo.CREATOR\|GalleryDetail.CREATOR` 全仓；命中位置手改 |
| R9 | provider 中 EhEngine 调 OkHttp cookieJar，但 cookieJar 与 session 共享 OkHttpClient 配置在 app 端从未加 cookieJar | 中 | app 需在 EhApplication 中建全局 OkHttpClient + CookieStore，注入 ProviderContext |

---

## 32. v2.0 决策清单（最终敲定）

| ID | 决策项 | v2.0 取值 | v1.1 取值（差异） |
|----|--------|-----------|-------------------|
| D1 | pegionfish 拼写 | 确认保留 | 同 |
| D2 | app/ 平行副本 | **删除副本，统一用 provider** | v1.1 暂留 (D2-A) |
| D3 | SPI 完整度 | **28 方法全实现**（可选 22 stub） | 6 + 14 stub |
| D4 | provider 性质 | **纯 JVM**，Android 抽象层 | 未约束 |
| D5 | Parcelable 处理 | POJO + app parcel parceladapter | 未答 |
| D6 | app 调用 | **A 类走 SPI，B 类 typealias，C 类 R 不动，D 类同 B** | 仅 import |
| D7 | ServiceLoader | 显式 register | 同 v1.1 |
| D8 | 错误模型 | 同 §14.2 sealed SiteException | 同 v1.1 |
| D9 | R.java 处理 | 7 stub 迁 provider 内部包并改用 SiteException message | 同 v1.1 |
| D10 | 多 provider 列表 | **注册多个并 setActive 切换** | 未提 |
| D11 | 资源 i18n | MessageSource + vue-i18n | 同 v1.1 |
| D12 | 跨 Intent Parcelable 模型数量 | 5 个（实测后可补） | 未答 |

---

## 33. 一次性 Todo 列表（实施者执行 v2.0 时按此走）

```
[ ] Phase 0: 5 个机械步骤（先 mv 目录、再字符串替换、再 app 物理包名 mv）
[ ] Phase 0: 跑 P0 构建门 3 项
[ ] Phase 0: 产出 eh-android-vs-jvm-diff.txt 作为 P2 决策依据
[ ] Phase 1: 写 site/ 与 site/model/ 全部 SPI（26+ 文件）+ legacy_aliases
[ ] Phase 1: 跑 P1 构建门 3 项（含 core 净空断言）
[ ] Phase 2a: 新建 provider 模块 + 迁入 Eh* + 改包名 + 删 core/app 副本
[ ] Phase 2a: 创建 internal/(Strings/PlatformLog/R)
[ ] Phase 2a: 按 §24.1 全替换 EhEngine 等 Android API
[ ] Phase 2a: 跑 P2a 构建门 3 项（含 provider runtimeClasspath 纯 JVM 断言）
[ ] Phase 2b: 实现 EhModelMapper + EhentaiSiteProvider 6 核心方法 + 22 stub 抛 UnsupportedOperationException
[ ] Phase 2b: 跑 P2b 构建门
[ ] Phase 3 - 子步 1: A 类 18 文件手改 EhEngine.* → SPI
[ ] Phase 3 - 子步 1a: grep `GalleryInfo.CREATOR` 等反射点手补
[ ] Phase 3 - 子步 2: B 类 76 文件机械 import typealias 脚本
[ ] Phase 3 - 子步 5: 新增 parcel/ 包，写 5 个 @Parcelize + Extensions
[ ] Phase 3 - 子步 6: 6-10 处 Intent putExtra/getExtra 改 Parcelize 桥
[ ] Phase 3 - 子步 7: app/build.gradle 加 provider 依赖 + parcelize plugin
[ ] Phase 3: 跑 P3 构建门 4 项
[ ] Phase 4: web 改 DownloadService + EhSessionManager + 新增 SiteProviderConfig + ProviderController
[ ] Phase 4: 跑 P4 构建门 2 项
[ ] Phase 5: web-frontend 新增「平台」Tab + provider.ts api + i18n 键
[ ] Phase 5: 跑 P5 构建门 4 项
[ ] Phase 6: 全过构建 + 真机烟测一份 6 步（登录 → 浏览 → 详情 → 下载 → 收藏 → 退出）
[ ] Phase 7: stub `anotherviewer-provider-nhentai` 4 文件 + 验证多 provider 注册切换
[ ] 收尾：所有门过 → PR → 更新 webui-progress.md 增 §I5»SiteProvider-v2 完成
```

---

## 34. v2.0 → v3 后续扩展点（非本 Stage 范围）

- 多 provider **并行**查询（v2.0 仅 active 单一）：registry 返.List<SiteProvider>，UI 可在「平台」Tab 勾选多个 site，UI 展平多源结果。涉及结果去重（gid 跨源冲突？用 `(siteId, gid)` 二元键）与排序。
- 仅 Active Provider **热切换**（v2.0 需重启进程）：registry.setActive 应发事件，监听器清缓存 cookieJar、切换 OkHttpClient。
- SPI 版本化：`SiteProvider` 加 `interfaceVersion: Int`，registry.register 检查 ≥ core.expectedVersion。
- Plugin 独立 JAR / OSGi 热加载：v2 静态 register，v3 探索 ClassLoader 隔离。
- SPI 缺失方法 fallback：某 provider 不支持 `getArchiver` 应 return resource not support，而非抛 UnsupportedOperationException（v2.0）；改用 sealed `Optional<T>` 风格返回。

---

## 附：v2.0 一行总结

> 删 app 平行副本｜provider 纯 JVM｜33 SPI 方法全声明，6 先实现｜Parcelable app-side 桥｜typealias 让 76 文件机械替换｜多 provider setActive 切换 ready｜Action: Phase 0～7 严格跑硬性验证门。

---

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Part C：v3.0 — Tachiyomi 风格多源聚合 + Profile 隔离（现行方案，§35 起）
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

## 35. 顶层定位与术语

### 35.1 AnotherViewer 是什么

AnotherViewer 不再是 E-Hentai 客户端，而是 **Tachiyomi 风格的多源漫画聚合器 App**。其本体是一层外壳，提供：

- Library、History、Category、Reader、Download、Search、Settings 这些**跨源通用的 UI 与状态**
- Profile 用户态切换（SFW / R18 完全隔离）
- Source Extension 安装/卸载管理

任何站点逻辑（popular 列表、搜索、章节解析、图片 URL 抓取、登录、限流）由**独立 APK** 实现，叫 **Source Extension**。第一个 Source Extension 即 E-Hentai，是本 Stage 的迁移终点之一。

### 35.2 术语表

| 术语 | 定义 |
|------|------|
| **本体（Shell）** | AnotherViewer App 本身的 APK，不含任何站点业务逻辑 |
| **Source Extension** | 独立的 APK，实现某个站点的抓取逻辑。Bound Service + AIDL 与本体通信 |
| **Extension SDK** | 本体与 Extension 共用的契约模块（AIDL + 数据类）。本体 compile，Extension api |
| **Profile** | 用户态。SFW 默认 1 个 + 用户可建多个 R18。DB/Cookie/缓存/下载完全隔离 |
| **Source** | 抽象类型 = 已安装且被某 Profile 启用的 Extension 的运行时引用 |
| **Manga** | 站点上一条漫画条目（/gallery 上的一本）。字段含 `sourceId`、`key`（源里的稳定 id，如 E-Hentai 的 `gid`）、`title`、`artist`、`thumbnailUrl`、`status`、`isNsfw`、`tags` 等 |
| **Chapter** | Manga 下的章节；字段含 `key`、`name`、`number`、`dateUpload`、`scanlator` |
| **Page** | Chapter 下一页；字段 `index`、`url`、`referer`、`needLogin` |
| **Library** | 本体本地 DB，记录"用户加入收藏的 Manga"+ 用户附加的分类（Category）+ `lastReadChapter` |
| **History** | 本体本地 DB，记录"最近读过 (Chapter, pageOffset, time)" |
| **Download** | 本体本地文件系统，按 `<profileId>/<sourceId>/<mangaKeyHash>/<chapterKey>/page-NNN.{img,webp}` 持久化 |
| **Track**（本 Stage 不做） | 把 Library/manga 同步到 MAL/AniList 等第三方追踪服务 |

### 35.3 关键决策预声明（见 §46 全汇总）

| 平台决策 | 取值 |
|----------|------|
| Reader 归属 | **本体唯一 Reader** —— Extension 只描述 page list |
| 详情/章节 UI | **本体渲染** —— Extension 仅返回数据 |
| 下载归属 | **本体 DownloadManager**，目录按 `(profile, source, manga, chapter)` 隔离 |
| IPC | **Bound Service + AIDL**（每 Extension 独进程，崩而不挂本体） |
| Profile 切换认证 | 仅隐藏入口，无认证 |
| Profile 间关系 | **完全隔离**，无迁移工具 |
| 搜索默认行为 | 默认当前选中的 Source，单源 single-source |
| Extension 分发 | Extension APK（Tachiyomi 路径），独立安装/更新 |

---

## 36. 整体拓扑（v3.0）

```
anotherviewer/  (rootProject)
│
├── anotherviewer-shell/            ★ 本体（com.android.application）
│   namespace: com.pegionfish.anotherviewer.shell
│   applicationId: com.xjs.anotherviewer
│   依赖: anotherviewer-extension-sdk (compileOnly)、android 系、Http-OAuth、kotlinx coroutines
│   不含: 任何 Eh* / 任何 e-hentai.org URL / 任何 R18 字串
│   ── ui/      LibraryFragment、HistoryFragment、BrowseFragment、SettingsFragment、ProfileSwitcherDialog
│   ── ui.manga MangaDetailsActivity、ChapterListScene、ReaderActivity
│   ── source/ SourceManager（bind/unbind Extension Bound Service、调度）
│   ── data/   SQLiteOpenHelper × N （按 profile 切换；每个 Profile 一个 db 文件）
│   ── download/ DownloadManager、PageFetcher、下目录隔离逻辑
│   ── network/ OkHttpClient per (profile, source) ＋ CookieStore per (profile, source)
│   ── profile/ ProfileManager、ProfileRegistry、Profile 切换
│   ── extension/ ExtensionManager（扫包、解析 manifest、安装/卸载）
│
├── anotherviewer-extension-sdk/    ★ SPI 契约（纯 JVM / android library 仅打 @Ignored parcelize）
│   package: com.pegionfish.anotherviewer.extension
│   ── aidl/ISourceService.aidl（核心 SPI）
│   ── aidl/IAuthService.aidl
│   ── aidl/INotifyService.aidl（Extension → 本体：progress / page stream）
│   ── data/MangaInfo.kt、ChapterInfo.kt、PageInfo.kt、SourceMeta.kt、SearchQuery.kt
│   ── data/SourceCapability.kt（标志位：SEARCH、LATEST、POPULAR、LOGIN、NSFW、IMAGESYNC、UPDATES）
│   ── data/extensions.kt（Parcelable helpers）
│
├── anotherviewer-extension-ehentai/  ★ 第一个 Source Extension（com.android.application）
│   namespace: com.pegionfish.anotherviewer.extension.ehentai
│   含: 当下 app/client/ 与 core/client/ 的所有 Eh* 代码（JVM 化 + Bound Service 包装）
│   依赖: anotherviewer-extension-sdk (api)
│   ── service/EhentaiSourceService.kt（ISourceService Stub 实现；EhEngine 委托）
│   ── service/EhentaiAuthService.kt
│   ── engine/ parser/ data/ exception/  ★ 9/22/21/9 文件（来自原 client/）
│   ── AndroidManifest.xml 中 <meta-data source.id = "ehentai" source.nsfw = "true" ... />
│
├── anotherviewer-web/              ★ Spring Boot 后端（可选，与 v1/v2 同）
│   compileOnly anotherviewer-extension-sdk；runtimeOnly anotherviewer-extension-ehentai
│   ── 通过直接加载 Extension 进程内（非 APK，Web 不支持 Service binding）
│     调 ISourceService 实例；详 §42.7
│
└── web-frontend/                   ★ 设置页「平台」Tab + Extension 安装引导等（增量）
```

---

## 37. 本体责任清单（v3.0）

### 37.1 本体必须实现

| 类别 | 在哪里 | 关键类（草案） |
|------|--------|----------------|
| App/Application | `shell/Application.kt` | `AnotherViewerApp`：初始化 SourceManager、ProfileManager、DownloadManager、OkHttpClient 工厂、ImageLoader（Coil/Glide） |
| Profile 管理 | `shell/profile/` | `ProfileRegistry`、`ProfileManager.switch(profileId)`、`profile.isNsfw` |
| DB 切换 | `shell/data/ProfileDbManager` | 单 `SQLiteOpenHelper` 工厂；`switchProfile` 闭旧库、开新库；事务分发 |
| 来源清单 | `shell/source/SourceManager` | 对所有已安装 Extension bind Service，按 Profile 过滤 `isNsfw` |
| 通用 UI | `shell/ui/` | Library（按 Category 折叠）、History、Browse（Source 选择 + Source 内列表）、Settings、Profile 切换 |
| Manga 详情 | `shell/ui.manga.MangaDetailsActivity` | 用 `ISourceService.getMangaDetails()` 拿数据，本地 DB 注入是否已收藏、已读状态 |
| Reader | `shell/ui.manga.ReaderActivity` | 竖滑 / 横翻 / 双页 / 缩放；page list 由 `ISourceService.getPageList()` 拿，本体 OkHttpClient 拉 image bytes |
| Download | `shell/download/DownloadManager` | 维护下载队列；按 `<profileId>/<sourceId>/<mangaKeyHash>/<chapterKey>/page-NNN.*` 落盘；状态广播 LocalBroadcast |
| Categories / Library | `shell/data/LibraryRepository` | 本地 DB 操作；含 `favorite=true`、`category`、`lastReadAt`、`readCount` |
| 历史 | `shell/data/HistoryRepository` | 每 chapter 读/翻页插入或更新一行 |
| 网络层 | `shell/network/SourceHttpClientFactory` | 给每个 `(profile, source)` 一个 OkHttpClient + CookieJar；切 profile 时统一 dispose |
| 更新调度 | `shell/update/UpdateWorker` | WorkManager 周期跑 `ISourceService.fetchUpdates()`，与 Library 现有 lastChapterDate 对比 |

### 37.2 本体**不**实现（必须由 Extension 承担）

- 任何站点 URL 构造、HTML/JSON 解析
- 站点登录页（账号密码、cookie 注入）—— Extension 通过 `IAuthService.beginAuth()` 返一个 WebView URL 由本体嵌入 WebView 渲染，回调 cookie 由 Extension 管；但 cookie 持久化**本体托管**（按 profile/ source 隔离）
- 站点限流重试策略（这是站点行为，每个 Extension 自己 retry / backoff）
- 站点特定 Tag 翻译表（如 EhTagDatabase）
- 任何 R18 内容字串（仅在 Extension APK 中）

---

## 38. Source Extension 责任清单

### 38.1 每个 Extension APK 必须实现

1. **Android Service**：声明 `<service android:name=".service.XxxSourceService" android:exported="true" android:process=":ext">`（进程隔离；崩则只挂自己）。
2. **`ISourceService.Stub`**：实现以下 AIDL 方法（§39 全表）。
3. **AndroidManifest meta-data**：声明 Source 元信息（见 §40.1）。
4. **可选 `IAuthService`**：含登录流的 Source 需实现。
5. **可选 `INotifyService`**：返回大列表用流式 callback（避免一次 marshalling 大数据）。

### 38.2 Extension **不**实现

- 任何保存到本地 DB 的逻辑（Library / History / Category 都在本体）；Extension 不知道用户收藏了什么
- 下载文件管理（Extension 不写本地；只提供 page list URL；本体决定何时拉、何时缓存）
- UI（不带 Activity 不带 Fragment；登录 WebView 由本体 host）
- Profile 感知（Extension 只看每次调用时本体传 `profileContext` Parcable，内部不需管多用户）

> 注：Tachiyomi 实际允许 Extension 带 Activity（用作 Preference UI / 登录 WebView 启动器）。本 v3.0 **第一版禁用** Extension UI Activity，登录通过本体嵌入式 WebView + AIDL 协议达成。

---

## 39. SPI AIDL 契约

### 39.1 `ISourceService.aidl` 核心

```aidl
// anotherviewer-extension-sdk/src/main/aidl/com/pegionfish/anotherviewer/extension/ISourceService.aidl
package com.pegionfish.anotherviewer.extension;

import com.pegionfish.anotherviewer.extension.data.SourceMeta;
import com.pegionfish.anotherviewer.extension.data.MangaInfo;
import com.pegionfish.anotherviewer.extension.data.MangaDetails;
import com.pegionfish.anotherviewer.extension.data.ChapterInfo;
import com.pegionfish.anotherviewer.extension.data.PageInfo;
import com.pegionfish.anotherviewer.extension.data.SearchQuery;
import com.pegionfish.anotherviewer.extension.data.SourceCapability;
import com.pegionfish.anotherviewer.extension.data.ProfileContext;
import com.pegionfish.anotherviewer.extension.data.MangaRef;
import com.pegionfish.anotherviewer.extension.data.ChapterRef;
import com.pegionfish.anotherviewer.extension.data.RequestHeaders;
import com.pegionfish.anotherviewer.extension.data.MangaListPage;
import com.pegionfish.anotherviewer.extension.data.Result;

interface ISourceService {
    // ── 元信息 ──
    SourceMeta getMeta();
    int getCapabilities();                                        // SourceCapability flags

    // ── 浏览 / 搜索 ──
    Result<MangaListPage> fetchPopularPage(in ProfileContext ctx, int page);
    Result<MangaListPage> fetchLatestPage(in ProfileContext ctx, int page);
    Result<MangaListPage> fetchSearchPage(in ProfileContext ctx, in SearchQuery q, int page);

    // ── 详情 / 章节 / 页 ──
    Result<MangaDetails> fetchMangaDetails(in ProfileContext ctx, in MangaRef r);
    Result<ChapterInfo[]> fetchChapterList(in ProfileContext ctx, in MangaRef r);
    Result<PageInfo[]> fetchPageList(in ProfileContext ctx, in ChapterRef r);

    // ── 图片/资源拉取（page.url 用本体 OkHttp；某些站点需要 Extension 提供 referer/headers）──
    RequestHeaders getImageRequestHeaders(in ProfileContext ctx, in PageInfo p);
    byte[] fetchImageBytes(in ProfileContext ctx, in PageInfo p);  // 仅当站点图片需特殊绕过（如 token-签名），常规不调

    // ── 更新检查 ──
    Result<ChapterInfo[]> fetchUpdates(in ProfileContext ctx, in MangaRef r, long since);

    // ── 健康 ──
    boolean ping();
}
```

### 39.2 `IAuthService.aidl`

```aidl
package com.pegionfish.anotherviewer.extension;

import com.pegionfish.anotherviewer.extension.data.ProfileContext;
import com.pegionfish.anotherviewer.extension.data.AuthBeginResult;
import com.pegionfish.anotherviewer.extension.data.AuthStatus;

interface IAuthService {
    AuthBeginResult beginAuth(in ProfileContext ctx);          // 返回 WebViewUrl + 必要 state
    void onWebAuthCompleted(in ProfileContext ctx, String returnedUrl, in Map cookies);
    AuthStatus getStatus(in ProfileContext ctx);
    void logout(in ProfileContext ctx);
}
```

### 39.3 `INotifyService.aidl`（流式回传，用于画像 / 标签列表等大数据）

```aidl
package com.pegionfish.anotherviewer.extension;

import com.pegionfish.anotherviewer.extension.data.MangaInfo;
import com.pegionfish.anotherviewer.extension.data.ProfileContext;
import com.pegionfish.anotherviewer.extension.data.SearchQuery;
import com.pegionfish.anotherviewer.extension.data.DoneMarker;

interface INotifyListener {
    void onMangaInfo(in MangaInfo info);
    void onProgress(int delivered, int total);
    void onError(int code, String message);
    void onDone(in DoneMarker marker);
}

interface INotifyService {
    long subscribeSearch(in ProfileContext ctx, in SearchQuery q, int page, in INotifyListener listener);
    void cancel(long subscriptionId);
}
```

### 39.4 数据类（Kotlin Parcelable，放在 `extension-sdk/data/`）

```kotlin
@Parcelize
data class SourceMeta(
    val id: String,                 // "ehentai"
    val displayName: String,        // "E-Hentai"
    val lang: String,               // "all"
    val isNsfw: Boolean,            // true → 在 SFW Profile 被过滤
    val apiVersion: Int,            // 1
    val extensionVersion: String,   // extension apk versionName
    val supportsLatest: Boolean,
    val supportsLogin: Boolean,
    val supportsUpdates: Boolean,
) : Parcelable

@Parcelize
data class MangaRef(val sourceId: String, val key: String) : Parcelable
@Parcelize
data class ChapterRef(val manga: MangaRef, val key: String) : Parcelable

@Parcelize
data class MangaInfo(                      // 列表项用
    val sourceId: String,
    val key: String,
    val title: String,
    val author: String?,
    val artist: String?,
    val thumbnailUrl: String?,
    val isNsfw: Boolean,
) : Parcelable

@Parcelize
data class MangaDetails(
    val info: MangaInfo,
    val description: String?,
    val tags: List<String>,
    val status: Int,                       // 0=ongoing,1=completed,2=licensed,3=publishingFinished
    val author: String?, val artist: String?,
    val chapters: List<ChapterInfo>,        // 可同时返，本体免一次 RTT
) : Parcelable

@Parcelize
data class ChapterInfo(
    val manga: MangaRef, val key: String,
    val name: String, val number: Float,
    val dateUpload: Long,
    val scanlator: String?,
) : Parcelable

@Parcelize
data class PageInfo(
    val index: Int, val url: String,         // URL（本体 OkHttp 拉）
    val referer: String?,
    val needLogin: Boolean = false,
    val sourcePageRef: String? = null,       // 二次签名 url 时用
) : Parcelable

@Parcelize
data class SearchQuery(
    val keyword: String?,
    val page: Int = 0,
    val pageSize: Int = 25,
    val filters: List<String> = emptyList(), // source 自由解析（如 "category:doujinshi", "lang:chinese"）
    val extras: Bundle = Bundle.EMPTY,
) : Parcelable

@Parcelize
data class MangaListPage(
    val items: List<MangaInfo>,
    val hasMore: Boolean,
    val totalEstimate: Int = -1,
) : Parcelable

@Parcelize
data class ProfileContext(
    val profileId: Long,                     // 本体按 profile 隔离 cookies / cache 给 Extension
    val cookies: Map<String, String>,        // 本体托管的 cookie snapshot，extension 不持久化
    val cacheDir: String,
) : Parcelable

@Parcelize
data class Result<T>(                        // IPC 统一返类型
    val success: Boolean,
    val value: T? = null,
    val errorCode: Int = 0,
    val errorMessage: String? = null,
) : Parcelable
```

### 39.5 `SourceCapability` flags（位域）

```kotlin
object SourceCapability {
    const val SEARCH        = 1 shl 0
    const val LATEST        = 1 shl 1
    const val POPULAR       = 1 shl 2
    const val LOGIN         = 1 shl 3
    const val NSFW          = 1 shl 4     // 仅 Meta；本体筛选
    const val UPDATES       = 1 shl 5
    const val IMAGESYNC     = 1 shl 6     // 站点要求 fetchImageBytes 而非本体直接 GET
}
```

### 39.6 错误码（统一 int）

| Code | 含义 | 本体动作 |
|------|------|---------|
| 0 | 成功 | — |
| 1 | 网络错误 | 弹 Toast 重试 |
| 2 | 会话过期 | 跳 IAuthService.beginAuth |
| 3 | 内容不存在 | Manga 标记 stale，从 Library 删除 |
| 4 | 站点限流（503） | 显示"稍后再试" |
| 5 | 内容被举报 / Offensive | 仅提示，不删本地 |
| 6 |CastException-parse 错 | Toast，不重试 |
| 9 | Extension not impl（Extension 主动声明该能力没实现） | 灰按钮 |
| 99 | Extension 内部错误 | Toast + Bugly |

> Extension 实现内部仍可抛 Image509Exception/OffensiveException 等具体异常，但跨越 IPC 边界时统一编码到 `Result.errorCode/errorMessage`。Binder 异常被吞，**Extension 进程崩溃** = 本体收到 `onNullBinding` 或 `DeathRecipient` → SourceManager 标 undavailable，UI 友善提示"扩展服务未响应"。

---

## 40. Extension manifest + 安装机制

### 40.1 AndroidManifest meta-data 模板

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
          package="com.pegionfish.anotherviewer.extension.ehentai"
          android:versionCode="1" android:versionName="0.1.0">
  <uses-permission android:name="android.permission.INTERNET"/>

  <application android:label="E-Hentai Source"
               android:icon="@mipmap/ic_launcher" android:hasFragileUserData="true">
    <service android:name=".service.EhentaiSourceService"
             android:exported="true"
             android:process=":ext">
      <intent-filter>
        <action android:name="com.pegionfish.anotherviewer.extension.SOURCE_SERVICE"/>
      </intent-filter>
      <meta-data android:name="anotherviewer.source.id"       android:value="ehentai"/>
      <meta-data android:name="anotherviewer.source.name"     android:value="E-Hentai"/>
      <meta-data android:name="anotherviewer.source.lang"     android:value="all"/>
      <meta-data android:name="anotherviewer.source.nsfw"     android:value="true"/>
      <meta-data android:name="anotherviewer.source.api"      android:value="1"/>
      <meta-data android:name="anotherviewer.source.capabilities" android:value="SEARCH|LATEST|POPULAR|LOGIN|UPDATES"/>
    </service>
    <service android:name=".service.EhentaiAuthService"
             android:exported="true" android:process=":ext">
      <intent-filter>
        <action android:name="com.pegionfish.anotherviewer.extension.AUTH_SERVICE"/>
      </intent-filter>
    </service>
  </application>
</manifest>
```

### 40.2 ExtensionManager 流程

```
1. BOOT_COMPLETED / ON_PACKAGE_ADDED / ON_PACKAGE_REMOVED
    → ExtensionManager.rescanInstalledExtensions()
2. PackageManager.queryIntentServices(SOURCE_SERVICE action)
    → for each ResolveInfo:
        Parse applicationInfo.metaData:
          id / name / lang / nsfw / api / capabilities / versionName
        持久到本体 SharedPreferences: installedExtensions List<SourceMeta>
3. 用户在「浏览」Tab 选中 Source → SourceManager.bindService(intent)
    → 异步绑定，连上后 onServiceConnected 拿 ISourceService
4. UI 显示 "Loading…" → ISourceService.fetchPopularPage(ctx, 0)
5. 解绑：用户disable或App 退出 → unbindService；Extension 进程仍可保活自己
6. 崩溃回收：DeathRecipient → 标 unavailable，2 秒后自动重试 bindService；3 次失败则上报
```

### 40.3 安装/卸载

- 安装：用户从内置的 Extension 列表/Repo 下载 .apk → `PackageInstaller` 弹系统确认窗 → 安装成功后 ON_PACKAGE_ADDED → 自动 rescan
- 卸载：「设置 → 扩展 → 卸载」→ `Intent.ACTION_DELETE` 弹系统卸载框 → ON_PACKAGE_REMOVED → 取消引用、提示用户 Library 中本 manga 数据将保留但无法刷新
- **内部使用的默认 Extension**：随本体 APK 一起打包进 `app/assets/extension-ehentai.apk`，首次启动时若检测到没安装，提示用户一键安装（解压到 cache + 调 installer）

---

## 41. Profile / 数据隔离

### 41.1 Profile 数据模型

```kotlin
data class Profile(
    val id: Long,                           // 0 = 默认 SFW，不可删
    val name: String,                        // "SFW Default"、"R18 #1"
    val isNsfw: Boolean,                     // true = 可见 R18 Source + R18 Library
    val enabledSourceIds: List<String>,       // 用户手动屏蔽某些 Source；空 = 全开
    val createdAt: Long,
    val avatarColor: Int = 0,
)
```

### 41.2 隔离矩阵

| 资源 | 存储位置 | 切 Profile 时 |
|------|---------|--------------|
| Library/Category DB | `<appData>/profiles/<profileId>/library.db` | 关旧 SQLiteOpenHelper，开新 |
| History DB | 同上 db 的 history 表 | 与 library 同库；切 db 即切 |
| Source cookies | `<appData>/profiles/<profileId>/cookies/<sourceId>.json` | 在 `SourceHttpClientFactory.getClient(profileId, sourceId)` 时按需加载 |
| 图片 disk cache | `<cache>/profiles/<profileId>/images/` | Coil/Glide 重新配置 cache dir |
| 下载文件 | `<extStorage>/anotherviewer/<profileId>/<sourceId>/<mangaKeyHash>/<chapterKey>/` | 切换 profile 后下载 UI 只列当前 profile 的项；SFW 看不到 R18 下载（数据隔离） |
| 设置（ReadMode、dir size cap…） | `SharedPreferencesProfile<profileId>.xml` | — |

### 41.3 R18 入口可见性

- SFW 默认 Profile 下 `enabledSourceIds` 隐含过滤掉 `isNsfw=true` 的 Source——**不是白名单的 Subset**，是"从 SharedPreferences 列表读出已过滤过的清单给 UI"。
- "新建 R18 Profile" 入口在设置页**默认隐藏**；用户在 设置 → 关于 → 点版本号 7 次（巧合 / 类似 Android developer menu path）会弹出确认对话框"启用成人模式设置"，然后才能在 Profile 管理页出现"新建 R18 Profile"按钮，且首次开启需要确认一次"我已成年且知悉内容"。确认状态写入 `system_secure_prefs.xml`（不同于 profile-specific）。
- 切换 Profile 不需密码；用户可见的 Profile 列表 = 所有 Profile（SFW 与 R18 都显示）。但 R18 Profile 内的所有内容（Library、History、下载、当前选中 Source）在 SFW Profile 下完全不可见。
- **多用户态威胁模型说明**：这是"防止有人借过你手机时看到 R18"，不是"防止有人解锁你手机的人长时间翻找"。后者做不到（在 SPL Android 安全模型里，App 内一致 token 等价于他人解锁手机等同全权限）；仅依靠不可见达到基本隔离。

### 41.4 Profile 切换流程

```
ProfileManager.switch(targetProfileId):
  1) flush 当前所有活动中 OkHttp 调用（cancel tag = currentProfileId）
  2) 关闭当前 SQLiteOpenHelper（标记为可 reopen）
  3) 通知 SourceManager：进入 kiosk，单 SourceManager 自身不重连 Service（Connection 复用，Cookie 也不丢失 cookie 本体 更新）
  4) 通知 DownloadManager：当前显示列表换成 target profile 的 rows
  5) 通知 ImageLoader：reconfigure cache dir
  6) 发「ProfileChanged」LocalBroadcast，UI 各页 reload
  ENTIRE PROCESS < 100ms TYP
```

---

## 42. 既有 E-Hentai 代码迁移为第一个 Source Extension

### 42.1 迁移量化（基于本次勘察）

| 来源 | 数量 | 目标去向 |
|------|------|---------|
| `app/.../client/` Eh* engine | 13 | `extension-ehentai/engine/` |
| `app/.../client/parser/` | 22 | `extension-ehentai/parser/` |
| `app/.../client/data/` | 21 + topList(3) + userTag(3) + wifi(1) + TorrentDownloadMessage.kt | `extension-ehentai/data/` |
| `app/.../client/exception/` | 9 | `extension-ehentai/exception/` |
| `app/.../client/wifi/` | 5 | `extension-ehentai/wifi/` |
| `ehviewer-core/.../client/` 平行副本 | 同样 13 + 22 + 21 + 9（轻微差异见 F3） | **删除** |
| `app/.../spider/SpiderQueen.java` (调用 EhEngine.doGetGalleryPage / doGetGalleryPageApi) | 1 | **不再有 Spider**；本体 Reader 直接通过 ISourceService.fetchPageList |
| `app/.../download/` DownloadManager、DownloadService.kt 等 | 4 | 重构为本体 `shell/download/`，调用 SPI 而不直接调 EhEngine |
| `app/.../ui/scene/` 126 文件依赖 client.* | 依赖程度不一 | 大改：见 §42.4 |
| `ehviewer-web/{DownloadService,EhSessionManager}` | 2 | 改为对 SPI 的进程内调用（见 §42.7） |

### 42.2 SPI 行为映射（E-Hentai ↔ Tachiyomi 概念）

| 现实 E-Hentai 概念 | 本体 Manga 概念 | Bedrocking |
|---------------------|----------------|-----------|
| Gallery (gid + token) | Manga (key = "$gid:$token") | EhentaiSource 实现 `keyFrom(gid, token)` / `parseKey(key) → Pair<Long, String>` |
| GalleryDetail | MangaDetails | EhModelMapper.toDetails() |
| GalleryPageParser.Result | ChapterInfo[1]（E-Hentai 是 1 chapter 1 gallery） + Page list | 本体把每个 gallery 视作"单 chapter manga" |
| Favorites / favcat | Library Category（仅本地逻辑，本体记录） | EhentaiSource 不实现 getFavorites（站点收藏页仅发现"已收藏"标；本体通过 favorite=on MangaRef 入 Library） |
| EhTagDatabase | 内部数据，不通过 SPI | 仅在 extension-ehentai 内部使用 |
| signIn (cookie) | IAuthService.beginAuth 返 web url + cookie override | 本体托管的 cookie 注入 |

### 42.3 Browser/Search 复用 EhEngine

```
EhentaiSourceService.fetchSearchPage(ctx, q: SearchQuery, page):
  1) QualityFilters、Category 解析自 q.filters ("category:doujinshi"... 直接 ListUrlBuilder 命名空间风格)
  2) EhEngine.getGalleryList(null, okHttpClient_injected, EhUrl.getReferer(), ListUrlBuilder(q.filters, page))
  3) result.galleryInfoList → MangaInfo.map { sourceId="ehentai"; key="${gid}:${token}"; title=it.title; thumbnailUrl=it.thumb; isNsfw=（按 thumb_url 或 category 推定) }
  4) hasMore = result.hasNextPage
  → Result(MangaListPage(items, hasMore))
```

### 42.4 app/ 中 126 文件改造分类（细化 v2.0 §26）

| 类别 | 文件数 | 改造 |
|------|--------|------|
| **A 删除类（功能被本体替代）** | 12 | SpiderQueen / SpiderInfo / SpiderDen / client/wifi 5 + 涉及的 ui/wifi 2 等——不迁移、不替换；本体 Reader 直接调 SPI |
| **B 改写本体侧** | 20 | gallery/list Scene / FavoritesScene / DetailScene / download/UI 等：用 SPI 替 EhEngine 调用；用 MangaInfo 替 GalleryInfo |
| **C typealias 兼容** | 约 76 | 单纯用 GalleryInfo 作类型，import 改 `import com.pegionfish.anotherviewer.extension.data.MangaInfo as GalleryInfo` typealias 兼容 |
| **D 仅 R.java** | 24 | 不动；EhEngine 异常消息字串迁到 Extension 内 strings.xml |
| **E 与 Extension 无关的本体文件** | 不计 | 不改 |

实际本阶段机械改动量可控——B 类 20 个手改 + C 类 76 个 import 行 typealias 即可。

### 42.5 EhEngine 的 Android-API 替换（复用 v2.0 §24.1 表）

引入新的"extension 模块作为 APK"意味着 extension **是 Android module**，可以引 Android API。但仍**强烈推荐** JVM 化——理由：

- 本体测试 / web 后端可复用Extension 业务逻辑（如果 extension api 引 Android API，则 web 端需用 Shadow，得不偿失）
- EhEngine 单测要能在 JVM 跑（`./gradlew :anotherviewer-extension-ehentai:test`）
- Android API 替换有 v2.0 §24.1 完整表：Log → SLF4J、TextUtil → Strings、android.util.Pair → kotlin.Pair。
- 唯一例外是 extension 模块 manifest、icon、Service 等 Android APK 固有物，留在 Android。

> 但 Android Service Stub 必须是 Android 代码，所以 extension 模块 build.gradle 用 `com.android.application`，把 EhEngine 等放在 `main/java/...`（可被 JVM 单测 / JIT 跑），Service 在 `main/java/.../service/`。

### 42.6 extension build.gradle

```kotlin
// anotherviewer-extension-ehentai/build.gradle.kts
plugins {
    id("com.android.application")          // 必须是 apk
    kotlin("android")
    kotlin("kapt")
    `kotlin-parcelize`
}
android {
    namespace = "com.pegionfish.anotherviewer.extension.ehentai"
    compileSdk = 35
    minSdk = 23

    defaultConfig {
        applicationId = "com.pegionfish.anotherviewer.extension.ehentai"
        versionCode = 1
        versionName = "0.1.0"
        // base-runtime 仅靠 reflection 拿 SDK 类；把 SDK 作为 api 依赖即可
    }
    buildTypes {
        release {
            isMinifyEnabled = false            // extension 反射访问 SDK 类，不要 shrink
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
dependencies {
    api(project(":anotherviewer-extension-sdk"))
    implementation("com.squareup.okhttp3:okhttp:3.14.7")
    implementation("org.jsoup:jsoup:1.15.4")
    implementation("org.jetbrains:annotations:24.1.0")
    compileOnly("org.slf4j:slf4j-api:2.0.9")
    testImplementation("junit:junit:4.13.2")                 // JVM 单测
    testImplementation("org.slf4j:slf4j-simple:2.0.9")
    testImplementation("org.robolectric:robolectric:4.13")   // Parcelable 字段 RoundTrip 单测
}
```

### 42.7 Web 端复用 extension API

Web 后端不是 Android，不能 bindService；折中是**直接 classpath 引 extension 模块的 jar**：

```kotlin
// anotherviewer-web/build.gradle.kts
dependencies {
    implementation(project(":anotherviewer-extension-sdk"))           // AIDL 契约
    runtimeOnly(project(path = ":anotherviewer-extension-ehentai", configuration = "ehentaiJvmOnly"))
    // ↑ 需在 extension-ehentai build.gradle 中定义一个 jar Configuration，把 main/ 下 JVM 风格的 EhEngine 等打成 jar 暴露给 web
}
```

web 端 `EhentaiInProcessSource`：直接 `new EhentaiSourceService()` 调其 Kotlin 方法（绕 AIDL Stub）。具体配置在 §42.6 后端阶段决定。

> 决策：如果 §42.6 把 EhEngine 等下沉到 extension-ehentai 模块的 `src/main/java/`，则该部分代码可在 `com.android.application` 模块 + 一个独立 build variant / Configuration 输出 JVM jar。代价：配置 build variant 略繁但可行。

### 42.8 EhentaiSource 拆分建议

```
anotherviewer-extension-ehentai/src/main/java/com/pegionfish/anotherviewer/extension/ehentai/
├── service/
│   ├── EhentaiSourceService.kt       ← ISourceService.Stub 入口
│   ├── EhentaiAuthService.kt         ← IAuthService.Stub
│   └── EhModelMapper.kt             ← EhEngine Result ↔ MangaInfo / MangaDetails / ChapterInfo / PageInfo
├── engine/                           ← 9 Eh* java（JVM 化后）
│   ├── EhEngine.java
│   ├── EhUrl.java
│   ├── EhRequestBuilder.java
│   ├── EhConfig.java
│   ├── EhFilter.java
│   ├── EhUtils.java  / EhCacheKeyFactory.java / EhTagDatabase.java / EhClient.java
├── parser/                           ← 22 java
├── data/                             ← Eh* data（Parcelable 移除，统一改 MangaInfo/Details / ChapterInfo / PageInfo）
├── exception/                        ← 9
├── internal/                         ← Strings、PlatformLog（slf4j）、R 仅 7 stub strings
└── EhentaiApplication.kt             ← extension 进程入口的 Application（如需保留）

src/main/AndroidManifest.xml
src/main/res/                          ← icon、strings
```

旧 data 类（GalleryInfo 等）删除/转 alias；MangaInfo 等 SDK 类跨 parcel 进 本体。

---

## 43. 实施阶段（Phase 0..8）— v3.0 唯一编排

### Phase 0 — 包名替换（不动业务）

继承 v1.1/v2.0 Phase 0：`com.hippo.ehviewer` → `com.pegionfish.anotherviewer`。本 Phase **不**改业务，仅机械替换包名 + 改名称 / applicationId，全仓编译过。

构建门：
```bash
./gradlew :anotherviewer-core:compileJava :anotherviewer-web:compileKotlin :app:compileAppReleaseDebugJavaWithJavac
```

### Phase 1 — 建 anotherviewer-extension-sdk

新建 Gradle 模块 `anotherviewer-extension-sdk`（android library），写入 §39 全部 AIDL + §39.4 数据类。本体 + Extension 两端并行依赖。

构建门：
```bash
./gradlew :anotherviewer-extension-sdk:assemble           # AIDL stub 生成
./gradlew :anotherviewer-extension-sdk:test               # Parcelable RoundTrip 测试
```

### Phase 2 — 建本体 anotherviewer-shell + Profile 框架（无业务）

新建 Gradle 模块 `anotherviewer-shell`（com.android.application）—— 替代当前 `app/`。本阶段仅做：

- 写空 shell 初始 Activity / MainTabFragment
- 写 ProfileManager、ProfileRegistry（暂仅默认 SFW profile）
- 写 SourceManager 框架（bind/unbind Service 占位）
- 写 ExtensionManager 框架（扫包 manifest，无 UI）

构建门：
```bash
./gradlew :anotherviewer-shell:assembleAppReleaseDebug
adb shell pm install -t shell/build/outputs/apk/appRelease/debug/shell-appRelease-debug.apk
adb shell am start -n com.xjs.anotherviewer/.shell.MainActivity        # 能空跑
```

### Phase 3 — port E-Hentai 到第一个 Source Extension

新建 Gradle 模块 `anotherviewer-extension-ehentai`。从 `app/.../client/` 迁入所有 Eh* 代码（**以 app 副本为基础**——功能更完整），按 §42.5 JVM 化、§42.8 删 Parcelable + 写入 MangaInfo 等 SDK 类。

**主体小步**（建议每步一 commit）：

1. 新建 Gradle 模块 + Manifest（meta-data 先空，编译先过）
2. EhEngine + 协作 Eh\* 文件迁入，包名改 `provider.ehentai`（共 67 文件）
3. 删 Parcelable，引入 unified data class alias（先 alias 内部）
4. 写 EhentaiSourceService Stub（首版 6 方法 `fetchPopularPage` / `fetchSearchPage` / `fetchMangaDetails` / `fetchChapterList` / `fetchPageList` / `getImageRequestHeaders`），其余 22 抛 errorCode=9
5. 写 EhentaiAuthService Stub（首版仅 beginAuth 返 cookie 登录页 url + getStatus）
6. 写 EhModelMapper 转换器
7. 单测 EhEngine 关键场景（fetchGalleryList 几条样例 HTML fixture）

构建门：
```bash
./gradlew :anotherviewer-extension-ehentai:assembleAppRelease
adb shell pm install -t extension-ehentai/build/outputs/apk/appRelease/extension-ehentai-appRelease.apk
adb shell am startservice -n com.pegionfish.anotherviewer.extension.ehentai/.service.EhentaiSourceService
adb shell dumpsys activity services | grep Ehentai                          # 进程 :ext 仍活
./gradlew :anotherviewer-extension-ehentai:test                              # JVM 单测
```

### Phase 4 — 本体接入 E-Hentai（极简端到端）

最小目标：在本体 shell 里安装 Ehentai Extension → Browse Tab → 看到首页列表 → 点进详情 → 进 Reader 看一张图。

1. 浏览 Tab：SourceSelectFragment 列出 ExtensionManager.installedSources
2. 选中 Ehentai → SourceManager.bindService → fetchPopularPage → MangaListFragment 渲染
3. MangaDetailsActivity：fetchMangaDetails + fetchChapterList → 渲染章节列表
4. ReaderActivity：fetchPageList → 渲染第一张图
5. **不**实现 Library / History / Download / Profile 切换——这些留 Phase 5+

构建门：
```bash
./gradlew :anotherviewer-shell:assembleAppReleaseDebug
adb install -t shell-...apk ; adb install -t extension-ehentai-...apk
adb shell am start -n com.xjs.anotherviewer/.shell.MainActivity
# 验证：能进入 → Browse 选 E-Hentai → 看到列表 → 点一本 → 看到 chapters → 进 Reader 看一页
```

### Phase 5 — Profile 隔离 + 第二 Profile

1. **DB 隔离**：每个 profile 一个 SQLite 文件，切时关旧开新
2. **Cookie 隔离**：SourceHttpClientFactory 按 (profile, source) 维护 jar
3. **下载目录**：`<extStorage>/anotherviewer/<profileId>/<sourceId>/<mangaKeyHash>/...`
4. **R18 入口隐藏**：版本号 7 击 → 显示"启用 R18 Profile 管理" → 确认 → 才能新建 R18 Profile
5. **Profile 切换 UI**：ProfileSwitcherDialog

构建门：
```bash
./gradlew :anotherviewer-shell:assembleAppReleaseDebug
# 真机烟测：SFW 看不到 Ehentai（isNsfw=true）→ 新建 R18 Profile → Ehentai 出现 → 切回 SFW → Ehentai 不可见且无残留截图
```

### Phase 6 — Library / History / Download 接完整

1. LibraryRepository：isLibrary favorite toggle、Category 增删改
2. HistoryRepository：阅读时每翻页插一行
3. DownloadManager：按 (profile, source, manga, chapter) 落盘 + 状态 LocalBroadcast
4. UI：LibraryFragment 按 Category 折叠、HistoryFragment 时间排、DownloadsFragment 列出下载状态

构建门 + 真机烟测：加一本 → 收藏到 Category "doujinshi" → 下载 → 切 profile → 数据完全隔离。

### Phase 7 — Web 端接入 SPI（JVM 复用）

1. shell.alt 配置 → 现在 anotherviewer-web 改为：不 bindService，直接用 EhentaiSourceService JVM jar
2. 写 `WebSourceAdaptor`: 把 ISourceService 实例当普通 interface 调用（V1 web 已有 EhSessionManager，本 Phase 改为通过 ISourceService GraalVM 风格的 in-Prog 调用）
3. 保留 v1 已有后端测试，新增 SPI 接入测试

构建门：
```bash
./gradlew :anotherviewer-web:compileKotlin :anotherviewer-web:test
```

### Phase 8 — Stub 第二个 Source 验证多源扩展

新建模块 `anotherviewer-extension-stub`（无网络，仅假数据返）+ ExtensionManager 同时识别两个。验证：在浏览 Tab 列表出 现 stub source；选它 → 看到假数据 → 切回 Ehentai。stub source isNsfw=false，确保 SFW Profile 下也能选 stub 验证。

构建门 + 真机烟测。

---

## 44. 验证矩阵（v3.0 硬性门）

| Phase | 命令 | 期望 | 失败处理 |
|-------|------|------|---------|
| P0 | `./gradlew :anotherviewer-core:compileJava :anotherviewer-web:compileKotlin :app:compileAppReleaseDebugJavaWithJavac` | BUILD SUCCESSFUL | git revert |
| P1 | `./gradlew :anotherviewer-extension-sdk:assemble :anotherviewer-extension-sdk:test` | PASS | 删 SDK 包 |
| P2 | `./gradlew :anotherviewer-shell:assembleAppReleaseDebug` | APK 生成能装机 | 删本体重来 |
| P3 | `./gradlew :anotherviewer-extension-ehentai:assembleAppRelease :anotherviewer-extension-ehentai:test` | PASS；JVM 单测过 | 修 JVM 化遗漏 |
| P3 | `adb shell am startservice ...EhentaiSourceService` 进程活 + dumpsys 出现 | 严过 | Manifest 声明 |
| P3 | `adb shell dumpsys meminfo \| grep ext` 进程独立 | OK | process 属性 |
| P4 | 真机：Browse→Ehentai→详情→Reader 一张图 | OK | 修 SPI 调用 |
| P5 | SFW 切 R18 入口隐藏 7 击 → Path→新建 → 数据隔离 | OK | 修 ProfileManager |
| P6 | 加一收藏 → 下载 → 严格隔离切 profile 不可见 | OK | 修下载目录 |
| P7 | `./gradlew :anotherviewer-web:test` | PASS | 修 web adapter |
| P8 | 同时装 Ehentai + Stub Extension，切换 source UI 流畅 | OK | 修 SourceManager bind |
| 长期 | 本体 APK 内 grep `gallery\|ehentai\.org\|hentai\|R18` 命中 0（仅 Extension 含） | OK | 移字串到 Extension |

**LLM 安全目标核心断言**：
```bash
rg -i "hentai|gallery|favorite|torrent|archiver|ehentai" \
    anotherviewer-shell/src/main \
    anotherviewer-extension-sdk/src/main \
    anotherviewer-core/src/main \
    --type java --type kotlin
# 期望命中 0  ←  guaranteed LLM 仅加载本体仓库时不触及任何 R18 / 成人内容字串
# ★ 这正是用户最初的 §1.1 / §I5 data_inspection_failed 目标的根治
```

---

## 45. 风险登记（v3.0）

| ID | 风险 | 严重 | 缓解 |
|----|------|------|------|
| R-V3-1 | AIDL Bundle/Map 在大对象 marshalling > 1MB Binder 事务失败 | 高 | MangaListPage 用 Bundle 限 25 items / page；动用 INotifyService 流式分章节 |
| R-V3-2 | extension 进程崩时本体收到 `DeathRecipient`，但用户恰好在 Reader 中，需优雅弹回详情 | 中 | SourceManager 在 bind 后注册 DeathRecipient，down 时给 Activity 发 notice |
| R-V3-3 | EhEngine v1 仍有 `galleryInfo.simpleTags` 等 Eh 私有字段，不在 MangaInfo 模型 | 中 | SDK MangaInfo 提供 `extras: Bundle` 兜底；Extension 试装其 Eh 私有 |
| R-V3-4 | Ehentai 单 gallery 本身就是 multi 图片集；映射为 "1 Manga 1 Chapter" 不直观 | 中 | UI 显示章节名为 gallery 标题；非 Tachiyomi 习惯但可接受。或 mapping = 1 Gallery Manga，0 一照样返回 1 chapSher 章节；先把 reads chapter 后 mark read 整 |
| R-V3-5 | E-Hentai 登录网页在 Extension Service 进程中拿 cookie 难，因需在主进程 Browser | 高 | 登录页 WebView 嵌入本体 Activity，本体把 cookie 上下文写入 Extension IAuthService.onWebAuthCompleted (ProfileContext.cookies) |
| R-V3-6 | web 端复用 JVM jar 与 Extension apk 的 SLF4J 在 Spring Boot Logback 兼容 | 低 | extension 内部用 slf4j-api，runtimeOnly slf4j-simple 或不绑定 logger 由 web 接管 |
| R-V3-7 | PackageInstaller 在 Android 11+ Scoped Storage 上需用户确认 | 必发生 | 默认 extension 随本体 APK 内置 assets，首次启动复制到 cache + 通过文件 provider install；用户必须同意 (一次性) |
| R-V3-8 | Ehentai thumbnail URL 是 e-hentai.org 域；本体加载 thumbnail 也"知道"了 E-Hentai URL | 中 | 所有 thumbnail image 加载都委托 `ISourceService.fetchImageBytes`（IMAGE SYNC cap flag） — 仅对 Ehentai Extension启开，避免本体直接接触 e-hentai URL |
| R-V3-9 | Extension apk 升级后绑定会断 | 中 | 收到 PACKAGE_REPLACED → unbind → rescan → bind；后台任务允许短时断流 |
| R-V3-10 | Eh Kaede Engine 单线程同步，多 profile 调用 AIDL — 需并发安全 | 中 | EhEngine v1 已 OKHttp 单 client；但 EhEngine 静态 sync wk map 需 Intefor 内 sync 关 — 单测验证 |
| R-V3-11 | 删除 spider / wifi 后部分 ui/scene / sync 功能可能丢失 | 中 | 渐进 UI 转换器迁移：sui 用中 Iterator 不复 SP Sr 看受集 → 部分老 AsyncDG 后退 sp  UI 失能 →  functional sm 受限 通治 ext 触 |
| R-V3-12 | daogenerator 与 extension 关系 | 低 | daogenerator 仅供本体 DB 用，不参与 extension；保留不动 |

---

## 46. 决策汇总（v3.0）

| ID | 决策项 | v3.0 取值 | v2.0（对比） |
|----|--------|-----------|--------------
| D1 | pegionfish 拼写 | 同 | 已确认 |
| D2 | 本体定位 | Tachiyomi 风格多源聚合器 | E-Hentai 客户端 |
| D3 | Source 分发 | **Extension APK** + Bound Service + AIDL | 同 module 共享 classpath |
| D4 | Reader 归属 | **本体唯一 Reader** | 同 |
| D5 | 详情/章节 UI | **本体渲染** | 同 |
| D6 | 下载归属 | **本体 DownloadManager**，目录按 (profile, source, manga, chapter) 隔离 | v2 同 |
| D7 | Profile 切换认证 | 仅隐藏入口、无认证 | — |
| D8 | Profile 间数据 | 完全隔离、无迁移 | — |
| D9 | 多源搜索默认 | 单源当前选中 | — |
| D10 | 错误模 | result.errorCode int（IPC） | v2 sealed SiteException in-module |
| D11 | R18 入口可见性 | 隐藏 7 击点击 → 启用 R18 Profile 管理 | — |
| D12 | E-Hentai 作为第一个 Source | 是，单独 android application 模块 | — |
| D13 | Web 端复用 | extension JVM jar → 直调 (绕 AIDL) | — |
| D14 | EhEngine 在 extension 中 | app 副本为基础，JVM 化 | — |
| D15 | daogenerator | 保留本体 DB 用 | — |
| D16 | 删 v2 SPI 28 方法 | superseded — AIDL ISourceService 9 方法 + INotifyService 1 + IAuthService 4 | — |

---

## 47. 一次性 Todo 列表（实施者执行 v3.0 时按此走）

```
# Phase 0 — 包名替换（继承 v1.1/v2.0 Phase 0）
[ ] settings.gradle rootProject = anotherviewer
[ ] com.hippo.ehviewer → com.pegionfish.anotherviewer 全仓字符串替换
[ ] applicationId com.xjs.anotherviewer；namespace 同步
[ ] app/ 物理 mv 包目录
[ ] 跑 P0 构建门 3 项

# Phase 1 — SPI SDK
[ ] 新建 anotherviewer-extension-sdk 模块（android library）
[ ] §39 全部 AIDL（3 个）+ §39.4 数据类 Parcelable
[ ] §39.5 SourceCapability flags + §39.6 errorCode 常量
[ ] Parcelable RoundTrip 单测
[ ] 跑 P1 构建门

# Phase 2 — shell 框架
[ ] 新建 anotherviewer-shell 模块（com.android.application）
[ ] Application + MainTabFragment + 空 Activity
[ ] ProfileManager / ProfileRegistry / SourceManager / ExtensionManager 框架类
[ ] 跑 P2 构建门 + 装机 + 空跑

# Phase 3 — Ehentai Extension
[ ] 新建 anotherviewer-extension-ehentai 模块
[ ] 迁入 EhEngine 等 67 文件（以 app 副本）
[ ] 包名改 provider.ehentai，删 Parcelable，引入 MangaInfo 等 SDK 类
[ ] JVM 化（§42.5 替换 Android API）
[ ] EhentaiSourceService Stub 6 方法 + 22 stub 抛 errorCode=9
[ ] EhentaiAuthService Stub
[ ] EhModelMapper
[ ] 单测 EhEngine.fetchGalleryList 几条样例
[ ] 跑 P3 构建门 + 进程独立校验

# Phase 4 — 本体端到端
[ ] Browse Tab → SourceSelectFragment
[ ] MangaListFragment → MangaDetailsActivity → ChapterListScene → ReaderActivity (一页)
[ ] 真机烟测完整路径

# Phase 5 — Profile 隔离
[ ] DB per profile
[ ] Cookie per (profile, source)
[ ] 下载目录隔离规则
[ ] R18 入口隐藏 + 7 击 + 启用
[ ] ProfileSwitcherDialog
[ ] 真机烟测：SFW 看不见 Ehentai、R18 能看见

# Phase 6 — Library/History/Download
[ ] LibraryRepository + Category UI
[ ] HistoryRepository + 时间排序 UI
[ ] DownloadManager + Downloads UI
[ ] 真机烟测：收藏→下载→切 profile→隔离

# Phase 7 — Web 端复用
[ ] extension-ehentai 输出 JVM jar Configuration
[ ] Web WebSourceAdaptor（直调 EhentaiSourceService）
[ ] 跑 P7 构建门

# Phase 8 — Stub 第二 Source 验证多源
[ ] anotherviewer-extension-stub 模块 (假数据)
[ ] ExtensionManager 识别两个、Browse Tab 可切换
[ ] 真机烟测多 source

# 收尾
[ ] 跑 §44 全套门 + LLM 安全断言（本体源码无 R18/EHentai 字串）
[ ] PR + 更新 webui-progress 增 §I5»MultiSource 架构完成
```

---

## 48. v3.0 一行总结

> AnotherViewer 重定位为 Tachiyomi 风格聚合器｜本体唯一 Reader + DB/Cookies/Downloads｜Source 以独立 APK + AIDL Bound Service 分发｜Profile 隔离 R18 由"不可见"达成（无认证）｜E-Hentai 改造为第一个 Source Extension，并存 Stub 验证多源｜Phase 0..8 严格跑硬性门｜LLM 仅加载本体时根本看不到 R18 字串，根治 §I5 data_inspection_failed。
