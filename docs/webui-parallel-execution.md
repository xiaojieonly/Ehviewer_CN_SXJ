# WebUI 协同系统 — 并行执行架构 (子代理编排规范)

> 状态：执行中（Wave 0-2 完成，Wave 3-4 推进中）— **实际进度见 `webui-progress.md`**
> 创建：2026-07-28
> 定位：本文档是 `webui-roadmap.md` v3 的**执行层补充**。
> 路线图定义"做什么"（What），本文档定义"如何用多子代理并行地做"（How）。

## 0. 阅读约定

- 执行 AI 是一个**能力强、善于开子代理、并行处理任务、善用工具**的大模型。
- 本文档刻意挑战其能力上限：把可独立的工作拆到最细粒度，用契约解耦依赖，
  让尽可能多的子代理**同时**推进。
- 每个子代理任务都有明确的：输入契约、输出物、依赖前置、验收门、写作用域。
- **写作用域（write scope）** 是并行安全的命脉：任何两个并行代理不得写同一文件。

---

## 1. 路线图批判性审计

### 1.1 优势（保留，不重做）

- 分阶段 + 验收标准的结构清晰。
- UI 复刻规范的精确值（颜色/尺寸/动效曲线）质量高，可直接转为契约。
- 技术决策表有理有据，无技术选型层面的争议。
- 风险章节诚实（PWA HTTPS、GPU 显存、Safari 限制）。

### 1.2 关键缺陷（按对并行执行的影响排序）

| # | 缺陷 | 影响 | 对策 |
|---|------|------|------|
| D1 | **无冻结契约层** | 前后端无法真正并行，前端必等后端 | 新增 Wave 0：先冻结所有接口规格 |
| D2 | **并行策略仅 3 bullet** | 子代理无法据其认领任务 | 本文档 §3-§5 重建 DAG + 波次编排 |
| D3 | **Phase 3 是单体** | 7 屏幕串行，浪费并行度 | 拆 7 路屏幕代理 + 组件契约前置 |
| D4 | **无 MVP 垂直切片** | 架构风险晚暴露 | Wave 1 末插入"列表屏端到端"联调切片 |
| D5 | **验收无自动化** | "逐像素一致"无法机器判定 | 新增视觉回归 + 契约测试代理 |
| D6 | **dev-run.sh 在 Phase 4** | 无法早期联调 | 上提为 Wave 0 任务 |
| D7 | **waifu2x 实装无规划** | 接口未冻结则后续无法接入 | 仅冻结必要接口（Wave 0 B7）；实装明确为本路线图范围外（后续 session 接入） |
| D8 | **无数据 Schema** | 同步代理无据可依 | Wave 0 冻结 JSON Schema |
| D9 | **无可观测性** | 后台管线验收靠肉眼 | Wave 0 加结构化日志规范 + 健康端点 |
| D10 | **同步冲突过简** | 双端同时下载会脏数据 | Wave 0 定义幂等键 + 仲裁规则 |

---

## 2. 并行执行核心原则

1. **契约先行（Contract-First）**：所有接口（OpenAPI、JSON Schema、CSS 令牌、TS prop 接口、WS 消息）在 Wave 0 冻结。实现代理只编码"对照契约"，不读彼此实现。
2. **Mock 解耦前后端**：Wave 0 产出 mock 后端（按 OpenAPI 返回 canned 响应）。前端代理全程用 mock，直到 Wave 3 才切真后端——后端慢不阻塞前端。
3. **写作用域不相交**：每个代理持有一组互斥文件/目录。契约文件对实现代理只读。
4. **依赖 DAG 驱动**：代理仅在"上游契约已冻结"时启动。DAG 公开发布，代理据此认领已解锁任务。
5. **单所有者**：每个 Vue 组件 / Kotlin 服务有且仅有一个所有者代理。跨组件需求走契约，不碰他人文件。
6. **worktree 隔离**：动代码的代理跑在独立 git worktree，互不干扰；lead 负责合并完成的 worktree。
7. **失败隔离**：某代理失败不阻塞兄弟代理（它们对照契约/mock，不依赖该实现）。lead 重派或自修。
8. **验收门机器化**：合并前必须过 lint / type-check / build / 单测 / 契约测试。视觉回归用截图对比。
9. **背景执行长任务**：图标转换（100+ 文件）、视觉回归套件等长任务后台跑，lead 继续别的工作，结果就绪再收。
10. **lead 不写实现**：lead 只做契约、编排、合并、验收。实现全派子代理，保留主上下文做协调。
11. **设计语言复刻 ≠ 固定像素硬编码**：Android 设计令牌（颜色/图标/比例/组件形态/动效曲线）逐值复刻；但 WebUI 布局与图片渲染必须按实际视口宽度、像素密度、屏幕比例自适应。详见 §2.1。

### 2.1 响应式自适应策略

> **动机**：WebUI 不只跑在 iPad，也跑在手机/桌面/任意浏览器窗口。Android 端的 dp 单位本身是密度无关的，且用 `AutoStaggeredGridLayoutManager` 按列宽自动分栏、用横竖屏检测触发双页——这些"按视口自适应"的行为本身就是 Android 设计语言的一部分，WebUI 必须等价实现，而非把 dp 硬映射成固定 px。

**固定复刻（精确值，不随视口变化）**：
- 颜色、分类色、grey 色阶
- 圆角、阴影、边距的 *比例*
- 图标路径与颜色
- 动效曲线与时长

**比例/流体复刻（保留设计语言比例感，跨视口缩放）**：
- 字号：用 `clamp()` 在最小/理想/最大之间平滑缩放，理想值 = Android sp 值
- 间距/缩略图尺寸：按视口宽度分段，保留比例关系
- 卡片网格：CSS `column-width` + 容器查询，列数随可用宽度自动增减——等价于 Android `AutoStaggeredGrid` 的按列宽分栏逻辑
- 缩略图容器：`aspect-ratio: 2/3` + `object-fit: cover`，宽高比钳制 0.333–1.333（对照 `FixedThumb`/`TileThumb`）

**布局断点（从 Android 自身阈值派生）**：
- 单列（窄视口/手机竖屏）→ 多列（平板/宽视口），断点对齐 Android `AutoStaggeredGrid` 的分栏触发宽度
- 阅读器双页模式：由视口 `aspect-ratio` / `orientation` 触发，对照 Android 横屏自动双页逻辑
- 抽屉：窄视口覆盖式（modal），宽视口常驻式

**图片渲染自适应**：
- 响应式图片：`<img srcset>` / `<picture>`，按视口宽度和 DPR 选择合适分辨率，服务器端 `GET /api/image/{id}/{page}?w={width}` 按需供图
- 大图直出由服务器解码，受限设备无需本地解码；前端按容器尺寸请求对应分辨率

**此策略影响的任务清单**：CA3（令牌含断点与流体策略）、F2（缩略图 aspect-ratio 容器）、S1（自动分栏瀑布流）、S3（双页按视口方向 + srcset）、B5/CA1（图片 API 增 `?w=` 参数）、H3（多分辨率适配）。

---

## 3. 多子代理开发规则

> 这些规则是硬约束，子代理在启动时即加载，违反即被 lead 打回。

### R1. 契约即法律

- Wave 0 产出的契约文件（`contracts/` 目录）是唯一事实源。
- 实现代理**禁止修改**契约文件。发现契约缺陷 → 向 lead 报告，lead 统一修订并广播。
- 后端 Controller 的请求/响应形状必须与 OpenAPI 逐字段一致；前端组件 props 必须与 TS 接口一致。

### R2. 写作用域不相交

- 每个代理拿到一个**白名单目录/文件 glob**（如 `web-frontend/src/components/rating/`）。
- 代理只能在其白名单内 `write_file`/`edit`。越界写 → 冲突风险。
- 共享文件（如 `web-frontend/src/tokens.css`）由唯一所有者维护，他人只读。
- 跨目录的共享类型（`types/`）由 Wave 0 的契约代理一次性产出，之后只读。

### R3. 对照契约编码，不对照实现

- 前端代理调 mock 后端，绝不直连真后端（Wave 3 前）。
- 后端代理之间也走接口：如 `ImageStreamingService` 依赖 `ImageCache` 接口，不依赖其具体类。
- 代理产出应自带**最小 mock**用于自测（如组件 storybook、服务的 in-memory 实现）。

### R4. 每个代理自带验收门

- 交付前必须本地过：`lint` + `type-check` + `build` + 与该任务相关的单测。
- 代理在完成报告中附：变更摘要、验证命令及输出、风险点。
- lead 复核后才合并；复核关注契约符合度与写作用域合规。

### R5. 长任务后台化

- 图标转换、视觉回归、大批量文件重写等预计 >2min 的任务，用 `run_in_background` 或子代理后台跑。
- lead 不空等：后台任务跑时继续推进非冲突工作，结果就绪通过完成通知收取。

### R6. 合并协议

- worktree 代理完成后，lead 执行合并。冲突由 lead 裁决，**不让两个代理互相改对方文件**。
- 合并顺序按 DAG 拓扑序，避免后合并者大面积 rebase。
- 每次合并后跑一次集成 smoke（至少 build + 契约测试）。

### R7. 认领与解锁

- 代理完成后查任务列表，按 ID 升序认领下一个**已解锁**（上游契约就绪）的任务。
- 若所有可用任务被阻塞，向 lead 报告，lead 决定是否解锁新波次或重派。

### R8. 上下文最小化

- 子代理启动不带完整主对话历史（用 fork 时设 `fork_turns` 限定窗口）。
- 给子代理的 prompt 必须自包含：任务、输入契约路径、输出物、验收门、写作用域。
- 不让代理"基于发现自行决定架构"——架构由 lead 在契约层定死。

---

## 4. 依赖关系 DAG

节点 = 任务（带 ID），边 = "必须先完成"。
虚线 = 仅需契约就绪（不需实现完成），用 `(contract)` 标注。

```
Wave 0a (契约并行):
  CA1 OpenAPI 规格        ──┐
  CA2 Sync JSON Schema    ──┤
  CA3 CSS 设计令牌        ──┤──→ Wave 0b
  CA4 TS 组件 prop 接口   ──┤
  CA5 WS 消息协议         ──┤
  CA6 结构化日志规范      ──┘
  (CA7 图标转换脚本) ← 独立，可与 0a 并行

Wave 0b:
  CB1 Mock 后端生成器     ← (CA1, CA2)
  CB2 dev-run.sh 基础设施  ← 独立

Wave 1 (最大并行):
  后端线 (对照契约):
    B1 DownloadService→core  ← (CA1)
    B2 Caffeine+磁盘缓存      ← (CA1)
    B3 Cookie/会话统一        ← (CA1)
    B4 core 接口补全          ← B1,B3 (需先看清缺什么)
    B5 图片流式 API           ← (CA1), B1(contract), B2(contract)
    B6 预读缓存               ← B5
    B7 处理管线接口+Noop      ← (CA1, CA5)
    B8 缓存管理 API           ← B2
    B9 Sync 服务端 API        ← (CA1, CA2)
    B10 健康端点+指标         ← (CA6)
  前端线 (对照契约+mock):
    F1 图标转换执行           ← CA7(脚本) 或自带脚本
    F2 基础组件批1            ← (CA3, CA4): RatingStars/CategoryChip/CategoryTriangle/AppCard
    F3 基础组件批2            ← (CA3, CA4): FabLayout/ProgressSpinner
    F4 复杂容器组件           ← (CA4): ContentLayout/FastScroller
    F5 NavigationDrawer       ← (CA3, CA4)
    F6 SearchBar+SearchLayout ← (CA4)
    F7 SeekBarPanel           ← (CA4)
    F8 主题切换基础设施       ← (CA3)
    F9 PWA manifest+SW骨架    ← 独立

  MVP 切片 (Wave 1 末):
    M1 列表屏端到端(mock)     ← F2,F3,F4,F5,F9,B5(mock)

Wave 2 (屏幕复刻并行, 需组件+mock):
  S1 画廊列表        ← M1(已验证), 组件就绪
  S2 画廊详情        ← 组件, mock
  S3 阅读器          ← 组件, mock, F7
  S4 下载列表        ← 组件, mock
  S5 搜索            ← F6, mock
  S6 设置            ← 组件, mock
  S7 收藏/历史/订阅/排行 ← 复用 S1 组件

Wave 3 (集成与高级, 需真后端+屏幕):
  I1 前端切真后端       ← B1-B9, S1-S7
  I2 WS 实时反馈集成     ← B10, (CA5), S3,S4
  I3 AI增强换源机制(机制) ← B7, (CA5), S3  # 真实增强图产出=范围外
  I4 性能优化(虚拟滚动/预读) ← S1, I1
  I5 Android 同步客户端  ← B9, (CA2)  [改 Android 端]

Wave 4 (硬化与验收):
  H1 视觉回归套件       ← S1-S7, Android 截图基线
  H2 PWA 离线支持        ← F9, I1
  H3 多设备/多分辨率适配 ← S3, F9, §2.1 响应式策略
  H4 部署硬化(systemd/HTTPS) ← CB2, I1
  # waifu2x 实装 = 本路线图范围外（接口已在 B7 冻结，后续 session 接入）
```

**关键路径**：CA1/CA2/CA3 → CB1 → F2-F5 → M1 → S1 → I1 → I4。
**最早可启动**：CA7(图标脚本)、CB2(dev-run)、F9(PWA骨架) 几乎与契约并行。

---

## 5. 波次编排

### Wave 0 — 契约冻结（前置，约 1 轮）

目标：冻结所有接口，产出 mock 后端与开发基础设施。**此波完成后，Wave 1 可铺开 18+ 路并行。**

| ID | 任务 | 输入 | 输出物 | 写作用域 |
|----|------|------|--------|---------|
| CA1 | OpenAPI 规格全 REST 端点 | roadmap Phase 0/1/2 API 列表 | `contracts/openapi.yaml` | `contracts/openapi.yaml` |
| CA2 | Sync 实体 JSON Schema | roadmap §2.1 实体 + 冲突规则 | `contracts/sync-schemas.json` + `contracts/sync-conflict-rules.md` | `contracts/sync*` |
| CA3 | CSS 设计令牌 + 响应式策略 | Android `colors/dimens/attrs/themes/styles` | `web-frontend/src/styles/tokens.css` + 三主题 + `contracts/responsive-strategy.md`（断点/流体值/自适应规则，见 §2.1） | `web-frontend/src/styles/tokens*`, `contracts/responsive*` |
| CA4 | TS 组件 prop 接口 | roadmap 控件映射表 | `web-frontend/src/types/components.ts` | `web-frontend/src/types/` |
| CA5 | WS 消息协议 | roadmap §3.4 实时反馈 | `contracts/websocket-protocol.md` | `contracts/websocket*` |
| CA6 | 日志/指标规范 | roadmap 验收需求 | `contracts/observability.md` | `contracts/observability*` |
| CA7 | 图标转换脚本 | Android `drawable/v_*.xml` | `web-frontend/scripts/convert-icons.*` + `assets/icons/*.svg` | `web-frontend/scripts/`,`assets/icons/` |
| CB1 | Mock 后端生成器 | CA1, CA2 | `ehviewer-web/src/test/mock/` 或独立 mock server | `mock-server/` |
| CB2 | dev-run.sh + 基础设施 | — | `scripts/dev-run.sh`, `.dev-data/` 布局 | `scripts/` |

**并行度**：CA1-CA7 七路并行；CB1 等 CA1+CA2；CB2 独立。共约 9 代理。

**Wave 0 验收门**：
- [ ] OpenAPI 可被 swagger-ui 渲染无错；图片端点含 `?w=` 参数（响应式供图）
- [ ] mock 后端启动后所有 OpenAPI 端点返回 canned 数据
- [ ] `tokens.css` 三主题切换生效，颜色值与 Android `colors.xml` 逐项对齐
- [ ] `responsive-strategy.md` 定义断点/流体值/双页触发规则，与 Android 分栏/横屏逻辑对齐
- [ ] 图标脚本跑完产出 ≥50 个 SVG，路径与源 XML 一致
- [ ] `dev-run.sh` 能拉起空壳后端 + 前端 dev server

### Wave 1 — 最大并行（约 18+ 路）

后端线与前端线**完全并行**（前端靠 mock，不等后端）。

**后端代理（10 路）**：B1-B10，见 DAG。每个代理写自己的 Controller/Service/Repository，对照 OpenAPI 与 core 接口。
- B1/B2/B3 可同时启动（均仅依赖 CA1）。
- B4 等 B1+B3（需先看缺什么接口）。
- B5 编码对照 `ImageCache` 接口与 core 解析器接口，不依赖 B2 的具体实现就绪。
- B7 只建 `ImageProcessor` 接口 + `NoopProcessor`；waifu2x 实装为本路线图范围外（后续 session），接口在此冻结。

**前端代理（9 路）**：F1-F9，见 DAG。组件代理对照 TS 接口 + tokens.css，用 mock 数据自测。
- F2/F3/F4/F5/F6/F7/F8/F9 互不依赖（仅依赖契约），可同时启动。
- F1（图标执行）依赖 CA7 脚本，或自带脚本。

**MVP 切片（M1）**：Wave 1 末，挑"画廊列表"做端到端联调（组件 + mock + 渲染）。验证架构可行，早暴露风险。由 lead 直接做或单代理。

**Wave 1 验收门**：
- [ ] 每个后端服务单测过 + 契约测试（响应形状符合 OpenAPI）
- [ ] 每个前端组件有 storybook/单测，props 符合 TS 接口
- [ ] M1 列表屏在 mock 下渲染正确，列表/网格双模式可切

### Wave 2 — 屏幕复刻并行（7 路）

前置：组件库就绪 + mock 后端就绪。7 个屏幕代理同时开工，各自对照 Android 布局文件复刻。

| ID | 屏幕 | Android 布局源 | 复用组件 |
|----|------|---------------|---------|
| S1 | 画廊列表 | `scene_gallery_list.xml` | AppCard/FabLayout/ContentLayout/SearchBar |
| S2 | 画廊详情 | `gallery_detail_*.xml` | AppCard/RatingStars/CategoryChip |
| S3 | 阅读器 | `activity_gallery.xml` | SeekBarPanel/ProgressSpinner |
| S4 | 下载列表 | `scene_download.xml` | AppCard/FabLayout/ProgressSpinner |
| S5 | 搜索 | `widget_search_bar.xml` | SearchBar/CategoryTable |
| S6 | 设置 | `settings_headers.xml` | NavigationDrawer 子页 |
| S7 | 收藏/历史/订阅/排行 | 复用 `scene_gallery_list.xml` | S1 组件 |

**并行度**：7 路。写作用域各为 `web-frontend/src/views/{screen}/`。

**Wave 2 验收门**：
- [ ] 7 屏在 mock 下渲染，布局对照 Android 截图无重大偏差（精细对齐留 Wave 4）
- [ ] 三主题切换各屏无破损
- [ ] 瀑布流列数/阅读器双页随视口宽度/方向自动切换（对照 §2.1，非硬编码设备）

### Wave 3 — 集成与高级特性（5 路）

前置：真后端就绪 + 屏幕就绪。

| ID | 任务 | 说明 |
|----|------|------|
| I1 | 前端切真后端 | 把 mock 换成真 API，修联调问题 |
| I2 | WS 实时反馈 | 下载/处理进度实时推送到 S3/S4 |
| I3 | AI 增强换源机制 | 实现换源机制（WS 监听 + 图片热替换），用 mock 增强图验证；真实增强图产出依赖 waifu2x 实装（路线图范围外） |
| I4 | 性能优化 | 虚拟滚动、IntersectionObserver 懒加载、preload 预热 |
| I5 | Android 同步客户端 | 改 Android 端：连接管理 + 远程阅读 + 委托下载 |

**Wave 3 验收门**：
- [ ] 真后端下 7 屏全可用
- [ ] WS 进度条实时更新
- [ ] AI 增强换源机制可用（mock 增强图验证；真实增强图依赖 waifu2x，范围外）
- [ ] Android 连服务器后收藏/历史同步可见

### Wave 4 — 硬化与验收（4 路）

| ID | 任务 | 说明 |
|----|------|------|
| H1 | 视觉回归套件 | Playwright 截图对比 Android 基线，量化"逐像素一致" |
| H2 | PWA 离线支持 | Service Worker 缓存策略 + 已缓存画廊离线阅读 |
| H3 | 多设备/多分辨率适配 | 不限 iPad：安全区、橡皮筋/双击缩放冲突、横竖屏、断点验证（对照 §2.1） |
| H4 | 部署硬化 | systemd 生产配置、Nginx/Caddy HTTPS 反代 |

> waifu2x 实装（`Waifu2xProcessor` + GPU 队列）为本路线图**范围外**；接口已在 B7 冻结，后续 session 接入即可。

**Wave 4 验收门** = 路线图原验收标准（全部机器可判定）。

---

## 6. 子代理任务规格（关键任务详表）

> 以下为高并行度任务的精确规格，供 lead 直接作为子代理 prompt 模板。
> 每个规格遵循：**目标 / 输入契约 / 输出 / 写作用域 / 验收门**。

### F2 — 基础组件批1（RatingStars / CategoryChip / CategoryTriangle / AppCard）

- **目标**：实现 4 个原子组件，对照 Android 控件与 TS 接口。
- **输入契约**：`types/components.ts`（prop 接口）、`tokens.css`（颜色/尺寸）、roadmap §UI 复刻规范。
- **输出**：`web-frontend/src/components/atoms/{RatingStars,CategoryChip,CategoryTriangle,AppCard}.vue` + 各自 `.spec.ts`。
- **写作用域**：`web-frontend/src/components/atoms/` 内上述 4 文件。
- **验收门**：`vue-tsc` 过；props 符合接口；三主题下渲染正确（附 storybook 用例）。
- **关键细节**：RatingStars 5 星 16px 1px 间隔 0-10 映射半星；AppCard 2dp 圆角/阴影/边距背景随主题。

### F5 — NavigationDrawer

- **目标**：左侧抽屉 280px，头部 160dp + 8 项菜单 + 底部配额/主题切换。
- **输入契约**：`types/components.ts`、`tokens.css`、roadmap 导航结构章节。
- **输出**：`web-frontend/src/components/layout/NavigationDrawer.vue` + spec。
- **写作用域**：`web-frontend/src/components/layout/NavigationDrawer.*`。
- **验收门**：8 菜单项单选组；头部高度/头像/用户名字号正确；主题切换按钮联动 tokens。

### S1 — 画廊列表

- **目标**：复刻 `scene_gallery_list.xml`：浮动搜索栏 + 瀑布流自动分栏 + 列表/网格双模式 + FabLayout 集群 + 下拉刷新 + 无限滚动分页。
- **输入契约**：`types/components.ts`、`tokens.css`、`responsive-strategy.md`（§2.1）、mock `/api/gallery`。
- **输出**：`web-frontend/src/views/gallery-list/*`。
- **写作用域**：`web-frontend/src/views/gallery-list/`。
- **验收门**：mock 下列表/网格双模式可切；瀑布流列数随视口宽度自动增减（对照 Android `AutoStaggeredGrid`）；三主题无破损。
- **关键细节（响应式）**：用 CSS `column-width` 实现自动分栏，断点对齐 Android 分栏触发宽度；窄视口单列，宽视口多列；缩略图 `aspect-ratio: 2/3` + `object-fit: cover`，宽高比钳制 0.333–1.333。

### S3 — 阅读器

- **目标**：复刻 `activity_gallery.xml`：顶部状态栏（时钟/描边页码/电量）+ 底部 SeekBar 面板 + 自动播放 + 双击缩放 + 阅读方向(LTR/RTL/垂直) + 双页。
- **输入契约**：mock 后端 `/api/image/{id}/{page}?w=`、`SeekBarPanel` 组件、WS 消息协议（进度推送）、`responsive-strategy.md`（§2.1）。
- **输出**：`web-frontend/src/views/reader/*`。
- **写作用域**：`web-frontend/src/views/reader/`。
- **验收门**：mock 下逐页加载；预读命中时翻页零等待（mock 模拟）；Seekbar 拖拽同步页码。
- **关键细节（响应式）**：双页模式由视口 `aspect-ratio`/`orientation` 触发（对照 Android 横屏自动双页），非硬编码设备类型；图片用 `srcset` 按容器宽 + DPR 选分辨率，请求 `/api/image/{id}/{page}?w={width}`；窄视口自动单页。

### B5 — 图片流式 API

- **目标**：`GET /api/image/{galleryId}/{page}` 磁盘缓存→未命中→core 解析→下载→缓存→返回，支持 Range + `?w={width}` 按需分辨率供图（响应式图片，见 §2.1）。
- **输入契约**：`openapi.yaml` 端点定义（含 `w` 参数）、core `GalleryPageParser` 接口、`ImageCache` 接口。
- **输出**：`ehviewer-web/.../controller/ImageController.kt` + `service/ImageStreamingService.kt`。
- **写作用域**：上述两文件 + 对应 test。
- **验收门**：契约测试（响应符合 OpenAPI）；并发控制生效；Range 请求正确；`?w=` 返回对应缩放尺寸。

### I3 — AI 增强换源机制

- **目标**：实现换源机制——增强版就绪时 WS 通知前端，静默热替换图片 src 不打断阅读。本任务只建机制；真实增强图产出依赖 waifu2x 实装（路线图范围外），用 mock 增强图验证。
- **输入契约**：`websocket-protocol.md`（`image.enhanced.ready` 消息）、`ImageProcessor` 接口（B7 冻结）。
- **输出**：阅读器内 WS 监听 + 图片 src 热替换逻辑。
- **写作用域**：`web-frontend/src/views/reader/composables/useEnhancedImage.ts`（新增）。
- **验收门**：mock WS 推送后图片无闪烁替换；阅读不中断。

---

## 7. 验证与集成策略

### 7.1 契约测试（每代理必过）

- 后端：用 OpenAPI 生成的请求/响应 fixture，断言 Controller 输出逐字段匹配。
- 前端：组件 props 类型 + 渲染快照；屏幕用 mock 数据渲染无错。

### 7.2 视觉回归（Wave 4，量化"逐像素一致"）

- 工具：Playwright + 截图对比（pixelmatch）。
- 基线：Android 端各屏幕截图（Light/Dark/Black 三套）。
- 阈值：允许 ≤1% 像素差异（字体渲染差异容忍）。
- CI：每次合并到主线跑全套对比。

### 7.3 集成 smoke（每合并点）

- build（前后端）+ 契约测试 + 已完成屏幕的 mock 渲染。
- 失败即阻断后续合并。

### 7.4 性能门（Wave 3/4）

- 首屏 LCP < 1s（局域网，mock 数据）。
- 翻页预读命中率指标（需 B10 可观测性端点输出）。

---

## 8. 与原路线图的差异对照

| 维度 | 路线图 v3 | 本文档（优化） |
|------|----------|--------------|
| 并行度 | 3 条 bullet | DAG + 5 波次，峰值 18+ 路 |
| 契约 | 隐含在 API 描述里 | Wave 0 显式冻结，实现只读 |
| 前后端关系 | 串行（前端等后端） | mock 解耦，全程并行 |
| Phase 3 | 单体 3.1-3.5 | 拆 9 组件代理 + 7 屏幕代理 |
| MVP | 无 | Wave 1 末列表屏端到端切片 |
| 测试 | 手动验收框 | 契约测试 + 视觉回归量化 |
| dev-run | Phase 4 | Wave 0 CB2 |
| waifu2x | "后续" | 仅冻结接口（B7），实装明确为范围外（后续 session） |
| 响应式自适应 | 仅提 iPad | §2.1 策略：设计语言复刻 + 布局/图片按视口自适应 |
| 子代理规则 | 无 | §3 八条硬约束 |
| 写作用域 | 无 | 每任务白名单，并行安全命脉 |

---

## 9. 风险与缓解（针对并行执行新增）

| 风险 | 缓解 |
|------|------|
| 契约设计错误导致大面积返工 | Wave 0 验收门严格；契约修改经 lead 统一广播 |
| worktree 合并冲突 | 写作用域严格不相交；合并按拓扑序；冲突 lead 裁决 |
| 子代理上下文膨胀 | `fork_turns` 限定；prompt 自包含；不传主对话全史 |
| mock 与真后端行为漂移 | 契约测试双写（后端 + mock 同一 fixture）；I1 切换时跑全量契约对比 |
| 视觉回归基线获取难 | Android 端截图脚本（可作为 H1 子任务，用 ADB 或 instrumentation 截图） |
| 图标转换漏文件 | CA7 脚本输出 manifest（源→目标映射），H1 校验完整性 |
| 响应式与复刻冲突（流体值破坏"逐像素"） | 固定值精确复刻，流体值保留比例感；视觉回归按断点分组对比而非单视口 |

---

## 10. 给 lead 的执行清单

1. **起 Wave 0**：并行派 CA1-CA7（7 路 foreground/背景混合），收齐后派 CB1。
2. **发 DAG**：把 §4 的 DAG 作为任务列表发布，代理据此认领。
3. **起 Wave 1**：CA7/CB2 可与契约并行先发；契约就绪后铺开 B1-B10 + F1-F9。
4. **盯 MVP**：Wave 1 末亲自做 M1，验证架构；失败则回 Wave 0 修契约。
5. **起 Wave 2**：组件 + mock 就绪后，7 屏幕代理同时开工。
6. **起 Wave 3**：真后端 + 屏幕就绪后，集成 + 高级特性 5 路。
7. **起 Wave 4**：硬化 + 验收，视觉回归量化"逐像素一致"。
8. **全程**：每个合并点跑集成 smoke；每个代理完成报告复核契约符合度。
