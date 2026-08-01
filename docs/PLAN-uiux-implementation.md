# AnotherViewer WebUI · UI/UX 治理开发计划

> **来源**：`docs/ui-ux-review-2026-08-01.md`（多模态 UI/UX 走查报告）
> **执行者**：无多模态能力的 AI 开发模型（善于子代理并行、工具调用）
> **日期**：2026-08-01 ｜ 分支：`BiLi_PC_Gamer`
> **前端根**：`web-frontend/`（Vue 3 + Composition API + Pinia + Vite + Vitest）

---

## 0. 执行者能力画像与验证策略

### 0.1 执行者特征

| 能力 | 状态 | 计划适配 |
|---|---|---|
| 代码读写/搜索/编辑 | ✅ 强 | 全部代码操作自主完成 |
| 子代理并行 | ✅ 强 | 大量使用并行子代理，按文件所有权隔离 |
| Shell/构建/测试 | ✅ 强 | 每个阶段设自动化验证门 |
| 多模态（看截图） | ❌ 无 | **禁止依赖视觉判断**；所有验证通过 DOM 断言、CSS 属性断言、类型检查、Playwright 像素 diff 完成 |

### 0.2 非多模态验证体系（核心约束）

执行者**不能看截图**，因此所有"看起来对不对"的判断必须转化为可自动化验证的断言：

| 验证层 | 工具 | 验什么 | 命令 |
|---|---|---|---|
| **类型安全** | `vue-tsc --noEmit` | Props 类型、emit 签名、import 正确性 | `npm run typecheck` |
| **单元/组件测试** | Vitest + @vue/test-utils + happy-dom | DOM 结构、CSS class、ARIA 属性、事件、slot 渲染 | `npm run test` |
| **CSS 属性断言** | Vitest 内 `wrapper.element.style` / `getComputedStyle`（happy-dom 有限，用 class 断言替代） | 关键样式 class 存在性、内联 style 值 | 同上 |
| **视觉回归** | Playwright + pixelmatch | 截图像素级对比（baseline vs actual） | `npm run test:visual` |
| **构建** | `vue-tsc && vite build` | 全量编译通过 | `npm run build` |
| **grep 审计** | grep/ripgrep | 残留的原生 `<select>`、重复 CSS class、硬编码颜色 | 手动 grep |

**关键规则**：每个新组件和每次迁移都必须附带 Vitest 测试。测试通过 = 功能正确的**唯一判据**。不要试图"看看效果"。

---

## 1. 阶段总览

```
Phase 0 ─ 基础设施准备（串行，1 agent）
  │
Phase 1 ─ 共享原语组件库（串行，1 agent 主力 + 1 agent 测试）
  │
  ├── Phase 2A ─ Settings 迁移 ──┐
  ├── Phase 2B ─ Admin 迁移 ────┤  并行（3 agents，文件隔离）
  ├── Phase 2C ─ 独立页迁移 ────┘
  │
  ├── Phase 3A ─ 结构性 Bug 修复 ─┐
  ├── Phase 3B ─ 导航/路由修复 ──┤  并行（3 agents，文件隔离）
  ├── Phase 3C ─ 对比度/PWA/收尾 ─┘
  │
Phase 4 ─ 全量集成验证 + 视觉回归基线更新（串行，1 agent）
```

---

## 2. 多代理并行规则（必读）

### 2.1 文件所有权隔离

**同一时刻，每个文件只能被一个 agent 修改。** 并行 agent 之间通过文件所有权隔离：

| Agent | 独占文件范围 | 只读引用 |
|---|---|---|
| 2A-settings | `src/views/settings/*.vue` | `src/components/form/`（只读 import） |
| 2B-admin | `src/views/admin/*.vue` | `src/components/form/`（只读 import） |
| 2C-standalone | `src/views/LoginView.vue`, `src/views/SmbBackupView.vue`, `src/components/smb/SmbConfigForm.vue` | `src/components/form/`（只读 import） |
| 3A-struct-bugs | `src/App.vue`, `src/views/HomeView.vue`, `src/views/settings/GeneralSettings.vue`（仅 UX-03 select 修复） | — |
| 3B-nav-router | `src/router/index.ts`, `src/views/settings/SettingsLayout.vue`, `src/views/admin/AdminLayout.vue`, `src/components/layout/NavigationDrawer.vue` | — |
| 3C-polish | `src/styles/tokens.css`, `public/manifest.json`, `src/App.vue`（仅 UX-13 汉堡槽位 CSS） | — |

**冲突解决**：若两个 agent 需改同一文件（如 3A 和 3C 都碰 `App.vue`），**串行化**——先 3A 后 3C，或合并为一个 agent。上表已设计为无冲突。

### 2.2 共享契约文件

Phase 1 产出的原语组件是**只读契约**。Phase 2 的 agent 只能 `import`，不能修改 `src/components/form/` 下的任何文件。如果发现原语 API 不满足需求：

1. 停止当前迁移
2. 向协调者（主 agent）报告具体缺口（哪个 prop/slot/event 缺失）
3. 协调者串行修改原语 → 重跑原语测试 → 通知所有并行 agent 继续

### 2.3 子代理调度模式

```
主 Agent（协调者）
├── 串行执行 Phase 0, 1
├── Phase 2: 同时 spawn 3 个子代理（2A, 2B, 2C）
│   ├── 每个子代理的 prompt 包含：
│   │   ├── 完整的原语 API 文档（Phase 1 产出）
│   │   ├── 该 agent 独占的文件清单
│   │   ├── 迁移规则（§4）
│   │   └── 验证命令（必须自行跑通 typecheck + test）
│   └── 子代理返回后，主 agent 跑全量 typecheck + test 确认无交叉破坏
├── Phase 3: 同时 spawn 3 个子代理（3A, 3B, 3C）
│   └── 同上模式
└── Phase 4: 主 agent 串行执行全量验证
```

### 2.4 子代理 Prompt 模板

每个子代理的 prompt 必须包含以下结构（不要省略任何部分）：

```markdown
## 你的角色
你是 Phase {N} 的迁移/修复 agent，代号 {agent-id}。

## 你独占的文件（只能你改）
{文件清单}

## 只读引用（只能 import，不能改）
{文件清单}

## 原语 API 参考
{完整的组件 Props/Events/Slots 文档，从 Phase 1 产出复制}

## 迁移规则
{从 §4 复制相关规则}

## 验证要求
完成所有修改后，你必须依次运行：
1. `cd web-frontend && npx vue-tsc --noEmit` — 必须 0 error
2. `cd web-frontend && npx vitest run` — 必须全部通过
3. 对每个修改的 .vue 文件，确认对应的 .spec.ts 存在且通过
如果任何一步失败，自行修复后重跑，直到全部通过。

## 提交规范
每完成一个逻辑单元（一个组件迁移 / 一个 bug 修复），单独 commit：
- `refactor(web-fe): migrate GeneralSettings to shared form primitives`
- `fix(web-fe): UX-03 launch page select shows empty value`
不要合并多个不相关的修改到一个 commit。
```

### 2.5 并行安全验证门

每个并行阶段完成后，主 agent 必须执行**集成验证**：

```bash
cd web-frontend
npx vue-tsc --noEmit          # 全量类型检查
npx vitest run                # 全量单元测试
npm run build                 # 生产构建
```

任何一步失败 → 定位是哪个 agent 的修改引起 → 串行修复 → 重跑。

---

## 3. Phase 0：基础设施准备（串行）

**目标**：为原语组件库建立目录结构、测试约定、导出入口。

### 3.1 任务清单

| # | 任务 | 文件 | 说明 |
|---|---|---|---|
| 0.1 | 创建表单原语目录 | `src/components/form/` | 新目录，存放所有共享表单组件 |
| 0.2 | 创建 barrel export | `src/components/form/index.ts` | `export { default as PrefRow } from './PrefRow.vue'` 等 |
| 0.3 | 创建测试目录 | `src/components/form/__tests__/` | 每个原语一个 `.spec.ts` |
| 0.4 | 在 tokens.css 追加表单 token | `src/styles/tokens.css` | 追加 `/* ── Form primitives ── */` 段，包含：`--field-height: 48px`、`--field-radius: 4px`（MD outlined）、`--field-label-size: 12px`、`--field-padding-h: 16px`、`--switch-width: 52px`、`--switch-height: 32px`、`--switch-thumb-size: 24px`、`--segment-radius: 8px`。**不要修改任何已有 token。** |
| 0.5 | 更新视觉回归路由 | `e2e/visual.spec.ts` | 在 `ROUTES` 数组追加所有 admin 路由：`/admin/download`, `/admin/server`, `/admin/devices`, `/admin/proxy`, `/admin/access`, `/admin/processing`, `/admin/advanced`, `/admin/about`；追加 `/settings/general`, `/settings/reader`, `/settings/privacy`, `/settings/transfer` |
| 0.6 | 捕获基线截图 | — | `cd web-frontend && npm run test:visual:update`（生成 `e2e/baseline/` 下所有路由×主题×视口截图） |

### 3.2 验证门

```bash
cd web-frontend
npx vue-tsc --noEmit     # 0 errors
npx vitest run           # all pass
npm run test:visual:update  # baseline 截图生成成功
```

### 3.3 提交

```
chore(web-fe): scaffold form primitives directory + extend visual regression routes
```

---

## 4. Phase 1：共享原语组件库（串行主力 + 测试）

**目标**：构建 6 个 MD3 对齐的共享表单原语，完全覆盖审查报告 §2.5 的需求。

**这是整个计划的关键路径。所有后续迁移都依赖这些组件的 API 稳定性。**

### 4.1 组件规格

#### 4.1.1 `PrefRow.vue` — 偏好设置行（标杆：`GeneralSettings.vue` 的 `.pref`）

```typescript
// Props
interface PrefRowProps {
  icon?: string           // AppIcon name，可选
  title: string           // 主标题
  summary?: string        // 副标题/说明
  disabled?: boolean      // 禁用态（降低不透明度）
}
// Slots
// #default — 右侧控件区域（switch/select/segment 等）
// #below   — 行下方全宽区域（用于展开内容）
// Events: 无（行本身不可点击；如需点击行，外层包 <button>）
```

**DOM 结构**（必须精确匹配，测试会断言）：

```html
<div class="pref-row" :class="{ 'pref-row--disabled': disabled }">
  <AppIcon v-if="icon" :name="icon" class="pref-row__icon" />
  <div class="pref-row__text">
    <span class="pref-row__title">{{ title }}</span>
    <span v-if="summary" class="pref-row__summary">{{ summary }}</span>
  </div>
  <div class="pref-row__control"><slot /></div>
  <div v-if="$slots.below" class="pref-row__below"><slot name="below" /></div>
</div>
```

**CSS 要求**（scoped，使用 tokens）：
- `.pref-row`: `display:flex; align-items:center; flex-wrap:wrap; gap:8px 16px; min-height:var(--field-height, 48px); padding:10px var(--keyline-margin, 16px)`
- `.pref-row__icon`: `flex:0 0 24px; color:var(--drawable-color-primary)`
- `.pref-row__text`: `flex:1 1 160px; display:flex; flex-direction:column; gap:1px`
- `.pref-row__title`: `font-size:clamp(14px, 16px, 18px); color:var(--text-color-primary)`
- `.pref-row__summary`: `font-size:clamp(11px, 12px, 14px); color:var(--text-color-secondary)`
- `.pref-row__control`: `margin-left:auto`（控件右对齐）
- `.pref-row--disabled`: `opacity:0.38; pointer-events:none`

#### 4.1.2 `SectionHeader.vue` — 分区标题（标杆：`.pref-group__title`）

```typescript
interface SectionHeaderProps {
  title: string
  icon?: string    // 可选图标（修复 UX-11：图标与标题同行）
}
```

**DOM**：
```html
<h2 class="section-header">
  <AppIcon v-if="icon" :name="icon" class="section-header__icon" :size="18" />
  <span>{{ title }}</span>
</h2>
```

**CSS**：`display:flex; align-items:center; gap:8px; color:var(--color-primary); font-size:var(--text-small); letter-spacing:0.08em; text-transform:uppercase; padding:16px var(--keyline-margin, 16px) 4px`

#### 4.1.3 `PrefCard.vue` — 偏好卡片容器

```typescript
// 无 Props（纯容器）
// Slots: #default
```

**DOM**：`<div class="pref-card"><slot /></div>`

**CSS**：`background:var(--color-background-floating); border-radius:var(--card-radius); box-shadow:0 var(--card-elevation, 2dp) ...`（从 GeneralSettings 的 `.pref-card` 提取）

内含 `PrefDivider` 子组件或 CSS `:not(:last-child)::after` 实现分隔线。

#### 4.1.4 `AppSwitch.vue` — MD 开关

```typescript
interface AppSwitchProps {
  modelValue: boolean
  disabled?: boolean
  ariaLabel?: string   // 无障碍标签（当无可见文本时必填）
}
// Events
// 'update:modelValue': [value: boolean]
```

**DOM**：
```html
<button
  class="app-switch"
  :class="{ 'app-switch--on': modelValue, 'app-switch--disabled': disabled }"
  role="switch"
  :aria-checked="String(modelValue)"
  :aria-label="ariaLabel"
  :disabled="disabled"
  @click="$emit('update:modelValue', !modelValue)"
>
  <span class="app-switch__track">
    <span class="app-switch__thumb" />
  </span>
</button>
```

**CSS**：从 GeneralSettings 的 `.switch` 提取，使用 token 化尺寸。Track 44×24，thumb 20px，on 态 `translateX(20px)`，颜色用 `var(--color-primary)` / `var(--color-surface-variant)`。

#### 4.1.5 `AppSelect.vue` — MD 下拉菜单（替代全部 13 处原生 `<select>`）

```typescript
interface AppSelectOption {
  value: string | number
  label: string
  disabled?: boolean
}
interface AppSelectProps {
  modelValue: string | number
  options: AppSelectOption[]
  label?: string          // 浮动标签（可选；在 PrefRow 内使用时通常不需要）
  placeholder?: string
  disabled?: boolean
}
// Events
// 'update:modelValue': [value: string | number]
```

**实现要求**：
- **不使用原生 `<select>`**。用 `<button>` 触发 + `<ul role="listbox">` 弹出菜单
- 菜单定位：`position:absolute` 相对于组件根，`z-index: var(--z-popup, 1000)`
- 点击外部关闭（`@click.outside` 或 `useClickOutside` 逻辑）
- 键盘导航：`↑↓` 移动焦点、`Enter/Space` 选中、`Escape` 关闭
- 选中项显示在触发按钮内（**修复 UX-03**：始终显示当前选中 label）
- 触发按钮右侧有 chevron 图标（`v_chevron_down` 或 CSS triangle）
- ARIA：`role="combobox"` + `aria-expanded` + `aria-haspopup="listbox"`

**DOM 骨架**：
```html
<div class="app-select" :class="{ 'app-select--open': open, 'app-select--disabled': disabled }">
  <button class="app-select__trigger" role="combobox" :aria-expanded="open" @click="toggle">
    <span class="app-select__value">{{ selectedLabel }}</span>
    <span class="app-select__arrow" />
  </button>
  <Teleport to="body">
    <ul v-if="open" class="app-select__menu" role="listbox">
      <li v-for="opt in options" :key="opt.value"
          class="app-select__option"
          :class="{ 'app-select__option--selected': opt.value === modelValue }"
          role="option"
          :aria-selected="opt.value === modelValue"
          @click="select(opt)">
        {{ opt.label }}
      </li>
    </ul>
  </Teleport>
</div>
```

**注意**：菜单用 `<Teleport to="body">` 避免被父容器 `overflow:hidden` 裁切。菜单定位需要计算触发按钮的 `getBoundingClientRect()`。

#### 4.1.6 `AppTextField.vue` — MD Outlined 文本框（标杆：`LoginView.vue` 的 `.field`）

```typescript
interface AppTextFieldProps {
  modelValue: string
  label?: string
  type?: 'text' | 'password' | 'number' | 'url'
  placeholder?: string
  helperText?: string
  errorText?: string       // 非空时显示错误态（红色边框+错误文案）
  disabled?: boolean
  maxlength?: number
}
// Events
// 'update:modelValue': [value: string]
```

**DOM**（复刻 LoginView 的浮动标签模式）：
```html
<label class="app-text-field" :class="{ 'app-text-field--error': errorText, 'app-text-field--disabled': disabled }">
  <input
    class="app-text-field__input"
    :type="type"
    :value="modelValue"
    :placeholder="placeholder || ' '"
    :disabled="disabled"
    :maxlength="maxlength"
    @input="$emit('update:modelValue', ($event.target as HTMLInputElement).value)"
  />
  <span v-if="label" class="app-text-field__label">{{ label }}</span>
  <span v-if="errorText || helperText" class="app-text-field__helper">
    {{ errorText || helperText }}
  </span>
</label>
```

**CSS**：从 LoginView 的 `.field` 提取。关键：
- 边框：`1px solid var(--color-outline, rgba(0,0,0,.38))`，聚焦 `2px solid var(--color-primary)`
- 浮动标签：`:focus + .app-text-field__label, :not(:placeholder-shown) + .app-text-field__label` → `top:0; font-size:12px; color:var(--color-primary)`
- 错误态：边框/标签/ helper 均 `var(--color-error, #b00020)`

#### 4.1.7 `AppSegmented.vue` — 分段按钮（标杆：GeneralSettings 的 `.segment`）

```typescript
interface AppSegmentedOption {
  value: string
  label: string
  icon?: string
}
interface AppSegmentedProps {
  modelValue: string
  options: AppSegmentedOption[]
  ariaLabel?: string
}
// Events
// 'update:modelValue': [value: string]
```

**DOM**：
```html
<div class="app-segmented" role="radiogroup" :aria-label="ariaLabel">
  <button v-for="opt in options" :key="opt.value"
    class="app-segmented__btn"
    :class="{ 'app-segmented__btn--active': opt.value === modelValue }"
    role="radio"
    :aria-checked="String(opt.value === modelValue)"
    @click="$emit('update:modelValue', opt.value)">
    <AppIcon v-if="opt.icon" :name="opt.icon" :size="18" />
    <span>{{ opt.label }}</span>
  </button>
</div>
```

### 4.2 测试要求（每个组件必须覆盖）

每个原语的 `.spec.ts` 至少覆盖：

| 测试类别 | 具体断言 |
|---|---|
| **渲染** | 默认 props 渲染出正确的 DOM 结构（class name、role、文本内容） |
| **Props 响应** | 修改 props 后 DOM 更新（如 `disabled=true` → class 包含 `--disabled`） |
| **事件** | 用户交互触发正确的 emit（如 click switch → `update:modelValue` with `!current`） |
| **v-model** | 双向绑定工作（mount 时传入 modelValue，触发 input，断言 emit） |
| **Slots** | 默认 slot / named slot 内容正确渲染 |
| **ARIA** | role、aria-checked、aria-expanded、aria-label 正确 |
| **键盘**（AppSelect） | Enter/Space 打开菜单、↑↓ 移动、Escape 关闭 |
| **边界** | 空 options、超长文本、disabled 态不响应交互 |

**测试风格**：参照 `src/components/atoms/__tests__/AppCard.spec.ts` 的模式——`describe` 分组、`mount` + `wrapper.find` + `wrapper.emitted`。

### 4.3 验证门

```bash
cd web-frontend
npx vue-tsc --noEmit
npx vitest run src/components/form/
npx vitest run   # 全量，确保没破坏已有测试
```

### 4.4 产出物

Phase 1 完成后，主 agent 必须生成一份 **API 文档**（纯文本），包含每个组件的：
- 完整 Props 接口（TypeScript）
- Events 列表
- Slots 列表
- DOM 结构（class name 树）
- 使用示例（在 PrefRow 内嵌 AppSwitch 的完整代码片段）

此文档将**原文嵌入** Phase 2 每个子代理的 prompt 中。

### 4.5 提交（按组件拆分）

```
feat(web-fe): add PrefRow + SectionHeader + PrefCard shared form primitives
feat(web-fe): add AppSwitch shared form primitive
feat(web-fe): add AppSelect (MD menu dropdown) shared form primitive
feat(web-fe): add AppTextField (MD outlined) shared form primitive
feat(web-fe): add AppSegmented shared form primitive
```

---

## 5. Phase 2：全量迁移（3 个并行子代理）

### 5.0 迁移通用规则（所有 2A/2B/2C agent 共享）

1. **替换行结构**：将每个 `.pref` + `.pref__icon` + `.pref__text` + `.pref__title` + `.pref__summary` 块替换为 `<PrefRow :icon="..." title="..." summary="...">`，控件放入默认 slot
2. **替换分区标题**：`.pref-group__title` → `<SectionHeader title="..." />`
3. **替换卡片容器**：`.pref-card` → `<PrefCard>`
4. **替换开关**：`.switch` + `.switch__thumb` → `<AppSwitch v-model="..." />`
5. **替换原生 select**：`<select>` → `<AppSelect v-model="..." :options="[...]" />`。**options 数组从原 `<option>` 标签提取，确保 value→label 映射完整（修复 UX-03）**
6. **替换分段控件**：`.segment` + `.segment__btn` → `<AppSegmented v-model="..." :options="[...]" />`
7. **替换文本框**（仅 2C）：`.field` + `.field__label` → `<AppTextField v-model="..." label="..." />`
8. **删除 scoped CSS 中的冗余**：迁移完成后，删除该文件 `<style scoped>` 中不再使用的 `.pref`, `.pref__*`, `.pref-card`, `.pref-group*`, `.pref-divider`, `.switch`, `.switch__thumb`, `.segment`, `.segment__btn`, `.select`, `.select__label` 等样式块。**只删确认不再被任何元素引用的样式。**
9. **保留非表单样式**：页面特有的布局、动画、snackbar 等样式不动
10. **不改变功能逻辑**：`<script setup>` 中的 API 调用、store 交互、computed、watch 一律不动。只改 template 中的组件引用和 style 中的冗余 CSS
11. **import 路径**：`import { PrefRow, SectionHeader, PrefCard, AppSwitch, AppSelect, AppSegmented } from '@/components/form'`

### 5.1 Agent 2A：Settings 迁移

**独占文件**：
- `src/views/settings/GeneralSettings.vue`
- `src/views/settings/ReaderSettings.vue`
- `src/views/settings/PrivacySettings.vue`
- `src/views/settings/TransferSettings.vue`

**任务明细**：

| 文件 | 替换内容 | 特别注意 |
|---|---|---|
| GeneralSettings | 4× select → AppSelect, 8× switch → AppSwitch, 1× segment → AppSegmented, 所有 .pref → PrefRow | **UX-03 重点**：启动页 select 的 options 必须包含所有路由的 label（首页/搜索/收藏/历史/下载），确保选中值有可见文案 |
| ReaderSettings | 3× select → AppSelect, 4× switch → AppSwitch, 1× segment → AppSegmented, 1× stepper 保留（不是原语范围）, 1× slider 保留 | stepper 和 slider 暂不迁移，保留原实现 |
| PrivacySettings | 1× switch → AppSwitch, .pref → PrefRow | 最简单的页面，作为迁移模板验证 |
| TransferSettings | .pref 变体 → PrefRow | 审查报告标注"图标行变体"，统一为标准 PrefRow |

**测试**：
- 更新 `src/views/__tests__/TransferSettings.spec.ts`（已有）
- 新建 `src/views/__tests__/GeneralSettings.spec.ts`：断言 AppSelect 渲染、options 数量、启动页默认选中值非空
- 新建 `src/views/__tests__/ReaderSettings.spec.ts`
- 新建 `src/views/__tests__/PrivacySettings.spec.ts`

### 5.2 Agent 2B：Admin 迁移

**独占文件**：
- `src/views/admin/AdminProxy.vue`
- `src/views/admin/AdminDevices.vue`
- `src/views/admin/AdminDownload.vue`
- `src/views/admin/AdminServer.vue`
- `src/views/admin/AdminAccess.vue`
- `src/views/admin/AdminProcessing.vue`
- `src/views/admin/AdminAdvanced.vue`
- `src/views/admin/AdminAbout.vue`

**任务明细**：

| 文件 | 替换内容 | 特别注意 |
|---|---|---|
| AdminProxy | 1× bare select → AppSelect, 4× `.pref__input` → AppTextField, 1× switch → AppSwitch, .pref → PrefRow | **UX-02 重点**：图标不再单独成行，改用 PrefRow 的 icon prop；标题/说明恢复两行堆叠 |
| AdminDevices | .pref → PrefRow, 表头图标 → SectionHeader(icon) | **UX-02/UX-11**：表头图标与标题同行 |
| AdminDownload | 3× switch → AppSwitch, 3× stepper 保留, 2× num-field 保留, .field dialog → AppTextField | stepper 暂不迁移 |
| AdminServer | 1× switch → AppSwitch, 2× `.server__input` → AppTextField, .pref → PrefRow | |
| AdminAccess | 1× switch → AppSwitch, 3× `.field` password → AppTextField(type=password), .pref → PrefRow | |
| AdminProcessing | 2× select → AppSelect, 1× switch → AppSwitch, slider 保留 | |
| AdminAdvanced | 1× select → AppSelect, 1× switch → AppSwitch, .pref → PrefRow | |
| AdminAbout | .pref → PrefRow（纯展示行） | 无控件，仅行结构统一 |

**测试**：
- 更新 `src/views/__tests__/AdminProxy.spec.ts`（已有）：断言 AppTextField 渲染、AppSelect 替代原生 select
- 更新 `src/views/__tests__/AdminDevices.spec.ts`（已有）
- 新建 `src/views/__tests__/AdminDownload.spec.ts`
- 新建 `src/views/__tests__/AdminServer.spec.ts`
- 新建 `src/views/__tests__/AdminAccess.spec.ts`
- 新建 `src/views/__tests__/AdminProcessing.spec.ts`
- 新建 `src/views/__tests__/AdminAdvanced.spec.ts`

### 5.3 Agent 2C：独立页迁移

**独占文件**：
- `src/views/LoginView.vue`
- `src/views/SmbBackupView.vue`
- `src/components/smb/SmbConfigForm.vue`

**任务明细**：

| 文件 | 替换内容 | 特别注意 |
|---|---|---|
| LoginView | 2× `.field` → AppTextField | 保留页面整体布局（独立全屏布局，不用 PrefRow）；仅替换输入框组件 |
| SmbBackupView | 1× select → AppSelect, 多个 `.field` → AppTextField, 1× switch → AppSwitch | 表单在 12-col grid 内，AppTextField 需支持外层 grid class |
| SmbConfigForm | 评估是否仍被引用 | 若已被 SmbBackupView 完全替代（无 import），标记 deprecated 或删除；若仍被引用，迁移其 `.form-group` → AppTextField |

**测试**：
- 新建 `src/views/__tests__/LoginView.spec.ts`：断言 AppTextField 渲染、label 文本、submit 事件
- 新建 `src/views/__tests__/SmbBackupView.spec.ts`

### 5.4 Phase 2 验证门（主 agent 执行）

```bash
cd web-frontend
npx vue-tsc --noEmit
npx vitest run
npm run build
```

**grep 审计**（主 agent 执行）：

```bash
# 确认原生 <select> 已全部替换（期望 0 命中，SmbConfigForm 若已删除）
grep -rn '<select' web-frontend/src/views/ web-frontend/src/components/ --include='*.vue'

# 确认 .switch__thumb 不再出现在 views/（应只在 form/AppSwitch.vue 内）
grep -rn 'switch__thumb' web-frontend/src/views/ --include='*.vue'

# 确认 .pref__icon 不再出现在 views/（应只在 form/PrefRow.vue 内）
grep -rn 'pref__icon' web-frontend/src/views/ --include='*.vue'
```

### 5.5 提交（每个 agent 按文件拆分）

```
# Agent 2A
refactor(web-fe): migrate GeneralSettings to shared form primitives
refactor(web-fe): migrate ReaderSettings to shared form primitives
refactor(web-fe): migrate PrivacySettings to shared form primitives
refactor(web-fe): migrate TransferSettings to shared form primitives

# Agent 2B
refactor(web-fe): migrate AdminProxy to shared form primitives (UX-02)
refactor(web-fe): migrate AdminDevices to shared form primitives (UX-02/11)
refactor(web-fe): migrate AdminDownload to shared form primitives
refactor(web-fe): migrate AdminServer to shared form primitives
refactor(web-fe): migrate AdminAccess to shared form primitives
refactor(web-fe): migrate AdminProcessing to shared form primitives
refactor(web-fe): migrate AdminAdvanced + AdminAbout to shared form primitives

# Agent 2C
refactor(web-fe): migrate LoginView to AppTextField
refactor(web-fe): migrate SmbBackupView to shared form primitives
```

---

## 6. Phase 3：Bug 修复与收尾（3 个并行子代理）

### 6.1 Agent 3A：结构性 Bug

**独占文件**：
- `src/App.vue`（UX-01 悬浮圆 + UX-13 汉堡槽位）
- `src/views/HomeView.vue`（UX-07 空状态）
- `src/views/settings/GeneralSettings.vue`（UX-03 启动页——**仅在 Phase 2 未完全修复时**）

| Bug ID | 任务 | 验证方法 |
|---|---|---|
| UX-01 | grep `position:\s*fixed` 审计所有 fixed 元素；找到被裁切的悬浮圆（审查报告已排除 FabLayout 和 app-hamburger），修正 inset/transform/z-index + safe-area | 单元测试：断言 fixed 元素的 CSS 包含 `right: env(safe-area-inset-right)` 或等效；Playwright 截图 diff |
| UX-07 | HomeView 空状态加引导 CTA（搜索/登录按钮）+ 文案 | 单元测试：空数据时渲染 CTA 按钮 |
| UX-13 | `.app-hamburger` 改为头部预留 slot（左 padding），不再 fixed 叠放 | CSS 断言：hamburger 不再 `position:fixed` 或头部有 `padding-left` |

### 6.2 Agent 3B：导航/路由

**独占文件**：
- `src/router/index.ts`（UX-05 404 兜底）
- `src/views/settings/SettingsLayout.vue`（UX-04 标签溢出）
- `src/views/admin/AdminLayout.vue`（UX-04 标签溢出）
- `src/components/layout/NavigationDrawer.vue`（UX-06 品牌文案）
- 新建 `src/views/NotFoundView.vue`

| Bug ID | 任务 | 验证方法 |
|---|---|---|
| UX-05 | router 末尾加 `{ path: '/:pathMatch(.*)*', name: 'NotFound', component: () => import('@/views/NotFoundView.vue') }`；NotFoundView 显示 404 + 回首页按钮 | 单元测试：mount router with 未知路径 → 渲染 NotFoundView |
| UX-04 | SettingsLayout + AdminLayout 的横标签栏加 `overflow-x:auto` + 右侧渐隐 mask + 激活标签 `scrollIntoView({inline:'center'})` | 单元测试：标签容器 class 包含 `overflow-x: auto`；CSS 断言 |
| UX-06 | NavigationDrawer 头部 "E-Hentai" → "AnotherViewer"；搜索框 placeholder "E-hentai" → 搜索提示文案 | 单元测试：断言文本内容 |

### 6.3 Agent 3C：对比度 / PWA / 收尾

**独占文件**：
- `src/styles/tokens.css`（UX-09/10 对比度）
- `public/manifest.json`（UX-12 PWA）

| Bug ID | 任务 | 验证方法 |
|---|---|---|
| UX-09 | `#009688` 小字号文本用途改为更深的 teal（如 `#00796b`，light 下 ≈4.6:1）；`#e040fb` 文本用途改为 `#ab47bc` 或加粗放大。**在 tokens.css 中新增 `--color-primary-text` token，不修改 `--color-primary`（图形/大字号仍用原值）** | grep 审计：所有 `font-size < 18px` 且 `color: var(--color-primary)` 的选择器改为 `var(--color-primary-text)` |
| UX-10 | 浅色次要文本 `rgba(0,0,0,.54)` → `rgba(0,0,0,.6)` 或 `#5f6368` | tokens.css 静态核算 |
| UX-12 | manifest.json 补 96×96 图标 + `form_factor: wide` | JSON schema 断言（测试读 manifest 验证字段存在） |

### 6.4 Phase 3 验证门

同 Phase 2 验证门 + 额外 grep 审计：

```bash
# UX-06: 确认 "E-Hentai" / "E-hentai" 不再出现（排除注释）
grep -rni 'e-hentai' web-frontend/src/ --include='*.vue' --include='*.ts' | grep -v '//'

# UX-09: 确认小字号文本不再直接用 --color-primary
# （人工审查 grep 结果，因为 CSS 选择器上下文需要判断）
grep -rn 'color:.*var(--color-primary)' web-frontend/src/ --include='*.vue' --include='*.css'
```

### 6.5 提交

```
fix(web-fe): UX-01 fix clipped floating circle controls + safe-area
fix(web-fe): UX-07 home empty state with guided CTA
fix(web-fe): UX-13 hamburger reserved slot in header
fix(web-fe): UX-05 add 404 catch-all route + NotFoundView
fix(web-fe): UX-04 sub-nav tab bar horizontal scroll + active scrollIntoView
fix(web-fe): UX-06 brand name E-Hentai → AnotherViewer
fix(web-fe): UX-09/10 contrast tokens for small text + light secondary
fix(web-fe): UX-12 PWA manifest icons + form_factor
```

---

## 7. Phase 4：全量集成验证（串行）

### 7.1 验证清单

| # | 验证项 | 命令/方法 | 通过标准 |
|---|---|---|---|
| 1 | 类型检查 | `npx vue-tsc --noEmit` | 0 errors |
| 2 | 全量单元测试 | `npx vitest run` | 0 failures |
| 3 | 测试覆盖率 | `npx vitest run --coverage` | `src/components/form/` 覆盖率 ≥ 90% |
| 4 | 生产构建 | `npm run build` | 成功，无 warning（terser drop_console 除外） |
| 5 | 视觉回归 | `npm run test:visual` | 与 Phase 0 基线 diff：设置/管理页**预期有差异**（因为组件替换），但首页/收藏/历史/下载/登录页应**无意外差异** |
| 6 | 原生 select 清零 | `grep -rn '<select' src/views/ src/components/ --include='*.vue'` | 0 命中 |
| 7 | 重复 CSS 清零 | `grep -rn 'switch__thumb' src/views/ --include='*.vue'` | 0 命中 |
| 8 | 品牌文案 | `grep -rni 'e-hentai' src/ --include='*.vue' --include='*.ts'` | 0 命中（排除注释） |
| 9 | 404 路由 | 单元测试或 `curl localhost:3000/nonexistent`（需 dev server） | 渲染 NotFoundView |
| 10 | 对比度静态核算 | 读 tokens.css 新 token 值，手动计算 WCAG 比值 | 小字号文本 ≥ 4.5:1 |

### 7.2 视觉回归基线更新

Phase 2/3 的修改**必然改变**设置/管理页的视觉输出。Phase 4 需要：

1. 运行 `npm run test:visual:update` 更新基线
2. 运行 `npm run test:visual` 确认 actual == baseline（自洽）
3. 在 commit message 中注明基线已更新

### 7.3 最终提交

```
test(web-fe): update visual regression baselines after form primitives migration
```

---

## 8. 风险与缓解

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| AppSelect 的 Teleport 菜单定位在 happy-dom 中不可测 | 高 | 菜单定位逻辑无单元测试覆盖 | 定位逻辑抽成 composable `useDropdownPosition`，纯数学函数可单独测试；DOM 交互用 Playwright e2e 覆盖 |
| 并行 agent 的 CSS 删除误伤共享 class | 中 | 样式丢失 | 每个 agent 只删自己文件 `<style scoped>` 内的 CSS；scoped 天然隔离 |
| Phase 2 迁移后视觉回归大面积 diff | 确定 | 基线失效 | Phase 4 统一更新基线；Phase 0 的基线用于比对"非迁移页是否被误伤" |
| 原生 select 的 keyboard 行为与 AppSelect 不一致 | 中 | 键盘用户受影响 | AppSelect 测试覆盖完整键盘导航；ARIA role 断言 |
| SmbConfigForm 仍有隐藏引用 | 低 | 删除后编译失败 | 2C agent 先 grep 全仓 import，确认无引用后再删 |

---

## 9. 审查报告复核清单（Phase 4 附带）

审查报告 §7 的未实拍项，开发模型在 Phase 4 用 Playwright 补充验证：

| 复核项 | 方法 |
|---|---|
| 4:3 横屏 1024×768 | 在 `e2e/visual.spec.ts` 的 VIEWPORTS 追加 `{ id: 'tablet-1024x768', width: 1024, height: 768 }`，截图确认内容区不过窄 |
| 320px 极窄屏 | 追加 `{ id: 'narrow-320x568', width: 320, height: 568 }`，确认汉堡不碰撞、标签可横滑 |
| 管理/服务器·处理·高级·关于 对齐基准 | Phase 2B 迁移后，这些页面已统一使用 PrefRow，通过单元测试断言 |
| 浅色/纯黑主题品牌青文本 | tokens.css 静态核算（Phase 3C） |
| 抽屉打开态 | Playwright 截图：390px 宽度下点击汉堡 → 截图 → 确认抽屉+遮罩渲染 |
| 阅读器横竖屏 | 无画廊数据无法完整测试；仅确认 ReaderView 组件 mount 不报错 |

---

## 10. 工作量估算与子代理数量

| Phase | 子代理数 | 并行度 | 估计文件变更数 |
|---|---|---|---|
| 0 | 0（主 agent） | 串行 | ~3 文件 |
| 1 | 0（主 agent）或 1 测试 agent | 串行 | ~13 新文件（6 组件 + 6 测试 + 1 barrel） |
| 2 | **3 并行** | 2A + 2B + 2C | ~20 文件修改 + ~10 新测试 |
| 3 | **3 并行** | 3A + 3B + 3C | ~10 文件修改 + 1 新文件 |
| 4 | 0（主 agent） | 串行 | 基线更新 |

**总计**：约 6 个子代理（Phase 2 × 3 + Phase 3 × 3），主 agent 负责 Phase 0/1/4 和所有集成验证。

---

## 11. 执行检查清单（给主 agent 的 checklist）

- [ ] Phase 0: 目录结构 + tokens + e2e 路由 + 基线截图
- [ ] Phase 1: 6 个原语组件 + 测试 + API 文档
- [ ] Phase 1 验证门: typecheck + test + build
- [ ] Phase 2: spawn 3 个子代理（2A/2B/2C），prompt 含完整 API 文档
- [ ] Phase 2 收集: 3 个子代理全部返回
- [ ] Phase 2 验证门: typecheck + test + build + grep 审计
- [ ] Phase 3: spawn 3 个子代理（3A/3B/3C）
- [ ] Phase 3 收集: 3 个子代理全部返回
- [ ] Phase 3 验证门: typecheck + test + build + grep 审计
- [ ] Phase 4: 全量验证 + 视觉回归基线更新 + 复核清单
- [ ] 最终: `git log --oneline` 确认提交历史清晰、按逻辑拆分
