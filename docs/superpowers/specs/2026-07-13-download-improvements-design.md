# 下载功能改造设计（2026-07-13）

## 背景与问题

三个问题，均从第一性原理分析根因后给出方案：

1. **画廊更新导致重复下载**：E-Hentai 的"画廊更新"是产生新 gid/token 的独立画廊。当前实现里新旧版本是两个完全独立的下载项，各自从零全量下载，磁盘上产生两份几乎相同的图片。
2. **下载列表 >1000 条时增删卡顿**：根因三重叠加——
   - `EhDB` 所有写库在调用线程（主线程）同步执行，且 `putDownloadInfo` 每条先 SELECT 再 INSERT（2 次往返）；批量删除是 for 循环逐条 `removeDownloadInfo`，N 条 = N 次主线程同步 DB。
   - 内存结构是 `LinkedList`，`remove(info)`/`indexOf(info)` 均 O(n)，批量删除 O(N×n)。
   - `addDownload(List)` 每加一条就 `Collections.sort` 一次 label 列表，结束后再全表重排。
3. **缺少"未完成"筛选**：下载画廊过多时无法只看未完成项。现有 `DownloadListInfosExecutor` 已支持按单一状态异步筛选（已完成/未开始/等待中/下载中/已失败），唯独没有"未完成"（= 非 FINISH 的并集）。

## 方案

### ① 画廊更新检测 + 增量更新下载

**核心洞察**：`SpiderQueen` 下载时通过 `SpiderDen.contain(index)` 检查本地 `%08d.ext` 文件是否存在来跳页（SpiderQueen.java:1643-1647）。只要把旧版本已下载的图片文件放进新版本的下载目录，下载新版本时就会自动跳过这些页、只下载新增页——增量下载不需要改下载引擎，只需要做一次文件迁移。

仓库中已有未接线的死代码可复活：`GalleryDetailScene.startUpdateDownload(url)`（RESULT_UPDATE 请求路径）和 `DownloadManager.replaceInfo(new, old)`（用新画廊替换下载列表中的旧项）。

**行为设计**：

1. **更新检测**：`GalleryDetailScene.onGetGalleryDetailSuccess` 中，若该画廊在下载列表中、状态为 `STATE_FINISH`、且 `newVersions != null` → 调用 `DownloadManager` 把状态改回 `STATE_NONE`（持久化 + 通知列表刷新）。详情页已有"有新版本"角标，保持不变。
2. **增量更新下载**：`onDownload()` 中，若画廊已在下载列表且 `newVersions != null` → 弹确认对话框（更新到最新版本/取消）。确认后取 `newVersions` 最后一项（最新版），走 `startUpdateDownload(versionUrl)` 获取新版详情，成功回调中执行迁移：
   - `SpiderDen.getGalleryDownloadDir(old)` → 将旧目录中的图片文件移动到新画廊的下载目录（不迁移 `.ehviewer`，其 gid/token/pTokenMap 必须由新画廊重新生成）；
   - 迁移 `DownloadDirname` 映射与标签；
   - `DownloadManager.replaceInfo(newInfo, oldInfo)`：下载列表中原位替换（保留 label、time），删除旧 DB 记录、写入新记录；
   - 对新 gid 调 `startDownload` → SpiderQueen 按文件存在性跳过旧页，只下载新增/缺失页。
3. **文件操作在 IO 线程**执行，完成后回主线程刷新。

**权衡说明**：按页序号复用旧图片。E-H 更新以追加页为主；若更新替换了旧页内容，该页会保留旧图（引擎只查文件存在性）。这是"只下载新数据"语义下的合理取舍，不做跨版本内容比对。

### ② 下载列表增删性能

分层修复，不动 UI 增量刷新已正确的部分：

1. **EhDB 批量 API**（greenDAO 事务能力现成未用）：
   - `putDownloadInfo`：改用 `insertOrReplace`，去掉多余的 load-then-insert；
   - 新增 `removeDownloadInfo(List<DownloadInfo>)` → `deleteByKeyInTx`（单事务）;
   - 新增 `putDownloadInfoList(List<DownloadInfo>)` → `insertOrReplaceInTx`（单事务）。
2. **DownloadManager 批量路径**：
   - `deleteRangeDownload`：内存删除改为 gid HashSet + `removeIf`（O(n) 一遍扫完），DB 删除单事务并移到 IO 线程；
   - `addDownload(List)`：循环内不排序，全部插入后每个受影响 label 列表只排一次、全表只排一次；DB 写入用批量事务；
   - 批量开始/停止下载（startAll/startRange/stopAll/stopRange）中的逐条 DB 写改为批量事务。
3. **UI 刷新方式不变**：单条走增量 notify，批量走 onReload→notifyDataSetChanged（1000 行一次全量 notify 在 RecyclerView + 分页下开销可接受，瓶颈在 DB 与 O(N×n)）。

### ③ "未完成"筛选

复用现有异步筛选框架（不阻塞主线程）：

- `scene_download.xml` 菜单新增"未完成"项（id: `download_not_finished`）；
- `DownloadListInfosExecutor.executeFilterAndSort` 新增分支：过滤 `state != STATE_FINISH`；
- `DownloadsScene.onMenuItemClick` 分发该 id。

## 打包与交付

- 仓库内 `test.key`（CN=ehviewer, SHA256 1F:10:92:AE:…）与作者 release APK 签名（CN=shuaixiaojie, SHA256 01:95:1E:5B:…，经下载 2.0.2.2 实测比对）**不一致**，发布 key 为作者私有。**无法在本地打出能覆盖安装官方版的包。**
- 按既定策略：完成实现并本地构建验证通过后，**提交 PR 到 xiaojieonly/Ehviewer_CN_SXJ** 等作者审核合并，由作者用私有 key 发版。
- versionCode/versionName 不在 PR 中改动（版本号由作者发版时提升，遵循仓库惯例）。

## 测试与验证

- `gradlew assembleAppReleaseDebug` 编译通过；
- 现有单测（robolectric）不受影响；
- 代码审查覆盖：迁移过程中断的容错（文件移动失败仅意味着该页重新下载，无数据丢失）、筛选后列表与增量回调的一致性（筛选产生的独立列表沿用现有 category 筛选的处理方式）。
