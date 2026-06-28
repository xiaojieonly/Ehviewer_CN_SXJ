# EhViewer Web App 详细架构设计规格

> 将 EhViewer Android 应用转换为局域网内任意设备可通过浏览器访问的 Web App，
> 第一阶段全面对标 Android App 功能，从 Android App 转向 WebUI。

---

## 1. 决策摘要

| 决策项 | 选择 | 理由 |
|--------|------|------|
| Core 提取策略 | 复制+适配（双模块独立演进） | 保留原始 Android 代码，降低风险 |
| 数据库 | SQLite（xerial/sqlite-jdbc） | 零运维，与 Android 端格式一致 |
| 认证 | Cookie + 账号密码 + API Key 三模式 | 覆盖所有用户场景 |
| 图片加载 | 全缓存模式（下载到本地后提供） | 离线可用，无跨域问题 |
| 前端 | Vue 3 + Vite + TypeScript + Pinia | 轻量灵活，生态成熟 |
| 部署 | Docker + 裸机 JAR 双支持 | 覆盖 NAS/Docker 和传统服务器 |
| 阅读器 | 专业级全功能 | 满足漫画阅读需求 |
| 下载并发 | 多级并发（画廊级 + 画廊内多线程） | 最大化下载速度 |
| 错误处理 | 智能重试（509 降速退避 + 网络重试） | 自动恢复，减少人工干预 |
| 响应式 | 三端平等（桌面/平板/手机） | 局域网场景多设备访问 |
| 国际化 | 仅中文 | 与 Android 版一致 |
| 缓存策略 | LRU 有界缓存（默认 10GB） | 控制磁盘占用 |

---

## 2. 整体架构

### 2.1 模块架构图

```
┌─────────────────────────────────────────────────────────┐
│                    浏览器 (任意设备)                       │
│  ┌─────────────────────────────────────────────────────┐ │
│  │  Vue 3 SPA (web-frontend)                          │ │
│  │  Pinia Store ←→ Axios HTTP ←→ WebSocket (STOMP)   │ │
│  └─────────────────────────────────────────────────────┘ │
└────────────────────────┬────────────────────────────────┘
                         │ HTTP / WebSocket
┌────────────────────────▼────────────────────────────────┐
│                ehviewer-web (Spring Boot)                │
│                                                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────────┐  │
│  │ REST API │  │ WebSocket│  │  Static File Server  │  │
│  │(Controllers)│ │ (STOMP) │  │  (Vue build output)  │  │
│  └─────┬────┘  └─────┬────┘  └──────────────────────┘  │
│        │              │                                   │
│  ┌─────▼──────────────▼──────────────────────────────┐  │
│  │              Service Layer                         │  │
│  │  AuthService / GalleryService / DownloadService    │  │
│  │  ImageCacheService / SmbBackupService              │  │
│  └─────┬──────────────────────────────────────────────┘  │
│        │                                                  │
│  ┌─────▼──────────────────────────────────────────────┐  │
│  │              ehviewer-core (Java Library)           │  │
│  │  EhEngine / Parsers / Data Models / SpiderQueen    │  │
│  │  SpiderDen / SmbConnection / EhFilter              │  │
│  └─────┬──────────────────────────────────────────────┘  │
│        │                                                  │
│  ┌─────▼──────────────┐  ┌───────────────────────────┐  │
│  │  SQLite (JPA)      │  │  File System (Cache/DL)   │  │
│  │  ehviewer.db        │  │  /data/ehviewer/          │  │
│  └────────────────────┘  └───────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

### 2.2 三层职责

| 层 | 模块 | 职责 | 语言 |
|----|------|------|------|
| 表现层 | web-frontend | UI 渲染、用户交互、状态管理 | TypeScript/Vue |
| 业务层 | ehviewer-web | REST API、WebSocket、认证、缓存管理、任务调度 | Kotlin |
| 核心层 | ehviewer-core | E-Hentai API 请求、HTML 解析、下载引擎、SMB、数据模型 | Java（从 Android 移植） |

---

## 3. ehviewer-core 提取与适配

### 3.1 Android 依赖替换清单

| Android API | 使用文件数 | 替换为 | 实现位置 |
|-------------|-----------|--------|---------|
| `android.text.TextUtils` | 48 | `com.hippo.ehviewer.util.TextUtil`（静态工具类） | ehviewer-core |
| `android.content.Context` | 194+ | 构造函数注入 `EhCoreConfig` | ehviewer-core |
| `android.os.AsyncTask` | ~10 | `ExecutorService` + `CompletableFuture` | ehviewer-core |
| `android.os.Looper/Handler` | ~8 | `ScheduledExecutorService` | ehviewer-core |
| `android.graphics.BitmapFactory` | ~5 | `javax.imageio.ImageIO` + `BufferedImage` | ehviewer-core |
| `android.webkit.MimeTypeMap` | ~8 | `Files.probeContentType()` + 扩展名映射表 | ehviewer-core |
| `android.os.Parcelable` | ~15 | 移除，纯 Java 对象 | ehviewer-core |
| `android.util.Log` | 广泛 | SLF4J | ehviewer-core |
| `com.hippo.unifile.UniFile` | 29 | `java.io.File` + `java.nio.file.*` | ehviewer-core |
| `android.database.sqlite.*` | ~5 | SQLite JDBC（ehviewer-web 层） | ehviewer-web |
| `android.content.SharedPreferences` | ~3 | Spring `@ConfigurationProperties` | ehviewer-web |
| `EventBus` | ~10 | Spring `ApplicationEventPublisher` | ehviewer-web |

### 3.2 EhCoreConfig 配置注入

```java
// ehviewer-core 中的配置类
public class EhCoreConfig {
    private String downloadPath;       // 下载目录
    private String cachePath;          // 缓存目录
    private long cacheSizeBytes;       // LRU 缓存上限
    private int workerCount;           // 并发下载线程数
    private int downloadDelay;         // 下载间隔 (ms)
    private int downloadTimeout;       // 下载超时 (ms)
    private boolean enableLogging;     // 日志开关
    private int maxConcurrentGalleries; // 最大并发画廊数
    private int maxConcurrentImages;   // 画廊内最大并发图片数
    // ... getter/setter
}
```

### 3.3 移植优先级

**P0 - 核心（Phase 1 必须）：**
- `client/EhEngine.java` (1429行) → 替换 TextUtils、Log
- `client/EhUrl.java` (320行) → 无修改
- `client/EhConfig.java` → 替换 SharedPreferences
- `client/EhFilter.java` (281行) → 替换 Log、EhDB 引用
- `client/parser/` (22个) → 替换 TextUtils
- `client/data/` (23个) → 移除 Parcelable
- `spider/SpiderQueen.java` (1832行) → 替换全部 Android 依赖
- `spider/SpiderDen.java` (458行) → 替换 UniFile/BitmapFactory
- `spider/SpiderInfo.java` → 替换 TextUtils
- `network/` (11个) → OkHttp 封装，替换 Android SSL
- `smb/SmbConnection.java` (593行) → 替换 TextUtils/MimeTypeMap

**P1 - 数据层（Phase 1 必须）：**
- `dao/` 10个 GreenDAO Entity → 迁移为 JPA Entity（在 ehviewer-web 中定义）

**P2 - 辅助（后续阶段）：**
- `util/` 工具类（FileUtils, StringUtils 等）
- `beerbelly/` 磁盘缓存（可替换为自定义 LRU 实现）
- `conaco/` 图片加载（可替换为自定义缓存）

### 3.4 构建配置

```kotlin
// ehviewer-core/build.gradle.kts
plugins {
    `java-library`
}
dependencies {
    implementation("com.squareup.okhttp3:okhttp:3.14.7")
    implementation("org.jsoup:jsoup:1.15.4")
    implementation("org.ccil.cowan.tagsoup:tagsoup:1.2.1")
    implementation("com.hierynomus:smbj:0.12.0")
    implementation("com.alibaba:fastjson:1.2.83")
    compileOnly("org.slf4j:slf4j-api:2.0.9")
}
```

---

## 4. 数据库设计（SQLite + JPA）

### 4.1 Entity 设计

```kotlin
// === 画廊信息基类 ===
@MappedSuperclass
abstract class GalleryInfoBase {
    @Id @Column(name = "GID")
    var gid: Long = 0
    var token: String = ""
    var title: String = ""
    var titleJpn: String = ""
    var thumb: String = ""
    var category: String = ""
    var posted: String = ""
    var uploader: String = ""
    var rating: Float = 0f
    var simpleLanguage: String = ""
}

// === 下载任务 ===
@Entity @Table(name = "DOWNLOADS")
class DownloadInfoEntity : GalleryInfoBase() {
    @Id @Column(name = "GID")
    override var gid: Long = 0
    var state: Int = 0
    var legacy: Boolean = false
    var time: Long = 0
    var label: String = ""
    @Transient var downloaded: Int = 0
    @Transient var total: Int = 0
    @Transient var speed: Long = 0
}

// === 下载标签 ===
@Entity @Table(name = "DOWNLOAD_LABELS")
class DownloadLabelEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
    var label: String = ""
    var time: Long = 0
}

// === 浏览历史 ===
@Entity @Table(name = "HISTORY")
class HistoryInfoEntity : GalleryInfoBase() {
    @Id @Column(name = "GID")
    override var gid: Long = 0
    var mode: Int = 0
    var time: Long = 0
}

// === 本地收藏 ===
@Entity @Table(name = "LOCAL_FAVORITES")
class LocalFavoriteInfoEntity : GalleryInfoBase() {
    @Id @Column(name = "GID")
    override var gid: Long = 0
    var time: Long = 0
}

// === 阅读书签 ===
@Entity @Table(name = "BOOKMARKS")
class BookmarkInfoEntity : GalleryInfoBase() {
    @Id @Column(name = "GID")
    override var gid: Long = 0
    var page: Int = 0
    var time: Long = 0
}

// === 快速搜索 ===
@Entity @Table(name = "QUICK_SEARCH")
class QuickSearchEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
    var name: String = ""
    var mode: Int = 0
    var category: String = ""
    var keyword: String = ""
    var advanceSearch: Boolean = false
    var minRating: Int = 0
    var pageFrom: Int = 0
    var pageTo: Int = 0
    var time: Long = 0
}

// === 过滤器 ===
@Entity @Table(name = "FILTER")
class FilterEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
    var mode: Int = 0
    var text: String = ""
    var enable: Boolean = true
}

// === 标签缓存 ===
@Entity @Table(name = "GALLERY_TAGS")
class GalleryTagsEntity {
    @Id @Column(name = "GID")
    var gid: Long = 0
    var rows: Int = 0
    var artist: String = ""
    var cosplayer: String = ""
    var character: String = ""
    var female: String = ""
    var group: String = ""
    var language: String = ""
    var male: String = ""
    var misc: String = ""
    var mixed: String = ""
    var other: String = ""
    var parody: String = ""
    var reclass: String = ""
    var createTime: Long = 0
    var updateTime: Long = 0
}

// === 黑名单 ===
@Entity @Table(name = "BLACK_LIST")
class BlackListEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
    var badgayname: String = ""
    var reason: String = ""
    var angrywith: String = ""
    var addTime: Long = 0
    var mode: Int = 0
}

// === 下载目录名映射 ===
@Entity @Table(name = "DOWNLOAD_DIRNAME")
class DownloadDirnameEntity {
    @Id @Column(name = "GID")
    var gid: Long = 0
    var dirname: String = ""
}

// === SMB 配置 ===
@Entity @Table(name = "SMB_CONFIG")
class SmbConfigEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
    var serverAddress: String = ""
    var sharedFolder: String = ""
    var username: String = ""
    var password: String = ""
    var domain: String = "WORKGROUP"
    var enabled: Boolean = false
}

// === 认证信息（新增）===
@Entity @Table(name = "AUTH_CONFIG")
class AuthConfigEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
    var authType: String = ""
    var cookieValue: String = ""
    var username: String = ""
    var password: String = ""
    var apiKey: String = ""
    var createdAt: Long = 0
    var updatedAt: Long = 0
}
```

### 4.2 Repository 层

```kotlin
interface DownloadInfoRepository : JpaRepository<DownloadInfoEntity, Long> {
    fun findByState(state: Int): List<DownloadInfoEntity>
    fun findByLabel(label: String): List<DownloadInfoEntity>
}

interface HistoryInfoRepository : JpaRepository<HistoryInfoEntity, Long> {
    fun findAllByOrderByTimeDesc(): List<HistoryInfoEntity>
}

interface LocalFavoriteInfoRepository : JpaRepository<LocalFavoriteInfoEntity, Long> {
    fun findAllByOrderByTimeDesc(): List<LocalFavoriteInfoEntity>
}

interface BookmarkInfoRepository : JpaRepository<BookmarkInfoEntity, Long> {
    fun findByGid(gid: Long): BookmarkInfoEntity?
}

interface QuickSearchRepository : JpaRepository<QuickSearchEntity, Long> {
    fun findAllByOrderByTimeDesc(): List<QuickSearchEntity>
}

interface FilterRepository : JpaRepository<FilterEntity, Long> {
    fun findByEnable(enable: Boolean): List<FilterEntity>
}

interface GalleryTagsRepository : JpaRepository<GalleryTagsEntity, Long> {
    fun findByGid(gid: Long): GalleryTagsEntity?
}

interface BlackListRepository : JpaRepository<BlackListEntity, Long> {
    fun findByMode(mode: Int): List<BlackListEntity>
}

interface DownloadDirnameRepository : JpaRepository<DownloadDirnameEntity, Long> {
    fun findByGid(gid: Long): DownloadDirnameEntity?
}

interface SmbConfigRepository : JpaRepository<SmbConfigEntity, Long> {
    fun findByEnabled(enabled: Boolean): List<SmbConfigEntity>
}

interface AuthConfigRepository : JpaRepository<AuthConfigEntity, Long> {
    fun findFirstByOrderByUpdatedAtDesc(): AuthConfigEntity?
}
```

### 4.3 SQLite 配置

```yaml
spring:
  datasource:
    url: jdbc:sqlite:data/ehviewer.db
    driver-class-name: org.sqlite.JDBC
  jpa:
    database-platform: org.hibernate.community.dialect.SQLiteDialect
    hibernate:
      ddl-auto: update
```

---

## 5. REST API 设计

### 5.1 通用规范

- 所有 API 前缀：`/api/v1/`
- 返回格式：`{ "code": 0, "message": "success", "data": { ... } }`
- 错误格式：`{ "code": 非0, "message": "错误描述", "data": null }`

### 5.2 认证 API

```
POST   /api/v1/auth/cookie          # Cookie 登录
       Body: { "cookie": "ipb_member_id=xxx; ipb_pass_hash=xxx" }

POST   /api/v1/auth/account         # 账号密码登录
       Body: { "username": "xxx", "password": "xxx" }

POST   /api/v1/auth/apikey          # API Key 登录
       Body: { "apiKey": "xxx" }

GET    /api/v1/auth/profile          # 当前用户信息
POST   /api/v1/auth/logout           # 登出
GET    /api/v1/auth/status           # 登录状态检查
```

### 5.3 画廊 API

```
GET    /api/v1/gallery/list          # 搜索/浏览画廊列表
       ?keyword=xxx&category=xxx&page=1&sort=xxx

GET    /api/v1/gallery/popular       # 热门列表
GET    /api/v1/gallery/detail/{gid}  # 画廊详情
GET    /api/v1/gallery/preview/{gid} # 缩略图预览集
GET    /api/v1/gallery/page/{gid}/{page}  # 图片页面信息
GET    /api/v1/gallery/image/{gid}/{page} # 获取图片（从本地缓存）
GET    /api/v1/gallery/thumb/{gid}/{index} # 获取缩略图（从本地缓存）
```

### 5.4 收藏 API

```
GET    /api/v1/favorite/list?slot=0  # 收藏列表
POST   /api/v1/favorite/add          # 添加收藏
DELETE /api/v1/favorite/remove       # 移除收藏

GET    /api/v1/local-favorites/list  # 本地收藏
POST   /api/v1/local-favorites/add
DELETE /api/v1/local-favorites/remove
```

### 5.5 评论 API

```
GET    /api/v1/comment/list/{gid}    # 评论列表
POST   /api/v1/comment/post          # 发表评论
POST   /api/v1/comment/vote          # 投票
```

### 5.6 下载 API

```
GET    /api/v1/download/list                 # 下载列表
GET    /api/v1/download/list/{label}          # 按标签获取
GET    /api/v1/download/info/{gid}            # 单任务详情
POST   /api/v1/download/add                   # 添加下载
POST   /api/v1/download/start                 # 开始/恢复
POST   /api/v1/download/start-all             # 开始所有
POST   /api/v1/download/pause                 # 暂停
POST   /api/v1/download/cancel                # 取消
DELETE /api/v1/download/delete/{gid}           # 删除（含文件）
POST   /api/v1/download/label                 # 创建/重命名标签
DELETE /api/v1/download/label/{label}          # 删除标签
```

### 5.7 归档与种子 API

```
GET    /api/v1/archive/list/{gid}    # 可用归档列表
POST   /api/v1/archive/download      # 下载归档
GET    /api/v1/torrent/list/{gid}    # 种子列表
GET    /api/v1/torrent/download      # 下载种子文件
```

### 5.8 SMB API

```
GET    /api/v1/smb/config            # 获取配置
PUT    /api/v1/smb/config            # 更新配置
POST   /api/v1/smb/test-connection   # 测试连接
POST   /api/v1/smb/sync              # 触发同步
POST   /api/v1/smb/sync-aggressive   # 高速同步
POST   /api/v1/smb/cancel            # 取消同步
```

### 5.9 其他 API

```
GET    /api/v1/history/list          # 浏览历史
DELETE /api/v1/history/clear         # 清除历史
GET    /api/v1/quick-search/list     # 快速搜索
POST   /api/v1/quick-search/add
DELETE /api/v1/quick-search/remove
GET    /api/v1/tag/search?q=xxx      # 标签搜索
GET    /api/v1/tag/database          # 标签数据库
GET    /api/v1/settings              # 获取设置
PUT    /api/v1/settings              # 更新设置
```

---

## 6. Service 层设计

### 6.1 Service 清单

| Service | 职责 | 关键方法 |
|---------|------|---------|
| `EhAuthService` | 认证管理 | loginByCookie, loginByAccount, loginByApiKey, getProfile, logout |
| `GalleryService` | 画廊业务 | searchGallery, getGalleryDetail, getPreviewSet, getPageInfo, getImage |
| `ImageCacheService` | 图片缓存管理 | cacheImage, getCachedImage, cacheThumbnail, getCachedThumbnail, getCacheStats, evictOldest |
| `DownloadService` | 下载管理 | addDownload, startDownload, pauseDownload, cancelDownload, deleteDownload, listDownloads, createLabel, deleteLabel, startAllDownloads |
| `SmbBackupService` | SMB 备份 | getConfig, updateConfig, testConnection, startSync, cancelSync, getProgress |
| `FavoriteService` | 收藏管理 | listFavorites, addFavorite, removeFavorite |
| `HistoryService` | 历史管理 | listHistory, clearHistory |
| `CommentService` | 评论管理 | listComments, postComment, voteComment |
| `QuickSearchService` | 快速搜索 | listQuickSearches, addQuickSearch, removeQuickSearch |
| `TagService` | 标签管理 | searchTags, getTagDatabase |
| `ArchiveService` | 归档下载 | listArchives, downloadArchive |
| `TorrentService` | 种子下载 | listTorrents, downloadTorrent |
| `SettingsService` | 设置管理 | getSettings, updateSettings |

### 6.2 WebSocket 设计

```
端点: /ws/progress (STOMP over WebSocket)

订阅主题:
  /topic/download/{gid}      # 单任务下载进度
  /topic/download/all         # 所有下载进度汇总
  /topic/smb/sync             # SMB 同步进度

消息格式:
{
    "type": "download_progress",
    "gid": 12345678,
    "state": 2,
    "downloaded": 15,
    "total": 30,
    "speed": 1024000,
    "label": "默认"
}
```

---

## 7. 下载系统与图片缓存

### 7.1 多级并发下载架构

```
DownloadService
    │
    ├── GalleryDownloadManager (画廊级并发)
    │   ├── 最大并发画廊数: configurable (默认 3)
    │   ├── 等待队列: LinkedList<GalleryDownloadTask>
    │   └── 活跃任务: Map<gid, GalleryDownloadTask>
    │
    └── GalleryDownloadTask (单画廊下载)
        ├── PageResolver → 解析画廊所有页面 URL
        ├── ImageDownloader (画廊内多线程)
        │   ├── 最大并发图片数: configurable (默认 3)
        │   ├── 线程池: FixedThreadPool(workerCount)
        │   └── OkHttp 异步请求
        ├── ProgressTracker → WebSocket 推送
        └── StateMachine:
            NONE → WAIT → DOWNLOADING → FINISH
                       ↘ FAILED (retry 3x) → FAILED
```

### 7.2 下载状态机

```
┌─────────┐  addDownload()  ┌─────────┐  startDownload()  ┌────────────┐
│  NONE   │ ──────────────> │  WAIT   │ ────────────────> │ DOWNLOADING│
│  (0)    │                 │  (1)    │                   │    (2)     │
└─────────┘                 └─────────┘                   └─────┬──────┘
                          pauseDownload() <─────────────────────┤
                          cancelDownload()                      │
                                                                │
                              ┌──────────┐  all pages done  ┌───▼──────┐
                              │  FAILED  │ <─────────────── │ FINISHED │
                              │   (4)    │   retry 3x fail  │   (3)    │
                              └──────────┘                  └──────────┘
```

### 7.3 SpiderQueen 适配

```java
public class SpiderQueen {
    private final EhCoreConfig config;
    private final OkHttpClient httpClient;
    private final ExecutorService workerPool;
    private final Consumer<DownloadProgress> progressCallback;
    
    public SpiderQueen(EhCoreConfig config, Consumer<DownloadProgress> progressCallback) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(config.getDownloadTimeout(), TimeUnit.MILLISECONDS)
            .readTimeout(config.getDownloadTimeout(), TimeUnit.MILLISECONDS)
            .build();
        this.workerPool = Executors.newFixedThreadPool(config.getWorkerCount());
        this.progressCallback = progressCallback;
    }
    
    public void downloadGallery(GalleryInfo gallery, String downloadPath) {
        List<String> pageUrls = resolvePageUrls(gallery);
        List<CompletableFuture<Void>> futures = pageUrls.stream()
            .map(url -> CompletableFuture.runAsync(() -> 
                downloadPage(url, downloadPath), workerPool))
            .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }
}
```

### 7.4 LRU 有界缓存设计

```
ImageCacheService
    │
    ├── 内存索引 (ConcurrentHashMap)
    │   key: "gid_page" → value: CacheEntry(filePath, size, lastAccess)
    │
    ├── 磁盘存储
    │   ├── 缓存目录: /data/ehviewer/cache/
    │   │   ├── images/{gid}/
    │   │   │   ├── 001.jpg, 002.jpg, ...
    │   │   └── thumbnails/{gid}/
    │   │       ├── 0.jpg, 1.jpg, ...
    │   └── 索引文件: cache-index.json (启动时加载)
    │
    └── LRU 淘汰策略
        ├── 最大容量: configurable (默认 10GB)
        ├── 淘汰时机: 每次写入后检查
        ├── 淘汰算法: LRU (按 lastAccess 排序)
        └── 保留策略: 已下载的图片不淘汰
```

### 7.5 图片请求流程

```
浏览器请求图片 → ImageProxyController
    │
    ├── 1. 检查本地缓存 (ImageCacheService)
    │   ├── 命中 → 直接返回 File
    │   └── 未命中 → 进入步骤 2
    │
    ├── 2. 从 E-Hentai 下载
    │   ├── 解析页面获取图片 URL
    │   ├── OkHttp 流式下载
    │   └── 写入缓存目录
    │
    ├── 3. 设置缓存头 Cache-Control: max-age=86400
    └── 4. 返回图片数据
```

### 7.6 SpiderDen 存储层适配

```java
public class SpiderDen {
    private final File downloadDir;
    private final File cacheDir;
    private final long maxCacheSize;
    
    public InputStream openForRead(int gid, int page) {
        File cached = findInCache(gid, page);
        if (cached != null) return new FileInputStream(cached);
        File downloaded = findInDownload(gid, page);
        if (downloaded != null) return new FileInputStream(downloaded);
        return null;
    }
    
    public OutputStream openForDownload(int gid, int page, String title) {
        File dir = new File(downloadDir, gid + "-" + sanitize(title));
        dir.mkdirs();
        return new FileOutputStream(new File(dir, String.format("%08d.jpg", page)));
    }
}
```

---

## 8. 前端架构（Vue 3 + Vite）

### 8.1 项目结构

```
web-frontend/
├── package.json
├── vite.config.ts
├── tsconfig.json
├── index.html
├── public/
│   └── favicon.ico
└── src/
    ├── main.ts
    ├── App.vue
    ├── router/index.ts
    ├── stores/
    │   ├── auth.ts
    │   ├── gallery.ts
    │   ├── download.ts
    │   ├── settings.ts
    │   └── cache.ts
    ├── api/
    │   ├── client.ts
    │   ├── auth.ts
    │   ├── gallery.ts
    │   ├── download.ts
    │   ├── favorite.ts
    │   ├── comment.ts
    │   ├── smb.ts
    │   ├── history.ts
    │   └── settings.ts
    ├── composables/
    │   ├── useWebSocket.ts
    │   ├── useInfiniteScroll.ts
    │   ├── useImageLoader.ts
    │   ├── useSwipeGesture.ts
    │   └── useKeyboardNav.ts
    ├── views/
    │   ├── LoginView.vue
    │   ├── HomeView.vue
    │   ├── GalleryDetailView.vue
    │   ├── ReaderView.vue
    │   ├── DownloadView.vue
    │   ├── FavoriteView.vue
    │   ├── HistoryView.vue
    │   ├── SettingsView.vue
    │   └── SmbBackupView.vue
    ├── components/
    │   ├── layout/
    │   │   ├── AppHeader.vue
    │   │   ├── AppSidebar.vue
    │   │   └── AppBottomNav.vue
    │   ├── gallery/
    │   │   ├── GalleryGrid.vue
    │   │   ├── GalleryCard.vue
    │   │   └── GalleryFilters.vue
    │   ├── reader/
    │   │   ├── ImageReader.vue
    │   │   ├── PageMode.vue
    │   │   ├── ScrollMode.vue
    │   │   ├── ReaderToolbar.vue
    │   │   └── ReaderSettings.vue
    │   ├── download/
    │   │   ├── DownloadList.vue
    │   │   ├── DownloadItem.vue
    │   │   └── DownloadProgress.vue
    │   ├── search/
    │   │   ├── SearchBar.vue
    │   │   └── QuickSearch.vue
    │   ├── common/
    │   │   ├── TagChip.vue
    │   │   ├── ProgressOverlay.vue
    │   │   ├── ImageGrid.vue
    │   │   ├── CommentList.vue
    │   │   └── RatingStars.vue
    │   └── smb/
    │       ├── SmbConfigForm.vue
    │       └── SyncProgress.vue
    ├── assets/styles/
    │   ├── variables.css
    │   ├── global.css
    │   └── responsive.css
    └── types/index.ts
```

### 8.2 路由配置

```typescript
const routes = [
  { path: '/login', component: LoginView, meta: { requiresAuth: false } },
  {
    path: '/',
    component: MainLayout,
    meta: { requiresAuth: true },
    children: [
      { path: '', component: HomeView },
      { path: 'gallery/:gid', component: GalleryDetailView },
      { path: 'reader/:gid/:page', component: ReaderView, meta: { fullscreen: true } },
      { path: 'downloads', component: DownloadView },
      { path: 'favorites', component: FavoriteView },
      { path: 'history', component: HistoryView },
      { path: 'settings', component: SettingsView },
      { path: 'smb-backup', component: SmbBackupView },
    ]
  }
]
```

### 8.3 响应式断点

```css
:root {
  --breakpoint-mobile: 640px;
  --breakpoint-tablet: 1024px;
  --breakpoint-desktop: 1280px;
}

.gallery-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
}

@media (min-width: 640px) {
  .gallery-grid {
    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  }
}

@media (min-width: 1024px) {
  .gallery-grid {
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  }
}
```

### 8.4 关键交互

| 功能 | 实现方案 |
|------|---------|
| 无限滚动 | Intersection Observer API |
| 图片懒加载 | Intersection Observer + loading="lazy" |
| 手势翻页 | TouchEvent + CSS transform |
| 键盘导航 | keydown 事件监听 |
| 实时进度 | WebSocket STOMP |
| 下拉刷新 | TouchEvent + overscroll |

### 8.5 核心 Composable

```typescript
// useWebSocket.ts
export function useWebSocket() {
  const client = new Client({
    brokerURL: `ws://${location.host}/ws/progress`,
    onConnect: () => { /* 订阅主题 */ },
  })
  const subscribeDownload = (gid: number, callback: ProgressCallback) => { ... }
  const subscribeAll = (callback: ProgressCallback) => { ... }
  const subscribeSmbSync = (callback: ProgressCallback) => { ... }
  return { connect, disconnect, subscribeDownload, subscribeAll, subscribeSmbSync }
}

// useInfiniteScroll.ts
export function useInfiniteScroll(loadMore: () => Promise<void>) {
  const sentinel = ref<HTMLElement>()
  const loading = ref(false)
  const hasMore = ref(true)
  onMounted(() => {
    const observer = new IntersectionObserver(entries => {
      if (entries[0].isIntersecting && !loading.value && hasMore.value) {
        loadMore().then(() => { loading.value = false })
      }
    })
    observer.observe(sentinel.value!)
  })
  return { sentinel, loading, hasMore }
}

// useImageLoader.ts
export function useImageLoader() {
  const imageCache = reactive(new Map<string, string>())
  const loadImage = async (url: string): Promise<string> => {
    if (imageCache.has(url)) return imageCache.get(url)!
    const blob = await fetch(url).then(r => r.blob())
    const objectUrl = URL.createObjectURL(blob)
    imageCache.set(url, objectUrl)
    return objectUrl
  }
  return { loadImage, imageCache }
}
```

---

## 9. 专业阅读器设计

### 9.1 功能矩阵

| 功能 | 说明 | 实现方式 |
|------|------|---------|
| 翻页模式 | 左右滑动/点击翻页 | CSS transform + TouchEvent |
| 滚动模式 | 上下连续滚动 | 原生 scroll + IntersectionObserver |
| 缩放 | 双指缩放 / 双击缩放 | CSS transform: scale() |
| 左右滑动翻页 | 手指左右滑 | TouchEvent 计算位移 |
| 键盘导航 | ←→ 翻页, +/- 缩放, F 全屏 | keydown 监听 |
| 亮度调节 | 调整图片亮度 | CSS filter: brightness() |
| 背景色切换 | 白/黑/灰/自定义 | CSS background-color |
| 白边裁切 | 自动检测并裁切图片白边 | Canvas 分析像素 + 裁切 |
| 双页并排 | 桌面端两页同时显示 | CSS Grid 双列 |
| 自动翻页 | 定时自动翻到下一页 | setInterval |
| 右至左阅读 | 日漫阅读方向 | direction: rtl + 逻辑反转 |
| 长条模式 | 所有图片纵向拼接 | CSS flex-direction: column |
| 书签同步 | 阅读进度自动保存 | API 调用 + 防抖 |
| PDF 导出 | 将画廊导出为 PDF | pdf-lib |
| 进度条 | 显示当前页/总页数 | 进度条组件 |

### 9.2 组件结构

```
ReaderView.vue
├── ReaderToolbar.vue          # 顶部工具栏
├── ReaderContent.vue          # 内容区域
│   ├── PageMode.vue           # 翻页模式
│   │   └── ImageSlide.vue     # 单页图片 (含缩放手势)
│   ├── ScrollMode.vue         # 滚动模式
│   │   └── ImageColumn.vue    # 纵向图片列表
│   └── LongStripMode.vue      # 长条模式
├── ReaderBottomBar.vue        # 底部栏 (页码, 进度)
├── ReaderSettings.vue         # 设置面板
└── PageSlider.vue             # 快速跳页滑块
```

### 9.3 手势处理

```typescript
export function useSwipeGesture(element: Ref<HTMLElement>, options: SwipeOptions) {
  let startX = 0, startY = 0, startTime = 0
  
  const onTouchStart = (e: TouchEvent) => {
    startX = e.touches[0].clientX
    startY = e.touches[0].clientY
    startTime = Date.now()
  }
  
  const onTouchEnd = (e: TouchEvent) => {
    const dx = e.changedTouches[0].clientX - startX
    const dy = e.changedTouches[0].clientY - startY
    const dt = Date.now() - startTime
    
    if (dt < 300 && Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy)) {
      dx > 0 ? options.onSwipeRight() : options.onSwipeLeft()
    } else if (Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy) * 2) {
      dx > 0 ? options.onSwipeRight() : options.onSwipeLeft()
    }
  }
}
```

### 9.4 白边裁切算法

```typescript
function detectContentBounds(image: HTMLImageElement): { x: number, y: number, w: number, h: number } {
  const canvas = document.createElement('canvas')
  const ctx = canvas.getContext('2d')!
  canvas.width = image.naturalWidth
  canvas.height = image.naturalHeight
  ctx.drawImage(image, 0, 0)
  
  const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height)
  const data = imageData.data
  const threshold = 240
  
  let top = 0, bottom = canvas.height - 1
  let left = 0, right = canvas.width - 1
  
  // 从四边向内扫描非白色像素边界
  // ...
  
  return { x: left, y: top, w: right - left, h: bottom - top }
}
```

### 9.5 PDF 导出

```typescript
import { PDFDocument } from 'pdf-lib'

async function exportToPdf(gid: number, totalPages: number): Promise<Blob> {
  const pdfDoc = await PDFDocument.create()
  
  for (let page = 1; page <= totalPages; page++) {
    const imageUrl = `/api/v1/gallery/image/${gid}/${page}`
    const imageBytes = await fetch(imageUrl).then(r => r.arrayBuffer())
    const image = isPng(imageBytes) 
      ? await pdfDoc.embedPng(imageBytes)
      : await pdfDoc.embedJpg(imageBytes)
    const pdfPage = pdfDoc.addPage([image.width, image.height])
    pdfPage.drawImage(image, { x: 0, y: 0 })
  }
  
  return pdfDoc.save() as Promise<Blob>
}
```

---

## 10. 安全设计

### 10.1 Session 管理

```kotlin
@Configuration
@EnableWebSecurity
class SecurityConfig {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement {
                it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                it.maximumSessions(1)
            }
            .authorizeHttpRequests {
                it.requestMatchers("/api/v1/auth/**").permitAll()
                it.requestMatchers("/ws/**").permitAll()
                it.requestMatchers("/**").authenticated()
            }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
        return http.build()
    }
}
```

### 10.2 密码加密

```kotlin
@Service
class EncryptionService {
    private val key: SecretKey = loadOrGenerateKey()
    
    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray())
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }
    
    fun decrypt(cipherText: String): String {
        val bytes = Base64.getDecoder().decode(cipherText)
        val iv = bytes.sliceArray(0..11)
        val encrypted = bytes.sliceArray(12 until bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted))
    }
}
```

### 10.3 CORS 配置

```kotlin
@Configuration
class WebConfig : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOrigins("*")
            .allowedMethods("GET", "POST", "PUT", "DELETE")
            .allowedHeaders("*")
    }
}
```

---

## 11. 部署方案

### 11.1 Docker 部署

```dockerfile
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y fonts-noto-cjk && rm -rf /var/lib/apt/lists/*
COPY ehviewer-web/build/libs/ehviewer-web-*.jar app.jar
VOLUME ["/app/data", "/app/cache", "/app/downloads"]
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```yaml
# docker-compose.yml
version: '3.8'
services:
  ehviewer:
    build: .
    container_name: ehviewer-web
    ports:
      - "8080:8080"
    volumes:
      - ./data:/app/data
      - ./cache:/app/cache
      - ./downloads:/app/downloads
    environment:
      - EHVIEWER_SERVER_PORT=8080
      - EHVIEWER_DOWNLOAD_PATH=/app/downloads
      - EHVIEWER_CACHE_PATH=/app/cache
      - EHVIEWER_CACHE_SIZE_MB=10240
      - EHVIEWER_DOWNLOAD_WORKER_COUNT=3
      - EHVIEWER_DB_URL=jdbc:sqlite:/app/data/ehviewer.db
    restart: unless-stopped
```

### 11.2 裸机部署

```bash
# 构建
./gradlew :ehviewer-web:bootJar

# 运行
java -jar ehviewer-web/build/libs/ehviewer-web-*.jar \
  --server.port=8080 \
  --ehviewer.download.path=/data/ehviewer/downloads \
  --ehviewer.cache.path=/data/ehviewer/cache \
  --ehviewer.cache.size-mb=10240
```

### 11.3 application.yml

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:sqlite:data/ehviewer.db
    driver-class-name: org.sqlite.JDBC
  jpa:
    hibernate:
      ddl-auto: update

ehviewer:
  download:
    path: /data/ehviewer/downloads
    worker-count: 3
    download-delay: 0
    download-timeout: 60000
    max-concurrent-galleries: 3
    max-concurrent-images: 3
  cache:
    path: /data/ehviewer/cache
    size-mb: 10240
    thumbnail-size-mb: 1024
  smb:
    enabled: false
  security:
    session-timeout: 86400
    encryption-key-path: data/security.key
```

---

## 12. 实施阶段

### Phase 1: 项目骨架 + 核心 API（1-2 周）

| 任务 | 产出 | 验证 |
|------|------|------|
| 创建 Gradle 多模块结构 | ehviewer-core, ehviewer-web, web-frontend | `./gradlew build` 通过 |
| 移植 ehviewer-core | EhEngine, EhUrl, 22 个 Parser, 23 个 DataModel | JUnit 单元测试通过 |
| Spring Boot 基础配置 | Security, Session, CORS, WebSocket | 启动无报错 |
| SQLite JPA Entity | 11 个 Entity + Repository | 数据库表自动创建 |
| 认证 API | Cookie/账号/APIKey 三种登录 | curl 测试通过 |
| 画廊 API | 列表/详情/预览 | 浏览器显示画廊列表 |
| 图片代理 API | 流式转发 | 图片正常显示 |
| 前端骨架 | 登录页 + 首页 + 画廊详情页 | 浏览器可操作 |

### Phase 2: 阅读器 + 收藏（1 周）

| 任务 | 产出 | 验证 |
|------|------|------|
| 阅读器组件 | 翻页/滚动/缩放/手势/键盘 | 浏览器全屏阅读流畅 |
| 收藏 API + 页面 | 列表/添加/移除 | 收藏操作正常 |
| 评论 API + 组件 | 列表/发表/投票 | 评论显示和发布正常 |
| 历史 API + 页面 | 浏览/清除 | 历史记录正确 |
| 书签同步 | 阅读进度自动保存 | 重新打开恢复进度 |

### Phase 3: 下载缓存系统（2 周）

| 任务 | 产出 | 验证 |
|------|------|------|
| 移植 SpiderQueen | 多线程下载引擎 | 单画廊下载成功 |
| 移植 SpiderDen | 双模式存储层 | 缓存读写正常 |
| LRU 有界缓存 | ImageCacheService | 超限自动淘汰 |
| 多级并发下载 | 画廊级+画廊内并发 | 多任务并行正常 |
| WebSocket 进度 | 实时推送下载进度 | 前端进度条实时更新 |
| 归档/种子 API | 归档下载、种子下载 | 功能正常 |
| 下载管理页面 | 队列/进度/速度/标签 | UI 操作正常 |
| 智能重试 | 509 降速 + 网络重试 | 模拟限速后自动恢复 |

### Phase 4: SMB 备份（1 周）

| 任务 | 产出 | 验证 |
|------|------|------|
| 移植 SmbConnection | SMB2 客户端 | 连接 NAS 成功 |
| SmbBackupService | 配置/测试/同步 | 同步文件到 NAS |
| 定时同步 | @Scheduled 支持 | 自动定时同步 |
| SMB 管理页面 | 配置/同步/进度 | UI 操作正常 |

### Phase 5: 优化 + 部署（1 周）

| 任务 | 产出 | 验证 |
|------|------|------|
| Docker 容器化 | Dockerfile + docker-compose.yml | `docker compose up` 启动 |
| 裸机部署脚本 | 启动脚本 + systemd 服务 | 直接运行 JAR 启动 |
| 性能优化 | 并发调优、缓存策略 | 压力测试通过 |
| 浏览器兼容性 | Chrome/Firefox/Safari/Edge/移动端 | 全平台正常 |
| 使用文档 | 部署指南 + 使用说明 | 文档完整 |

### 时间估算

| 阶段 | 时间 | 累计 |
|------|------|------|
| Phase 1 | 1-2 周 | 1-2 周 |
| Phase 2 | 1 周 | 2-3 周 |
| Phase 3 | 2 周 | 4-5 周 |
| Phase 4 | 1 周 | 5-6 周 |
| Phase 5 | 1 周 | 6-7 周 |

---

## 13. 验证方案

| 验证项 | 方式 | 通过标准 |
|--------|------|---------|
| Parser 移植 | JUnit 单元测试 | 与 Android 版输出一致 |
| API 端点 | curl / Postman | 每个端点返回正确 JSON |
| 图片缓存 | 浏览器访问 | 图片从本地缓存加载 |
| 下载系统 | 添加下载 → 观察进度 → 验证文件 | 文件完整，目录结构正确 |
| 阅读器 | 浏览器全屏阅读 | 翻页流畅，缩放正常 |
| SMB 备份 | 配置 → 测试连接 → 同步 | NAS 上文件一致 |
| 响应式 | 手机/平板/电脑浏览器 | 三端布局正常 |
| 长时间运行 | 服务器运行 24 小时 | 无内存泄漏，下载正常 |
| 并发性能 | 同时 5 个下载任务 | 全部正常完成 |

---

## 14. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| Parser 在 Web 环境解析失败 | 核心功能不可用 | 逐个 Parser 编写测试用例 |
| E-Hentai 509 限速 | 下载中断 | 智能重试 + 降速退避 |
| 图片缓存磁盘占满 | 写入失败 | LRU 淘汰 + 监控告警 |
| SQLite 并发写入冲突 | 数据损坏 | WAL 模式 + 连接池配置 |
| 浏览器兼容性 | 部分功能不可用 | 阅读器 Canvas 降级方案 |
| 内存压力 | 服务器 OOM | 流式处理 + 并发限制 |
