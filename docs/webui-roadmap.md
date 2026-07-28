# WebUI 协同系统 — 架构规划 (v3)

> 状态：规划完成，待执行
> 创建：2026-07-28
> 更新：2026-07-28（v3：新增 UI 像素级复刻规范；明确以服务器性能全面优化 UX）
> 更新：2026-07-28（v2：部署目标改为 systemd Linux + NVIDIA GPU；新增 PWA、图片处理管线）
> 分支：BiLi_PC_Gamer

## 目标

将 EhViewer (AnotherViewer) 拓展为 **Android 客户端 + 本地服务器 WebUI** 的混合架构：

- iPad 等无法 sideload 的设备通过浏览器（PWA）获得完整阅读体验
- **WebUI 与 Android 端像素级复刻**：设计语言、操作逻辑、交互反馈完全一致
- **以服务器性能全面优化体验**：利用更强的 CPU/GPU/网络带宽和高 IOPS SSD，
  让受限设备获得比原生端更快、更流畅的体验（秒开、激进预读、AI 增强）
- Android 端保持独立工作能力，可选连接服务器进行同步/远程阅读/委托下载
- 服务器可接入 waifu2x 等 AI 模型进行后台图片增强（超分/降噪）

## 核心设计原则

1. **混合模式**：Android 端完全独立可用；服务器协同是可选增强
2. **UI 像素级复刻**：Android 端设计系统是唯一规范，WebUI 不按 Web 惯例重新设计
   （详见「UI 复刻规范」章节）
3. **性能即特性**：服务器 SSD/网络/CPU 优势转化为用户可感知的体验
   （首屏秒开、翻页零等待、后台 AI 增强）
4. **渐进迭代**：在现有 `ehviewer-core` / `ehviewer-web` / `web-frontend` 基础上改进
5. **图片处理管线化**：抽象接口先行，waifu2x 作为可插拔实现
6. **PWA 优先**：WebUI 面向 iPad Safari 设计，支持 Add to Home Screen
7. **可配置**：所有有争议的行为暴露为用户设置

## 目标环境

| 阶段 | 环境 | 用途 |
|------|------|------|
| 开发测试 | macOS (MacBook) | 本地脚本一键启动，快速迭代 |
| 生产部署 | Linux x86_64 + NVIDIA GPU | systemd 服务，waifu2x CUDA 推理 |

## 架构概览

```
┌────────────────────────────────────────────────────────────────┐
│              Linux Server (systemd, NVIDIA GPU)                  │
│                                                                │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │            ehviewer-web (Spring Boot 3.4, JAR)            │  │
│  │                                                          │  │
│  │  ehviewer-core ─── Download Engine ─── Image Cache       │  │
│  │  (解析/API)        (多线程下载)        (磁盘+内存)        │  │
│  │                                                          │  │
│  │  Image Processing Pipeline (抽象接口)                     │  │
│  │    └── Waifu2xProcessor (HTTP → waifu2x-ncnn-vulkan)     │  │
│  │                                                          │  │
│  │  Sync API ─── WebSocket ─── PWA WebUI (Vue 3 SPA)        │  │
│  │  (REST同步)    (进度推送)    (iPad/浏览器入口)            │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  waifu2x-ncnn-vulkan (独立进程, CUDA, HTTP API)           │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                │
│  [存储: 下载目录 / 图片缓存 / 增强图片 / ehviewer.db]          │
└────────────────────────────────────────────────────────────────┘
       ▲ REST + WebSocket (LAN)          ▲ HTTPS (浏览器/PWA)
       │                                 │
┌──────┴──────────┐               ┌──────┴──────────┐
│ Android 客户端   │               │  iPad / 浏览器   │
│ (独立 + 协同)    │               │  (PWA 阅读器)   │
└─────────────────┘               └─────────────────┘
```

---

## UI 复刻规范（WebUI 必须遵守）

> **原则**：Android 端的 `res/values/*.xml` 和 `res/layout/*.xml` 是 WebUI 的唯一设计规范。
> WebUI 不引入任何 Android 端不存在的设计语言。所有颜色、尺寸、间距、动效曲线
> 必须从 Android 资源文件中提取精确值，不允许"近似"。

### 设计系统来源（权威文件）

| 内容 | Android 源文件 |
|------|---------------|
| 主题/颜色 | `app/src/main/res/values/themes.xml`, `colors.xml` |
| 文字样式 | `app/src/main/res/values/styles.xml`, `dimens.xml` |
| 语义属性 | `app/src/main/res/values/attrs.xml`（`declare-styleable Theme`） |
| 布局结构 | `app/src/main/res/layout/*.xml` |
| 图标 | `app/src/main/res/drawable/v_*.xml`（Material 矢量图，需转为 SVG） |
| 动效曲线 | `app/src/main/res/anim/scene_*.xml` |
| 导航结构 | `app/src/main/res/menu/nav_drawer_main.xml`, `activity_main.xml` |

### 色彩系统（精确值）

**品牌色**：
- Primary (teal 500): `#009688`，PrimaryDark (teal 700): `#00796b`
- Accent (purple A200): `#e040fb`
- 评分星 (yellow 800): `#f9a825`

**三套主题的背景/表面色**：

| 主题 | windowBackground | contentColorPrimary (卡片) |
|------|-----------------|---------------------------|
| Light | `#ffffff` | `#f5f5f5` (grey_100) |
| Dark | `#323232` (grey_850) | `#3a3a3a` (grey_825) |
| Black (AMOLED) | `#000000` | `#191919` (grey_925) |

- 分割线：Light `rgba(0,0,0,0.125)`，Dark `rgba(255,255,255,0.125)`
- Grey 色阶：从 `grey_975`(#080808) 到 `grey_100`(#f5f5f5)，25 级步进

**分类色**（卡片标签底色 / 网格角标三角形）：

| 分类 | 颜色 | 分类 | 颜色 |
|------|------|------|------|
| Doujinshi | `#f44336` | Non-H | `#2196f3` |
| Manga | `#ff9800` | Image Set | `#3f51b5` |
| Artist CG | `#fbc02d` | Cosplay | `#9c27b0` |
| Game CG | `#4caf50` | Asian Porn | `#9575cd` |
| Western | `#8bc34a` | Misc | `#f06292` |

### 排版

- 无自定义字体，使用系统字体栈（Web 端对应 `-apple-system, Roboto, sans-serif`）
- 字号阶梯（sp → px 按 1:1 映射为 CSS px）：
  `super_large 24 / large 22 / little_large 20 / medium 18 / little_small 16 / small 14 / super_small 12`
- 卡片标题：16sp，`textColorPrimary`，maxLines 2，end ellipsize
- 卡片副标题：14sp，`textColorSecondary`，单行

### 卡片规范（克制的 M2 风格，非 Material 默认值）

- 圆角 **2dp**，阴影 **2dp**，外边距 **2dp**
- 背景 = `contentColorPrimary`（随主题）
- 列表模式卡片：左侧固定 **80×120dp** 缩略图（2:3），右侧标题/上传者/星级/分类 chip
- 网格模式卡片：纯图片 tile + 右上角 **32×24dp 分类色三角形** + 语言代码（10sp 白色粗体）

### 导航结构

- **左侧抽屉**（非底部 Tab），最大宽度 **280dp**
- 抽屉头部：160dp 高，背景图（`sadpanda_low_poly`，用户可换），64dp 圆形头像，14sp 白色用户名
- 抽屉菜单 8 项（单选中组）：首页 / 订阅 / 热门 / 排行榜 / 收藏 / 历史 / 下载 / 设置
- 抽屉底部：配额控件（`LimitsCountView`）+ 切换主题按钮
- Toolbar：高度 `actionBarSize`，背景 `toolbarColor`（Light=teal，Dark/Black=背景色），白色图标

### 关键屏幕复刻清单

| 屏幕 | Android 布局 | WebUI 复刻要点 |
|------|-------------|---------------|
| 画廊列表 | `scene_gallery_list.xml` | 浮动搜索栏卡片（顶部 48dp 行）+ 瀑布流（AutoStaggeredGrid，按列宽自动分栏）+ FabLayout 集群 + 列表/网格双模式 |
| 画廊详情 | `gallery_detail_*.xml` | 彩色头部带（128×192 缩略图 + 白字标题）+ Download/Read 按钮卡 + 信息表格 + 横滑操作按钮行 + 标签组 + 评分条 + 预览网格 |
| 阅读器 | `activity_gallery.xml` | 顶部状态栏（时钟/页码进度/电量，56sp 描边页码）+ 底部 SeekBar 面板（`#424242`/`#212121`，48dp 高，左右页码 32dp）+ 自动播放按钮 45dp |
| 下载列表 | `scene_download.xml` | 分类 Spinner + 卡片列表（80×120 缩略图 + 进度条 + 百分比/速度 12sp + 状态色）+ FabLayout（全选/播放/暂停/删除/移动/随机） |
| 搜索 | `widget_search_bar.xml` | 浮动搜索卡（48dp：菜单图标 + 标题/输入框 + 操作图标）+ 可展开建议列表 + CategoryTable 色块 + 高级搜索面板 |
| 设置 | `settings_headers.xml` | 6 项头部列表（主色图标）→ 各 Preference 子页 |

### 自定义控件 → Web 组件映射

| Android 控件 | Web 实现 |
|-------------|---------|
| `SimpleRatingView`（5 星，16dp，1dp 间隔，0-10 映射） | SVG 星级组件（满/半/空星） |
| `FixedThumb`/`TileThumb`（宽高比钳制 0.333–1.333） | `object-fit: cover` + aspect-ratio 容器 |
| `FabLayout`（主 FAB 56dp + 次 FAB 40dp，展开动画） | Vue 组件，展开/收起过渡 |
| `ContentLayout`（ProgressView + 提示 + 下拉刷新 + 列表 + FastScroller） | 组合容器组件 |
| `SearchBar` + `SearchLayout`（浮动卡片 + 筛选面板） | Vue 组件，展开/收起 |
| `CategoryTable`（分类色块选择器，40dp 行高） | 色块网格组件 |
| `AutoStaggeredGridLayoutManager`（按列宽自动分栏） | CSS `column-width` 瀑布流 / Masonry |
| `ProgressView`（自定义转圈，Small 16dp / Large 76dp） | SVG/CSS spinner |
| `ReversibleSeekBar`（可反向进度条） | 自定义 slider（支持 RTL 方向） |
| `FastScroller`（右缘 30dp 拖拽） | 自定义滚动条组件 |

### 动效规范

- 场景切换：`scene_open_enter` = 透明度 0→1（200ms decelerate_quart）+ translateY 8%→0（350ms decelerate_quint）
- Web 端用 `cubic-bezier` 还原 decelerate 曲线（quart ≈ `cubic-bezier(0.165,0.84,0.44,1)`，quint ≈ `cubic-bezier(0.23,1,0.32,1)`）
- 共享元素过渡（缩略图→详情）：Web 端用 FLIP 动画或 View Transitions API 近似
- 下拉刷新边缘色：Light=primary teal，Dark=grey_500

### 图标

- 全部为 Material 矢量图（24dp 标准），命名 `v_<name>_<color>_<size>.xml`
- WebUI 需将这些 XML 矢量图批量转为 SVG（保持精确路径和颜色）
- 转换脚本应放入 `web-frontend/scripts/`，作为构建流程一部分

### 性能即特性（服务器优势 → 可感知体验）

像素级复刻是"看起来一样"，服务器性能是"用起来更爽"。WebUI 必须利用服务器优势：

1. **首屏秒开**：服务器 SSD 高 IOPS → 缩略图/列表数据毫秒级响应；App Shell 预缓存
2. **翻页零等待**：激进预读（N+1~N+K）+ 服务器内存热缓存，翻页时图片已在缓存
3. **后台 AI 增强**：GPU 跑 waifu2x，用户阅读原图时增强版在后台生成，完成后无缝替换
4. **大图直出**：服务器解码/传输原图，受限设备无需本地解码大文件
5. **进度可视化**：WebSocket 实时推送下载/处理进度，UI 即时反馈（进度条、速度、状态色）

### 复刻验收标准

- [ ] 三套主题（Light/Dark/Black）切换，颜色与 Android 端逐像素一致（截图对比）
- [ ] 画廊列表的列表/网格双模式布局与 Android 端一致（缩略图尺寸、间距、分类色）
- [ ] 抽屉导航 8 项 + 头部 + 底部配额控件完整复刻
- [ ] 阅读器顶部状态栏 + 底部 SeekBar 面板与 Android 端一致
- [ ] 所有 Material 矢量图标已转 SVG 且颜色/尺寸正确
- [ ] 场景切换动效曲线与 Android 端一致（200/350ms decelerate）
- [ ] iPad 横竖屏、Light/Dark 模式下布局无破损

---

## Phase 0：基础修复与整合

**目标**：让 `ehviewer-web` 正确复用 `ehviewer-core`，消除脆弱的正则爬取和重复逻辑。

### 0.1 DownloadService 改用 ehviewer-core 解析器

- 现状：`ehviewer-web/.../service/DownloadService.kt` 用 `java.net.http.HttpClient` + 正则
  (`IMAGE_URL_PATTERN`, `SHOW_KEY_PATTERN`) 爬取页面
- 改为：调用 `ehviewer-core` 的 `GalleryPageParser` / `GalleryPageApiParser`
- core 已包含 OkHttp 3.14.7 依赖，web 端可直接使用

### 0.2 图片缓存升级

- 现状：`ImageCacheService.kt` = 纯内存 LRU（500 条目 / 200MB），重启即丢失
- 改为：Caffeine（内存热缓存）+ 磁盘目录（持久缓存）
- 配置项：
  - `ehviewer.cache.image-path`（磁盘缓存路径）
  - `ehviewer.cache.image-max-size`（容量上限，默认 5GB）
  - `ehviewer.cache.memory-max-entries`（内存条目数，默认 200）
- 淘汰策略：LRU，优先保留最近访问的画廊

### 0.3 Cookie/会话管理统一

- 复用 `ehviewer-core` 的 `EhCookieStore`（SQLite Cookie 持久化）
- web 端和 core 共享同一 Cookie 存储
- 登录态过期检测 + 前端提示

### 0.4 ehviewer-core 接口补全

- 检查 web 端 Controller 中绕过 core 的功能（archiver、torrent 等）
- 缺失的解析/请求逻辑迁入 core

### 验收标准

- [ ] web 端下载画廊全程通过 core 解析器（无正则）
- [ ] 重启后缓存图片仍可直接访问
- [ ] 重启后不需要重新登录

---

## Phase 1：图片流式服务 + 处理管线

**目标**：按需供图（无需下载整个画廊即可阅读）；建立可扩展的图片处理管线。

### 1.1 按需图片流式 API

- `GET /api/image/{galleryId}/{page}` — 返回单页原图
- 流程：磁盘缓存 → 命中返回 → 未命中 → core 解析器获取 URL → 下载 → 缓存 → 返回
- 支持 Range 请求
- 并发控制：同一画廊并发抓取数限制（避免 509）
- 可选参数 `?enhanced=true`：返回增强版本（若已处理）

### 1.2 预读缓存

- 请求第 N 页时异步预取 N+1 ~ N+K 页（K 可配置，默认 3）
- 预读低优先级，不阻塞当前请求
- 配置：`ehviewer.reader.prefetch-pages`

### 1.3 图片处理管线（抽象接口）

```kotlin
interface ImageProcessor {
    /** 处理器唯一标识 */
    val id: String
    /** 是否可用（检查外部服务连通性） */
    fun isAvailable(): Boolean
    /** 处理单张图片，返回处理后的文件 */
    suspend fun process(input: Path, options: ProcessingOptions): Path
    /** 支持的处理类型 */
    val capabilities: Set<ProcessingType>
}

enum class ProcessingType {
    UPSCALE_2X,    // 2倍超分
    UPSCALE_4X,    // 4倍超分
    DENOISE,       // 降噪
    DENOISE_UPSCALE // 降噪+超分
}

data class ProcessingOptions(
    val type: ProcessingType,
    val outputFormat: String = "png",  // png/webp/jpg
    val quality: Int = 90
)
```

- 管线调度器 `ImageProcessingService`：
  - 维护处理任务队列（后台异步执行）
  - 任务状态：PENDING → PROCESSING → DONE / FAILED
  - 结果存储：`{cache}/enhanced/{galleryId}/{page}.{ext}`
  - 原图不受影响，增强版本是独立副本
  - 并发数可配置（GPU 显存有限，默认 1-2 并发）

### 1.4 Waifu2x 实现（后续插入，本阶段只建接口）

- 实现为 `Waifu2xProcessor : ImageProcessor`
- 通过 HTTP 调用独立运行的 `waifu2x-ncnn-vulkan` HTTP wrapper
- 配置：`ehviewer.processing.waifu2x.url`（如 `http://localhost:9000`）
- 本阶段用 `NoopProcessor` 占位，确保管线可运行

### 1.5 缓存管理 API

- `GET /api/cache/stats` — 使用统计
- `DELETE /api/cache/gallery/{id}` — 清除指定画廊缓存
- `POST /api/process/gallery/{id}` — 触发画廊图片增强（后台队列）
- `GET /api/process/status/{taskId}` — 查询处理进度

### 验收标准

- [ ] 浏览器逐页阅读未下载画廊，首次短暂等待，后续秒开
- [ ] 预读生效：阅读第 1 页时 2-4 页已后台缓存
- [ ] `ImageProcessor` 接口定义完成，`NoopProcessor` 可注册
- [ ] 处理队列 API 可调用（即使实际处理为 noop）
- [ ] LRU 淘汰正常工作

---

## Phase 2：Android-Server 同步协议

**目标**：Android 端可连接服务器，双向同步数据，支持远程阅读和委托下载。

### 2.1 同步协议

- 传输：REST over HTTP（局域网）
- 认证：服务器生成 API Token，Android 端手动输入
- 同步实体：
  - 收藏（LocalFavoriteInfo）— union merge
  - 历史（HistoryInfo）— last-write-wins by timestamp
  - 下载列表（DownloadInfo）— union merge + 状态同步
  - 阅读进度（书签/页码）— last-write-wins
  - 筛选器/快速搜索 — union merge
- 增量同步：`lastSyncTimestamp`，只传变更
- 冲突：时间戳 last-write-wins；收藏/历史 union（不删远端条目）

### 2.2 服务器端 Sync API

- `POST /api/sync/push` — 接收客户端变更
- `GET /api/sync/pull?since={timestamp}` — 返回服务端变更
- `GET /api/sync/status` — 同步状态

### 2.3 Android 端：连接管理

- 设置页新增"服务器连接"
- 手动输入 `IP:Port`
- Token 认证 + 连接测试
- 状态指示（已连接/断开/同步中）
- 可选：WiFi 自动连接，移动网络断开

### 2.4 Android 端：远程阅读

- 连接服务器时，`GalleryActivity` 可选"从服务器加载"
- 图片请求重定向到 `/api/image/{galleryId}/{page}`
- 本地保留少量阅读缓存（`SpiderDen` read cache）

### 2.5 Android 端：委托下载

- 下载对话框新增"在服务器上下载"
- `POST /api/download/start` 触发服务器端任务
- 进度通过轮询/WebSocket 获取
- 完成后文件在服务器，不占手机空间

### 验收标准

- [ ] Android 输入 IP 后连接成功
- [ ] 手机收藏 → 服务器可见
- [ ] 服务器下载完成 → 手机列表显示"已完成（服务器）"
- [ ] 手机"从服务器阅读"正常逐页加载
- [ ] 委托下载流程完整

---

## Phase 3：PWA WebUI（像素级复刻 Android 端）

**目标**：iPad Safari 上通过 PWA 获得与 Android 端**视觉和交互完全一致**的全屏阅读体验，
并利用服务器性能做到比原生端更快。

> 所有 UI 工作必须遵守「UI 复刻规范」章节。本 Phase 以设计系统基础为起点，
> 逐屏幕复刻，最后叠加 PWA 能力和性能优化。

### 3.1 设计系统基础（所有 UI 工作的前置）

- **CSS 设计令牌**：从 Android `colors.xml`/`dimens.xml`/`attrs.xml` 提取精确值，
  建立 CSS 自定义属性体系（`:root` + `[data-theme="dark"]` + `[data-theme="black"]`）：
  - 色彩：primary/accent/三套主题的 bg+surface+divider、10 个分类色、grey 色阶
  - 尺寸：字号阶梯、卡片圆角/阴影/边距、缩略图尺寸、抽屉宽度、toolbar 高度
  - 动效：decelerate_quart/quint 的 cubic-bezier 曲线、过渡时长
- **图标管线**：编写脚本将 `app/src/main/res/drawable/v_*.xml`（Android VectorDrawable）
  批量转为 SVG，输出到 `web-frontend/src/assets/icons/`，保持精确路径和颜色。
  纳入构建流程（`web-frontend/scripts/convert-icons.*`）
- **基础组件库**（Vue 组件，逐个对照 Android 控件实现）：
  - `RatingStars`（SimpleRatingView：5 星、16px、1px 间隔、0-10 映射、半星）
  - `CategoryChip` / `CategoryTriangle`（分类色标签 / 网格角标）
  - `AppCard`（2dp 圆角/阴影/边距，背景随主题）
  - `FabLayout`（主 FAB 56px + 次 FAB 40px，展开/收起动画）
  - `ProgressSpinner`（ProgressView：Small 16px / Large 76px）
  - `ContentLayout`（下拉刷新 + 列表 + FastScroller + 空态提示）
  - `SearchBar` + `SearchLayout`（浮动搜索卡 + 筛选面板 + CategoryTable）
  - `SeekBarPanel` / `ReversibleSeekBar`（阅读器底部，支持 RTL）
  - `NavigationDrawer`（280px，头部 + 8 项菜单 + 底部配额/主题）
- **主题切换**：Light/Dark/Black 三主题，与 Android 端 `Settings.THEME_*` 语义一致，
  通过同步协议与 Android 端共享用户主题偏好

### 3.2 核心屏幕复刻（按 Android 布局逐个实现）

按「UI 复刻规范 → 关键屏幕复刻清单」实现，每个屏幕对照 Android 布局文件：
1. **画廊列表**（`scene_gallery_list.xml`）：列表/网格双模式、瀑布流自动分栏、
   浮动搜索栏、FabLayout 集群、下拉刷新、无限滚动分页
2. **画廊详情**（`gallery_detail_*.xml`）：彩色头部带、Download/Read 按钮卡、
   信息表格、横滑操作行、标签组、评分条、预览网格、评论区
3. **阅读器**（`activity_gallery.xml`）：顶部状态栏（时钟/描边页码/电量）、
   底部 SeekBar 面板、自动播放、双击缩放、阅读方向（LTR/RTL/垂直）、双页模式
4. **下载列表**（`scene_download.xml`）：卡片 + 进度条 + 速度/百分比、FabLayout 批量操作
5. **搜索**（`widget_search_bar.xml`）：分类色块、高级搜索、快速搜索、图搜
6. **设置**（`settings_headers.xml`）：6 项头部 → Preference 子页
7. **收藏/历史/订阅/排行榜**：复用画廊列表组件 + 各自数据源

### 3.3 PWA 基础设施

- `manifest.json`：`display: standalone`、多尺寸图标（192/512）、
  `theme_color`/`background_color`（跟随当前主题）、`start_url: /`
- Service Worker：
  - App Shell 预缓存（HTML/CSS/JS/图标）
  - 图片缓存：Cache API + 容量管理（`navigator.storage.estimate()`）
  - 离线支持：已缓存画廊可离线阅读
- iOS/iPadOS 适配：
  - `apple-mobile-web-app-capable: yes` + `apple-touch-icon`
  - 安全区域适配（`env(safe-area-inset-*)`）
  - 阅读模式下禁止橡皮筋滚动和双击缩放冲突

### 3.4 性能优化（服务器优势落地）

- **首屏秒开**：App Shell 预缓存 + 关键 CSS 内联 + 缩略图懒加载（IntersectionObserver）
- **翻页零等待**：阅读器请求第 N 页时，服务器已预读 N+1~N+K（Phase 1.2），
  前端配合 `<link rel="preload">` / fetch 预热
- **AI 增强无缝替换**：阅读时若增强版就绪（WebSocket 通知），后台静默换源，不打断阅读
- **虚拟滚动**：超长画廊列表用虚拟滚动，DOM 节点数恒定
- **WebSocket 实时反馈**：下载/处理进度条、速度、状态色即时更新

### 3.5 设置页面（服务器配置可视化）

- 服务器：下载路径、缓存大小、并发数、预读页数
- 图片处理：waifu2x 开关、增强参数、队列状态
- 账户：E-Hentai 登录态、Cookie 管理
- 同步：已连接设备、手动触发同步
- 外观：主题切换（Light/Dark/Black，与 Android 端同步）

### 验收标准

- [ ] 三套主题与 Android 端截图逐像素对比一致（色彩/间距/字号/圆角）
- [ ] 7 个核心屏幕全部复刻，布局与 Android 端一致
- [ ] 所有图标为 Android 矢量图精确转换的 SVG
- [ ] 场景切换动效曲线与 Android 端一致
- [ ] iPad Safari "添加到主屏幕"后全屏运行，无浏览器 UI
- [ ] 离线状态下已缓存画廊可正常阅读
- [ ] 阅读器手势流畅（翻页、缩放），横屏自动双页
- [ ] 下载管理 + AI 增强触发可用
- [ ] 首屏加载 < 1s（局域网），翻页零等待（预读命中时）

---

## Phase 4：部署

### 4.1 本地测试脚本 (macOS)

`scripts/dev-run.sh`：
```bash
#!/bin/bash
# 一键构建并启动 ehviewer-web（开发测试用）
set -e

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DATA_DIR="${PROJECT_ROOT}/.dev-data"

mkdir -p "${DATA_DIR}"/{downloads,cache,db}

# 构建前端
cd "${PROJECT_ROOT}/web-frontend"
npm install && npm run build

# 构建后端
cd "${PROJECT_ROOT}"
./gradlew :ehviewer-web:bootJar -x test

# 启动
java -jar ehviewer-web/build/libs/ehviewer-web-*.jar \
  --ehviewer.data-dir="${DATA_DIR}/db" \
  --ehviewer.download.path="${DATA_DIR}/downloads" \
  --ehviewer.cache.image-path="${DATA_DIR}/cache" \
  --server.port=8080
```

### 4.2 systemd 服务 (Linux 生产)

`/etc/systemd/system/ehviewer-web.service`：
```ini
[Unit]
Description=EhViewer Web Server
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=ehviewer
Group=ehviewer
WorkingDirectory=/opt/ehviewer
ExecStart=/usr/bin/java -Xmx1g -jar /opt/ehviewer/ehviewer-web.jar
EnvironmentFile=/opt/ehviewer/ehviewer.env
Restart=on-failure
RestartSec=5
StandardOutput=journal
StandardError=journal

# 安全加固
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/opt/ehviewer/data /opt/ehviewer/downloads /opt/ehviewer/cache

[Install]
WantedBy=multi-user.target
```

`/opt/ehviewer/ehviewer.env`：
```env
EHVIEWER_DATA_DIR=/opt/ehviewer/data
EHVIEWER_DOWNLOAD_PATH=/opt/ehviewer/downloads
EHVIEWER_CACHE_IMAGE_PATH=/opt/ehviewer/cache
EHVIEWER_CACHE_IMAGE_MAX_SIZE=10GB
EHVIEWER_SERVER_PORT=8080
# waifu2x (Phase 后续)
# EHVIEWER_PROCESSING_WAIFU2X_URL=http://localhost:9000
```

### 4.3 waifu2x 服务 (后续)

`/etc/systemd/system/waifu2x.service`：
```ini
[Unit]
Description=Waifu2x NCNN Vulkan Server
After=network.target

[Service]
Type=simple
User=ehviewer
ExecStart=/opt/waifu2x/waifu2x-ncnn-vulkan-server -m models-upconv_7_anime_style_art_rgb -g 0 -p 9000
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

### 4.4 构建与发布

- Gradle `bootJar` 产出 fat JAR
- 前端构建产物嵌入 JAR（`web-frontend` → `ehviewer-web/src/main/resources/static/`）
- 可选：GitHub Actions CI 产出 release JAR

### 4.5 文档

- 部署指南（systemd + 目录结构 + 权限）
- Nginx/Caddy HTTPS 反代配置（iPad PWA 在非 localhost 需要 HTTPS）
- waifu2x 安装与配置
- Android 端连接指南

### 验收标准

- [ ] MacBook 上 `scripts/dev-run.sh` 一键启动成功
- [ ] Linux 上 systemd 服务正常运行、开机自启
- [ ] 数据持久化：服务重启后数据不丢失
- [ ] iPad 通过 HTTPS 访问 PWA 正常

---

## 技术决策记录

| 决策 | 选择 | 理由 |
|------|------|------|
| 后端框架 | Spring Boot 3.4 (保留) | 已有基础，生态成熟 |
| 前端框架 | Vue 3 + Vite (保留) | 已有基础，轻量 |
| 数据库 | SQLite (保留) | 单用户场景足够，零运维 |
| 图片缓存 | Caffeine + 磁盘目录 | 轻量可控 |
| 图片处理 | 抽象管线 + waifu2x 插件 | 可扩展，不耦合具体实现 |
| waifu2x 运行方式 | 独立进程 + HTTP API | GPU 资源隔离，崩溃不影响主服务 |
| waifu2x 推理 | waifu2x-ncnn-vulkan (CUDA) | 有 NVIDIA GPU，性能最优 |
| PWA | manifest + Service Worker | iPad 无法 sideload，PWA 是最佳方案 |
| 服务发现 | 手动输入 IP:Port | 简单可靠 |
| 同步协议 | REST + 时间戳增量 | 简单、调试方便 |
| 测试部署 | macOS shell 脚本 | 快速迭代 |
| 生产部署 | systemd + fat JAR | 直接、可控、无 Docker 开销 |
| **UI 设计规范** | **Android 端 res/ 资源为唯一规范** | 像素级复刻是硬需求，不允许 Web 端自创设计语言 |
| **设计令牌** | CSS 自定义属性（三主题） | 从 Android colors/dimens/attrs 提取精确值，保证逐像素一致 |
| **图标方案** | Android VectorDrawable → SVG 批量转换 | 复用现有矢量资产，不重绘，保证路径/颜色精确 |
| **主题体系** | Light/Dark/Black 三套（对应 Android THEME_*） | 与 Android 端语义一致，可通过同步协议共享偏好 |
| **导航模式** | 左侧抽屉（280px），非底部 Tab | 严格复刻 Android 端 DrawerLayout 结构 |
| **组件实现** | 逐个对照 Android 自定义控件手写 Vue 组件 | 不引入第三方 UI 库（其设计语言与 M2 定制风格冲突） |

## 风险与注意事项

1. **ehviewer-core 与 app 代码分叉**：core 是 app 的副本，bugfix 不自动同步。
   长期应考虑 app 也依赖 core，但不在本路线图范围。
2. **E-Hentai 反爬/509 限制**：服务器并发请求需合理控制间隔。
3. **Cookie 过期**：需检测并提示重新登录。
4. **PWA 需要 HTTPS**：iPad 上 Service Worker 要求安全上下文。
   局域网内需配置自签证书 + 设备信任，或用 Nginx 反代。
5. **GPU 显存管理**：waifu2x 并发处理数受显存限制，需队列控制。
6. **iPad Safari 限制**：
   - Service Worker 缓存有容量限制（通常 ~1GB）
   - 后台执行受限，离线预缓存需在阅读时主动触发
   - 不支持 Web Push（无通知能力）

## 执行顺序

```
后端线：  Phase 0 (地基) → Phase 1 (图片服务+管线) → Phase 2 (Android协同)
前端线：  Phase 3.1 (设计系统基础) → Phase 3.2 (屏幕复刻) → Phase 3.3-3.5 (PWA+性能+设置)
部署线：  Phase 4 (测试脚本随时可写 → systemd)
```

**并行策略**：
- 前端线（Phase 3.1 设计系统）与后端线（Phase 0/1）**可以并行**——
  设计令牌、图标转换、基础组件都是纯前端工作，不依赖后端改动。
  屏幕复刻（3.2）需要后端 API 就绪后联调。
- Phase 2（Android 协同）和 Phase 3（WebUI）可部分并行（一个改 Android 端，一个改前端）。
- Phase 4 的 MacBook 测试脚本在 Phase 0 完成时即可使用，systemd 配置随时可写。

**关键路径**：Phase 3.1 设计系统基础是前端所有工作的前置，应尽早启动。
图标转换脚本（VectorDrawable → SVG）是独立的工具任务，可以最先完成。
