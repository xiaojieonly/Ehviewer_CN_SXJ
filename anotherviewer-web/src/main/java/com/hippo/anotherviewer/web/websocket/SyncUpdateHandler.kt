package com.hippo.anotherviewer.web.websocket

import com.hippo.anotherviewer.web.service.SyncUpdateEvent
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * v4 sync.update → STOMP 推送（WP3）：push 落库 / 回收站恢复与清除在**事务提交后**
 * 广播到 `/topic/sync`，WebUI 免轮询即刷新同步面板与列表。
 *
 * 信封沿用冻结格式 `{type, timestamp, version, payload}`（websocket-protocol 惯例，
 * 新事件族 sync.* 提前一个 MINOR 宣布 → "1.3"）；payload.entities 只含本次实际
 * 写入的实体名（计数 >0），counts 为全量非零计数。
 *
 * AFTER_COMMIT + fallbackExecution=true（同 TrashMoveHandler）：事务回滚不发，
 * 无事务上下文（直构/自调用路径）立即发。发送异常仅 warn，绝不上抛打断事件消费。
 */
@Component
class SyncUpdateHandler(
    private val messagingTemplate: SimpMessagingTemplate,
) {

    private val logger = LoggerFactory.getLogger(SyncUpdateHandler::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun onSyncUpdate(event: SyncUpdateEvent) {
        try {
            val payload = mapOf(
                "entities" to event.counts.keys.toList(),
                "deviceId" to event.deviceId,
                "counts" to event.counts,
                "syncTimestamp" to event.serverTimestamp,
            )
            val envelope = mapOf(
                "type" to "sync.update",
                "timestamp" to System.currentTimeMillis(),
                "version" to "1.3",
                "payload" to payload,
            )
            messagingTemplate.convertAndSend("/topic/sync", envelope)
        } catch (e: Exception) {
            logger.warn("Sync update STOMP send failed for device={}: {}", event.deviceId, e.message)
        }
    }
}
