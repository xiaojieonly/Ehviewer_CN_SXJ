# 方案：WebUI 输入交互（键鼠/触屏）深度修复

日期：2026-09-05
来源：用户实测反馈「很多 UI/UX 逻辑既没做好键鼠操作又没做好触屏操作」；三路并行代码审计（阅读器 / 卡片与全局布局 / 搜索表单详情）+ 09-04 夜间真实交互走查佐证。证据均为 file:line 实读，非推测。
基线：web-frontend 测试 1015 绿、web 模块 908 绿；2026-09-04 性能 P1 与 toplist 打码修复（cad587ea）已上线。

## 0. 根因（为什么两头都不讨好）

这套 WebUI 是 Android App 交互范式的移植，移植时做了「PC 化」的一半（hover 操作栏、右键菜单），另一半 Android 语义原样保留（3 秒自动收起的工具栏、长按删除、空格语义漂移、speed-dial FAB），结果三种输入各自都有核心链路是断的：

1. **触屏被 `pcInput = pointer:fine && ≥720px` 一刀切**：卡片操作栏 `v-if="pcInput"` 不渲染（GalleryCard.vue:65）、右键菜单 `!pcInput` 直接 return（GalleryCard.vue:543-548），又没有长按/滑动的触屏替代（useSwipeGesture 只在阅读器用）——触屏用户不进详情页就无法收藏/下载/复制链接。
2. **hover 是唯一 affordance 且无 `(hover:none)` 豁免**：QuickActions 纯 CSS `:hover` 唤醒（`opacity:0; pointer-events:none` → `:hover`/`:focus-within`，CardQuickActions.vue:94-102 + GalleryCard.vue:594-598），全 src 无一处 `@media (hover:none)/(pointer:coarse)`（grep 0 命中）——触屏粘滞 hover、不 hover 的点击穿透到封面 img 变成「打开详情」。
3. **键盘是没人管的第三通道**：阅读器全局 keydown 无修饰键守卫（Ctrl+A/D/±被吞成翻页/缩放，useKeyboardNav.ts:18-60）、seekbar 聚焦后方向键被劫持、空格破坏按钮激活、A/D 只匹配小写；网格无方向键导航；全站快捷键仅 SearchView 的 `/` 和 `f`。
4. **IME 组合输入零保护（中文用户搜索第一步就坏）**：SearchBar Enter/Esc 无 isComposing 守卫（SearchBar.vue:69-70，grep composition 0 命中），拼音选词回车=用拼音串搜索且输入框被收起；三个列表搜索框 400ms 防抖在组合期间照发。
5. **「看起来成功、实际没发生」的零反馈失败**：feed 页搜索词静默丢弃（HomeView.vue:299-309）；详情页 tag/uploader 点击跳 `/?keyword=` 而全站无消费者（GalleryDetailView.vue:501-511，grep query.keyword 0 命中）；HomeView 少绑 FilterPanel 两个 v-model（Keyword mode 单选点了不高亮、Save-as-quick-search 无效，HomeView.vue:39-44 vs SearchView.vue:100-106）；详情页 Favorite 只加不减（前端 toggle 对称，但 `favoriteSlot` 数据源恒 -2——`addFavorite` 从不回写 history 行，FavoriteService.kt:98-121 + GalleryService.kt:554，后端问题）。
6. **阅读器桌面鼠标路径整体缺席**：全 src 无 wheel 监听（页模式滚轮彻底死亡）；热区无视觉提示无 cursor；单击被双击判定人为延迟 280ms；chrome 3 秒强收且无鼠标唤出途径；**`pageScaling ≠ fit` 时 `pannable` 恒真，鼠标点击、滑动、chrome 唤出全部失灵**（PageMode.vue:325, 517-519）——单一条件同时击穿键鼠和触屏，是最高危单项。
7. **Web 基本预期缺失**：卡片和导航项均非 `<a href>`（AppCard.vue:2-10、NavigationDrawer.vue:45-53）——中键/Ctrl+点击新标签全不可用；卡片 `user-select:none`。

## 1. 交互基线（修复的目标契约）

- **鼠标**：hover 是增强不是唯一入口；滚轮、右键、中键/Ctrl+新标签、文本选择等浏览器惯例保留；一切点击有即时反馈，无双击判定延迟。
- **触屏**：任何操作都有无 hover 的等价路径（常显操作/长按菜单/详情页承载）；目标 ≥44px；手势与 App 对齐（滑动翻页、长按菜单）。
- **键盘**：不劫持浏览器与已聚焦控件的默认行为（修饰键守卫、input/range 焦点豁免、isComposing 豁免）；Tab 序合理 + `:focus-visible` 全覆盖；核心流有快捷键，不强求全键盘。
- **反直觉清零**：不存在「UI 动了但操作没发生」的路径；所有失败可见。

## 2. 改动总览（4 个并行实现单元，文件域互不重叠）

| # | 任务 | 文件域 | 优先级 |
|---|---|---|---|
| A | 阅读器输入修复（8 项） | `components/reader/*`、`composables/useKeyboardNav.ts`、`useSwipeGesture.ts`、`views/ReaderView.vue` | P0×2 + P1×6 |
| B | 卡片/列表/全局布局 | `components/gallery/*`、`components/layout/*`、`atoms/AppCard.vue`、`App.vue`、`assets/styles/global.css` | P0×1 + P1×4 |
| C | 搜索/表单/详情零反馈失败 | `search/SearchBar.vue`、`search/FilterPanel.vue`、`views/HomeView.vue`、`views/GalleryDetailView.vue`、三个列表视图搜索框、`form/*` | P0×3 + P1×3 |
| D | 后端 favoriteSlot 回写 | `anotherviewer-web/.../FavoriteService.kt`（+ 测试） | P0 |

---

## 任务 A：阅读器（重灾区）

### A1 [P0] keydown 全局守卫
`useKeyboardNav.ts:16-66`：handler 开头加三道豁免——(1) `e.ctrlKey||e.metaKey||e.altKey` 直接 return（不 preventDefault）；(2) `e.isComposing` return；(3) `event.target` 为 `input/textarea/select/[contenteditable]` 时 return（保住 seekbar range 的原生方向键）。`'a'/'d'` 改 `e.key.toLowerCase()`。Space 分支仅 `keyboardPaging=true` 时翻页（保留现语义），且 target 为 button 时 return（不破坏空格激活）。

### A2 [P0] `pannable` 不再吞掉热区/滑动
`PageMode.vue:325, 517-519`：`suppressed` 的条件从 `zoom>1.001 || scaling!=='fit'` 改为仅 `zoom>1.001`（真正放大拖拽时才抑制热区）。`scaling≠fit` 只是布局基准，不构成 pan 手势理由。

### A3 [P1] 滚轮翻页
PageMode/DualPageMode 容器加 `wheel` 监听（passive:false + 防抖 ~150ms，一次滚动=一页）；ScrollMode 保持原生滚动。 deltaY 方向随阅读方向镜像（RTL 时 deltaY<0 = 下一页？不——滚轮保持物理直觉：向下滚=下一页，不镜像）。

### A4 [P1] 热区可视提示
PageMode/DualPageMode 左右边缘加 hover 提示层（渐变 + 单侧箭头图标，`opacity` 0→1 on `:hover`，触屏不显示），页面边缘 cursor 用 `w-resize/e-resize` 语义化；点击后即刻反馈（不新增 ripple，避免过度设计）。

### A5 [P1] 单击 280ms 延迟消除（含双页双翻 bug）
桌面（pointer:fine）：改用原生 `dblclick` 做缩放循环，`click` 立即翻页，删除 setTimeout 判定窗；触屏保留现双击判定窗（从 280ms 缩到 240ms）。同时修 `tapTimer` 未清理的双翻 bug（PageMode.vue:199-232：DualPageMode 复用时两次快速点击残留两个定时器——统一封装到 composable，unmount/click 时 clearTimeout）。

### A6 [P1] chrome 鼠标唤出 + 不抢焦点
阅读器舞台加 `mousemove`（节流）→ 重置 idle 计时并显示 chrome；鼠标悬停在 chrome 区域内时暂停 3 秒自收（ReaderStatusBar.vue:117-129 的 idle 回调改为通知而非直接隐藏，由 ImageReader.vue:217-222 统一决策）。chrome 显示期间不重置 Tab 序：去掉隐藏时对焦点元素的无条件 `tabindex=-1` 抢夺（ImageReader.vue:373-379）——仅 `visibility:hidden` 即可（元素不可聚焦本就离开 Tab 序），保持焦点原地。

### A7 [P1] 缩放语义统一
步长统一 0.25、范围 [0.5,3]、双击循环 1→1.5→2→1、捏合钳 [1,3]（对齐 ReaderSettings.vue:213-214 与 preferences 默认值）；scroll/dual 模式下 +/- 直接不注册（不再无声改值）。

### A8 [P2] seekbar 拖动即时预览
ImageReader.vue:69-76 补监听 `update:currentPage`（input 事件）→ scroll 模式 `scrollToPage`（去掉平滑动画改 instant）；page 模式维持 change 提交。RTL 镜像保留。

验收：`pageScaling=width` 下纯触屏可翻页/唤出工具栏；纯鼠标可用滚轮翻页、悬停见热区提示、点击即翻无迟滞；Ctrl+A/D/±、seekbar 聚焦方向键、按钮空格激活全部保持原生行为；中文 IME 不受影响（阅读器无文本框，回归即可）。

---

## 任务 B：卡片 / 列表 / 全局布局

### B1 [P0] 触屏操作入口
采用「长按=右键菜单」方案（对齐 Android 范式 + CategoryTable.vue:58 先例）：GalleryCard 加 `contextmenu` 的 touch 等价——`touchstart` 起 500ms 计时、移动 >10px 取消、触发时 `preventDefault` 并打开 CardContextMenu（复用 PC 菜单，含 Details/Favorite/Download/Copy link）；同时 `onContextMenu` 的 `!pcInput return` 改为：长按已处理的不重复弹。Chrome Android 长按自带 contextmenu 事件（iOS 无）——iOS 走 touchstart 计时器兜底。**不做**：操作栏在触屏常显（占空间，详情页可承载全部操作）。

### B2 [P1] 链接语义
AppCard 根元素改 `<a :href="detailUrl">`（保留 role/tabinit/键盘处理；中键/Ctrl+点击/新标签右键项全部免费获得）；内部 QuickActions 按钮 `@click.stop.prevent` 不受影响。NavigationDrawer 菜单项同样改 `<a>`（router-link 生成 href + @click.prevent 走既有 router.push 逻辑，保持 active 态逻辑）。

### B3 [P1] 无限滚动可达性
ContentLayout footer 加「加载更多」按钮（hasMore 时显示，滚动自动加载照旧——按钮是键盘/读屏/自动加载失败时的兜底）；配套给视图补 `hasMore` 传递（HomeView 等已有 total 字段可算）。

### B4 [P1] 全局触屏 CSS 豁免
global.css 加 `@media (hover: none)`：抹平粘滞 hover（对 `.app-card:hover` 等规则做 pointer 门控不现实，改为给 QuickActions 类组件补 hover:none 下不依赖 hover 的可见性——与 B1 长按方案配合，仅处理「粘滞 hover 导致样式卡住」的通用规则，逐条审 UI 后最小化处理）。

### B5 [P2] 触控目标与细节
视图切换按钮 34×28→≥44×44（GalleryList.vue:189-191）；列表搜索清除按钮 24→32px+padding；卡片 `user-select:none` 改为仅对操作区生效（标题可选中复制）。

验收：触屏（DevTools touch 模拟 + 真机）长按卡片弹菜单并可完成收藏/下载；中键点卡片新标签打开详情；「加载更多」按钮 Tab 可达；触屏无粘滞 hover 残留。

---

## 任务 C：搜索 / 表单 / 详情「零反馈失败」

### C1 [P0] IME 保护
SearchBar.vue:69-70：`applySearch`/`emit('back')` 前加 `e.isComposing || e.keyCode===229` 守卫；HistoryView/FavoriteView/DownloadView 的搜索防抖 watch 同加组合守卫（compositionstart 置位、compositionend 后再放行防抖）。

### C2 [P0] feed 页搜索不再静默丢词
HomeView.loadPage 的 feed 分支不变（frozen contract 保留），但 `applySearch` 入口改：`feedMode` 非空时 `router.replace({ path: '/', query: { keyword: q } })`（离开 feed 态进普通搜索）——用户意图（搜这个词）被满足且可见。同步实现 HomeView 消费 `route.query.keyword`（initial + watch），**顺手救活详情页 tag/uploader 死链**（它们已经在跳 `/?keyword=`）。

### C3 [P0] FilterPanel 接线补全 + 开关竞态
HomeView.vue:39-44 补 `v-model:keyword-mode` 与 `@save-quick-search`（照抄 SearchView.vue:100-106 的接线）。FilterPanel.vue:323-327 的 document mousedown 关闭判定加「target 在锚点/开关按钮内则忽略」（参照 AppSelect.vue:209-218 的 contains 保护），修复「只能开不能关」。

### C4 [P0] 详情页 Favorite 状态（前端侧配合）
前端不改逻辑（toggle 本来对称）。配合任务 D 的后端修复后，`detail.favoriteSlot` 才会有正确值。验收时验证「收藏→重进详情→按钮呈已收藏态→再点→取消」。

### C5 [P1] 详情页返回按钮兜底
GalleryDetailView.vue:418-420：`history.state?.back` 为空时 `router.push('/')`，深链不再无响应。

### C6 [P1] 筛选即时搜索防抖
HomeView.applyFilters 的立即全量搜索改 500ms 防抖（连续勾选 N 个分类只发一次请求）。

### C7 [P2] 表单控件细节
AppSegmented 加方向键 roving tabindex（radiogroup 惯例）；搜索输入框补 `:focus-visible` 边框（History/Favorite/Download 三处 outline:none 的）；dialog Esc 统一挂 window（打开时注册）而非依赖焦点在面板内。

验收：中文输入法下搜索/取消不误触发；feed 页搜索跳转普通搜索并带词；tag 点击真的执行搜索；FilterPanel 开关自如；收藏→重进→取消全链路走通（依赖任务 D）。

---

## 任务 D：后端 favoriteSlot 回写

`FavoriteService.kt:98-121` `addFavorite` 成功后回写对应来源行的 `favoriteSlot`：有 history 行则更新 `history_info.favoriteSlot`（HistoryService 提供更新方法），无则仅记日志（不新建行，避免为收藏凭空造历史）。`removeFavorite` 对称清除（置回 -2）。同时核对详情读取链 `GalleryService.kt:400-434/554`：favorite 分支命中时直接用本地收藏行的 slot。
测试：GalleryServiceTest/FavoriteServiceTest 补「add→详情 favoriteSlot≥0」「remove→回 -2」「无 history 行时 add 不新建行」三例。

---

## 3. 明确不做（决策记录）

- ❌ 全站 a11y 合规改造（焦点陷阱全覆盖、SR 全适配）：个人自用产品，做到「键盘不劫持、焦点可见」即可，不过度设计。
- ❌ 网格方向键 roving tabindex：Tab + 加载更多按钮已可用，收益/成本比低；将来有需要再做。
- ❌ 容器查询 / 统一 `usePcInput` 与 CSS 断点的架构重构：720px 双机制目前仅边界处有轻微不同步，记为已知债，不动。
- ❌ 触屏 QuickActions 常显：占空间且与长按菜单重复。
- ❌ 全局快捷键体系扩展（Ctrl+K 等）：SearchView 已有 `/`、`f`，够用。

## 4. 实施与验收

- 实施顺序：A/B/C/D 四单元文件域不重叠，可按既有惯例并行（每单元一个 Agent）；A、C 改动面较大，各自先跑 `:anotherviewer-web:test` / 前端 vitest 全绿再交付。
- 主会话汇合后统一 `./build.sh` → 部署 → 真机 + DevTools touch 模拟手测验收清单（每任务验收条目逐条过）。
- 回归重点：打码模式不受影响（B1 长按菜单、B2 链接语义都在打码覆盖范围内——菜单项标题仍走 maskedTitle）；阅读进度同步（A8 seekbar 改动触碰 ImageReader 的页码链路）；性能基线不回退（新增的 mousemove/wheel 监听都要节流）。
