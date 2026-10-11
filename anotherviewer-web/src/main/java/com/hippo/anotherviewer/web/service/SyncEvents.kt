package com.hippo.anotherviewer.web.service

/**
 * v4 下载墓碑事件（WP3/D）：下载行被墓碑化（删除）时发布，内容目录由
 * [TrashMoveHandler] 在**事务提交后**移入回收站 `{dataDir}/trash/`。
 *
 * 为什么走事件而不是就地删目录：删除现在可能发生在事务内（DownloadService.deleteDownload
 * / DownloadMaintenanceService.clean 均已 @Transactional），事务回滚时目录必须原样保留；
 * AFTER_COMMIT 监听器保证「行墓碑化已落库」才移动文件，二者不会各说各话。
 *
 * @param gid 被墓碑化的画廊 gid（目录名解析兜底用）。
 * @param downloadDir 行内存储的内容目录（可空：App push 来的墓碑行没有该字段，
 *   由 [TrashMoveHandler] 回落 `<downloads 根>/<gid>` 解析）。
 */
data class DownloadTombstonedEvent(val gid: Long, val downloadDir: String?)

/**
 * v4 同步变更事件（WP2）：任何改变同步数据的写入（push 转账、回收站恢复/清除）
 * 在事务提交后经 [com.hippo.anotherviewer.web.websocket.SyncUpdateHandler] 广播到
 * STOMP `/topic/sync`，让 WebUI 免轮询即时刷新。
 *
 * @param deviceId 写入方（push 的 deviceId；服务器自身动作 = "server"）。
 * @param counts 本次写入中各实体的「生效合并」计数（仅含 >0 的键）。
 * @param serverTimestamp 本次写入的服务器戳（与 push 响应/更新设备行同一值）。
 */
data class SyncUpdateEvent(
    val deviceId: String,
    val counts: Map<String, Int>,
    val serverTimestamp: Long,
)
