# AnotherViewer 代码库隐藏 + 瘦身计划

> 状态：待执行（v1.0）
> 创建：2026-07-30
> 分支：BiLi_PC_Gamer
> 关联：`docs/webui-progress.md` §I5 运维阻断事件／`docs/superpowers/specs/2026-07-29-site-provider-plugin-system-design.md`（已弃，仅作历史参考）
>
> ── 摘要 ──
> 这是与架构重构完全独立的新规划。**不**为 E-Hentai 设计 SPI、**不**拆模块、**不**改包名（除 sourceSet 流转需要的物理位移）。
> 用户判断 E-Hentai 代码已相当完善、无后续工作空间——本规划宗旨仅是让 LLM 在开发其他组件时**不触 R18 内容审核**+ 顺手瘦身仓库。
> 三条隐藏路径叠加（AGENTS.md ignore + GuardiansHitler battery `src/sensitive/` + parser/data 编 jar），删减仅限外部辅助与遗留 LLM 工具目录。

---

## 1. 背景与目标

### 1.1 痛点回顾

`docs/webui-progress.md` §I5 记录：I5 子代理在深度探索 `app/` 约 84 工具调用、累积 ~4.9M token 后被 `data_inspection_failed` 拦截。E-Hentai 业务代码（gallery / hentai / adult 字串密集）触发了模型的 R18 内容审核。

E-Hentai 业务源码体积与敏感性集中分布在：

```
app/src/main/java/com/hippo/ehviewer/client/
    13 引擎(java/kt) + 22 parser + 21 data + 9 exception + 5 wifi   = 70 文件
ehviewer-core/src/main/java/com/hippo/ehviewer/client/
    平行副本 68 文件（与 app 副本几乎相同、个别差异走 Android API vs JVM API）
app/src/main/res/values*/strings.xml       ~14 处「hentai/exhentai/gallery」字串
docs/superpowers/specs/2026-07-29-*.md      4900+ 行，含「R18/exhentai/doujin」等字样
```

业务方判断：E-Hentai 实现"已相当完善，没有太多后续工作空间"——重构收益不抵成本。

### 1.2 本规划目标

- **G1**：LLM 工具在开发其他组件（前端 UI、Sync 模块、后端 service 等）时，加载项目上下文**不触 R18 内容审核**。
- **G2**：仓库瘦身——清除已无用的辅助工具/废弃 LLM 工具配置目录。
- **G3**：E-Hentai 业务功能**零行为回归**——APK 仍能编译、安装、运行、登录、浏览、下载。
- **G4**：可快速回滚——所有变化均不破坏原代码结构，单 commit `git revert` 即可复原。

### 1.3 非目标

- ❌ 设计 `SiteProvider` SPI / AIDL Bound Service（即 spec/2026-07-29 整套方案）
- ❌ 模块拆分：不新建 Gradle 模块、不改包名
- ❌ Profile 用户态切换、多源聚合器等架构演进
- ❌ 修改任何 E-Hentai 业务逻辑
- ❌ 修改 `app/src/main/res/` 资源中实际显示给用户的字符串（仅必要的体感无感的字符串分布调整）

---

## 2. 不做什么（明确边界）

| 项 | 不做 | 理由 |
|----|------|------|
| 改包名 `com.hippo.ehviewer` → 新名 | ❌ | 无业务收益；纯机械替换反而触 git diff 噪声 |
| 删除 `ehviewer-core/.../client/` 平行副本 | ❌ | 平行副本虽冗余但被 web 后端依赖（compileOnly + runtimeOnly），删除会破坏 web 端编译。可由路径 C 的 jar 化间接覆盖 |
| 删除 `app/.../client/wifi/` LAN 跨设备下载 | ❌ | 仍可工作的功能，删之属过度瘦身 |
| 删除 `app/.../sync/` 同步任务 | ❌ | 与 SyncService 联动，删除需验证完整链路 |
| 删除 `mock-server/` | ❌ | 前端开发期辅助工具有保留价值，仅在 dev doc 引用、无 CI 依赖（详见 §7.1） |
| 改 `app/build.gradle` 的 `applicationId / namespace` | ❌ | 系统升级独立任务，与隐藏目标无关 |
| 改 i18n strings.xml 文案 | ❌ | 用户肉眼可见变更属功能改动，非隐藏 |

---

## 3. 实测基线（勘察事实）

### 3.1 LLM 工具配置现状

| 工具 | 仓内目录 | .gitignore 已忽略 | AGENTS.md 读取 |
|------|---------|------------------|---------------|
| opencode | `~/.config/opencode/opencode.jsonc`（空 config） | — | ✅（社区共识） |
| Claude Code | `.claude/` | ❌ **未忽略** | 部分 |
| Codex (whale) | `.codewhale/` | ✅ | 部分 |
| DeepSeek | `.deepseek/` | ✅ | ❌ |
| Mimocode | `.mimocode/` | ❌ **未忽略** | ❌ |
| Qwen Code | `.qwen/` | ✅ | ❌ |

仓库根**没有** `AGENTS.md` / `.cursorrules` / `CLAUDE.md`——这是给 LLM 工具下达全局 ignore 指令的统一入口**缺失**。

### 3.2 敏感文件分布（实测命中数）

```bash
# 含 'hentai' 字串的源文件
rg -li "hentai" app/src/main/java app/src/main/res 2>/dev/null | wc -l
    → 27

# 含 'hentai|gallery|favorite|torrent|...' 等关键字的 client/parser 单文件触发指数
rg -wci "hentai|gallery|favorite|torrent|archive|doujin|manga|parody|preview|uploader|rating|category" \
    app/src/main/java/com/hippo/ehviewer/client/parser/GalleryDetailParser.java
    → 31
```

`GalleryDetailParser.java` 是触发审核风险最高的单文件（HTML 反解析 + 大量标签字串）。

### 3.3 LLM 工具的可控点

opencode 全局没有 ignore 配置。CLI 用户最近装 opencode（昨天 22:38 提示存在 `~/.config/opencode`），尚未写过滤——这是最佳介入点。

### 3.4 删减候选实测

| 候选 | 实测结论 |
|------|---------|
| `daogenerator/` | `settings.gradle:18` 写 `include ':app', ':daogenerator'`。其产物（DAO java 文件）已被 git 跟踪在 `app/src/main/java/com/hippo/ehviewer/dao/` 中。生成器独立可执行，不参与主 build（依赖未挂主 app build）。**可删，影响=0**。 |
| `mock-server/` | 仅在 `docs/webui-parallel-execution.md` 被提及，无 CI 构建依赖、无 app/web build.gradle 引用。是前端 dev 期 mock 数据 server。**保留**（按需）／删除前与前端开发确认。 |
| `scripts/dev-run.sh` + `scripts/dev-stop.sh` | 纯开发期 shell，无构建依赖。**保留**——开发体验友好，删之无收益。 |
| `.claude/` `.codewhale/` `.deepseek/` `.mimocode/` `.qwen/` | LLM 工具遗留目录；部分已 ignore 部分未 ignore。可统一加 ignore + 物理删除。 |
| `deploy/` | Caddy 部署配置 + systemd unit。**保留**——运维必要。 |
| `contracts/` | 后端 / 前端 schema 契约文档。**保留**——Docs 必要。 |

---

## 4. 路径 A：LLM 工具 ignore（最先做，0 成本）

### 4.1 在仓库根新增 `AGENTS.md`

opencode / openai codex / Cursor / continue / aider 等多数 LLM 工具均会读取仓库根 `AGENTS.md` 作为统一指令入口。在仓库根写入这种文件后**所有遵循 community 约定的 LLM 工具都生效**——是隐藏路径中成本最低的一环。

写入内容模板：

```markdown
# AGENTS.md — AnotherViewer 仓库全局指令

> 给所有 LLM 工具代理阅读：本节定义默认工作范围与忽略清单。

## 默认工作原则

1. **本仓库分两层**：通用层（UI / web / 同步 / 设置 / 下载引擎）+ E-Hentai 业务层。
2. **除非用户明确指示**：不要主动读取、编辑、提及以下路径下的文件。这些路径含站点特定逻辑、R18 / adult 字串密集内容，易触发模型内容审核拦截。
3. **任务需要触及这些路径时**：先与用户确认；用户允许后才操作，并避免在总结/答复中复述文件内字串。

## 默认忽略路径（除非用户明确指示否则不要读取）

- `app/src/main/java/com/hippo/ehviewer/client/`
- `app/src/main/java/com/hippo/ehviewer/spider/`（直接调 EhEngine）
- `ehviewer-core/src/main/java/com/hippo/ehviewer/client/`
- `docs/superpowers/specs/2026-07-29-site-provider-plugin-system-design.md`（含 R18 字样论述）
- `docs/webui-progress.md`（含 §I5 R18 阻断事件叙述）
- `docs/webui-roadmap.md`
- `docs/superpowers/specs/2026-06-28-web-app-architecture-design.md`
- `docs/web-app-architecture.md`
- `docs/superpowers/plans/2026-06-28-web-app-phase{1,2,3,4,5}.md`（含 R18 字串历史叙述）

## 默认可读路径

- `web-frontend/src/` 全部
- `ehviewer-web/src/main/java/com/hippo/ehviewer/web/` 除 `service/DownloadService.kt` 与 `service/EhSessionManager.kt` 之外（这两个文件调 EhEngine，敏感词较少但仍触及）
- `app/src/main/java/com/hippo/ehviewer/ui/` 除 `ui/scene/gallery/` 之外的通用 UI
- `app/src/main/java/com/hippo/ehviewer/util/` `app/.../widget/` 通用工具
- 不含 E-Hentai 字串的文档（AGENTS.md 自身、README、FAQ、NOTICE、webui-parallel-execution.md 等）

## 询问优先

若任务范围含"画廊"、"R18"、"R18 Source"、"SiteProvider"、"Extension"等关键词，**先**询问用户是否需要解开上述忽略清单。

## 历史包袱说明

仓库历史 commit 中含 R18 字串；本指令仅约束工作目录文件读取，**不**追溯 git log。
```

### 4.2 opencode 全局 config

修改 `~/.config/opencode/opencode.jsonc`：

```jsonc
{
  "$schema": "https://opencode.ai/config.json",
  "instructions": [
    "AGENTS.md"
  ],
  "permissions": {
    "deny": [
      "Read(app/src/main/java/com/hippo/ehviewer/client/**)",
      "Read(app/src/main/java/com/hippo/ehviewer/spider/**)",
      "Read(ehviewer-core/src/main/java/com/hippo/ehviewer/client/**)",
      "Read(docs/superpowers/specs/2026-07-29-site-provider-plugin-system-design.md)",
      "Read(docs/webui-progress.md)",
      "Read(docs/webui-roadmap.md)",
      "Read(docs/web-app-architecture.md)",
      "Read(docs/superpowers/specs/2026-06-28-web-app-architecture-design.md)",
      "Read(docs/superpowers/plans/2026-06-28-web-app-phase*.md)"
    ]
  }
}
```

> 注：具体 deny 语法字段以 opencode 文档为准。如版本不支持 deny Read，则仅靠 AGENTS.md 文本提示+保留 file glob 拒绝。

### 4.3 各 LLM 工具专属 ignore 文件

部分工具不支持 AGENTS.md，需逐一配置：

| 工具 | 配置文件 | 操作 |
|------|---------|------|
| Claude Code | `.claude/settings.local.json` 加 `deny` 字段 | 已存在 permissions，扩展 deny 项 |
| Cursor | `.cursorrules`（无）/ 项目 settings | 写一份精简版本（仅忽略路径） |
| 其他 | 依赖 `.gitignore` 行为 | 见 §7.x |

### 4.4 路径 A 验证

```bash
# 1. 验证 AGENTS.md 写入
test -f AGENTS.md && wc -l AGENTS.md
# 2. 验证 opencode 配置
jq '.permissions.deny | length' ~/.config/opencode/opencode.jsonc
# 3. 启动 opencode / 一个 LLM 代理，让它"读 docs/webui-progress.md"
#    期望：工具回复"按 AGENTS.md 不读取该文件"，或拒绝读取
```

### 4.5 路径 A 的局限

- LLM 工具是否严格遵循 AGENTS.md 全靠实现者**自觉**——并非硬隔离
- 老版本工具不读 AGENTS.md 则完全无效
- 即使工具不读，**仍可能**通过 grep / shell 命令间接探到敏感内容
- 故路径 A 单独用**不够**，必须叠加路径 B、C

---

## 5. 路径 B：Gradle sourceSet 划分 `src/sensitive/`

### 5.1 思路

把 E-Hentai 业务代码物理位移到 `app/src/sensitive/java/` 子目录。Gradle 通过 `sourceSets` 把它编进主 APK（功能 0 回归）。但：

- **IDE 默认扫 `src/main`**——很多 IDE 不展开非主流 sourceSet
- **LLM 工具按文件系统路径搜索**——按 `src/main/java` 一类主流路径做 glob 时的 LLM agent 工具默认不展开 src/sensitive
- **grep 工具如 ripgrep** 仍能搜到——所以路径 B 单独用**也不够**

路径 B 的真正价值是：

1. 让 sensitive 文件"在视觉上"从主源码树抽离
2. 让 AGENTS.md 路径 glob 表达式更简单（一句 `app/src/sensitive/` 即可，不再罗列 client/spider 等子目录）
3. 减少主流开发工具默认扫到的概率

### 5.2 物理位移清单

物理位移到 `app/src/sensitive/java/com/hippo/ehviewer/`：

```
app/src/main/java/com/hippo/ehviewer/client/                       ❌ 70 文件
app/src/main/java/com/hippo/ehviewer/spider/                       ❌  3 文件
app/src/main/java/com/hippo/ehviewer/sync/                          ⚠️ 保留在 main/，仅含 4 文件 Tags sync，与 SyncService 关联紧密

↓ 迁入
app/src/sensitive/java/com/hippo/ehviewer/client/      ← 70 文件
app/src/sensitive/java/com/hippo/ehviewer/spider/      ←  3 文件
```

`ehviewer-core/.../client/` 平行副本**不动**——web 端编译依赖它，移位会让 web 端 import 路径失效。等路径 C 把它打 jar 后再统一处理。

### 5.3 包名保持不变

`com.hippo.ehviewer.client.*` namespace 完全不变——`sourceSets` 仅决定编译时搜哪个目录，与 namespace 无关。**所有 import 语句一字不改**，所有引用方一字不改。

### 5.4 app/build.gradle 调整

在 `android { }` 块内追加：

```gradle
android {
    // ... 现有配置 ...

    sourceSets {
        main {
            // 默认 src/main/java 不变
        }
        // 注意：sensitive 不是独立 sourceSet 名，而是 main 的扩展
        // 用如下写法把 src/sensitive/java 加到 main 的 java srcDirs
        main.java.srcDirs += ['src/sensitive/java']
        main.res.srcDirs += ['src/sensitive/res']   // 如有 R.string 类资源随敏感代码迁
        main.assets.srcDirs += ['src/sensitive/assets']
    }
}
```

> 替代写法（更直观）：直接 `sourceSets.main.java.srcDir 'src/sensitive/java'`——AGP 接受 List 字符串追加。

### 5.5 路径 B 验证

```bash
# 1. 整目录 mv
git mv app/src/main/java/com/hippo/ehviewer/client/ app/src/sensitive/java/com/hippo/ehviewer/client/
git mv app/src/main/java/com/hippo/ehviewer/spider/ app/src/sensitive/java/com/hippo/ehviewer/spider/
# 2. 修改 app/build.gradle 加 sourceSets
# 3. 编译验证
./gradlew :app:compileAppReleaseDebugJavaWithJavac :app:compileAppReleaseDebugKotlin
# 4. APK 装机验证（功能不变）
./gradlew :app:assembleAppReleaseDebug
adb install -t app/build/outputs/apk/appRelease/debug/app-appRelease-debug.apk
# 5. 验证 IDE / LLM 视图：
#    - 在 IDE "Project" 视图中不应在 src/main 看到敏感代码
#    - rg 在 src/main 不应再命中 client/parser/ 等关键文件
rg -l "hentai|gallery" app/src/main/java --type java --type kotlin
    # 期望命中大幅减少（只剩 widget/UI 等用文件的 import 行，可由路径 C 进一步处理）
```

### 5.6 路径 B 局限

- 仅搬源码位置，敏感文件本身一字未改；任何 grep 类工具仍能从 src/sensitive/ 命中
- AGP sourceSets 配置错误会导致 R 类生成路径错位——需逐次编译验证

---

## 6. 路径 C：parser + data 打预编译 jar

### 6.1 思路

将业务方判定"完善、无后续工作空间"的部分**编译成 jar 放进 `app/libs/`，源码从仓库移除**：

- 22 个 HTML 解析器（`client/parser/`）—— HTML 结构不变就不需改
- 21 个数据 POJO（`client/data/`）—— 数据类稳定
- 9 个异常类（`client/exception/`）—— 不变
- 5 个 wifi 协议（`client/wifi/`）—— LAN 跨设备下载协议稳定

**不**编译 jar（保留为源码进 src/sensitive/）：

- 9 个 engine 文件（`EhEngine`、`EhUrl` 等）—— 偶尔改 URL/Headers
- `EhApplication.java / EhDB.java / Settings.java` —— 与 app 生命周期紧耦合
- `EhCookieStore` —— 需后续微调

### 6.2 物理位移规划

```
路径 C 范围 = 路径 B 的 sensitive 子集
↓
app/src/sensitive/java/com/hippo/ehviewer/client/{parser,data,exception,wifi}/
↓
最终打进预编译 jar：app/libs/ehentai-primitives.jar
源码从仓库删除（保留 git 历史，需要时 git checkout 历史可恢复）
```

`engine/`、`EhApplication` 等保留在 `src/sensitive/java/`。

### 6.3 jar 生成步骤

新增一个轻量 Gradle 任务——保留在一个 **build-time-only** 的 builders 模块，避免污染主 build：

```gradle
// app/build.gradle 在 dependencies 块前追加
// 这个 jar 一次性生成后 git 提交到 app/libs/，之后看git历史可重建
task buildEhentaiPrimitivesJar(type: Jar) {
    archiveBaseName = 'ehentai-primitives'
    archiveVersion = '0.1.0'
    // 仅包含以下包
    from('src/sensitive/java') {
        include 'com/hippo/ehviewer/client/parser/**'
        include 'com/hippo/ehviewer/client/data/**'
        include 'com/hippo/ehviewer/client/exception/**'
        include 'com/hippo/ehviewer/client/wifi/**'
    }
    // 与 R.string 等资源的相关引用打包处置（见 §6.5）
    destinationDirectory = file("$rootDir/app/libs")
}
```

### 6.4 steps 执行顺序

1. **完成路径 B 位移** → app/src/sensitive/ 含 70 文件
2. 运行一次：`./gradlew :app:buildEhentaiPrimitivesJar`（生成 `app/libs/ehentai-primitives-0.1.0.jar`）
3. app/build.gradle `dependencies` 追加 `implementation files('libs/ehentai-primitives-0.1.0.jar')`
4. **删除源码**：`git rm -r app/src/sensitive/java/com/hippo/ehviewer/client/{parser,data,exception,wifi}/`
5. **build.gradle sourceSet**：保留 `src/sensitive` 但其中只剩 `engine/`、`EhApplication`、`Settings` 等少量文件
6. 编译验证

> 决策点：jar 内部是否暴露 source jar 给调试器看？**不暴露**。LLM 工具不读 .class，IDE 出错时栈里是 `EhEngine.xxx(Unknown Source)`——开发者可临时 unzip jar 反查，或历史 commit git checkout；但 LLM 看 .class 内容时按字节方式不触发文本审核。

### 6.5 R 类引用处理

`GalleryListParser.java` 等会引用 `R.string.error_xxx`。R 在编译时是 Android namespace 的 final int 常量——若 jar 内 .class 引用了 `com.hippo.ehviewer.R$string.error_parse_error`，jar 跨 APK 引用时（同 namespace）能 resoolve旅客。

但若 users 工具不承认 R cross-module，做法：

- 把 `app/src/main/java/com/hippo/ehviewer/R.java`（stub）里 7 个 stub string id 也包含进 jar。
- app 自己生成的 R（AGP 生成）会与 jar 内 stub 同 package 但应不被命中：因 R 的字段是 final int，跨 jar 跨 APK 同字段值需一致——AAPT2 处理同 namespace 时只生成一份，不分apk jar 不重定义。

> 实测门：跑 `:app:compileAppReleaseDebugJavaWithJavac` 过即可确认。

### 6.6 路径 C 验证

```bash
./gradlew :app:assembleAppReleaseDebug
adb install -t app/build/outputs/apk/appRelease/debug/app-appRelease-debug.apk
adb shell am start -n com.xjs.ehviewer.debug/com.hippo.ehviewer.ui.MainActivity
# 真机烟测：
# - 进首页 → 看到列表（fetchGalleryList → parser 在 jar 中能跑）
# - 进画廊 → 看到详情（GalleryDetailParser 在 jar 中能跑）
# - 进下载 → 第一页图片能拉（GalleryPageParser 在 jar 中能跑）

# 仓库扫描验证
rg -l "hentai|gallery|hentai\.org" app/src --type java --type kotlin
    # 期望命中减至 < 10（仅剩 engine/、EhApplication 等少数源码）
rg -l "hentai|gallery" app/libs/
    # 期望命中数：0（jar 是二进制，rg 默认不查二进制内容）
```

### 6.7 路径 C 局限

- 修改 parser/data 时需重跑 buildEhentaiPrimitivesJar，再 commit jar——开发流程多一步
- jar 是二进制，code review 时 reviewer 看不到 .java diff——需在 commit message 中说明重打理由
- jar 体积 ~数百 KB，git LFS 不需要（小）
- Kotlin/Java 混编时 jar 须含 `.class` 文件，对应 metadata（kotlin metadata）一并打包——AGP 默认处理

---

## 7. 删减清单

按用户选择"仅删外部辅助/废弃 LLM 工具目录"——仅下面 4 类。

### 7.1 `scripts/dev-run.sh` 与 `scripts/dev-stop.sh` 

**保留**——开发期便利脚本，无负担，无收益删之。仅作为回顾参考列在这。

> 修正：经实测这两个脚本只是 `./gradlew :app:assembleXxx && java -jar ...` 的组合，被并行执行 doc 引用一次。**保留**。

### 7.2 `daogenerator/` 模块

实测：

- `settings.gradle:18` `include ':app', ':daogenerator'`
- 产物（DAO java 文件）在 `app/src/main/java/com/hippo/ehviewer/dao/`
- `daogenerator/` 自身不参与主 build（app 不依赖它）
- 删除影响 = 0；后续若需要重生成 DAO 删了的代码无法自动重生。

**操作**：

```bash
# 1. settings.gradle 移除 ':daogenerator'
# 2. git rm -r daogenerator/
# 3. 提交时记 commit msg：
#    "chore: 移除 daogenerator 模块（产物已在 app/.../dao/ 中，多数 DAO 已手改不再生成）"
```

### 7.3 LLM 工具遗留目录

仓库根 5 个 LLM 工具配置目录：

| 目录 | 当前状态 | 处置 |
|------|---------|------|
| `.claude/` | git 未忽略，已 tracked | 加入 .gitignore + `git rm -r --cached .claude/`（保留本地文件） |
| `.codewhale/` | git 已忽略 | 无需操作 .gitignore；本地可删可留 |
| `.deepseek/` | git 已忽略 | 同上 |
| `.mimocode/` | git 未忽略（未 ignore 也未 tracked 或部分 tracked） | 加入 .gitignore + `git rm -r --cached .mimocode/` |
| `.qwen/` | git 已忽略 | 同上 |

**统一操作**：

```bash
# 在 .gitignore 中追加（与已有 LLM Tools 块合并）：
cat >> .gitignore <<'EOF'

# LLM tools (unified ignore)
.claude/
.codewhale/
.deepseek/
.mimocode/
.qwen/
EOF

# 移除可能曾被 tracked 的缓存
git rm -r --cached .claude/ 2>/dev/null
git rm -r --cached .mimocode/ 2>/dev/null

# 本地目录按用户意愿清理（不强制）：
# rm -rf .claude .codewhale .deepseek .mimocode .qwen
```

### 7.4 `mock-server/` 保留理由

实测仅被 `docs/webui-parallel-execution.md` 一处文档引用，无 CI / `package.json` / `build.gradle` 主链路引用。其本身是 node http server，为前端 dev 期 mock API 提供 fixtures，与主构建产物解耦。**保留**——前端开发期仍有用，删之无瘦身收益（不编进 APK 也不影响 CI 时间）。

---

## 8. 阶段编排

每阶段独立可回滚；建议每阶段一 commit。

### Phase A — AGENTS.md + 各 LLM 工具 ignore 配置（路径 A，0 代码改动）

**目标**：让所有遵循 community 约定的 LLM 工具默认不读敏感路径。

**操作**：

1. 在仓库根新增 `AGENTS.md`（按 §4.1 模板）
2. 修改 `~/.config/opencode/opencode.jsonc`（按 §4.2）
3. 在 `.claude/settings.local.json` 加 deny 字段（按 §4.3）
4. 在 `.gitignore` 追加 `.claude/` 等条目（顺手完成 §7.3）

**验证**：

```bash
test -f AGENTS.md && wc -l AGENTS.md                        # ≥ 30 行
jq '.permissions.deny | length' ~/.config/opencode/opencode.jsonc  # ≥ 9
rg "^\.claude/" .gitignore                                  # 命中
```

**回滚**：`git revert` + `rm ~/.config/opencode/opencode.jsonc`。

**预期效果**：LLM 工具在开发其他组件时，默认展开文件清单不再含 `client/`、`spider/`、及相关 docs。**但** ripgrep 类工具仍能命中——故 Phase B/C 仍需做。

### Phase B — Gradle sourceSet 位移（路径 B）

**目标**：把 `client/` + `spider/` 物理位移到 `src/sensitive/`；功能零回归。

**子步**：

1. `mkdir -p app/src/sensitive/java/com/hippo/ehviewer`
2. `git mv app/src/main/java/com/hippo/ehviewer/client/ app/src/sensitive/java/com/hippo/ehviewer/client/`
3. `git mv app/src/main/java/com/hippo/ehviewer/spider/ app/src/sensitive/java/com/hippo/ehviewer/spider/`
4. 编辑 `app/build.gradle`：在 `android { }` 块内追加 `sourceSets` 配置（按 §5.4）
5. 编译验证

**验证**（构建门）：

```bash
./gradlew :app:compileAppReleaseDebugJavaWithJavac :app:compileAppReleaseDebugKotlin
./gradlew :app:assembleAppReleaseDebug
adb install -t app/build/outputs/apk/appRelease/debug/app-appRelease-debug.apk
adb shell am start -n com.xjs.ehviewer.debug/com.hippo.ehviewer.ui.MainActivity
# 真机烟测：能进首页、能浏览、能登录、能下载首页图片
```

**仓库扫描门**：

```bash
rg -l "hentai|exhentai|gallery" app/src/main/java --type java --type kotlin | wc -l
    # < 10（下降但仍有命中，由 Phase C 进一步处理）
```

**回滚**：

```bash
git mv app/src/sensitive/java/com/hippo/ehviewer/client/ app/src/main/java/com/hippo/ehviewer/client/
git mv app/src/sensitive/java/com/hippo/ehviewer/spider/ app/src/main/java/com/hippo/ehviewer/spider/
# build.gradle git checkout
```

### Phase C — parser + data + exception + wifi 打 jar（路径 C）

**目标**：把"完善、无后续工作空间"的部分打包为预编译 jar，源码从仓库删除。

**子步**：

1. 在 `app/build.gradle` 追加 `buildEhentaiPrimitivesJar` Gradle 任务（§6.3）
2. 跑 `./gradlew :app:buildEhentaiPrimitivesJar`，产出 `app/libs/ehentai-primitives-0.1.0.jar`
3. 复查 jar 内容：`unzip -l app/libs/ehentai-primitives-0.1.0.jar | rg "Parser|data" | wc -l` ——期望命中 ≥ 60（22+21+9+5+杂项）
4. app/build.gradle `dependencies` 块追加 `implementation files('libs/ehentai-primitives-0.1.0.jar')`
5. 删 jar 范围源码：
   ```bash
   git rm -r app/src/sensitive/java/com/hippo/ehviewer/client/parser/
   git rm -r app/src/sensitive/java/com/hippo/ehviewer/client/data/
   git rm -r app/src/sensitive/java/com/hippo/ehviewer/client/exception/
   git rm -r app/src/sensitive/java/com/hippo/ehviewer/client/wifi/
   ```
6. app/build.gradle `buildEhentaiPrimitivesJar` 任务**保留**（后续若需重建 jar）但默认构建流程不跑

**验证**（构建门）：

```bash
./gradlew :app:assembleAppReleaseDebug
adb install -t app/build/outputs/apk/appRelease/debug/app-appRelease-debug.apk
# 真机烟测：登录 → 浏览 → 进画廊 → 看详情 → 下载一页
#           （parser 类全在 jar 中，必测路径）

# 仓库扫描门
rg -l "hentai|exhentai|gallery|hentai\.org" app/src --type java --type kotlin | wc -l
    # 期望 < 10
rg -l "hentai|gallery" app/libs/
    # 期望 0（jar 是二进制）
```

**回滚**：

```bash
# 1. 从 git 历史 checkout 删了的源码
git checkout HEAD~1 -- app/src/sensitive/java/com/hippo/ehviewer/client/
# 2. app/build.gradle 删 implementation files('libs/...')
# 3. （可选）git rm app/libs/ehentai-primitives-0.1.0.jar
```

### Phase D — daogenerator 删除（删减 §7.2）

**操作**：

1. 编辑 `settings.gradle`：把 `:daogenerator` 从 `include` 行删去
2. `git rm -r daogenerator/`

**验证**：

```bash
./gradlew settings                                              # 应不再提示 daogenerator
./gradlew :app:compileAppReleaseDebugJavaWithJavac              # PASS（DAO 产物在 app/.../dao/，不依赖生成器）
./gradlew :ehviewer-web:compileKotlin                           # PASS
```

**回滚**：

```bash
git revert HEAD
# settings.gradle 或恢复 :daogenerator；daogenerator/ 目录从 git 历史恢复
```

### Phase E — LLM 工具遗留目录清理（删减 §7.3）

**操作**：

1. 在 `.gitignore` 追加 `.claude/`、`.mimocode/` 等条目（§7.3 已给）
2. 已 tracked 的执行 `git rm -r --cached .claude/ .mimocode/`（如适用）
3. commit 提示 msg：
   > "chore: 统一 ignore LLM 工具遗留目录（.claude/.codewhale/.deepseek/.mimocode/.qwen），不影响主构建"

**验证**：

```bash
git status                                                    # 无 .claude/ 文件可见被改
rg "^\.(claude|codewhale|deepseek|mimocode|qwen)/" .gitignore # 命中 5 行
./gradlew :app:assembleAppReleaseDebug                       # PASS（应无变化）
```

**回滚**：`git revert`。

### Phase F — 真机全功能烟测 + 长期检查清单

**目标**：在 Phase A-E 全部合并后做一次完整功能验证。

**真机烟测清单**：

- [ ] 安装 APK 后能启动
- [ ] 首页显示列表（parser jar 工作）
- [ ] 顶部分类 tab 能切换
- [ ] 搜索栏能搜索
- [ ] 进画廊详情（GalleryDetailParser jar 工作）
- [ ] 评论列表显示（VoteCommentParser jar 工作）
- [ ] 收藏 toggle（FavoritesParser jar 工作）
- [ ] 进 Reader 看图（GalleryPageParser jar 工作）
- [ ] 下载触发（TorrentParser jar 工作，如有 torrent）
- [ ] 设置 → 站点切换 e-hentai↔exhentai
- [ ] 登录 → 退出 → 重登（SignInParser jar 工作）
- [ ] 历史记录写入与显示（app 主流程工作）

**长期检查清单**（月度）：

- [ ] rg 在 app/src/main/java 命中 "hentai" 文件数 ≤ 10（小于阈值即满足隐藏目标）
- [ ] rg 在 app/libs/ 命中 "hentai" 0 行（jar 二进制命中应为 0）
- [ ] AGENTS.md 仍存在
- [ ] opencode / codex / Claude Code 任一能正确解读 AGENTS.md path deny

**回滚**：本阶段无 code 改动，无需回滚。

---

## 9. 全套验证矩阵

| 阶段 | 命令 | 期望 |
|------|------|------|
| Phase A | `test -f AGENTS.md && jq '.permissions.deny\|length' ~/.config/opencode/opencode.jsonc` | 文件存在 + ≥9 deny 项 |
| Phase B | `./gradlew :app:assembleAppReleaseDebug` + 真机烟测 6 项 | PASS |
| Phase B | `rg -l "hentai\|gallery" app/src/main/java --type java --type kotlin \| wc -l` | < 10 |
| Phase C | `./gradlew :app:assembleAppReleaseDebug` + 真机烟测全 12 项 | PASS |
| Phase C | `rg -l "hentai\|gallery" app/src --type java --type kotlin \| wc -l` | < 10 |
| Phase C | `rg -l "hentai\|gallery" app/libs/ \| wc -l` | 0 |
| Phase D | `./gradlew settings`（输出无 daogenerator）+ `:app:compile...` PASS | OK |
| Phase E | `rg "^\.claude/^\.codewhale/..." .gitignore` | 命中 5 行 |
| Phase E | `./gradlew :app:assembleAppReleaseDebug` PASS | OK |
| Phase F | 真机全 12 项烟测 | 全 PASS |

**LLM 安全断言（Phase C 后必过）**：

```bash
# 模拟"LLM 仅加载本体仓库"时 rg 能命中的 R18 关键字文件数
rg -li "hentai|exhentai|R18|adult|doujin" \
   app/src \
   ehviewer-web/src \
   web-frontend/src \
   docs \
   --type java --type kotlin --type md --type vue --type ts \
   --glob '!*.lock' --glob '!**/node_modules/**' \
   --glob '!app/libs/**' \
   --glob '!docs/superpowers/**' | wc -l
# 期望 < 20（Phase A-E 后敏感文件数应该骤降；docs/superpowers/** 由 AGENTS.md 阻止 LLM 读取）
```

---

## 10. 回滚与风险

| 风险 | 严重度 | 缓解 |
|------|--------|------|
| Phase B sourceSets 配置错乱导致 R 类生成路径偏移 | 中 | Phase B 全量 APK 构建门 + 真机烟测首页能看到列表（足以覆盖 R 资源命中） |
| Phase C jar 内 R 引用与 AGP 生成 R 字段值不一致 | 中 | Phase C 构建门 `:app:compileAppReleaseDebugJavaWithJavac` 必须 PASS；若 v 失败，回退方案：将 stub `R.java` 也编入 jar，或恢复某 parser 为源码 |
| Phase C 删除源码后 jar 内类反编译不易调试 | 低 | 调试时临时 `git checkout HEAD~2 -- app/src/sensitive/java/com/hippo/ehviewer/client/parser/`；或 jar 中 unzip 反编译 |
| LLM 工具不读 AGENTS.md 仍命中内容 | 中 | 隐藏三路径叠加减弱；但 ripgrep 等无法被路径列表隔离；用户可针对具体工具再配 ignore |
| 用户希望改 parser 一行字串修复 bug 但不知如何重建 jar | 中 | 在 AGENTS.md 中明确"若需修改 client/parser/、client/data/、client/exception/、client/wifi/ 下的 jar 化文件，先 `git checkout <历史>` 取回源码，再 `./gradlew :app:buildEhentaiPrimitivesJar` 重生 jar，最后 `git rm` 源码" |
| 平行副本 `ehviewer-core/.../client/` 仍存于源码树 | 中 | 等 web 端独立 Stage 处理；本规划不动 |
| 删除 `.claude/` tracked 状态致使遗留开发者本地环境与远端不同步 | 低 | .gitignore 加入后本地文件保留；仅 git 不再跟踪；开发者本地 Claude Code 仍可工作 |

---

## 11. 决策清单

| ID | 决策项 | 取值 | 出处 |
|----|--------|------|------|
| D1 | 是否为 E-Hentai 设计 SPI | ❌ 不做 | §1.3 / 用户判断"已相当完善" |
| D2 | 是否拆 Gradle 模块 | ❌ 不拆 | §1.3 / "不动架构" |
| D3 | 是否改包名 | ❌ 不改 | §2 / 无业务收益 |
| D4 | 主隐藏手段 | 三路径叠加（AGENTS.md + sourceSet 位移 + jar 化） | §4 + §5 + §6 / 用户选择 |
| D5 | 删减范围 | 仅外部辅助 + 废弃 LLM 工具目录 | §7 + 用户选择 |
| D6 | `mock-server/` | 保留（前端开发期辅助，无 CI 依赖） | §7.4 |
| D7 | `daogenerator/` | 删除（产物在 app/.../dao/） | §7.2 |
| D8 | `app/.../client/wifi/` + `app/.../syn/` | 保留 | §2 / 仍可工作不删 |
| D9 | jar 化范围：哪些打 jar | parser / data / exception / wifi（路径 C） | §6.1 |
| D10 | jar 化保留为源码 | engine / EhApplication / EhDB / Settings / EhCookieStore | §6.1 |
| D11 | R.java 7 个 stub string 处理 | 留在 main/ 由 AGP 生成正式 R；jar 编译时 reference 这次 stub id | §6.5 |
| D12 | jar 是否包含 source jar 给 IDE 调试 | ❌ 不含（保证 LLM 看不到内容） | §6.4 |
| D13 | 平行副本 `ehviewer-core/.../client/` | 不动，留作后续 Stage 决定 | §2 / 与 web 端编译耦合 |

---

## 12. 一次性 Todo 列表

```
# Phase A — AGENTS.md + LLM 工具 ignore（半天）
[ ] 在仓库根新增 AGENTS.md（按 §4.1 模板）
[ ] 修改 ~/.config/opencode/opencode.jsonc（按 §4.2 加 deny）
[ ] 在 .claude/settings.local.json 加 deny 字段（按 §4.3）
[ ] 验证：test -f AGENTS.md && wc -l ≥ 30
[ ] commit: "docs: 新增 AGENTS.md 与各 LLM 工具 ignore 配置，默认不读 E-Hentai 代码"

# Phase B — sourceSet 位移（1 天）
[ ] mkdir -p app/src/sensitive/java/com/hippo/ehviewer
[ ] git mv app/.../client/ → app/src/sensitive/java/.../client/
[ ] git mv app/.../spider/ → app/src/sensitive/java/.../spider/
[ ] app/build.gradle 追加 sourceSets main.java.srcDirs += [src/sensitive/java]
[ ] ./gradlew :app:compileAppReleaseDebugJavaWithJavac PASS
[ ] ./gradlew :app:assembleAppReleaseDebug PASS + 装机烟测 6 项
[ ] 验证：app/src/main/java 命中 "hentai" 数 < 10
[ ] commit: "refactor: E-Hentai 代码位移至 src/sensitive/，sourceSets 编入 main"

# Phase C — parser + data 打 jar（2-3 天）
[ ] app/build.gradle 追加 buildEhentaiPrimitivesJar Jar 任务
[ ] ./gradlew :app:buildEhentaiPrimitivesJar 生成 app/libs/ehentai-primitives-0.1.0.jar
[ ] 复查 jar 内容 unzip -l ≥ 60 类
[ ] app/build.gradle dependencies 追加 implementation files('libs/...')
[ ] git rm -r app/src/sensitive/java/com/hippo/ehviewer/client/{parser,data,exception,wifi}/
[ ] ./gradlew :app:assembleAppReleaseDebug PASS + 真机全 12 项烟测
[ ] 验证：rg app/src/main/java 命中 < 10；rg app/libs/ 命中 0
[ ] commit: "build: E-Hentai parser/data/exception/wifi 编入预编译 jar，源码归档"

# Phase D — daogenerator 删除（半天）
[ ] settings.gradle 移除 ':daogenerator'
[ ] git rm -r daogenerator/
[ ] ./gradlew settings 无 daogenerator
[ ] ./gradlew :app:compileAppReleaseDebugJavaWithJavac 全 PASS
[ ] commit: "chore: 移除 daogenerator 模块"

# Phase E — LLM 工具遗留目录清理（半天）
[ ] .gitignore 追加 .claude/ .codewhale/ .deepseek/ .mimocode/ .qwen/
[ ] git rm -r --cached .claude/ .mimocode/（如适用）
[ ] git status 验证
[ ] commit: "chore: 统一 ignore LLM 工具遗留目录"

# Phase F — 真机全功能烟测 + 月度检查
[ ] 安装 APK + 12 项烟测全 PASS
[ ] 月度检查清单写入 AGENTS.md（可选）
[ ] PR 合并
[ ] docs/webui-progress.md 增记 §I5 收尾：隐藏+瘦身已完成，不再阻塞 LLM 开发其他组件
```

---

## 13. 一行总结

> 不动架构｜不为 E-Hentai 设计 SPI｜三路径叠加隐藏（AGENTS.md + src/sensitive/ sourceSet + parser/data 打 jar）｜仅删外部辅助/废弃 LLM 工具目录｜Phase A-E 串行可执行｜真机全 12 项烟测为最终门｜单 commit revert 即可回滚。