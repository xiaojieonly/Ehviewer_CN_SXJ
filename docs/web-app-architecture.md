# AnotherViewer Web App 架构设计文档

> 将 Android 应用转换为局域网内任意设备可通过浏览器访问的 Web App，
> 获得与 Android App 一致的完整体验，且不受设备性能限制。

---

## 1. 项目概览

### 1.1 目标

- 在本地服务器（PC / NAS / 树莓派等）上运行 Spring Boot 应用
- 局域网内任意设备（手机、平板、电脑、电视）通过浏览器访问 `http://服务器IP:端口`
- 功能完整覆盖 Android App：浏览、搜索、下载、阅读、收藏、评论、SMB 备份
- 下载缓存路径指向本地挂载点，可通过 Linux 挂载机制接入 CIFS/NFS/WebDAV 等网络存储
- 服务端持续运行，不受手机熄屏/切后台/省电策略影响

### 1.2 技术选型

| 层级 | 技术 | 理由 |
|------|------|------|
| 后端框架 | Spring Boot 3.x (Kotlin/Java) | 可最大程度复用现有 Java 业务代码 |
| 前端框架 | Vue.js 3 + Vite + TypeScript | 组件化开发，响应式适配多端 |
| 数据库 | H2 (嵌入式) / SQLite | 零运维，单文件部署，兼容现有表结构 |
| ORM | Spring Data JPA / Hibernate | 替代 GreenDAO，注解驱动 |
| 网络请求 | OkHttp 3.14.7 (服务端) | 与 Android 版一致，保持行为兼容 |
| HTML 解析 | Jsoup 1.15.4 | 与 Android 版一致 |
| SMB 协议 | smbj 0.12.0 | 纯 Java SMB2/3 客户端 |
| 实时通信 | WebSocket (Spring STOMP) | 下载进度、备份进度实时推送 |
| 构建工具 | Gradle (多模块) | 与现有项目一致 |

---

## 2. 项目结构

### 2.1 Gradle 多模块布局

```
Anotherviewer_CN_SXJ/
├── anotherviewer-core/                    # 核心业务库 (纯 Java, 无 Android 依赖)
│   ├── build.gradle.kts
│   └── src/main/java/com/hippo/anotherviewer/
│       ├── client/
│       │   ├── SiteEngine.java         # API 请求引擎 (直接移植, ~1429 行)
│       │   ├── SiteUrl.java            # URL 构建 (直接移植, ~320 行)
│       │   ├── SiteConfig.java         # 用户配置
│       │   ├── SiteFilter.java         # 画廊过滤
│       │   ├── SiteRequestBuilder.java # HTTP 请求构建
│       │   ├── SiteCacheKeyFactory.java
│       │   ├── parser/               # 22 个 HTML 解析器 (直接移植)
│       │   │   ├── GalleryListParser.java
│       │   │   ├── GalleryDetailParser.java
│       │   │   ├── GalleryPageParser.java
│       │   │   ├── GalleryPageApiParser.java
│       │   │   ├── FavoritesParser.java
│       │   │   ├── SignInParser.java
│       │   │   ├── ArchiveParser.java
│       │   │   ├── TorrentParser.java
│       │   │   ├── TopListParser.java
│       │   │   ├── SiteHomeParser.java
│       │   │   ├── ProfileParser.java
│       │   │   ├── RateGalleryParser.java
│       │   │   ├── VoteCommentParser.java
│       │   │   ├── GalleryTokenApiParser.java
│       │   │   ├── GalleryApiParser.java
│       │   │   ├── GalleryDetailUrlParser.java
│       │   │   ├── GalleryListUrlParser.java
│       │   │   ├── GalleryPageUrlParser.java
│       │   │   ├── MyTagLitParser.java
│       │   │   ├── ForumsParser.java
│       │   │   ├── SiteEventParse.java
│       │   │   └── ParserUtils.java
│       │   ├── data/                 # 23 个数据模型 (直接移植, 纯 POJO)
│       │   │   ├── GalleryInfo.java
│       │   │   ├── GalleryDetail.java
│       │   │   ├── GalleryComment.java
│       │   │   ├── GalleryCommentList.java
│       │   │   ├── GalleryPreview.java
│       │   │   ├── GalleryTagGroup.java
│       │   │   ├── Tag.java
│       │   │   ├── ArchiverData.java
│       │   │   ├── TorrentInfo.java
│       │   │   ├── ListUrlBuilder.java
│       │   │   ├── FavListUrlBuilder.java
│       │   │   ├── PreviewSet.java
│       │   │   ├── NormalPreviewSet.java
│       │   │   ├── LargePreviewSet.java
│       │   │   └── ...
│       │   └── exception/            # 异常定义
│       ├── spider/
│       │   ├── SpiderQueen.java      # 多线程下载引擎 (移植, 移除 Android 依赖)
│       │   ├── SpiderDen.java        # 双模式存储层 (移植, UniFile -> java.io.File)
│       │   └── SpiderInfo.java       # 画廊元数据 (直接移植)
│       └── smb/
│           ├── SmbConnection.java    # SMB2 连接 (移植, ~593 行)
│           ├── SmbConfig.java        # SMB 配置
│           └── SmbSettings.java      # SMB 设置
│
├── anotherviewer-web/                     # Spring Boot Web 应用
│   ├── build.gradle.kts
│   └── src/main/
│       ├── java/com/hippo/anotherviewer/web/
│       │   ├── SiteWebApplication.kt
│       │   ├── config/
│       │   │   ├── WebConfig.kt          # CORS, 静态资源
│       │   │   ├── SecurityConfig.kt     # Session 认证
│       │   │   ├── WebSocketConfig.kt    # STOMP WebSocket
│       │   │   └── AsyncConfig.kt        # 异步线程池
│       │   ├── api/                      # REST Controllers
│       │   │   ├── AuthController.kt
│       │   │   ├── GalleryController.kt
│       │   │   ├── FavoriteController.kt
│       │   │   ├── CommentController.kt
│       │   │   ├── DownloadController.kt
│       │   │   ├── ArchiveController.kt
│       │   │   ├── SmbController.kt
│       │   │   ├── HistoryController.kt
│       │   │   ├── LocalFavoriteController.kt
│       │   │   ├── QuickSearchController.kt
│       │   │   ├── TagController.kt
│       │   │   └── TorrentController.kt
│       │   ├── service/                  # 业务逻辑层
│       │   │   ├── SiteAuthService.kt
│       │   │   ├── GalleryService.kt
│       │   │   ├── ImageProxyService.kt
│       │   │   ├── DownloadService.kt
│       │   │   ├── SmbBackupService.kt
│       │   │   └── CacheService.kt
│       │   ├── proxy/
│       │   │   └── ImageProxyController.kt  # 图片流式代理
│       │   ├── entity/                   # JPA 实体 (从 GreenDAO 迁移)
│       │   │   ├── DownloadInfoEntity.kt
│       │   │   ├── DownloadLabelEntity.kt
│       │   │   ├── HistoryInfoEntity.kt
│       │   │   ├── LocalFavoriteInfoEntity.kt
│       │   │   ├── BookmarkInfoEntity.kt
│       │   │   ├── QuickSearchEntity.kt
│       │   │   ├── FilterEntity.kt
│       │   │   ├── GalleryTagsEntity.kt
│       │   │   ├── BlackListEntity.kt
│       │   │   ├── DownloadDirnameEntity.kt
│       │   │   └── SmbConfigEntity.kt
│       │   ├── repository/               # Spring Data JPA Repositories
│       │   └── websocket/
│       │       └── DownloadProgressHandler.kt
│       └── resources/
│           ├── application.yml
│           └── static/                   # Vue.js 构建输出
│
├── web-frontend/                          # Vue.js 3 前端项目
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   └── src/
│       ├── main.ts
│       ├── App.vue
│       ├── router/
│       │   └── index.ts
│       ├── stores/                        # Pinia 状态管理
│       │   ├── auth.ts
│       │   ├── gallery.ts
│       │   ├── download.ts
│       │   └── settings.ts
│       ├── api/                           # Axios API 封装
│       │   ├── client.ts
│       │   ├── auth.ts
│       │   ├── gallery.ts
│       │   ├── download.ts
│       │   └── smb.ts
│       ├── views/                         # 页面组件
│       │   ├── LoginView.vue
│       │   ├── HomeView.vue              # 搜索/浏览
│       │   ├── GalleryDetailView.vue     # 画廊详情
│       │   ├── ReaderView.vue            # 图片阅读器
│       │   ├── DownloadView.vue          # 下载管理
│       │   ├── FavoriteView.vue          # 收藏管理
│       │   ├── HistoryView.vue           # 浏览历史
│       │   ├── SettingsView.vue          # 设置
│       │   └── SmbBackupView.vue         # SMB 备份管理
│       ├── components/                    # 通用组件
│       │   ├── GalleryGrid.vue           # 画廊网格
│       │   ├── GalleryCard.vue           # 画廊卡片
│       │   ├── SearchBar.vue             # 搜索栏
│       │   ├── ImageReader.vue           # 图片阅读器组件
│       │   ├── DownloadItem.vue          # 下载项
│       │   ├── ProgressOverlay.vue       # 进度浮层
│       │   └── TagChip.vue              # 标签芯片
│       └── composables/                   # 可复用逻辑
│           ├── useWebSocket.ts
│           ├── useImageLoader.ts
│           └── useInfiniteScroll.ts
│
├── app/                                   # 原有 Android App (保留)
│   └── build.gradle                       # 改为依赖 anotherviewer-core
│
└── settings.gradle                        # include anotherviewer-core, anotherviewer-web
```

---

## 3. 核心模块详细设计

### 3.1 anotherviewer-core -- 代码复用策略

#### 可直接移植 (仅需替换 Android 依赖)

| 模块 | 原始依赖 | 替换方案 | 工作量 |
|------|----------|----------|--------|
| 22 个 HTML Parser | `android.text.TextUtils` | `java.lang.String` 方法 | 极小 |
| SiteEngine (1429行) | `android.text.TextUtils` | 同上 | 极小 |
| SiteUrl (320行) | 无 Android 依赖 | 无需修改 | 无 |
| 23 个 Data Model | 无 Android 依赖 | 无需修改 | 无 |
| SmbConnection (593行) | 无 Android 依赖 (smbj 是纯 Java) | 无需修改 | 无 |
| SpiderInfo | 无 Android 依赖 | 无需修改 | 无 |

#### 需要适配移植

| 模块 | Android 耦合点 | 适配方案 |
|------|---------------|----------|
| SpiderQueen (1832行) | Context, AsyncTask, Process, BitmapFactory, UniFile | Spring @Async + java.io.File |
| SpiderDen (458行) | UniFile, SimpleDiskCache, BitmapFactory | java.io.File + 自定义磁盘 LRU 缓存 |
| SiteCookieStore | SharedPreferences | Spring Session / 内存 Map |
| SiteFilter | Context (字符串资源) | 硬编码或 YAML 配置 |

### 3.2 下载缓存系统

#### 3.2.1 架构总览

```
+-----------------------------------------------------+
|                    anotherviewer-web                       |
|                                                       |
|  +-------------+    +--------------+    +----------+ |
|  |  Download    |--->|  SpiderQueen  |--->| SpiderDen| |
|  |  Service     |    |  (Worker Pool)|    | (Storage)| |
|  +------+-------+    +--------------+    +----+-----+ |
|         |                                      |       |
|  +------v-------+                    +---------v-----+ |
|  |  WebSocket   |                    |  java.io.File | |
|  |  Progress    |                    |               | |
|  +--------------+                    +--------+------+ |
|                                              |        |
+----------------------------------------------|--------+
                                               |
                              +----------------v----------------+
                              |         Linux 文件系统           |
                              |                                  |
                              |  /data/anotherviewer/downloads/       |
                              |  +-- {gid1}-{title1}/            |
                              |  |   +-- 00000001.jpg            |
                              |  |   +-- 00000002.jpg            |
                              |  |   +-- ...                     |
                              |  +-- {gid2}-{title2}/            |
                              |                                  |
                              |  +-- 挂载点 (可选) ----------+   |
                              |  | //NAS/media (CIFS)         |   |
                              |  | NAS:/volume (NFS)          |   |
                              |  | rclone:cloud (WebDAV/S3)   |   |
                              |  +----------------------------+   |
                              +----------------------------------+
```

#### 3.2.2 SpiderQueen 适配要点

原始 Android 版本的核心下载循环:

```
SpiderQueen (Worker线程池, 1-10线程)
  +-- 解析页面URL (GalleryPageParser / GalleryPageApiParser)
  +-- OkHttp下载图片流
  +-- SpiderDen.openOutputStreamPipe() -> 写入文件
  +-- 状态追踪: STATE_NONE -> STATE_DOWNLOADING -> STATE_FINISHED
  +-- 进度回调: OnSpiderListener.onPageSuccess/Failure/Download
  +-- 限速处理: 509检测, downloadDelay
```

Web App 适配:
- Context 参数全部移除, 配置通过构造函数注入
- UniFile -> java.io.File (Linux 原生)
- SimpleDiskCache -> 基于文件系统的 LRU 缓存 (磁盘目录 + 内存索引)
- AsyncTask -> Spring @Async + CompletableFuture
- EventBus -> Spring ApplicationEventPublisher
- 下载进度通过 WebSocket STOMP 推送到前端

#### 3.2.3 SpiderDen 存储层

```
SpiderDen (双模式)
  |
  +-- MODE_READ (浏览模式)
  |   +-- 优先: 本地磁盘缓存 (LRU, 可配置 40-640MB)
  |   +-- 回退: 下载目录 (如果已下载)
  |
  +-- MODE_DOWNLOAD (下载模式)
      +-- 直接写入下载目录
          +-- 文件命名: {00000001}.{jpg|png|gif|webp}
          +-- 目录结构: {downloadPath}/{gid}-{sanitizedTitle}/
```

#### 3.2.4 DownloadManager 适配

```
DownloadManager
  +-- 队列管理: LinkedList<DownloadInfo> (等待队列)
  +-- 标签系统: Map<label, List<DownloadInfo>>
  +-- 单任务执行: waitList -> currentTask -> finish/fail -> next
  +-- 状态机:
  |   +-- STATE_NONE (0)     -- 空闲
  |   +-- STATE_WAIT (1)     -- 等待中
  |   +-- STATE_DOWNLOAD (2) -- 下载中
  |   +-- STATE_FINISH (3)   -- 完成
  |   +-- STATE_FAILED (4)   -- 失败
  +-- 事件通知: DownloadInfoListener -> Spring Event
```

### 3.3 图片流式代理

```
浏览器                    Spring Boot                  Gallery Site
  |                          |                            |
  |  GET /api/v1/gallery/    |                            |
  |  image/{gid}/{page}      |                            |
  | ------------------------>|                            |
  |                          |  解析页面获取图片URL         |
  |                          |  (GalleryPageApiParser)     |
  |                          | -------------------------->|
  |                          | <---------------------------|
  |                          |                            |
  |                          |  OkHttp 流式请求图片         |
  |                          | -------------------------->|
  |                          | <===========================|
  |                          |  (chunked transfer)         |
  | <======================= |                            |
  |  流式转发 + Range支持     |                            |
  |  + Cache-Control头       |                            |
```

关键特性:
- **流式转发**: 不缓冲完整图片, 降低内存占用
- **Range 支持**: 前端可断点续传, 大图友好
- **缓存头**: `Cache-Control: max-age=86400` 减少重复请求
- **Session 透传**: 服务端携带用户 Cookie 访问 Gallery Site
- **错误处理**: 509 限速自动暂停并通知前端

### 3.4 SMB 备份系统

```
+---------------------------------------------+
|              anotherviewer-web                     |
|                                               |
|  +--------------+    +---------------------+ |
|  | SmbController |--->| SmbBackupService    | |
|  | (REST API)    |    | (Spring Service)    | |
|  +--------------+    +---------+-----------+ |
|                                |               |
|                       +--------v-----------+  |
|                       |  SmbConnection      |  |
|                       |  (anotherviewer-core)    |  |
|                       +--------+-----------+  |
|                                |               |
+--------------------------------|---------------+
                                 |
                    +------------v------------+
                    |     SMB/CIFS 服务器      |
                    |     (NAS / 共享目录)      |
                    +-------------------------+
```

服务端优势:
- 无需前台服务/WakeLock/保活, 服务器 24x7 运行
- 支持 Spring @Scheduled 定时同步
- 支持多个 SMB 服务器配置
- 同步进度通过 WebSocket 实时推送

---

## 4. REST API 设计

### 4.1 认证

```
POST   /api/v1/auth/login           # Cookie 登录 (输入 cookie 值)
POST   /api/v1/auth/sign-in         # 论坛账号登录 (username + password)
GET    /api/v1/auth/profile          # 当前用户信息
POST   /api/v1/auth/logout           # 登出
```

### 4.2 画廊浏览

```
GET    /api/v1/gallery/list          # 搜索/浏览画廊列表
       ?keyword=xxx                  # 搜索关键词
       &category=xxx                 # 分类筛选
       &page=1                       # 分页
       &sort=xxx                     # 排序
GET    /api/v1/gallery/popular       # 热门列表
GET    /api/v1/gallery/detail/{gid}  # 画廊详情 (标签、评分、评论数等)
GET    /api/v1/gallery/preview/{gid} # 缩略图预览集
GET    /api/v1/gallery/page/{gid}/{page} # 图片页面信息 (URL、尺寸)
GET    /api/v1/gallery/image/{gid}/{page} # 代理获取图片 (流式)
```

### 4.3 收藏

```
GET    /api/v1/favorite/list         # 收藏列表
       ?slot=0                       # 收藏夹编号 (0=全部)
POST   /api/v1/favorite/add          # 添加收藏
       { gid, token, category }
DELETE /api/v1/favorite/remove       # 移除收藏
       { gid, token, category }
```

### 4.4 评论

```
GET    /api/v1/comment/list/{gid}    # 评论列表
POST   /api/v1/comment/post          # 发表评论
POST   /api/v1/comment/vote          # 投票
       { gid, comment_id, vote }
```

### 4.5 下载管理

```
GET    /api/v1/download/list                 # 下载列表 (含标签分类)
GET    /api/v1/download/list/{label}          # 按标签获取
GET    /api/v1/download/info/{gid}            # 单任务详情 (进度/速度/状态)
POST   /api/v1/download/add                   # 添加下载
       { gid, token, title, thumb, label }
POST   /api/v1/download/start                 # 开始/恢复下载
POST   /api/v1/download/start-all             # 开始所有
POST   /api/v1/download/pause                 # 暂停下载
POST   /api/v1/download/cancel                # 取消下载
DELETE /api/v1/download/delete/{gid}           # 删除已下载 (含文件)
POST   /api/v1/download/label                 # 创建/重命名标签
DELETE /api/v1/download/label/{label}          # 删除标签
WebSocket /ws/progress                        # 实时下载进度推送
```

### 4.6 归档与种子

```
GET    /api/v1/archive/list/{gid}    # 可用归档列表
POST   /api/v1/archive/download      # 下载归档
GET    /api/v1/torrent/list/{gid}    # 种子列表
GET    /api/v1/torrent/download      # 下载种子文件
```

### 4.7 SMB 备份

```
GET    /api/v1/smb/config            # 获取 SMB 配置
PUT    /api/v1/smb/config            # 更新 SMB 配置
POST   /api/v1/smb/test-connection   # 测试连接
POST   /api/v1/smb/sync              # 触发同步 (普通模式)
POST   /api/v1/smb/sync-aggressive   # 触发同步 (高速模式)
GET    /api/v1/smb/progress          # 同步进度
POST   /api/v1/smb/cancel            # 取消同步
```

### 4.8 其他

```
GET    /api/v1/history/list          # 浏览历史
DELETE /api/v1/history/clear         # 清除历史
GET    /api/v1/local-favorites/list  # 本地收藏
POST   /api/v1/local-favorites/add   # 添加
DELETE /api/v1/local-favorites/remove # 移除
GET    /api/v1/quick-search/list     # 快速搜索列表
POST   /api/v1/quick-search/add      # 添加
DELETE /api/v1/quick-search/remove   # 移除
GET    /api/v1/tag/search            # 标签搜索
GET    /api/v1/tag/database          # 标签数据库
```

---

## 5. 数据库设计

从现有 GreenDAO 12 张表迁移为 JPA Entity, 保持表结构兼容:

| 表名 | Entity | 说明 |
|------|--------|------|
| download_info | DownloadInfoEntity | 下载任务 (gid, state, label, progress, speed...) |
| download_label | DownloadLabelEntity | 下载标签 |
| history_info | HistoryInfoEntity | 浏览历史 |
| local_favorite_info | LocalFavoriteInfoEntity | 本地收藏 |
| bookmark_info | BookmarkInfoEntity | 阅读进度书签 |
| quick_search | QuickSearchEntity | 快速搜索 |
| filter | FilterEntity | 过滤器 |
| gallery_tags | GalleryTagsEntity | 标签缓存 |
| black_list | BlackListEntity | 黑名单 |
| download_dirname | DownloadDirnameEntity | 下载目录名映射 |
| smb_config | SmbConfigEntity | SMB 服务器配置 |

数据库文件: `data/anotherviewer.mv.db` (H2) 或 `data/anotherviewer.db` (SQLite)

---

## 6. Linux 网络存储接入

### 6.1 设计理念

Web App 运行在 Linux 服务器上, Java File I/O 对挂载点透明读写。
应用不关心底层是本地磁盘还是网络存储 -- 管理员在操作系统层面配置挂载即可。

### 6.2 配置

```yaml
# application.yml
anotherviewer:
  download:
    # 下载路径 (可以是本地目录或已挂载的网络存储)
    path: /data/anotherviewer/downloads
    # 缓存路径 (建议本地 SSD, 加速读取)
    cache-path: /data/anotherviewer/cache
    cache-size-mb: 512
    # 并发下载线程数
    worker-count: 3
    # 下载延迟 (ms)
    download-delay: 0
    # 下载超时 (ms)
    download-timeout: 60000
```

### 6.3 支持的网络存储接入方式

#### CIFS/SMB (直接复用现有 NAS 配置)

```bash
# /etc/fstab
//192.168.6.141/media  /data/anotherviewer/downloads  cifs  credentials=/etc/smb-cred,uid=1000,gid=1000,iocharset=utf8  0  0

# /etc/smb-cred
username=admin
password=xxxxxx
domain=WORKGROUP
```

#### NFS

```bash
# /etc/fstab
192.168.6.141:/volume1/media  /data/anotherviewer/downloads  nfs  defaults,soft,timeo=10,retrans=3  0  0
```

#### WebDAV

```bash
mount.davfs https://server/dav /data/anotherviewer/downloads
```

#### rclone (支持 Google Drive, OneDrive, S3, 等)

```bash
rclone mount cloud:/data /data/anotherviewer/downloads --vfs-cache-mode full --vfs-cache-max-size 1G
```

#### mergerfs (合并多个挂载点)

```bash
mergerfs /mnt/disk1:/mnt/disk2 /data/anotherviewer/downloads -o category=eplus,fsname=mergerfs
```

#### autofs (按需自动挂载)

```bash
# /etc/auto.smb
media -fstype=cifs,credentials=/etc/smb-cred ://192.168.6.141/media
```

### 6.4 推荐部署方案

```
服务器 (Linux)
+-- 本地 SSD
|   +-- /data/anotherviewer/cache/          # 图片缓存 (高速读写)
|   +-- /data/anotherviewer/data/           # 数据库文件
+-- 网络存储挂载点
|   +-- /data/anotherviewer/downloads/      # 挂载 NAS (CIFS/NFS)
+-- Spring Boot 应用
    +-- 端口 8080 (可配置)
```

---

## 7. 前端页面设计

### 7.1 页面路由

```
/login                 -- 登录页 (Cookie 输入 / 论坛账号登录)
/                      -- 首页/搜索 (画廊网格, 搜索栏, 分类)
/gallery/:gid          -- 画廊详情 (封面, 标签, 评论, 缩略图)
/reader/:gid/:page     -- 图片阅读器 (全屏, 翻页/滚动/缩放)
/downloads             -- 下载管理 (队列, 进度, 标签筛选)
/favorites             -- 收藏管理 (分类收藏夹)
/history               -- 浏览历史
/settings              -- 设置 (Gallery Site, SMB, 代理, 缓存)
/smb-backup            -- SMB 备份管理 (配置, 同步, 进度)
```

### 7.2 关键交互

- **无限滚动**: 画廊列表/搜索结果使用 Intersection Observer 实现
- **图片阅读器**: 支持翻页模式、滚动模式、缩放、键盘/手势导航、亮度调节
- **实时进度**: WebSocket 推送下载/备份进度, 无需轮询
- **响应式**: CSS Grid + Flexbox, 自适应手机/平板/电脑/电视屏幕
- **PWA**: 可选添加到主屏幕, 离线缓存静态资源

---

## 8. 实施阶段

### Phase 1: 项目骨架 + 核心 API (1-2 周)

- 创建 Gradle 多模块项目结构 (anotherviewer-core, anotherviewer-web, web-frontend)
- 从现有代码抽取 anotherviewer-core (移除 Android 依赖, 替换 TextUtils)
- Spring Boot 基础配置 (Security, Session, CORS, WebSocket)
- 数据库 JPA Entity 定义 + 迁移脚本
- 实现认证 API (Cookie 登录)
- 实现画廊 API (列表/详情/预览)
- 实现代理 API (图片流式代理)
- 前端: 登录页 + 首页搜索 + 画廊详情页

### Phase 2: 阅读器 + 收藏 (1 周)

- 前端: 全屏阅读器组件 (翻页/滚动/缩放/手势)
- 实现收藏 API (列表/添加/移除)
- 实现评论 API (列表/发表/投票)
- 实现历史 API
- 前端: 收藏管理页 + 浏览历史页

### Phase 3: 下载缓存系统 (2 周)

- 移植 SpiderQueen (移除 Android 依赖, Spring @Async)
- 移植 SpiderDen (UniFile -> java.io.File, 磁盘 LRU 缓存)
- 移植 DownloadManager (EventBus -> Spring Event)
- 实现下载 API + WebSocket 进度推送
- 实现归档/种子 API
- 前端: 下载管理页 (队列, 进度, 速度, 标签)
- 服务器配置文档: Linux 挂载网络存储指南

### Phase 4: SMB 备份 (1 周)

- 移植 SmbConnection 到 anotherviewer-core
- Spring Service 封装 (配置管理, 连接测试, 同步执行)
- Spring @Scheduled 定时任务支持
- 实现 SMB API + WebSocket 进度推送
- 前端: SMB 备份管理页

### Phase 5: 优化 + 部署 (1 周)

- 图片缓存策略优化 (内存索引 + 磁盘存储)
- Docker 容器化 (Dockerfile + docker-compose.yml)
- 配置文件外部化 (环境变量 / volume)
- 性能测试 (并发连接, 图片代理吞吐)
- 浏览器兼容性测试 (Chrome, Firefox, Safari, Edge, 移动端)
- 使用文档编写

---

## 9. 验证方案

| 阶段 | 验证方式 | 通过标准 |
|------|----------|----------|
| Parser 移植 | JUnit 单元测试 | 与 Android 版输出一致 |
| API 端点 | curl / Postman | 每个端点返回正确 JSON |
| 图片代理 | 浏览器直接访问 | 图片正常显示, 支持 Range |
| 下载系统 | 添加下载 -> 观察进度 -> 验证文件 | 文件完整, 目录结构正确 |
| 阅读器 | 浏览器全屏阅读 | 翻页流畅, 缩放正常 |
| SMB 备份 | 配置 -> 测试连接 -> 同步 | NAS 上文件一致 |
| 前端适配 | 手机/平板/电脑浏览器 | 响应式布局正常 |
| 长时间运行 | 服务器运行 24 小时 | 无内存泄漏, 下载正常 |
| 网络存储 | 挂载 NFS/CIFS 后下载 | 文件写入挂载点正常 |

---

## 10. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| Parser 在 Web 环境解析失败 | 核心功能不可用 | 逐个 Parser 编写测试用例, 对比 Android 输出 |
| Gallery Site 509 限速 | 下载中断 | SpiderQueen 已有 509 检测, 自动暂停重试 |
| 网络存储延迟高 | 下载速度受限 | 缓存层使用本地 SSD, 下载完成后批量同步 |
| 图片代理内存压力 | 服务器 OOM | 流式转发, 不缓冲完整图片; 限制并发连接数 |
| 浏览器兼容性 | 部分功能不可用 | 阅读器使用 Canvas/WebGL 降级方案 |
| Session 管理 | 登录态丢失 | Spring Session 持久化到数据库 |
