package com.hippo.anotherviewer.web.service

import org.springframework.stereotype.Component

/**
 * R4-2: 还原运行态感知。restore 成功后置 [restorePending]=true，提示"需重启生效"。
 *
 * 内存态（非持久化）即语义本身：还原写入的是磁盘上的 db/config，必须重启进程才加载；
 * 服务重启后本 bean 重新初始化为 false，横幅随之消失——"重启即生效、重启即清除"。
 * GET /api/v1/backup/state 读取该标志供前端横幅展示。
 */
@Component
class BackupStateHolder {
    @Volatile
    var restorePending: Boolean = false
}
