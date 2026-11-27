# 数据库导入：新增「覆盖导入」模式

## 背景与问题

EhViewer 的「设置 → 高级 → 导入数据」原本只有**合并**语义：导入一个数据库时，
对于已存在的记录（按主键判断）一律**跳过**，导致：

- 在电脑上迁移漫画、修改了标题/标签/标签分组等信息后，重新导入数据库，
  **同 GID 的旧记录纹丝不动**，新信息无法生效；
- 唯一的变通办法是**卸载 App 再导入**（让数据库回到空表状态再灌入），非常麻烦。

根因在导入逻辑里：

- `DownloadManager.addDownload(List)`：`if (containDownloadInfo(info.gid)) continue;`
  —— 同 GID 直接跳过。
- `EhDB.importDB(...)` 中 `GALLERY_TAGS` 也是「不存在才插入」。

## 方案

新增一个**可选的「覆盖导入」模式**，与原「合并导入」并存，由用户在导入时选择：

- **合并（保留现有记录）**：完全等同旧行为，默认、零风险。
- **覆盖（以导入数据为准）**：同 GID 的下载记录与画廊标签用导入数据**替换**，
  效果等价于「卸载重装 + 导入」，但**不会删除本地已下载的图片文件**。

## 修改清单

### 1. `app/src/main/java/com/hippo/ehviewer/download/DownloadManager.java`

- 保留原 `addDownload(List<DownloadInfo>)`，改为委托调用新方法 `addDownload(list, false)`。
- 新增 `addDownload(List<DownloadInfo>, boolean overwrite)`：
  - `overwrite == false`：同 GID 跳过（旧行为）。
  - `overwrite == true`：先调用 `removeDownloadFromMemory(gid)` 移除旧的内存记录，
    再走正常添加流程；下方 `EhDB.putDownloadInfo(info)` 为 insert-or-update，
    会刷新数据库行。**磁盘上的图片文件不受影响。**
- 新增私有方法 `removeDownloadFromMemory(long gid)`：仅清理内存结构
  （`mAllInfoList` / `mAllInfoMap` / `mWaitList` / 对应 label 列表），
  **不删除文件、不删除数据库行**（由后续 put 覆盖）。

### 2. `app/src/main/java/com/hippo/ehviewer/EhDB.java`

- `importDB(Context, File, Handler)` 改为委托 `importDB(context, file, handler, false)`，
  保持对旧调用方的兼容。
- 新增 `importDB(Context, File, Handler, boolean overwrite)`：
  - 下载记录：`manager.addDownload(downloadInfoList, overwrite)`。
  - 画廊标签 `GALLERY_TAGS`：`overwrite == true` 时按 GID 查询已有记录、删除后重新插入，
    使修改过的标签信息生效；`overwrite == false` 时维持「不存在才插入」。
  - 其余表（dirname 本就是 insert-or-update、history / quicksearch / 收藏 / filter /
    blacklist）行为不变。

### 3. `app/src/main/java/com/hippo/ehviewer/ui/fragment/AdvancedFragment.java`

- 选择数据库文件后，新增二级对话框 `chooseImportMode(...)` 让用户选择
  「合并 / 覆盖」。
- `showProgress(...)` 增加 `boolean overwrite` 参数并透传给 `EhDB.importDB(...)`。

### 4. 字符串资源

- `app/src/main/res/values/strings.xml`（英文）
- `app/src/main/res/values-zh-rCN/strings.xml`（简体中文）

新增三条：`import_mode_title`、`import_mode_merge`、`import_mode_overwrite`。

## 行为对照

| 数据 | 合并（旧/默认） | 覆盖（新增） |
|------|----------------|--------------|
| 下载记录（同 GID 标题/标签/状态/分组） | 跳过，旧值保留 | 用导入值替换 |
| 下载目录名 DIRNAME | 覆盖（原本即是） | 覆盖 |
| 画廊标签 GALLERY_TAGS | 不存在才插入 | 按 GID 替换 |
| 历史 / 快速搜索 / 本地收藏 / 过滤 / 黑名单 | 合并 | 合并（不变） |
| 本地已下载图片文件 | 不动 | **不动**（仅改数据库记录） |

## 安全性说明

- 「合并」为默认项，旧用户路径零变化。
- 「覆盖」只触及数据库记录，**绝不删除已下载的漫画图片**，因此即使误选也不会丢图，
  大不了再导入一次正确的数据库即可恢复记录。
- 建议覆盖导入前仍按惯例先 `导出数据` 备份一次。
