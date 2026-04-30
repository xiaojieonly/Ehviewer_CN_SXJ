# PR Stack Plan (2026-04-30)

## 基线与对比
- 基线分支: upstream/BiLi_PC_Gamer
- 本地主线: BiLi_PC_Gamer
- 独有提交: 63 (含 merge)
- 最终文件差异: 大范围跨模块 (task/download/ui/layout/scripts/dao)
- 可直接 cherry-pick 的非 merge 提交: 2
  - 5974c5bd (Miui 后台下载优化)
  - 562ffa16 (pre-release workflow)

## 已提交 Draft PR（当前栈）
1. #2545 fix(ci): 恢复 Gradle Wrapper 文件（底层 root）
2. #2548 chore(ci): 新增预发布自动构建工作流（依赖 #2545）
3. #2549 feat(download-core): 小米系统后台下载优化与通知增强（依赖 #2545）

## 细粒度分层规划（下一步）

### Layer 0: CI/Workflow 基础
- PR-0.1 (#2545): wrapper 修复
- PR-0.2 (#2548): 预发布 workflow

### Layer 1: 后台任务基础设施（需文件级重放）
- PR-1.1 Task 基类与执行器骨架
  - 文件目标:
    - app/src/main/java/com/hippo/ehviewer/task/BackgroundTask.kt
    - app/src/main/java/com/hippo/ehviewer/task/TaskExecutor.kt
    - app/src/main/java/com/hippo/ehviewer/task/TaskExecutionInfo.kt
- PR-1.2 后台管理器与运行器
  - 文件目标:
    - app/src/main/java/com/hippo/ehviewer/BackgroundTaskManager.java
    - app/src/main/java/com/hippo/ehviewer/task/BackgroundTaskRunner.kt
- PR-1.3 UI 状态与任务列表
  - 文件目标:
    - app/src/main/java/com/hippo/ehviewer/ui/task/BackgroundTaskInfo.java
    - app/src/main/java/com/hippo/ehviewer/ui/task/BackgroundTaskStatusManager.java
    - app/src/main/java/com/hippo/ehviewer/ui/task/BackgroundTaskAdapter.java

### Layer 2: 下载链路（建立在 Layer 1 上）
- PR-2.1 DownloadManager 接入后台任务
  - 文件目标:
    - app/src/main/java/com/hippo/ehviewer/download/DownloadManager.java
- PR-2.2 DownloadService 保活与通知细化
  - 文件目标:
    - app/src/main/java/com/hippo/ehviewer/download/DownloadService.kt
    - app/src/main/AndroidManifest.xml
- PR-2.3 下载列表缓存预热
  - 文件目标:
    - app/src/main/java/com/hippo/ehviewer/ui/scene/download/part/DownloadAdapter.java

### Layer 3: 重复画廊合并能力
- PR-3.1 合并任务核心
  - 文件目标:
    - app/src/main/java/com/hippo/ehviewer/task/MergeDuplicateGalleryTask.kt
- PR-3.2 下载完成触发合并
  - 文件目标:
    - app/src/main/java/com/hippo/ehviewer/download/DownloadManager.java
- PR-3.3 设置项与偏好入口
  - 文件目标:
    - app/src/main/java/com/hippo/ehviewer/Settings.java
    - app/src/main/java/com/hippo/ehviewer/preference/MergeDuplicateGalleryPreference2.java
    - app/src/main/res/xml/download_settings.xml

### Layer 4: UI 搜索与筛选
- PR-4.1 CheckboxAdapter 与筛选控件
  - 文件目标:
    - app/src/main/java/com/hippo/ehviewer/ui/scene/download/part/CheckboxAdapter.java
    - app/src/main/res/layout/item_checkbox.xml
    - app/src/main/res/layout/dialog_sort_filter.xml
- PR-4.2 下载搜索对话框统一
  - 文件目标:
    - app/src/main/java/com/hippo/ehviewer/ui/scene/download/DownloadsScene.java
    - app/src/main/res/layout/dialog_sort_filter_v2.xml
    - app/src/main/res/layout/download_search_dialog_v2.xml

## 提交策略
- 先保底层：#2545 -> #2548 -> #2549
- 再按 Layer 1 -> Layer 2 -> Layer 3 -> Layer 4
- 每个 PR 仅允许单一职责和最小文件集；跨域文件移出下一 PR
- 对混合提交采用“从 BiLi_PC_Gamer 按文件 checkout + 手工编译修正”而非整提交 cherry-pick

## 风险提示
- 63 个提交中 46 个非 merge 提交无法直接落地（冲突），必须文件级重放
- DownloadManager/DownloadsScene 属高冲突文件，需后置并最小化每次改动块
