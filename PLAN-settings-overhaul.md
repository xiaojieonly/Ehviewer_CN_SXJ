# 设置体系重构 — 并行执行计划

> 本文档是给 AI 执行者的完整蓝图。按 Wave 顺序执行，每个 Wave 内的 Agent 并行启动。
> 执行者职责：按 Wave 派发子代理 → 等待完成 → 运行验证命令 → 提交 → 下一 Wave。

---

## 0. 架构共识（所有 Agent 的前提）

### 产品定位（用户确认，硬约束）

> **Android App 必须可以独立运行，体验完整；WebUI 是锦上添花。**
>
> 体验阶梯：有服务器 → WebUI 全功能，体验最好；没有服务器但有 SMB NAS →
> 至少保留备份能力；什么都没有 → 手机/平板上装 App 本身仍是完整产品。

由此推导的硬约束（所有 Agent 必须遵守）：

1. **App 零服务器依赖**：App 的所有功能（阅读/下载/收藏/历史/SMB 备份/设置）在
   没有 WebUI 服务器时完全可用。任何新代码不得在启动、主流程或关键路径上访问
   服务器；同步只能手动触发。
2. **失败静默降级**：WebUI 相关调用（`webui/` 包、PreferenceSyncHelper）必须
   后台执行、短超时、失败仅 Toast/日志，绝不影响主线程与主功能，绝不 crash。
3. **本地优先**：App 设置的唯一事实源是设备上的 SharedPreferences
   （`Settings.java`）；服务器偏好只是按需 push/pull 的副本。
4. **SMB 备份不回退**：无服务器场景的备份通道是现有 SMB 功能，本计划不得破坏；
   每次涉及 App 的 Wave 都要做 SMB 相关编译/回归确认。
5. **优先级排序**：App 独立运行 → App 侧同步增强（Wave 8）→ WebUI 设置体系
   （Wave 1–7）。Wave 8 与 WebUI 各 Wave 并行推进，互不阻塞，但验收时先过
   App 独立运行门。

### 配置分区

| 区域 | 路由 | 内容 | 存储 | 同步 |
|------|------|------|------|------|
| 用户偏好 | `/settings/*` | 通用、阅读器、隐私 | SQLite `user_preference` (JSON) | ✅ Android ↔ WebUI 双向 |
| 管理面板 | `/admin/*` | 下载、服务器、访问、图像处理、高级、关于 | `EhCoreConfigProperties` + `server_config` 表 | ❌ 设备级，不同步 |

### 单用户场景

- 无用户管理、无角色系统
- 管理面板是 UX 分区，不是权限隔离
- WebUI 登录默认关闭，可在管理面板开启（简单密码门禁）
- 下载路径等设备相关配置不同步

### 技术栈

- 后端: Spring Boot 3.4.5 + Kotlin + SQLite (JPA/Hibernate, `ddl-auto: update`)
- 前端: Vue 3 + TypeScript + Pinia + vue-router + Vite
- Android: Java + SharedPreferences + PreferenceFragmentCompat

### 代码规范（所有 Agent 必须遵守）

- **Kotlin**: 无分号，4 空格缩进，data class 字段一行一个，Spring 注解用构造器注入
- **Vue**: `<script setup lang="ts">`，组合式 API，CSS 用 BEM (`block__element--modifier`)，中文 UI 文案
- **Java (Android)**: 遵循现有 `Settings.java` / `WebUiSettings.java` 风格
- **注释**: 默认不写，只在 why 不明显时加
- **现有文件**: 先 read_file 确认当前内容再修改，不假设内容

---

## 0.5 现状基线（2026-07-31 核对，Agent 以此为准，不必重新探索）

> 本节是执行前的事实快照。所有 Wave 的规格以此为基础；若实现时发现与基线不符，
> 先向 lead 报告，不要自行假设。

| 事实 | 现状 |
|------|------|
| ehviewer-core 依赖 | `ehviewer-web/build.gradle.kts` 已有 `implementation(project(":ehviewer-core"))` — E-Hentai 逻辑模块**已导入**，本计划只需保证接入点正确，无需新增依赖 |
| core `Settings.java` | `ehviewer-core/src/main/java/com/hippo/ehviewer/Settings.java` 是桩类（`getGallerySite()` 固定 `SITE_E`）。**本计划不改它**：E-Hentai 逻辑（EhUrl/EhEngine/parsers）对 App 与 WebUI 完全一致且从未变动，直接复用即可；gallery site 偏好不在本计划范围 |
| 复制/分享链接 | App：`ClipboardUtil` + `EhUrl.getGalleryDetailUrl(gid, token)`（GalleryInfoScene 同款）。WebUI：`GalleryDetailView.share()` 硬编码 `https://e-hentai.org/g/${gid}/${token}/` — Wave 0 改为消费服务端 DTO 的 `galleryUrl`（由**现有** `EhUrl.getGalleryDetailUrl(gid, token)` 生成），两端口径一致 |
| Gallery DTO | `ehviewer-web/.../dto/GalleryDto.kt` 的 `GalleryItemDto` / `GalleryDetailDto` **无 `galleryUrl` 字段**（Wave 0 新增） |
| Settings DTO/Service | `SettingsResponse` 仅含 download/cache/smb；`SettingsService` 直接读写 `EhCoreConfigProperties`（进程内存，重启即丢）— Wave 1/2 的 `server_config` 表补持久化 |
| AuthStatusResponse | 现有字段 `authenticated / username / ehSessionValid / ehSessionExpired`，**无 `authRequired`**（Wave 1D 新增，默认 false） |
| SecurityConfig | 当前规则：`/api/v1/auth/**`、`/api/v1/health`、`/api/v1/metrics/**` permitAll；`/api/**` 需 Bearer；**静态资源 / SPA 深链 / `/ws/**` 已放行**（2026-07-31 修复）。Wave 2C 动态认证必须保留这三条静态放行 |
| 前端路由守卫 | `web-frontend/src/router/index.ts` 现有硬性守卫：无 token 一律跳 `/login`（与"登录默认关闭"冲突，Wave 7A 改为动态 `authRequired` 判定） |
| SyncService | `push(request)` 从 body 取 `deviceId`，**当前无 username**；`pull(since)` 无鉴权参数。preferences 按用户归属，Wave 3B 需从 `Authentication` 取 username |
| Mock server | `/api/v1/settings` GET/PUT 已存在；**无 `/api/v1/preferences`**（Wave 0/1 同步补 mock，保持前后端并行契约一致） |
| OpenAPI | 契约中无 `/api/v1/preferences`；gallery schema 无 `galleryUrl`（Wave 0 冻结后实现只读，遵守 R1） |
| 测试基线 | 后端 JUnit5 43 条；前端 vitest 205 条；构建配方见 `docs/webui-progress.md` §8（GRADLE_USER_HOME + AS JBR 21 + gradle 9.5.0 直连，wrapper 9.4.1 分发损坏不可用） |
| 品牌 | 对外名称 AnotherViewer；源码包名/类名（`com.hippo.ehviewer`、`EhUrl`、`EhEngine` 等）与许可证声明**保留不动**，确保开源合规 |
| 调用一致性 | 2026-07-31 已审计并修复：core `EhRequestBuilder` 带 Chrome 指纹、core `EhConfig` 分类位值与 app 对齐、`DownloadService` 复用 `EhSessionManager` 共享会话客户端。本计划不得回退这些点 |

---

## 1. 依赖图

```
Wave 0 ──→ Wave 1 ──→ Wave 2 ──→ Wave 3 ──┬──→ Wave 4 ──┬──→ Wave 5 ──┐
 (3 agents)  (4 agents)  (3 agents)  (2 agents)│  (3 agents)│  (3 agents) ├──→ Wave 7
                                        │             │                  │    (2 agents)
                                        │             └──→ Wave 6 ──────┘
                                        │                (4 agents)
                                        │
                                        └──→ Wave 8
                                           (2 agents, Android)
                                           与 Wave 4-7 并行
```

**并行组：**
- 组 0: Wave 0 (3 agents) — 复制功能复用 E-Hentai 逻辑契约
- 组 1: Wave 1 (4 agents)
- 组 2: Wave 2 (3 agents)
- 组 3: Wave 3 (2 agents)
- 组 4: Wave 4 + Wave 8 (5 agents 并行)
- 组 5: Wave 5 + Wave 6 (7 agents 并行)
- 组 6: Wave 7 (2 agents)

---

## 2. Wave 详细规格

---

### Wave 0: 复制功能复用 E-Hentai 逻辑契约（3 agents 并行，先于 Wave 1）

> **原则（用户确认）**：E-Hentai 相关逻辑从未变动，Android App 与 WebUI 共用同一套
> （`ehviewer-core` 已由 `ehviewer-web/build.gradle.kts` 导入）。本 Wave **只做接入与
> 契约**，不改任何 E-Hentai 逻辑文件（`EhUrl.java` / `EhEngine.java` / parsers /
> `Settings.java` 一律只读）。复制/分享链接两端统一由现有
> `EhUrl.getGalleryDetailUrl(gid, token)` 生成。

#### Agent 0A: 后端 galleryUrl 契约（DTO + OpenAPI + mock）

**读取参考:**
- `ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/GalleryDto.kt` — 现状（无 galleryUrl）
- `ehviewer-core/src/main/java/com/hippo/ehviewer/client/EhUrl.java` — **只读**，
  使用现有 `getGalleryDetailUrl(long, String)`
- `contracts/openapi.yaml` — gallery item schema
- `mock-server/routes/gallery.mjs` + `mock-server/fixtures/galleries.mjs`

**修改文件:**
- `ehviewer-web/.../dto/GalleryDto.kt`: `GalleryItemDto` 与 `GalleryDetailDto` 各新增
  `val galleryUrl: String`（不可空，服务端生成）
- 构造点：`service/GalleryService.kt`（history/favorites 的 toDto）与
  `service/DownloadService.kt`（如构造 GalleryItem）— galleryUrl 一律
  `EhUrl.getGalleryDetailUrl(gid, token)`，**不引入新逻辑**
- `contracts/openapi.yaml`: gallery item schema 增加 `galleryUrl`（只读字段）
- `mock-server/routes/gallery.mjs` 与 fixtures: 返回项补 `galleryUrl`
  （`https://e-hentai.org/g/${gid}/${token}/`，与 EhUrl 输出一致）

**验证:** `:ehviewer-web:compileKotlin`；mock 起服后
`curl http://localhost:8080/api/v1/gallery/2801001 | jq .galleryUrl`

#### Agent 0B: 前端复制/分享消费 galleryUrl

**读取参考:**
- `web-frontend/src/views/GalleryDetailView.vue`（share() 现状，硬编码 URL）
- `web-frontend/src/types/components.ts`（GalleryInfo 接口）
- 现有 GalleryDetailView spec（如有）

**修改文件:**
- `web-frontend/src/types/components.ts`: `GalleryInfo` 增加 `galleryUrl?: string`
- `web-frontend/src/views/GalleryDetailView.vue`:
  ```typescript
  async function share() {
    const g = gallery.value
    if (!g) return
    const url = g.galleryUrl ?? `https://e-hentai.org/g/${g.gid}/${g.token}/`
    // Web Share API 优先，fallback navigator.clipboard.writeText(url)
  }
  ```
  （`??` fallback 仅兼容旧缓存数据；主路径使用服务端 `galleryUrl`）

**验证:** `npm run typecheck`；新增/更新 spec 断言 clipboard 写入 URL 与 DTO
`galleryUrl` 一致（happy-dom 下 mock `navigator.clipboard.writeText`）

#### Agent 0C: 复制功能回归守卫（后端契约测试 + 前端 spec）

**创建文件:**
- `ehviewer-web/src/test/.../service/GalleryUrlContractTest.kt`：断言
  `GalleryItemDto(galleryUrl)` == `EhUrl.getGalleryDetailUrl(gid, token)`，
  形状 `https://e-hentai.org/g/{gid}/{token}/`；App 侧同方法（只读断言，防漂移）
- `web-frontend/src/views/__tests__/GalleryDetailView.copy.spec.ts`：渲染详情页 →
  点击分享/复制 → mock clipboard 收到 `g.galleryUrl`

**验证:** `:ehviewer-web:test`（新增测试通过）+ `npm test`

**Wave 0 验证门:**
```bash
cd /Users/bob/AnotherViewer && <gradle9.5 with JBR/GRADLE_USER_HOME> :ehviewer-web:test
cd web-frontend && npm test && npm run typecheck
rg -n "e-hentai.org/g/" web-frontend/src   # 仅允许 GalleryDetailView 的 fallback 一处
git diff --stat ehviewer-core               # 期望为空：core 一行不改
```

**Wave 0 提交:**
```
feat(web): galleryUrl 契约 — 复制/分享链接复用 EhUrl.getGalleryDetailUrl
```

---

### Wave 1: 后端实体 + DTO（4 agents 并行）

所有 Agent 的工作目录: `/Users/bob/AnotherViewer`
基础包路径: `ehviewer-web/src/main/java/com/hippo/ehviewer/web`

#### Agent 1A: UserPreferenceEntity + Repository

**读取参考:**
- `ehviewer-web/src/main/java/com/hippo/ehviewer/web/entity/AuthConfigEntity.kt` — 实体风格
- `ehviewer-web/src/main/java/com/hippo/ehviewer/web/repository/AuthConfigRepository.kt` — Repository 风格

**创建文件:**

`entity/UserPreferenceEntity.kt`:
```kotlin
@Entity
@Table(name = "user_preference")
class UserPreferenceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @Column(nullable = false, unique = true, length = 256)
    var username: String = ""

    @Column(nullable = false, columnDefinition = "TEXT")
    var preferences: String = "{}"    // JSON blob

    @Column(nullable = false)
    var updatedAt: Long = System.currentTimeMillis()

    @Column(length = 64)
    var updatedBy: String = ""        // 来源设备标识: "webui" / "android-xxx"
}
```

`repository/UserPreferenceRepository.kt`:
```kotlin
interface UserPreferenceRepository : JpaRepository<UserPreferenceEntity, Long> {
    fun findByUsername(username: String): UserPreferenceEntity?
}
```

**验证:** 文件语法正确，import 完整。

---

#### Agent 1B: ServerConfigEntity + Repository + Service

**读取参考:**
- `entity/AuthConfigEntity.kt`
- `repository/AuthConfigRepository.kt`
- `config/EhCoreConfigProperties.kt` — 理解现有服务器配置结构

**创建文件:**

`entity/ServerConfigEntity.kt`:
```kotlin
@Entity
@Table(name = "server_config")
class ServerConfigEntity {
    @Id
    @Column(length = 128)
    var key: String = ""

    @Column(nullable = false, columnDefinition = "TEXT")
    var value: String = ""
}
```

`repository/ServerConfigRepository.kt`:
```kotlin
interface ServerConfigRepository : JpaRepository<ServerConfigEntity, String>
```

`service/ServerConfigService.kt`:
```kotlin
@Service
class ServerConfigService(private val repo: ServerConfigRepository) {

    fun get(key: String, default: String = ""): String =
        repo.findById(key).map { it.value }.orElse(default)

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        get(key, default.toString()).toBoolean()

    fun getLong(key: String, default: Long = 0): Long =
        get(key, default.toString()).toLongOrNull() ?: default

    fun set(key: String, value: String) {
        val entity = repo.findById(key).orElse(ServerConfigEntity().apply { this.key = key })
        entity.value = value
        repo.save(entity)
    }

    fun setBoolean(key: String, value: Boolean) = set(key, value.toString())

    /** 启动时将 EhCoreConfigProperties 的默认值写入 DB（仅首次） */
    @PostConstruct
    fun initDefaults() {
        // 只写入尚不存在的 key，不覆盖已有值
        defaults.forEach { (k, v) ->
            if (!repo.existsById(k)) repo.save(ServerConfigEntity().apply { key = k; value = v })
        }
    }

    companion object {
        const val KEY_REQUIRE_AUTH = "security.require_auth"
        const val KEY_SESSION_TIMEOUT = "security.session_timeout"

        val defaults = mapOf(
            KEY_REQUIRE_AUTH to "false",
            KEY_SESSION_TIMEOUT to "86400",
        )
    }
}
```

**验证:** 编译通过。

---

#### Agent 1C: PreferenceDto

**读取参考:**
- `dto/SettingsDto.kt` — DTO 风格
- 本计划 §0 中的偏好 JSON Schema

**创建文件:**

`dto/PreferenceDto.kt`:
```kotlin
/** GET /api/v1/preferences 响应 */
data class PreferenceResponse(
    val general: GeneralPreferences = GeneralPreferences(),
    val reader: ReaderPreferences = ReaderPreferences(),
    val privacy: PrivacyPreferences = PrivacyPreferences(),
)

data class GeneralPreferences(
    val theme: String = "light",
    val themeAutoSwitch: Boolean = false,
    val launchPage: String = "homepage",
    val listMode: String = "list",
    val showReadProgress: Boolean = true,
    val detailSize: String = "long",
    val thumbSize: String = "middle",
    val historyInfoSize: Int = 100,
    val showJpnTitle: Boolean = false,
    val showGalleryPages: Boolean = false,
    val showTagTranslations: Boolean = true,
    val showGalleryComment: Boolean = true,
    val showGalleryRating: Boolean = true,
    val showEhEvents: Boolean = true,
    val showEhLimits: Boolean = true,
)

data class ReaderPreferences(
    val readingDirection: String = "rtl",
    val pageMode: String = "dual",
    val firstPageCover: Boolean = true,
    val pageScaling: String = "fit",
    val startPosition: String = "top_right",
    val autoPlayIntervalSec: Int = 2,
    val showProgress: Boolean = true,
    val showPageInterval: Boolean = true,
    val fullscreen: Boolean = true,
    val brightness: Int = 0,
)

data class PrivacyPreferences(
    val enableAnalytics: Boolean = true,
)

/** PUT /api/v1/preferences 请求 — 所有字段可选，深度合并 */
data class PreferenceUpdateRequest(
    val general: GeneralPreferences? = null,
    val reader: ReaderPreferences? = null,
    val privacy: PrivacyPreferences? = null,
)
```

**验证:** 编译通过。

---

#### Agent 1D: 扩展 SettingsDto + AuthResponse

**读取参考:**
- `dto/SettingsDto.kt` — 当前内容
- `dto/AuthResponse.kt` — 当前内容（找到 AuthStatusResponse 所在文件）

**修改文件:**

`dto/SettingsDto.kt` — 在现有 `SettingsResponse` 中新增两个 section:
```kotlin
data class SettingsResponse(
    val download: DownloadSettings,
    val cache: CacheSettings,
    val smb: SmbSettings,
    val security: SecuritySettings,          // 新增
    val processing: ProcessingSettings,      // 新增
)

// 新增:
data class SecuritySettings(
    val requireAuth: Boolean = false,
    val sessionTimeout: Long = 86400,
)

data class ProcessingSettings(
    val enabled: Boolean = false,
    val defaultType: String = "UPSCALE_2X",
    val outputFormat: String = "png",
    val outputQuality: Int = 90,
)
```

同时扩展 `SettingsUpdateRequest`:
```kotlin
data class SettingsUpdateRequest(
    val download: DownloadSettings? = null,
    val cache: CacheSettings? = null,
    val smb: SmbSettings? = null,
    val security: SecuritySettings? = null,       // 新增
    val processing: ProcessingSettings? = null,   // 新增
)
```

`dto/AuthResponse.kt`（或 AuthStatusResponse 所在文件）— 扩展:
```kotlin
data class AuthStatusResponse(
    val authenticated: Boolean,
    val username: String?,
    val authRequired: Boolean = true,     // 新增
    val ehSessionValid: Boolean,
    val ehSessionExpired: Boolean,
)
```

**注意:** 不要破坏现有字段的顺序和默认值。先 read_file 确认当前内容。

**验证:** 编译通过。

---

### Wave 1 验证门

```bash
cd /Users/bob/AnotherViewer && ./gradlew :ehviewer-web:compileKotlin
```

### Wave 1 提交

```
feat(web): 新增 UserPreference/ServerConfig 实体与偏好 DTO
```

一个 commit 包含 Wave 1 所有文件。

---

### Wave 2: 后端服务 + 控制器（3 agents 并行）

#### Agent 2A: PreferenceController + UserPreferenceService

**读取参考:**
- `api/SettingsController.kt` — Controller 风格
- `service/SettingsService.kt` — Service 风格
- `config/AuthTokenFilter.kt` — 理解 SecurityContext 中 principal 是 username 字符串
- Wave 1 产出的 `entity/UserPreferenceEntity.kt`, `dto/PreferenceDto.kt`, `repository/UserPreferenceRepository.kt`

**创建文件:**

`service/UserPreferenceService.kt`:
```kotlin
@Service
class UserPreferenceService(private val repo: UserPreferenceRepository) {

    private val mapper = jacksonObjectMapper()

    fun get(username: String): PreferenceResponse {
        val entity = repo.findByUsername(username) ?: return PreferenceResponse()
        return mapper.readValue(entity.preferences, PreferenceResponse::class.java)
    }

    fun update(username: String, request: PreferenceUpdateRequest, source: String): PreferenceResponse {
        val entity = repo.findByUsername(username)
            ?: UserPreferenceEntity().apply { this.username = username }

        // 深度合并: 只覆盖 request 中非 null 的 section
        val current = mapper.readValue(entity.preferences, PreferenceResponse::class.java)
        val merged = PreferenceResponse(
            general = request.general ?: current.general,
            reader = request.reader ?: current.reader,
            privacy = request.privacy ?: current.privacy,
        )

        entity.preferences = mapper.writeValueAsString(merged)
        entity.updatedAt = System.currentTimeMillis()
        entity.updatedBy = source
        repo.save(entity)
        return merged
    }

    /** 同步用: 全量覆盖 */
    fun replace(username: String, json: String, source: String) {
        val entity = repo.findByUsername(username)
            ?: UserPreferenceEntity().apply { this.username = username }
        entity.preferences = json
        entity.updatedAt = System.currentTimeMillis()
        entity.updatedBy = source
        repo.save(entity)
    }

    fun getRaw(username: String): String {
        return repo.findByUsername(username)?.preferences ?: "{}"
    }
}
```

`api/PreferenceController.kt`:
```kotlin
@RestController
@RequestMapping("/api/v1/preferences")
class PreferenceController(private val preferenceService: UserPreferenceService) {

    @GetMapping
    fun get(authentication: Authentication): ResponseEntity<PreferenceResponse> {
        val username = authentication.name
        return ResponseEntity.ok(preferenceService.get(username))
    }

    @PutMapping
    fun update(
        @RequestBody request: PreferenceUpdateRequest,
        authentication: Authentication,
    ): ResponseEntity<PreferenceResponse> {
        val username = authentication.name
        val merged = preferenceService.update(username, request, "webui")
        return ResponseEntity.ok(merged)
    }
}
```

**关键:** `authentication.name` 获取当前用户名（AuthTokenFilter 设置的 principal）。当 requireAuth=false 时，AuthTokenFilter 会设置一个 "anonymous" 用户（见 Agent 2C）。

**验证:** 编译通过。

---

#### Agent 2B: SettingsService 扩展

**读取参考:**
- `service/SettingsService.kt` — 当前完整内容
- Wave 1 产出的 `service/ServerConfigService.kt`, `dto/SettingsDto.kt`

**修改文件:**

`service/SettingsService.kt`:
- 注入 `ServerConfigService`
- `getSettings()` 新增 security 和 processing section 的读取:
  ```kotlin
  security = SecuritySettings(
      requireAuth = serverConfig.getBoolean(ServerConfigService.KEY_REQUIRE_AUTH, false),
      sessionTimeout = serverConfig.getLong(ServerConfigService.KEY_SESSION_TIMEOUT, 86400),
  ),
  processing = ProcessingSettings(
      enabled = serverConfig.getBoolean("processing.enabled", false),
      defaultType = serverConfig.get("processing.default_type", "UPSCALE_2X"),
      outputFormat = serverConfig.get("processing.output_format", "png"),
      outputQuality = serverConfig.get("processing.output_quality", "90").toIntOrNull() ?: 90,
  ),
  ```
- `updateSettings()` 新增 security 和 processing 的写入:
  ```kotlin
  request.security?.let { sec ->
      serverConfig.setBoolean(ServerConfigService.KEY_REQUIRE_AUTH, sec.requireAuth)
      serverConfig.set(ServerConfigService.KEY_SESSION_TIMEOUT, sec.sessionTimeout.toString())
  }
  request.processing?.let { proc ->
      serverConfig.setBoolean("processing.enabled", proc.enabled)
      serverConfig.set("processing.default_type", proc.defaultType)
      serverConfig.set("processing.output_format", proc.outputFormat)
      serverConfig.set("processing.output_quality", proc.outputQuality.toString())
  }
  ```

**验证:** 编译通过。

---

#### Agent 2C: SecurityConfig 动态认证

**读取参考:**
- `config/SecurityConfig.kt` — 当前完整内容
- `config/AuthTokenFilter.kt` — 当前完整内容
- Wave 1 产出的 `service/ServerConfigService.kt`

**修改文件:**

`config/AuthTokenFilter.kt`:
- 构造器新增 `ServerConfigService` 参数
- `doFilterInternal` 开头加判断:
  ```kotlin
  // 登录关闭时，所有请求以 anonymous 身份放行
  if (!serverConfig.getBoolean(ServerConfigService.KEY_REQUIRE_AUTH, false)) {
      val auth = UsernamePasswordAuthenticationToken("default", null, listOf(SimpleGrantedAuthority("ROLE_USER")))
      SecurityContextHolder.getContext().authentication = auth
      filterChain.doFilter(request, response)
      return
  }
  ```

`config/SecurityConfig.kt`:
- `securityFilterChain` 中 `AuthTokenFilter` 构造改为 `AuthTokenFilter(authService, serverConfigService)`
- 方法签名新增 `serverConfigService: ServerConfigService` 参数（Spring 自动注入）

**验证:** 编译通过。

---

### Wave 2 验证门

```bash
cd /Users/bob/AnotherViewer && ./gradlew :ehviewer-web:compileKotlin
```

### Wave 2 提交

```
feat(web): 偏好 CRUD API + 服务器配置服务 + 动态认证
```

---

### Wave 3: 后端 Auth 扩展 + Sync 扩展（2 agents 并行）

#### Agent 3A: EhAuthService + AuthController 扩展

**读取参考:**
- `service/EhAuthService.kt` — 当前完整内容
- `api/AuthController.kt` — 当前完整内容
- Wave 1 产出的 `dto/AuthResponse.kt`（已含 authRequired 字段）
- Wave 1 产出的 `service/ServerConfigService.kt`

**修改文件:**

`service/EhAuthService.kt`:
- 注入 `ServerConfigService`
- `getStatus()` 方法新增:
  ```kotlin
  val authRequired = serverConfig.getBoolean(ServerConfigService.KEY_REQUIRE_AUTH, false)
  // 传入 AuthStatusResponse 的 authRequired 字段
  ```

`api/AuthController.kt`:
- 确认 `getStatus` 端点正确传递新字段（通常不需要改 Controller，只改 Service）

**验证:** 编译通过。

---

#### Agent 3B: SyncDto + SyncService 偏好同步

**读取参考:**
- `dto/SyncDto.kt` — 当前完整内容
- `service/SyncService.kt` — 当前完整内容（理解 push/pull 流程）
- Wave 1 产出的 `service/UserPreferenceService.kt`

**修改文件:**

`dto/SyncDto.kt`:
- 新增:
  ```kotlin
  data class SyncPreferencesDto(
      val preferences: String = "{}",      // 完整偏好 JSON 字符串
      val lastModified: Long = 0,
      val deviceId: String = "",
  )
  ```
- `SyncEntityCollection` 新增字段:
  ```kotlin
  data class SyncEntityCollection(
      // ... 现有字段 ...
      val preferences: SyncPreferencesDto? = null,   // 新增
  )
  ```

`service/SyncService.kt`:
- 注入 `UserPreferenceService`
- `push()` 方法中处理 preferences:
  ```kotlin
  request.entities.preferences?.let { pref ->
      // last-write-wins: 只有推送方更新时才覆盖
      preferenceService.replace(username, pref.preferences, pref.deviceId)
  }
  ```
- `pull()` 方法中返回 preferences:
  ```kotlin
  val prefJson = preferenceService.getRaw(username)
  val prefEntity = preferenceRepo.findByUsername(username)
  // 构造 SyncPreferencesDto 放入 SyncEntityCollection
  ```

**注意:** SyncService 的 push/pull 方法签名中需要能获取当前 username。检查现有实现是从 Authentication 还是从 request body 获取 deviceId。preferences 的归属是 per-user（不是 per-device），所以用 username 作 key。

**验证:** 编译通过。

---

### Wave 3 验证门

```bash
cd /Users/bob/AnotherViewer && ./gradlew :ehviewer-web:compileKotlin
```

### Wave 3 提交（2 个 commit）

```
feat(web): auth/status 返回 authRequired，登录开关生效
feat(web): 同步协议扩展 preferences 双向同步
```

---

### Wave 4: 前端基础架构（3 agents 并行）+ Wave 8: Android（2 agents 并行）

> **本组共 5 个 agent 并行。** Wave 4 改 `web-frontend/`，Wave 8 改 `app/`，零冲突。

#### Agent 4A: preferences store + API 模块

**读取参考:**
- `web-frontend/src/api/settings.ts` — API 模块风格
- `web-frontend/src/api/client.ts` — Axios 实例
- `web-frontend/src/stores/theme.ts` — Store 风格
- `web-frontend/src/stores/auth.ts` — Store 风格

**创建文件:**

`web-frontend/src/api/preferences.ts`:
```typescript
import client from './client'

export interface GeneralPreferences {
  theme: string
  themeAutoSwitch: boolean
  launchPage: string
  listMode: string
  showReadProgress: boolean
  detailSize: string
  thumbSize: string
  historyInfoSize: number
  showJpnTitle: boolean
  showGalleryPages: boolean
  showTagTranslations: boolean
  showGalleryComment: boolean
  showGalleryRating: boolean
  showEhEvents: boolean
  showEhLimits: boolean
}

export interface ReaderPreferences {
  readingDirection: string
  pageMode: string
  firstPageCover: boolean
  pageScaling: string
  startPosition: string
  autoPlayIntervalSec: number
  showProgress: boolean
  showPageInterval: boolean
  fullscreen: boolean
  brightness: number
}

export interface PrivacyPreferences {
  enableAnalytics: boolean
}

export interface Preferences {
  general: GeneralPreferences
  reader: ReaderPreferences
  privacy: PrivacyPreferences
}

export const preferencesApi = {
  async get(): Promise<Preferences> {
    const { data } = await client.get('/preferences')
    return data
  },
  async update(prefs: Partial<Preferences>): Promise<Preferences> {
    const { data } = await client.put('/preferences', prefs)
    return data
  },
}
```

`web-frontend/src/stores/preferences.ts`:
```typescript
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { preferencesApi, type Preferences, type GeneralPreferences, type ReaderPreferences, type PrivacyPreferences } from '@/api/preferences'

export const usePreferencesStore = defineStore('preferences', () => {
  const prefs = ref<Preferences | null>(null)
  const loading = ref(false)

  let saveTimer: ReturnType<typeof setTimeout> | null = null

  async function load() {
    loading.value = true
    try {
      prefs.value = await preferencesApi.get()
    } finally {
      loading.value = false
    }
  }

  function updateGeneral(patch: Partial<GeneralPreferences>) {
    if (!prefs.value) return
    prefs.value.general = { ...prefs.value.general, ...patch }
    scheduleSave({ general: prefs.value.general })
  }

  function updateReader(patch: Partial<ReaderPreferences>) {
    if (!prefs.value) return
    prefs.value.reader = { ...prefs.value.reader, ...patch }
    scheduleSave({ reader: prefs.value.reader })
  }

  function updatePrivacy(patch: Partial<PrivacyPreferences>) {
    if (!prefs.value) return
    prefs.value.privacy = { ...prefs.value.privacy, ...patch }
    scheduleSave({ privacy: prefs.value.privacy })
  }

  function scheduleSave(payload: Partial<Preferences>) {
    if (saveTimer) clearTimeout(saveTimer)
    saveTimer = setTimeout(async () => {
      try {
        await preferencesApi.update(payload)
      } catch (e) {
        console.error('Failed to save preferences', e)
      }
    }, 600)
  }

  return { prefs, loading, load, updateGeneral, updateReader, updatePrivacy }
})
```

**验证:** `npm run build` 不报错（此文件暂未被引用，不会破坏构建）。

---

#### Agent 4B: 路由重构 + SettingsLayout + NavigationDrawer

**读取参考:**
- `web-frontend/src/router/index.ts` — 当前完整内容
- `web-frontend/src/components/layout/NavigationDrawer.vue` — 当前完整内容
- `web-frontend/src/views/SettingsView.vue` — 前 80 行（理解现有结构）
- `web-frontend/src/App.vue` — 理解布局

**修改文件:**

`web-frontend/src/router/index.ts`:
- 将 `/settings` 改为带子路由:
  ```typescript
  {
    path: '/settings',
    component: () => import('@/views/settings/SettingsLayout.vue'),
    children: [
      { path: '', redirect: '/settings/general' },
      { path: 'general', name: 'SettingsGeneral', component: () => import('@/views/settings/GeneralSettings.vue') },
      { path: 'reader', name: 'SettingsReader', component: () => import('@/views/settings/ReaderSettings.vue') },
      { path: 'privacy', name: 'SettingsPrivacy', component: () => import('@/views/settings/PrivacySettings.vue') },
    ],
  },
  {
    path: '/admin',
    component: () => import('@/views/admin/AdminLayout.vue'),
    children: [
      { path: '', redirect: '/admin/download' },
      { path: 'download', name: 'AdminDownload', component: () => import('@/views/admin/AdminDownload.vue') },
      { path: 'server', name: 'AdminServer', component: () => import('@/views/admin/AdminServer.vue') },
      { path: 'access', name: 'AdminAccess', component: () => import('@/views/admin/AdminAccess.vue') },
      { path: 'processing', name: 'AdminProcessing', component: () => import('@/views/admin/AdminProcessing.vue') },
      { path: 'advanced', name: 'AdminAdvanced', component: () => import('@/views/admin/AdminAdvanced.vue') },
      { path: 'about', name: 'AdminAbout', component: () => import('@/views/admin/AdminAbout.vue') },
    ],
  },
  ```
- 保留 `/smb-backup` 路由不变
- 路由守卫逻辑暂不改（Wave 7 处理）

`web-frontend/src/components/layout/NavigationDrawer.vue`:
- 在 `DEFAULT_NAV_ITEMS` 的 settings 项之后，添加分隔线和管理面板入口:
  ```typescript
  // 在 items 数组末尾或 settings 之后
  { id: 'admin', icon: 'settings', label: '管理面板', route: '/admin' }
  ```
- 具体实现需看现有 `DEFAULT_NAV_ITEMS` 的结构和模板渲染方式，可能需要支持 divider 类型

**创建文件:**

`web-frontend/src/views/settings/SettingsLayout.vue`:
```vue
<script setup lang="ts">
import { useRoute } from 'vue-router'

const tabs = [
  { path: '/settings/general', label: '通用' },
  { path: '/settings/reader', label: '阅读器' },
  { path: '/settings/privacy', label: '隐私' },
]

const route = useRoute()
</script>

<template>
  <div class="settings-layout">
    <header class="toolbar">
      <h1 class="toolbar__title">设置</h1>
    </header>
    <nav class="settings-tabs">
      <router-link
        v-for="tab in tabs"
        :key="tab.path"
        :to="tab.path"
        class="settings-tabs__tab"
        :class="{ 'is-active': route.path === tab.path }"
      >
        {{ tab.label }}
      </router-link>
    </nav>
    <main class="settings-content">
      <router-view />
    </main>
  </div>
</template>
```
样式参考现有 SettingsView.vue 的 `.settings-scene` 和 `.pref-group` 风格。

**验证:** `npm run build` 通过（子页面组件尚未创建，路由 lazy import 不会在构建时报错，但需确认 Vite 不报错。如果报错，创建空的占位组件）。

---

#### Agent 4C: AdminLayout

**读取参考:**
- `web-frontend/src/views/SettingsView.vue` — 样式参考
- `web-frontend/src/components/layout/NavigationDrawer.vue` — 布局参考

**创建文件:**

`web-frontend/src/views/admin/AdminLayout.vue`:
```vue
<script setup lang="ts">
import { useRoute } from 'vue-router'

const sections = [
  { path: '/admin/download', label: '下载', icon: 'download' },
  { path: '/admin/server', label: '服务器', icon: 'server' },
  { path: '/admin/access', label: '访问', icon: 'lock' },
  { path: '/admin/processing', label: '图像处理', icon: 'image' },
  { path: '/admin/advanced', label: '高级', icon: 'tune' },
  { path: '/admin/about', label: '关于', icon: 'info' },
]

const route = useRoute()
</script>

<template>
  <div class="admin-layout">
    <aside class="admin-layout__sidebar">
      <h2 class="admin-layout__heading">管理面板</h2>
      <nav class="admin-layout__nav">
        <router-link
          v-for="s in sections"
          :key="s.path"
          :to="s.path"
          class="admin-layout__link"
          :class="{ 'is-active': route.path === s.path }"
        >
          {{ s.label }}
        </router-link>
      </nav>
    </aside>
    <main class="admin-layout__content">
      <router-view />
    </main>
  </div>
</template>
```

布局: 宽屏时左侧固定侧边栏 (240px) + 右侧内容区；窄屏时侧边栏变为顶部水平滚动 tab。参考 Jellyfin 管理面板的响应式布局。CSS 变量复用现有主题 (`--color-primary`, `--color-background-floating` 等)。

**验证:** `npm run build` 通过。

---

#### Agent 8A: Android PreferenceSyncHelper（与 Wave 4 并行）

> **本计划 App 侧是重点**（用户确认：工作主要在 App 自身上下手）。App 的 E-Hentai
> 逻辑（`EhUrl` / `EhEngine` / `Settings.java` / 阅读器 / `ClipboardUtil`）**原样复用，
> 禁止修改、禁止在 helper 里复制任何 E-Hentai 逻辑**。PreferenceSyncHelper 只做
> SharedPreferences ↔ JSON 的读写与同步传输，不碰站点逻辑。

**读取参考:**
- `app/src/main/java/com/hippo/ehviewer/Settings.java` — 前 200 行 + 搜索所有 `get`/`put` 方法理解 key 名
- `app/src/main/java/com/hippo/ehviewer/util/ClipboardUtil.java` — 复制功能现状（只读）
- `app/src/main/java/com/hippo/ehviewer/ui/scene/GalleryInfoScene.java` — 链接展示用
  `EhUrl.getGalleryDetailUrl(gid, token)`（只读，复制功能回归对照）
- `app/src/main/java/com/hippo/ehviewer/webui/WebUiSettings.java` — 完整内容
- `app/src/main/java/com/hippo/ehviewer/webui/WebUiConfig.java` — 完整内容
- 搜索现有 sync 相关代码: `grep -r "sync/push" app/src/main/`

**创建文件:**

`app/src/main/java/com/hippo/ehviewer/webui/PreferenceSyncHelper.java`:

功能:
1. `exportPreferences(Context) → String`: 从 `Settings.java` 读取所有用户偏好 key，序列化为与后端 `PreferenceResponse` 对齐的 JSON
2. `importPreferences(Context, String json)`: 从 JSON 反序列化，写入 SharedPreferences
3. `pushToServer(WebUiConfig config, Context)`: 调用 `POST /api/v1/sync/push`，将 preferences 放入 `SyncEntityCollection.preferences`
4. `pullFromServer(WebUiConfig config, Context)`: 调用 `GET /api/v1/sync/pull?since=0`，取回 preferences 并 import

JSON 映射（Android key → JSON field）:
```
theme → general.theme (int 0/1/2 → "light"/"dark"/"black")
theme_auto_switch → general.themeAutoSwitch
launch_page → general.launchPage (int → "homepage"/"subscription"/"whats_hot")
list_mode → general.listMode
show_read_progress → general.showReadProgress
detail_size → general.detailSize
thumb_size → general.thumbSize
history_info_size → general.historyInfoSize
show_jpn_title → general.showJpnTitle
show_gallery_pages → general.showGalleryPages
show_tag_translations → general.showTagTranslations
show_gallery_comment → general.showGalleryComment
show_gallery_rating → general.showGalleryRating
show_eh_events → general.showEhEvents
show_eh_limits → general.showEhLimits
reading_direction → reader.readingDirection (int → "ltr"/"rtl"/"vertical")
reading_dual_page → reader.pageMode (映射逻辑)
reading_first_page_cover → reader.firstPageCover
page_scaling → reader.pageScaling
start_position → reader.startPosition
start_transfer_time → reader.autoPlayIntervalSec
gallery_show_progress → reader.showProgress
gallery_show_page_interval → reader.showPageInterval
reading_fullscreen → reader.fullscreen
custom_screen_lightness + screen_lightness → reader.brightness
enable_analytics → privacy.enableAnalytics
```

使用 `org.json.JSONObject`（Android 内置，不引入新依赖）。网络请求复用现有的 HTTP 客户端（检查项目中用什么发请求 — OkHttp/HttpURLConnection）。

**验证:** `./gradlew :app:compileDebugJavaWithJavac` 通过。

**细化（执行时强制）:**
- key 名以 `app/src/main/java/com/hippo/ehviewer/Settings.java` 实际常量为准，
  不要照抄本计划的映射表（以代码为准，映射表只作索引）
- JSON 字段名与后端 `PreferenceResponse` 严格一一对应（Wave 1C 的 DTO 是唯一事实源）
- 序列化/反序列化用 `JSONObject`/`JSONArray`，`theme`、`readingDirection` 等枚举
  值转换在 helper 内完成，但**不碰** `EhUrl`/`EhEngine`/`Settings` 的站点逻辑
- 复制/分享链接功能（`ClipboardUtil` + `EhUrl.getGalleryDetailUrl`）不得被本次
  改动影响；`GalleryInfoScene` 的链接展示与复制保持原样
- **离线安全（产品定位硬约束）**：
  - `pushToServer` / `pullFromServer` 全部在后台线程（复用现有 executor），
    连接/读写超时 ≤ 10s，失败仅返回结果给 UI 显示 Toast，不抛到主线程；
  - App 启动**不得**自动连接服务器；同步入口只在 WebUiSyncFragment 手动触发；
  - 服务器不可达 / JSON 解析失败 / 字段缺失时：跳过该项或整体放弃本次同步，
    本地 SharedPreferences 保持不变，绝不 crash、绝不回滚本地设置；
  - `webui/` 包编译进 APK 但运行时与主流程解耦（无 Application 初始化依赖、
    无 ContentProvider 注册等副作用）。

---

#### Agent 8B: Android WebUiSyncFragment UI（与 Wave 4 并行）

**读取参考:**
- `app/src/main/java/com/hippo/ehviewer/ui/fragment/WebUiSyncFragment.java` — 完整内容
- `app/res/xml/webui_sync_settings.xml` — 完整内容
- Wave 8A 产出的 `PreferenceSyncHelper.java`

**修改文件:**

`app/res/xml/webui_sync_settings.xml`:
- 新增 preference 项:
  ```xml
  <Preference
      android:key="webui_sync_preferences"
      android:title="同步配置"
      android:summary="将本机设置同步到 WebUI 服务器" />
  <Preference
      android:key="webui_pull_preferences"
      android:title="拉取配置"
      android:summary="从 WebUI 服务器恢复设置" />
  ```

`app/src/main/java/com/hippo/ehviewer/ui/fragment/WebUiSyncFragment.java`:
- 处理新增的两个 preference 点击事件
- 调用 `PreferenceSyncHelper.pushToServer()` / `pullFromServer()`
- 显示进度和结果 Toast

**依赖:** Agent 8A 必须先完成（需要 PreferenceSyncHelper 类）。如果并行，8B 在 8A 完成后启动，或 8B 的 prompt 中包含 8A 的接口定义。

**实际操作:** 由于 8B 依赖 8A，将 8A 和 8B 合并为一个 Agent 8AB 串行完成，或 8B 延迟到 8A 完成后再启动。推荐合并为一个 agent。

**验证:** `./gradlew :app:compileDebugJavaWithJavac` 通过。

---

### Wave 4 + Wave 8 验证门

```bash
# 前端
cd /Users/bob/AnotherViewer/web-frontend && npm run build

# Android
cd /Users/bob/AnotherViewer && ./gradlew :app:compileDebugJavaWithJavac
# 复制功能回归（App 侧）: 确认未触碰以下文件
git diff --stat app/src/main/java/com/hippo/ehviewer/client app/src/main/java/com/hippo/ehviewer/util/ClipboardUtil.java app/src/main/java/com/hippo/ehviewer/ui/scene/GalleryInfoScene.java

# App 独立运行门（Wave 8 合入前必须过）
rg -n "WebUiApiClient|WebUiConfig" app/src/main/java/com/hippo/ehviewer/EhApplication.java app/src/main/java/com/hippo/ehviewer/Settings.java
#   期望: 无输出 — 服务器相关类不得出现在 App 启动/设置初始化路径
rg -rn "new WebUi|WebUiSyncEngine|PreferenceSyncHelper" app/src/main/java --glob '!**/webui/**' --glob '!**/WebUiSyncFragment.java'
#   期望: 仅 WebUiSyncFragment / 设置入口引用，主流程（阅读/下载/收藏）零引用
```

### Wave 4 提交

```
feat(web-fe): preferences store + API + 路由重构 + 管理面板布局
```

### Wave 8 提交

```
feat(android): 配置双向同步 — PreferenceSyncHelper + UI 入口
```

---

### Wave 5: 用户设置页面（3 agents 并行）+ Wave 6: 管理面板页面（4 agents 并行）

> **本组共 7 个 agent 并行。** 全部创建新文件，零冲突。

#### Agent 5A: GeneralSettings.vue

**读取参考:**
- `web-frontend/src/views/SettingsView.vue` — 完整内容（理解现有 UI 模式和 CSS 类名）
- `web-frontend/src/stores/preferences.ts` — store API
- `web-frontend/src/stores/theme.ts` — 主题 store（主题切换仍走 theme store + localStorage）

**创建文件:** `web-frontend/src/views/settings/GeneralSettings.vue`

内容（对齐 Android `eh_settings.xml` 中 WebUI 适用的项）:

| 设置项 | 控件 | 绑定 |
|--------|------|------|
| 外观 (主题) | 三段选择 (亮/暗/纯黑) | `themeStore.setTheme()` + `preferencesStore.updateGeneral({theme})` |
| 跟随系统主题 | Switch | `preferencesStore.updateGeneral({themeAutoSwitch})` |
| 启动页 | 下拉选择 (首页/订阅/热门) | `preferencesStore.updateGeneral({launchPage})` |
| 列表模式 | 下拉选择 (列表/网格) | `preferencesStore.updateGeneral({listMode})` |
| 显示阅读进度 | Switch | `preferencesStore.updateGeneral({showReadProgress})` |
| 详情栏宽度 | 下拉选择 (长/短) | `preferencesStore.updateGeneral({detailSize})` |
| 缩略图大小 | 下拉选择 (大/中/小) | `preferencesStore.updateGeneral({thumbSize})` |
| 历史记录数 | 数字输入 | `preferencesStore.updateGeneral({historyInfoSize})` |
| 显示日文标题 | Switch | `preferencesStore.updateGeneral({showJpnTitle})` |
| 显示画廊页数 | Switch | `preferencesStore.updateGeneral({showGalleryPages})` |
| 显示标签翻译 | Switch | `preferencesStore.updateGeneral({showTagTranslations})` |
| 显示评论 | Switch | `preferencesStore.updateGeneral({showGalleryComment})` |
| 显示评分 | Switch | `preferencesStore.updateGeneral({showGalleryRating})` |
| 显示 EH 事件 | Switch | `preferencesStore.updateGeneral({showEhEvents})` |
| 显示 EH 限额 | Switch | `preferencesStore.updateGeneral({showEhLimits})` |

UI 结构: 复用 SettingsView.vue 的 `.pref-group` / `.pref-card` / `.pref` / `.pref__title` / `.pref__summary` CSS 类。每个设置项一行，左侧标题+说明，右侧控件。

**验证:** `npm run build` 通过。

---

#### Agent 5B: ReaderSettings.vue

**读取参考:**
- `web-frontend/src/components/reader/ReaderSettings.vue` — 现有阅读器内设置面板（复用逻辑）
- `web-frontend/src/views/SettingsView.vue` — 阅读器 section 的现有实现
- `web-frontend/src/stores/preferences.ts`

**创建文件:** `web-frontend/src/views/settings/ReaderSettings.vue`

| 设置项 | 控件 | 绑定 |
|--------|------|------|
| 阅读方向 | 三段选择 (LTR/RTL/竖向) | `preferencesStore.updateReader({readingDirection})` |
| 翻页模式 | 下拉选择 (自动/单页/双页/滚动) | `preferencesStore.updateReader({pageMode})` |
| 首页作为封面 | Switch | `preferencesStore.updateReader({firstPageCover})` |
| 页面缩放 | 下拉选择 (适应/宽度/高度/原始) | `preferencesStore.updateReader({pageScaling})` |
| 起始位置 | 下拉选择 (右上/左上/右下/左下) | `preferencesStore.updateReader({startPosition})` |
| 自动播放间隔 | Stepper (1-15秒) | `preferencesStore.updateReader({autoPlayIntervalSec})` |
| 显示进度 | Switch | `preferencesStore.updateReader({showProgress})` |
| 显示页间隔 | Switch | `preferencesStore.updateReader({showPageInterval})` |
| 全屏阅读 | Switch | `preferencesStore.updateReader({fullscreen})` |
| 亮度 | Slider (0-100, 0=系统) | `preferencesStore.updateReader({brightness})` |

**验证:** `npm run build` 通过。

---

#### Agent 5C: PrivacySettings.vue

**读取参考:**
- `web-frontend/src/views/SettingsView.vue` — 样式参考
- `web-frontend/src/stores/preferences.ts`

**创建文件:** `web-frontend/src/views/settings/PrivacySettings.vue`

| 设置项 | 控件 | 绑定 |
|--------|------|------|
| 启用统计 | Switch | `preferencesStore.updatePrivacy({enableAnalytics})` |

页面简短，但保持与其他设置页一致的布局结构。

**验证:** `npm run build` 通过。

---

#### Agent 6A: AdminDownload.vue

**读取参考:**
- `web-frontend/src/views/SettingsView.vue` — 下载 section 的现有实现（迁移过来）
- `web-frontend/src/api/settings.ts` — settingsApi
- `web-frontend/src/api/download.ts` — downloadApi（如果有清理操作）

**创建文件:** `web-frontend/src/views/admin/AdminDownload.vue`

从 SettingsView.vue 的下载 section 迁移，并扩展:

| 设置项 | 控件 | 存储 |
|--------|------|------|
| 下载路径 | 文本输入 + 对话框 | `settingsApi.update({download: {path}})` |
| 并发线程数 | Stepper (1-10) | `settingsApi.update({download: {workerCount}})` |
| 下载延迟 | 数字输入 | `settingsApi.update({download: {downloadDelay}})` |
| 下载超时 | 数字输入 | `settingsApi.update({download: {downloadTimeout}})` |
| 最大并发画廊数 | Stepper | `settingsApi.update({download: {maxConcurrentGalleries}})` |
| 最大并发图片数 | Stepper | `settingsApi.update({download: {maxConcurrentImages}})` |
| 预加载图片数 | Stepper (1-20) | `preferencesStore` 或本地（待定） |
| 下载列表分页 | Switch | 本地 |
| 排序方向 | Switch (升序/降序) | 本地 |
| 自动开始下载 | Switch | 本地 |
| 清理冗余文件 | 按钮 | `POST /api/v1/download/clean`（如存在） |
| 清理无效下载 | 按钮 | 同上 |

**验证:** `npm run build` 通过。

---

#### Agent 6B: AdminServer.vue

**读取参考:**
- `web-frontend/src/views/SettingsView.vue` — 缓存相关
- `web-frontend/src/api/settings.ts`
- `web-frontend/src/api/smb.ts`
- `web-frontend/src/views/SmbBackupView.vue` — 前 100 行（理解 SMB 配置结构）

**创建文件:** `web-frontend/src/views/admin/AdminServer.vue`

| 设置项 | 控件 | 存储 |
|--------|------|------|
| 缓存路径 | 文本输入 | `settingsApi.update({cache: {path}})` |
| 缓存大小 (MB) | 数字输入 | `settingsApi.update({cache: {sizeMb}})` |
| SMB 备份 | Switch + 链接到 /smb-backup | `settingsApi.update({smb: {enabled}})` |
| 清除缓存 | 按钮 | `POST /api/v1/cache/clear`（如存在） |
| 缓存统计 | 只读显示 | `GET /api/v1/cache/stats`（如存在） |

**验证:** `npm run build` 通过。

---

#### Agent 6C: AdminAccess.vue

**读取参考:**
- `web-frontend/src/api/settings.ts` — settingsApi（含 security section）
- `web-frontend/src/api/auth.ts` — authApi
- `web-frontend/src/stores/auth.ts`

**创建文件:** `web-frontend/src/views/admin/AdminAccess.vue`

| 设置项 | 控件 | 存储 |
|--------|------|------|
| 需要登录 | Switch | `settingsApi.update({security: {requireAuth}})` |
| Session 超时 (秒) | 数字输入 | `settingsApi.update({security: {sessionTimeout}})` |
| 修改密码 | 表单 (旧密码/新密码/确认) | `POST /api/v1/auth/change-password`（需新增后端端点，或标注 TODO） |

**重要:** 登录开关关闭时显示警告提示："关闭后，局域网内任何人都可以访问此服务器"。

**验证:** `npm run build` 通过。

---

#### Agent 6D: AdminProcessing + AdminAdvanced + AdminAbout

**读取参考:**
- `web-frontend/src/views/SettingsView.vue` — 高级/关于 section
- `web-frontend/src/api/settings.ts` — settingsApi（含 processing section）

**创建文件（3 个）:**

`web-frontend/src/views/admin/AdminProcessing.vue`:

| 设置项 | 控件 | 存储 |
|--------|------|------|
| 启用图像处理 | Switch | `settingsApi.update({processing: {enabled}})` |
| 默认处理类型 | 下拉选择 (2X放大/4X放大/降噪/降噪+放大) | `settingsApi.update({processing: {defaultType}})` |
| 输出格式 | 下拉选择 (png/jpeg/webp) | `settingsApi.update({processing: {outputFormat}})` |
| 输出质量 | Slider (1-100) | `settingsApi.update({processing: {outputQuality}})` |

`web-frontend/src/views/admin/AdminAdvanced.vue`:

| 设置项 | 控件 | 存储 |
|--------|------|------|
| 界面语言 | 下拉选择 | `preferencesStore.updateGeneral` 或 localStorage |
| 保存解析错误日志 | Switch | `settingsApi` 或 serverConfig |
| 导出数据 | 按钮 | `GET /api/v1/export`（TODO 标注） |
| 导入数据 | 按钮 + 文件选择 | `POST /api/v1/import`（TODO 标注） |
| 清除本地数据 | 按钮 + 确认 | localStorage 清理 |

`web-frontend/src/views/admin/AdminAbout.vue`:

| 内容 | 说明 |
|------|------|
| 应用名称 + 版本 | 从 package.json 或 API 获取 |
| 许可证 | Apache 2.0 |
| 项目地址 | GitHub 链接 |
| 构建信息 | Java 版本、Spring Boot 版本等 |

**验证:** `npm run build` 通过。

---

### Wave 5 + Wave 6 验证门

```bash
cd /Users/bob/AnotherViewer/web-frontend && npm run build
```

### Wave 5 提交

```
feat(web-fe): 用户设置页面 — 通用/阅读器/隐私
```

### Wave 6 提交

```
feat(web-fe): 管理面板页面 — 下载/服务器/访问/图像处理/高级/关于
```

---

### Wave 7: 集成 + 登录流程（2 agents 并行）

#### Agent 7A: 路由守卫 + 登录流程适配

**读取参考:**
- `web-frontend/src/router/index.ts` — Wave 4 修改后的版本
- `web-frontend/src/views/LoginView.vue` — 完整内容
- `web-frontend/src/stores/auth.ts` — 完整内容
- `web-frontend/src/api/auth.ts` — 完整内容

**修改文件:**

`web-frontend/src/router/index.ts`:
- 路由守卫改为:
  ```typescript
  router.beforeEach(async (to, _from, next) => {
    if (to.name === 'Login') {
      next()
      return
    }
    // 检查服务器是否要求登录
    const token = localStorage.getItem('token')
    if (token) {
      next()
      return
    }
    // 无 token 时查询服务器是否需要认证
    try {
      const { authApi } = await import('@/api/auth')
      const status = await authApi.status()
      if (!status.authRequired) {
        next()  // 服务器不要求登录，放行
        return
      }
    } catch {
      // 服务器不可达，走正常登录流程
    }
    next({ name: 'Login' })
  })
  ```
- 注意: `authApi.status()` 需要能在无 token 时调用（后端 `/api/v1/auth/status` 已经是 permitAll）

`web-frontend/src/api/auth.ts`:
- 确认 `status()` 方法存在且返回 `authRequired` 字段
- 如果返回类型需要更新，添加 `authRequired: boolean`

**验证:** `npm run build` 通过。

---

#### Agent 7B: 导航集成 + 旧代码清理

**读取参考:**
- `web-frontend/src/views/SettingsView.vue` — 确认哪些 section 已迁移
- `web-frontend/src/components/layout/NavigationDrawer.vue` — Wave 4 修改后的版本
- `web-frontend/src/App.vue` — 理解布局

**修改文件:**

- 删除 `web-frontend/src/views/SettingsView.vue`（已被 settings/ 子路由替代）
  - **前提:** 确认 router 中不再引用此文件
  - 如果其他组件引用了 SettingsView 中的工具函数/常量，先迁移到独立模块

- 确认 NavigationDrawer 中管理面板入口正确渲染
  - 图标选择: 使用现有 AppIcon 组件中可用的图标
  - 分隔线样式

- 确认 `/smb-backup` 路由仍正常工作（从 AdminServer 页面可跳转）

**验证:**
```bash
cd /Users/bob/AnotherViewer/web-frontend && npm run build
# 如果有 lint:
npm run lint
```

---

### Wave 7 验证门

```bash
cd /Users/bob/AnotherViewer/web-frontend && npm run build && npm run lint
```

### Wave 7 提交（2 个 commit）

```
feat(web-fe): 路由守卫适配动态登录开关
refactor(web-fe): 移除旧 SettingsView，完成导航集成
```

---

## 3. 全局验证（所有 Wave 完成后）

```bash
# 后端编译
cd /Users/bob/AnotherViewer && JAVA_HOME="<AS JBR 21>" \
  GRADLE_USER_HOME="<workspace>/.gradle-user-home" \
  <workspace>/.gradle-user-home/wrapper/dists/gradle-9.5.0-bin/*/gradle-9.5.0/bin/gradle \
  :ehviewer-web:test :ehviewer-web:bootJar

# 前端构建 + lint
cd /Users/bob/AnotherViewer/web-frontend && npm run build && npm run lint

# Android 编译
cd /Users/bob/AnotherViewer && JAVA_HOME="<AS JBR 21>" \
  GRADLE_USER_HOME="<workspace>/.gradle-user-home" \
  <workspace>/.gradle-user-home/wrapper/dists/gradle-9.5.0-bin/*/gradle-9.5.0/bin/gradle \
  :app:compileDebugJavaWithJavac

# 后端启动测试（可选）
cd /Users/bob/AnotherViewer && java -jar ehviewer-web/build/libs/ehviewer-web-*.jar --server.port=8080 &
sleep 10
curl -s --noproxy '*' http://localhost:8080/api/v1/auth/status | jq .
curl -s --noproxy '*' http://localhost:8080/api/v1/preferences | jq .
curl -s --noproxy '*' http://localhost:8080/api/v1/settings | jq .
# 验证 authRequired: false 时 preferences 可无 token 访问

# 复制功能验收（两端）
curl -s --noproxy '*' http://localhost:8080/api/v1/gallery/2801001 | jq -r .galleryUrl
#   期望输出: https://e-hentai.org/g/2801001/<token>/
cd web-frontend && npx vitest run src/views/__tests__/GalleryDetailView.copy.spec.ts
git diff --stat ehviewer-core app/src/main/java/com/hippo/ehviewer/client
#   期望: ehviewer-core 无改动；app client/ClipboardUtil/GalleryInfoScene 无改动
```

---

## 4. 提交总览

| # | Commit | Wave |
|---|--------|------|
| 0 | `feat(web): galleryUrl 契约 — 复制/分享链接复用 EhUrl.getGalleryDetailUrl` | 0 |
| 1 | `feat(web): 新增 UserPreference/ServerConfig 实体与偏好 DTO` | 1 |
| 2 | `feat(web): 偏好 CRUD API + 服务器配置服务 + 动态认证` | 2 |
| 3 | `feat(web): auth/status 返回 authRequired，登录开关生效` | 3 |
| 4 | `feat(web): 同步协议扩展 preferences 双向同步` | 3 |
| 5 | `feat(web-fe): preferences store + API + 路由重构 + 管理面板布局` | 4 |
| 6 | `feat(android): 配置双向同步 — PreferenceSyncHelper + UI 入口` | 8 |
| 7 | `feat(web-fe): 用户设置页面 — 通用/阅读器/隐私` | 5 |
| 8 | `feat(web-fe): 管理面板页面 — 下载/服务器/访问/图像处理/高级/关于` | 6 |
| 9 | `feat(web-fe): 路由守卫适配动态登录开关` | 7 |
| 10 | `refactor(web-fe): 移除旧 SettingsView，完成导航集成` | 7 |

---

## 5. 子代理派发规则（给执行者）

### 派发模板

每个子代理的 prompt 必须包含:
1. **角色**: "你负责 Wave N Agent NX: [任务名]"
2. **上下文文件列表**: 必须先 read_file 的文件（精确路径）
3. **产出文件列表**: 要创建/修改的文件（精确路径）
4. **实现规格**: 从本计划中复制该 Agent 的完整规格
5. **代码规范**: 从 §0 复制
6. **验证命令**: 完成后运行什么命令确认
7. **约束**: "只修改指定文件，不要触碰其他文件"

### 并行规则

- 同一 Wave 内的 Agent 在**同一条消息**中并行启动（多个 Agent tool call）
- Wave 8（App 侧）与 WebUI 各 Wave 并行，**不因 WebUI 阻塞**；每次 App 侧合入前
  必须先过"App 独立运行门"（见 Wave 4+8 验证门）
- 每个 Agent 使用 `subagent_type` 匹配其任务:
  - 后端 Kotlin: `backend-developer` 或 `general-purpose`
  - 前端 Vue: `frontend-developer` 或 `general-purpose`
  - Android Java: `general-purpose`
- Wave 之间**严格串行**: 等所有 Agent 完成 → 运行验证门 → 提交 → 下一 Wave
- 如果验证门失败: 读取错误 → 派发修复 Agent → 重新验证

### 冲突预防

- 每个 Agent 的文件集互不重叠（已在规格中确保）
- 需要修改同一文件的 Agent 放在不同 Wave（如 router/index.ts 只在 Agent 4B 和 7A 中修改，分属 Wave 4 和 7）
- Agent 不运行 `git add/commit`，由执行者统一提交

### 错误处理

- Agent 报告编译错误 → 执行者读取错误，判断是哪个 Agent 的文件 → 派发修复 Agent
- Agent 报告"文件不存在" → 检查依赖 Wave 是否完成
- 验证门超时 → 检查是否有 Agent 未完成

---

## 6. E-Hentai 逻辑复用约定（执行时强制）

> 用户确认：E-Hentai 相关逻辑从未变动，Android App 与 WebUI 共用同一套，
> 直接复用现有代码完全合理。本计划的执行者与所有 Agent 必须遵守：

1. **只读白名单（禁止修改）**：
   `ehviewer-core/.../client/EhUrl.java`、`EhEngine.java`、`EhRequestBuilder.java`、
   `EhConfig.java`、`Settings.java`、全部 `parser/`；`app/.../client/*`、
   `app/.../util/ClipboardUtil.java`、`app/.../ui/scene/GalleryInfoScene.java`。
2. **接入即调用**：WebUI 需要 E-Hentai 能力时，直接调用 core 现有公开方法
   （如复制链接 = `EhUrl.getGalleryDetailUrl(gid, token)`），不重写、不封装新逻辑。
3. **复制功能数据流（两端一致）**：
   ```
   App :   ClipboardUtil ← EhUrl.getGalleryDetailUrl(gid, token)   （现状，不改）
   WebUI:  GalleryService → EhUrl.getGalleryDetailUrl → DTO.galleryUrl
           → GET /api/v1/gallery/{gid} → GalleryDetailView.share() → clipboard
   ```
4. **验收红线**：任何 Wave 提交后，
   `git diff --stat ehviewer-core` 为空；复制链接单测（Wave 0C）保持通过。
5. 若执行中发现 core 确有问题（而非 WebUI 接入问题），禁止自行修改——报告 lead，
   由 lead 评估是否属于"E-Hentai 逻辑变动"（原则上不改）。

## 7. 风险与缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| WebUI 复制链接硬编码与 EhUrl 漂移 | 复制链接与 App 不一致 | Wave 0 引入 DTO `galleryUrl` + 前端消费 + 双端契约测试 |
| mock 与真后端 DTO 漂移 | 前端联调假绿 | Wave 0 同时更新 mock fixtures；契约测试双写（后端 + mock 同一 fixture） |
| 登录开关切换导致前端 401 循环 | 登录流程不可用 | Wave 7 动态守卫 + client.ts 401 处理（已有）联动；curl 冒烟覆盖开/关两态 |
| `server_config` 与 `EhCoreConfigProperties` 双写不一致 | 重启后配置丢失/冲突 | ServerConfigService 为唯一持久化源；启动时 initDefaults 只补默认不覆盖；SettingsService 读写均经 ServerConfigService |
| preferences 同步并发覆盖 | 双端设置互相覆盖 | push 采用 last-write-wins（按 lastModified）；偏好归属 per-user |
| Android Settings 与 core Settings 是两个类 | 映射表错位 | Wave 8A 以 app `Settings.java` 实际常量为准；后端 DTO 为唯一事实源 |
| 删除 SettingsView 时遗留引用 | 构建/路由断裂 | Wave 7B 先 rg 全量引用再删；router 确认无引用；npm build + vitest 兜底 |
| E-Hentai 只读白名单被误改 | 违反用户约束、破坏开源合规 | §6 红线 + 每 Wave 验证门 `git diff --stat ehviewer-core` |
| WebUI 依赖侵入 App 主流程 | 无服务器时 App 卡顿/崩溃/功能不可用 | §0 产品定位硬约束 + Wave 8 离线安全规格 + 独立运行门（启动路径零引用、失败静默降级） |
| 双端偏好同步互相覆盖 | 用户设置被意外还原 | 本地 SharedPreferences 为唯一事实源；同步手动触发 + last-write-wins（按 lastModified）；失败不动本地 |
