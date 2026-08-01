# AnotherViewer PWA 离线能力说明

对应 roadmap Phase 3.3。本文档描述离线/安装能力的实现方式与手动验证方法。

## 1. 架构总览

| 组件 | 文件 | 职责 |
| --- | --- | --- |
| Web App Manifest | `public/manifest.json` | 安装元数据：standalone 显示、192/512 图标（含 maskable）、`theme_color`/`background_color`、快捷方式 |
| Service Worker | `public/sw.js` | App Shell 预缓存、运行时缓存策略、离线回退、图片容量管理、缓存版本失效 |
| 注册与消息协议 | `src/register-sw.ts` | 仅生产环境注册；SKIP_WAITING 更新流；`clearImageCache()` / `getCacheStats()` |
| iOS 元标签 | `index.html` | `apple-mobile-web-app-capable`、`apple-touch-icon`（120/152/167/180）、`viewport-fit=cover` |
| 图标 | `public/icons/` | 192/512（manifest）+ 120/152/167/180（iOS 主屏幕） |

## 2. 缓存策略（sw.js）

所有缓存以 `CACHE_NAME`（当前 `ehviewer-v1`）为前缀。**发版需要作废旧缓存时，bump 该常量**（如 `ehviewer-v2`），activate 阶段会自动删除所有旧前缀缓存。

| 请求类型 | 策略 | 缓存名 | 说明 |
| --- | --- | --- | --- |
| App Shell（`/`、`/index.html`、manifest、图标） | install 时预缓存 | `*-shell` | Vite 产物带内容 hash，运行时首次命中后写入 shell 缓存（CacheFirst，hash 名不可变，可安全长缓存） |
| 导航请求（SPA 路由） | NetworkFirst → 回退已缓存 shell | `*-shell` | 在线时刷新最新 HTML；离线时任意已访问路由可打开 |
| API GET（`/api/*`） | NetworkFirst → 回退缓存（30 分钟 TTL） | `*-api` | 画廊列表/详情在线保持最新；写入时打时间戳，**离线仅回退 30 分钟内的缓存**（`API_MAX_AGE_MS`），过期条目直接清除；离线且无新鲜缓存时返回可解析 JSON：`{"error":"offline",...}`（HTTP 503），供应用层展示友好离线态 |
| 图片（`/api/v1/image/*` 及所有 image 请求） | CacheFirst + 过期淘汰 | `*-images` | 上限 500 条 / 30 天；每次写入调用 `navigator.storage.estimate()`，用量超过配额 80% 时上限减半，主动释放空间 |
| 同源静态资源（hashed JS/CSS/字体） | CacheFirst | `*-shell` | 内容寻址，永久安全 |

不拦截：非 GET 请求、WebSocket（`/ws`）、Range 请求、跨域非图片请求。

## 3. 更新流程（SW 版本迭代）

1. 新 `sw.js` 部署后，浏览器后台安装新 worker（旧 worker 仍控制页面）。
2. `register-sw.ts` 检测到 `installed` 且存在旧 controller → 发送 `SKIP_WAITING`。
3. 新 worker activate：删除旧版本缓存 → `clients.claim()`。
4. 页面收到 `controllerchange` → 自动 reload 一次，UI 与新 shell 缓存对齐。
   （首次安装时不会触发 reload，避免打断首访。）

## 4. 消息协议（应用 ↔ SW）

| 应用 → SW | SW → 应用 | 用途 |
| --- | --- | --- |
| `{ type: 'SKIP_WAITING' }` | — | 立即激活等待中的新 worker |
| `{ type: 'CLEAR_IMAGE_CACHE' }` | `{ type: 'IMAGE_CACHE_CLEARED' }` | 清空图片缓存（设置页，`clearImageCache()`） |
| `{ type: 'GET_CACHE_STATS' }` | `{ type: 'CACHE_STATS', stats }` | 各缓存条目数 + `storageUsage`/`storageQuota`（`getCacheStats()`） |

## 5. iOS / iPadOS 适配

- `apple-mobile-web-app-capable: yes` + `status-bar-style: black-translucent`：添加到主屏幕后全屏运行，无 Safari UI。
- `apple-mobile-web-app-title: AnotherViewer`：主屏幕图标名称。
- `apple-touch-icon` 四尺寸（180 iPhone / 167 iPad Pro / 152 iPad / 120 旧 iPhone），由 `icon-512.png` 缩放生成。
- `viewport-fit=cover` + `tokens.css` 中的 `--safe-area-*` 令牌（`env(safe-area-inset-*)`）：刘海/圆角/Home 指示条安全区适配。
- 主题色：`<meta name="theme-color">` 由 `src/stores/theme.ts` 随 light/dark/black 主题实时改写（#009688 / #323232 / #000000），manifest 中为初始值。

## 6. 手动验证清单

### 6.1 添加到主屏幕（iPad Safari）

- [ ] Safari 打开站点 → 分享 → "添加到主屏幕"，名称显示 **AnotherViewer**
- [ ] 主屏幕图标清晰（152/167px，无模糊、无异常裁切）
- [ ] 从图标启动：全屏运行，**无地址栏/工具栏**，状态栏半透明融入背景
- [ ] 切换 light/dark/black 主题，状态栏/启动背景颜色跟随
- [ ] 长按图标出现快捷方式：首页 / 搜索 / 收藏，且可直达对应页面

### 6.2 离线阅读已缓存画廊

- [ ] 在线浏览若干画廊（列表 + 详情 + 阅读若干页图片）
- [ ] DevTools → Application → Service Workers：确认 sw.js 状态 activated & running
- [ ] DevTools → Application → Cache Storage：确认 `ehviewer-v1-shell` / `-api` / `-images` 均有内容
- [ ] 开启飞行模式（或 DevTools 勾选 Offline）
- [ ] 重新打开应用 → 首页/已访问路由可加载（shell 回退）
- [ ] 进入之前浏览过的画廊详情 → 元数据可显示（API 缓存回退，需在 30 分钟 TTL 内；超时则返回 503 JSON）
- [ ] 进入阅读器翻阅之前加载过的页面 → 图片正常显示（图片缓存）
- [ ] 请求一个**从未访问过**的接口 → 收到 HTTP 503 + JSON `{"error":"offline",...}`，而不是浏览器原始网络错误页
- [ ] 恢复网络后刷新 → 数据恢复最新

### 6.3 SW 更新流程

- [ ] 修改并重新构建部署（或本地 `vite build` 后刷新）
- [ ] 刷新页面一次：新 SW 安装 → 自动 SKIP_WAITING → 页面自动 reload 一次
- [ ] DevTools 确认新 worker activated，旧缓存前缀已被清理（Cache Storage 中无旧版本键）
- [ ] 需要强制全量失效时：bump `sw.js` 顶部 `CACHE_NAME`

### 6.4 缓存容量

- [ ] 控制台执行（页面受 SW 控制时）：
      ```js
      navigator.serviceWorker.controller.postMessage({ type: 'GET_CACHE_STATS' })
      navigator.serviceWorker.addEventListener('message', e => console.log(e.data))
      ```
      收到 `CACHE_STATS`，含各缓存条目数与 `storageUsage` / `storageQuota`
- [ ] 大量浏览图片后，`-images` 条目数不超过 500

## 7. 已知边界

- 开发环境（`npm run dev`）不注册 SW，避免与 HMR 冲突；离线能力仅在构建产物上验证。
- iOS 上"添加到主屏幕"时图标的抓取不经过页面的 SW，需在线完成安装。
- 离线时登录态接口（401）仍会触发跳转 `/login`；离线登录需后端支持，不在 3.3 范围。
- 应用层"友好离线提示 UI"（如离线横幅）由视图层实现，SW 仅保证返回可解析的 503 JSON。
