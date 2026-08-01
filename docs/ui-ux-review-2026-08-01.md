# AnotherViewer · WebUI 前端 UI/UX 审查报告

> 用途：多模态 UI/UX 走查结论，**交由开发模型落地**。本报告只评估“用户看到什么”+ 跨模块一致性，不含后端架构。
> 日期：2026-08-01 ｜ 分支：`BiLi_PC_Gamer` ｜ 环境：Edge + DevTools 设备模拟，dev server `:3000`（`/api` 代理 `:8080`）

---

## 0. 一句话结论

骨架（720px 断点、三级导航、三套主题 token、48dp 触控/2dp 卡片等度量）做得扎实；但产品整体是 **“Material 的皮（调色板+度量），非 Material 的骨（组件）”**，且各模块**各自拼装表单**，导致**跨模块 UX 不统一**——这是本次最该治理的结构性问题（见 §2，根因 = 没有共享表单原语）。在此之上还有 3 个高严重度的“看起来坏了 / 设置不可读”问题（全局悬浮圆被裁切、管理页偏好行排版坏、启动页下拉空值）。**统一化基准：设置页的行 + 登录/访问页的输入框。**

严重度统计：🔴 高 4 ｜ 🟡 中 6 ｜ 🔵 低 4 ｜ 做得好 6。

---

## 1. 方法与分辨率 / 比例覆盖

走查方式：Edge DevTools 设备模拟逐页截图（**实测**）；中间宽度由断点逻辑推断（**推理**）；浅色 / 纯黑主题读 `tokens.css` 静态核算对比度（**静态**）。

| 比例 | 分辨率（设备） | 方向 | 覆盖方式 | 说明 |
|---|---|---|---|---|
| ~9:19.5 | 390×844（iPhone 12 Pro） | 竖 | 实测 | 手机主场景，模态抽屉+汉堡 |
| 16:10 | 1280×800（Nest Hub Max） | 横 | 实测 | 桌面/横屏，抽屉常驻+子导航轨 |
| **4:3** | 768×1024（iPad Mini） | 竖 | 实测 | 落在 720–959，子导航=顶部标签栏 |
| **4:3** | 1024×768 | 横 | 推理 | 结构同 1280，内容区≈494px，**建议复核** |
| **3:2** | 912×1368（Surface Pro 7） | 竖 | 实测 | 720–959；并实测 `/admin/devices` 作管理页对照 |
| **3:2** | 1200×800 | 横 | ≈实测 | 与 1280×800 同宽量级，结构已覆盖 |

> 实测页面：首页、设置/通用、管理/代理、管理/设备（+ 源码级比对全部设置/管理模块）。
> 4:3 / 3:2 的**横屏**未单独实拍（DevTools 旋转按钮无无障碍标签、像素定位不可靠）；其结构等同已实测桌面，仅内容区更窄，标注“推理/建议复核”。
> 关键断点：`720px`（抽屉模态↔常驻）、`959px`（子导航 轨↔顶部标签栏）。源码：`App.vue`、`NavigationDrawer.vue`、`SettingsLayout.vue:132`、`AdminLayout.vue:136`。

---

## 2. Material 一致性与跨模块统一性（专项审计）

> 结论：**展示层较 Material，表单/控件层不 Material 且模块间不统一。** 这正是“看上去配置不像 Material、体验不统一”的来源。

### 2.1 🔴 根因（UX-14）：没有共享表单原语组件库
- `src/components/atoms/` 仅有 `AppCard / AppIcon / CategoryChip / CategoryTriangle / FabLayout / ProgressSpinner / RatingStars`；**没有** `Switch / Select / TextField / SegmentedButton / PrefRow(ListItem)`。
- 全仓搜共享行/控件名（`PrefRow|AppSwitch|AppSelect|AppTextField|SegmentedButton|ListItem|FormRow`…）仅 1 处命中，且是阅读器内联注释（`components/reader/ReaderSettings.vue:336` “Segmented radio groups”）——即**手写**，非共享组件。
- 后果：每个设置/管理页在自己的 `<style scoped>` 里**重复实现**行、开关、下拉、分段、文本框；同名 BEM 也各自定义、各自漂移（例：`pref__select` 同时出现在 `GeneralSettings.vue` 与 `AdminProxy.vue`，但行布局不同）。**不一致是结构性的、必然的。**
- 建议（治本）：抽出一小组 MD 对齐原语 —— `PrefRow` / `SectionHeader` / `AppSwitch` / `AppSelect`(MD menu) / `AppTextField`(outlined + 浮动标签 + helper/error) / `AppSegmented`，各页迁移复用。这是消除跨模块漂移的唯一可靠手段。

### 2.2 控件不符合 Material 组件规范
- **下拉用原生 `<select>`（12 处）**：`GeneralSettings.vue`(73/93/137/157)、`ReaderSettings.vue`(56/94/114)、`AdminProxy.vue:51`、`AdminProcessing.vue`(59/84)、`AdminAdvanced.vue:34`、`SmbBackupView.vue:160`。原生 select 弹**操作系统原生菜单**，无法主题化、跨平台外观不一，不符合 MD 的 exposed dropdown menu / 带菜单文本框；UX-03“启动页空值”即原生 select 的 option 标签缺失症状。
- **文本框两种实现并存**：`LoginView.vue:317`、`SmbBackupView.vue:1092`、`AdminDownload.vue:817`、`AdminAccess.vue:416` 实现了 MD outlined 字段（分隔线边框→聚焦主题色 + **浮动标签**）；而 `AdminProxy.vue` 的 host/port/用户名/密码是**裸框、无浮动标签、无 helper text**。同一产品“有/无浮动标签”两种输入框并存。
- **分段控件 / 开关 / 行结构各页手写**：图标位置（内联 vs 单独成行）、标题/说明（两行堆叠 vs 挤成一行）、字段宽度（整宽卡片右对齐 vs 窄字段左对齐）均不统一（见 UX-02 / UX-08 / UX-11）。

### 2.3 符合 Material 的部分（保留，别误伤）
- 度量基本合规：`min-height:48px` 触控目标在 drawer 项、search bar、reader toolbar、各 admin/settings 行普遍出现；`--card-elevation:2dp`、`--toolbar-height:56px`、`--spinner-default:48px` 等 token 对齐 MD；注释多处引用 Android 对应值（`AdminDownload.vue:595` “Android preference item height”、`GalleryDetailView.vue:734` “ButtonInCard minHeight”、`NavigationDrawer.vue:348` 72px keyline）。
- 卡片/列表/进度/搜索栏/抽屉等**展示型**组件 MD 还原度较高（`AppCard`、`GalleryCard`、`DownloadItem`、`ProgressSpinner`、`SearchBar`、`NavigationDrawer`）。

### 2.4 跨模块一致性矩阵（实测 + 源码）

| 模块 | 行 / 表头结构 | 下拉 | 文本框 | 48dp 度量 | 与设置标杆一致 |
|---|---|---|---|---|---|
| 设置/通用 `GeneralSettings` | 内联图标行 + 两行标签 + 右对齐（**标杆**） | 原生 select | — | ✅ | — |
| 设置/阅读器 `ReaderSettings` | 同标杆 | 原生 select ×3 | — | ✅ | ✅ |
| 设置/隐私 `PrivacySettings` | 同标杆 | 无 | — | ✅ | ✅ |
| 设置/传输 `TransferSettings` | 页头 + “图标行”**变体** | — | — | ✅ | ⚠️ |
| 管理/代理 `AdminProxy` | 图标单独成行 + 标题/说明挤一行 + 窄字段左对齐 | 原生 select | 裸框无浮动标签 | ✅ | ❌ |
| 管理/设备 `AdminDevices` | 表头图标单独成行 | 无 | 无 | ✅ | ❌（表头） |
| 管理/下载 `AdminDownload` | 待核 | 待核 | MD outlined 浮动标签 ✅ | ✅ | ⚠️ |
| 管理/访问 `AdminAccess` | 待核 | — | MD outlined 浮动标签 ✅ | ✅ | ⚠️ |
| 管理/服务器·处理·高级·关于 | 待逐页核 | 多原生 select/手写 | 待核 | ✅ | ⚠️/❌ |
| 登录 `LoginView` | 独立布局（无 chrome） | — | MD outlined 浮动标签 ✅ | ✅ | （基准之一） |
| SMB `SmbBackupView` | 独立布局 | 原生 select | MD outlined 浮动标签 ✅ | ✅ | （基准之一） |

> 读法：**度量列几乎全绿**（Material 的皮在）；**行/表头、下拉、文本框三列红黄交错**（Material 的骨不在，且模块间不统一）。

### 2.5 统一化落地顺序（给开发模型）
1. **建原语**：`PrefRow` / `SectionHeader` / `AppSwitch` / `AppSelect`(MD menu) / `AppTextField`(outlined + 浮动标签 + helper + error + state layer) / `AppSegmented`，按 MD3 度量（48dp 目标、label 状态、ripple 可选）。
2. **冻结基准**：以 `GeneralSettings` 的行 + `LoginView`/`AdminAccess` 的输入框为视觉基准。
3. **迁移**：先 admin 全家（消除 proxy/devices 的堆叠图标与裸框；原生 select→`AppSelect`），再统一 settings 内 `TransferSettings` 的“图标行”变体，最后 smb/login 复用同一原语。
4. 原生 `<select>` **全量替换**为 `AppSelect`（顺带修 UX-03）。
5. 表单容器统一 max-width 度量（UX-08）。

---

## 3. 问题清单

> 严重度：🔴 高 ｜ 🟡 中 ｜ 🔵 低。每条含 现象 / 影响 / 建议 / 定位。

### 🔴 UX-14 结构性：无共享表单原语，控件非 MD 且跨模块漂移
- 见 §2。这是 UX-02 / UX-08 / UX-11 与“不像 Material”的**共同根因**；优先级最高（治本）。

### 🔴 UX-01 全局悬浮圆控件在视口左右边缘被裁切
- **现象**：每个路由、每个实测宽度（390/768/912/1280 全中），视口左缘与右缘各有一个圆形悬浮控件被切掉一半；移动端左侧几乎完全出屏。
- **影响**：像渲染故障；移动端那个基本不可点，破坏“可安装 PWA / 触屏可用”预期。
- **建议**：审计全局 `position: fixed` 元素，修正 inset / `transform-origin` / `z-index` 并补 safe-area。
- **定位**：已**排除** `components/atoms/FabLayout.vue`（`.fab-layout__cluster` 正确固定右下，次级按钮 `scale(0)` 隐藏）与 `App.vue` 的 `.app-hamburger`。候选清单：grep `position:\s*fixed`（各 `views/*.vue`、`views/admin/*.vue`、`views/settings/*.vue`、`NavigationDrawer.vue`）。

### 🔴 UX-02 管理页偏好行排版坏，且与设置页不一致
- **现象**：管理页（代理）每行图标**单独成行**，标题与说明挤成**同一行**；桌面与移动均如此。设置页则是图标内联、标题/说明两行堆叠、控件右对齐。实测 `AdminDevices` 的表头图标同样单独成行 → **管理系通病**。
- **建议**：admin 各页复用设置页同一 `PrefRow` / `SectionHeader`（见 §2.5）。
- **定位**：`views/admin/AdminProxy.vue`、`views/admin/AdminDevices.vue` vs `views/settings/GeneralSettings.vue`（标杆）。

### 🔴 UX-03 “启动页”下拉框不显示当前选中值
- **现象**：设置/通用“启动页”选择框为空（只有箭头），同页“列表模式=列表”“详情栏宽度=长”正常；所有实测宽度一致。
- **建议**：原生 select 的 value→label 映射缺失；随 UX-14 迁移到 `AppSelect` 时一并修复（确保选中值有可见文案）。
- **定位**：`views/settings/GeneralSettings.vue` 启动页 select。

### 🟡 UX-04 子导航折叠为水平标签栏时溢出、无滚动提示
- **现象**：≤959px 子导航变横向标签行。**实测**：390 设置页“传输”被无声裁切；**768 设置页 5 标签排下**；但 **912 管理页 8 标签仍溢出**（高级/关于被切）。即设置页溢出主要在窄手机，管理页因标签多，溢出持续到 ~959。始终**无横滑渐隐/箭头**，激活标签可能未滚入可视区。
- **建议**：标签容器 `overflow-x:auto` + 右侧渐隐；激活标签 `scrollIntoView({inline:'center'})`；或窄屏改图标+短标签。
- **定位**：`views/settings/SettingsLayout.vue`、`views/admin/AdminLayout.vue` 的 `@media (max-width: 959px)`。

### 🟡 UX-05 路由无 404 兜底，无效路径渲染空白外壳
- **现象**：不匹配路径只渲染空白内容区 + 汉堡 + 被裁切悬浮圆，无提示或回首页入口。
- **建议**：`router/index.ts` 末尾加 catch-all `/:pathMatch(.*)*` → 404 视图。
- **定位**：`src/router/index.ts`（末条为 `/smb-backup`，无兜底）。

### 🟡 UX-06 品牌 / 身份混淆：抽屉与搜索框都写 “E-Hentai”
- **现象**：抽屉头部回退为“E”+“E-Hentai”，搜索框占位符也是“E-hentai”；产品名实为 AnotherViewer。
- **建议**：抽屉头部用产品名（或标“内容源 / 账号”）；搜索占位符改搜索提示。
- **定位**：`components/layout/NavigationDrawer.vue:34`、各视图搜索框 placeholder。

### 🟡 UX-07 首页空状态薄弱，宽屏与移动端都显空洞
- **现象**：无数据时只有小熊猫 + “这里什么都没有”，无引导/CTA；横屏大片死区，像“坏了”。
- **建议**：加引导动作（搜索 / 登录 / 订阅）+ 更有信息量的插画文案；大画布收敛视觉重心。

### 🟡 UX-08 桌面端表单宽度策略不统一，宽屏稀疏失衡
- **现象**：设置页“整宽卡片 + 控件右对齐”；管理页“窄字段左对齐 + 右侧大片留白”；短内容页（设备）在竖屏高画布下上方一小块、下方全空。
- **建议**：统一带可读 max-width（约 640–720px）的内容度量，或双列字段 / 右侧说明栏。

### 🟡 UX-09 品牌青 / 品红用作小字号文本，对比度不达 WCAG AA
- **现象**：分区标签、激活导航/标签文字用 `#009688`，小字号 light≈3.4:1、dark≈3.1:1（仅 black≈4.8 通过）；`#e040fb` 作文本 light≈3.1–3.3 不达。详见 §5。
- **建议**：文本用途改更深 teal 变体，或加粗放大到 large-text 阈值。

### 🔵 UX-10 浅色主题次要文本对比度处于 AA 边界
- **现象**：`rgba(0,0,0,.54)` 在 `#f5f5f5` 上≈4.5:1 压线。
- **建议**：alpha ≥0.6 或固定 `#5f6368`。

### 🔵 UX-11 管理页分区/表头图标与标题换行错位
- **现象**：代理“出站代理”、设备“配对码”下的图标像孤立挂在标题下一行（与 UX-02 同根，管理系通病）。
- **建议**：修正 `SectionHeader` 的 flex 对齐（图标作左列、与标题同行居中）。

### 🔵 UX-12 PWA 可安装性告警：缺图标与 form_factor
- **现象**：缺 96×96 快捷方式图标、缺 `form_factor: wide`；`display: standalone` 已设。
- **建议**：若重视“安装为应用”，补齐 manifest 图标与 `form_factor`。

### 🔵 UX-13 全局汉堡以 fixed 叠在视图头部左上，存在碰撞隐患
- **现象**：`.app-hamburger` `position:fixed; left:8px`，靠头部左留白让位；320px 或头部含左控件时易重叠。
- **建议**：头部为汉堡预留固定槽位（左 padding）。
- **定位**：`src/App.vue` `.app-hamburger`。

---

## 4. 做得好的地方（保留，勿误伤）

- **P-01 响应式骨架清晰**：单一 720px 断点；<720 模态抽屉+遮罩+汉堡、≥720 常驻；带过渡动画。
- **P-02 桌面三级导航层次清楚**：全局抽屉 + 分区子导航轨 + 内容三栏，激活态明确。
- **P-03 设置页偏好行组件规范**：图标内联、标题/说明两行、控件右对齐、分段+开关样式统一——**全产品应对齐的标杆**。
- **P-04 代理/设备页文案质量高**：出站代理说明、生效范围提示、配对码说明、测试连接/保存主次按钮配对清楚。
- **P-05 三套主题 token + MD 度量体系完整**：light/dark/black 对齐 Material；48dp 触控、2dp 卡片、56dp app bar 普遍落实；主文本对比度充足。
- **P-06 展示型组件 MD 还原度高**：卡片/列表/进度/搜索栏/抽屉等接近 Material 规格。

---

## 5. 对比度审计（静态核算，WCAG AA：正文≥4.5 / 大号≥3）

色板取自 `src/styles/tokens.css`（light 355+ / dark 456+ / black 548+）。

| 文本色 | 背景 | 比值 | AA 正文 | 备注 |
|---|---|---|---|---|
| `#009688` 品牌青 | `#f5f5f5` 浅卡 | 3.4:1 | ❌ | 分区标签/激活文字 |
| `#009688` 品牌青 | `#3a3a3a` 暗卡 | 3.1:1 | ❌ | 同上 |
| `#009688` 品牌青 | `#191919` 纯黑卡 | 4.8:1 | ✅ | 仅纯黑通过 |
| `#e040fb` 品红 | `#ffffff` 浅底 | 3.3:1 | ❌ | 进度/标签/按钮文字 |
| `rgba(0,0,0,.54)` 浅次要 | `#f5f5f5` 浅卡 | 4.5:1 | ⚠️ 压线 | UX-10 |
| `rgba(255,255,255,.7)` 暗次要 | `#3a3a3a` 暗卡 | 6.5:1 | ✅ | |
| `#ffffff` 暗主文本 | `#3a3a3a` 暗卡 | 10.3:1 | ✅ AAA | |
| `rgba(0,0,0,.87)` 浅主文本 | `#ffffff` 浅底 | 16:1 | ✅ AAA | |

> 风险集中在“把品牌色当正文色用” + 浅色次要文本边界值。图形/大号用品牌青（≥3:1）合格，问题只在小字号文本。

---

## 6. 建议执行顺序

1. **UX-14 建共享表单原语 + 冻结基准** —— 治本，消除跨模块漂移与 MD 组件不合规（顺带覆盖 UX-02/08/11 与原生 select）。
2. **UX-01 裁切悬浮圆 + UX-03 启动页空值** —— 视觉故障感与设置不可读，感知最强。
3. **UX-04 子导航横滑 + 激活可见** —— 窄手机 + 管理页 8 标签溢出。
4. **UX-05 404 兜底 + UX-06 品牌文案 + UX-07 空状态** —— 边界路径与身份文案。
5. **UX-09/10 对比度 + UX-12/13 收尾** —— AA 合规、PWA 告警、汉堡槽位。

---

## 7. 复核清单（未实拍项，供开发模型自验）

- [ ] 4:3 横屏 1024×768：抽屉+轨+内容(≈494px) 是否过窄。
- [ ] 320px 极窄屏：汉堡与头部左控件碰撞（UX-13）；设置标签溢出（UX-04）。
- [ ] 管理/服务器·处理·高级·关于 的行/控件实现，逐页核对是否已对齐基准（§2.4 标“待核”者）。
- [ ] 浅色 / 纯黑主题下品牌青小字号文本与次要文本的实际观感（§5）。
- [ ] 抽屉打开态（移动端模态抽屉+遮罩）的视觉与动效。
- [ ] 阅读器页（`ReaderView.vue` 用 `matchMedia('(min-aspect-ratio: 1/1)')`）横/竖屏版式（本次无画廊数据，未实拍）。

---

*审查人：多模态 UI/UX 走查 ｜ 工具：computer_use + Edge DevTools 设备模拟 ｜ 交付格式：Markdown（供开发模型落地）*
