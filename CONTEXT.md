# AnotherViewer

Android 漫画查看器（fork 自 ehviewer）与其 WebUI 伴侣服务器组成的自托管系统：App 与 WebUI 之间双向同步用户数据，WebUI 负责下载、阅读代理、备份与迁移。

## Language

### 同步（Sync）

**同步（Sync）**:
App 与 WebUI 服务器之间按高水位增量交换用户数据（收藏/历史/下载/书签/过滤/快速搜索/下载标签/偏好）的周期流程。
_Avoid_: 云同步、镜像

**配对（Pairing）**:
服务器生成 6 位一次性码，App 输入码换取设备专属 token 并成为受信设备的过程。
_Avoid_: 登录、连接

**高水位（high-water mark）**:
上次成功同步的服务器时间戳；增量拉取只取高于该值的变更。
_Avoid_: lastSync、游标

**快照/待删集合（snapshot / pending）**:
App 端本地持久化的已推送 key 集合（快照）与待传播删除的 key 集合（待删）；两者与当前本地 key 集合差分即得删除事件。
_Avoid_: 删除队列、脏集合

**tombstone**:
标记某实体已删除的同步记录；软删实体（收藏/下载/过滤等）在服务器保留 tombstone，硬删实体（历史/书签）到达即删行。
_Avoid_: 墓碑、删除标记

**完全同步（full sync）**:
同一用户多设备间 last-write-wins 覆盖即视为一致，不进行跨用户冲突仲裁（见 ADR-0001）。

### 部署（Deployment）

**data-dir**:
WebUI 服务器唯一的权威数据目录（`--data-dir` 参数或 `EHVIEWER_DATA_DIR`），固定派生 ehviewer.db、security.key、downloads/、cache/、backups/ 的默认位置。
_Avoid_: 数据文件夹、工作目录

**备份（backup）**:
将 data-dir 的固定结构（db+key+配置，可选下载内容）打包为分片 7z + manifest 的产物；与路径无关，可跨机器还原即迁移。分片是可独立存储/传输/校验的实体（可拷到 NAS、U 盘异地备份，见 ADR-0002）。
_Avoid_: 导出、快照

**迁移（migration）**:
备份包在新机器还原，或直接拷贝 data-dir；因结构固定，目标路径可以不同。下载文件随备份可选迁移（includeDownloads）或随目录拷贝；同设备包名迁移时经同步迁移元数据、重新授权 SAF 目录即复用原文件。

**legacy 包**:
`-PapplicationId=com.xjs.ehviewer` 构建的旧包名 APK，用于覆盖安装旧版本保留数据、经同步把数据推上服务器完成包名迁移。
_Avoid_: 旧版、兼容包

### 应用标识（App Identity）

**applicationId**:
App 的应用标识 `com.pf.anotherviewer`（debug 后缀 `.debug`）；仅此标识随包名迁移变更，源码 namespace `com.hippo.ehviewer` 原样保留。
_Avoid_: 包名（歧义，可与源码包名混淆）
