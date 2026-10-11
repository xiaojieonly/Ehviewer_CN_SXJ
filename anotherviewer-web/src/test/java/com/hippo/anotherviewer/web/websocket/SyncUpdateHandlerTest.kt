package com.hippo.anotherviewer.web.websocket

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.service.SyncUpdateEvent
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.messaging.simp.SimpMessagingTemplate

/**
 * v4 sync.update → STOMP（WP3）：topic 与冻结信封字段的回归锁；
 * 推送异常不得上抛（发送失败仅 warn，事件消费绝不被打断）。
 */
class SyncUpdateHandlerTest {

    @Test
    fun `sync update event is broadcast on topic sync with the frozen envelope`() {
        val template = mock(SimpMessagingTemplate::class.java)

        SyncUpdateHandler(template).onSyncUpdate(
            SyncUpdateEvent(
                deviceId = "android-1",
                counts = mapOf("favorites" to 2, "history" to 1),
                serverTimestamp = 1_700_000_000_000L,
            )
        )

        verify(template).convertAndSend(
            eq("/topic/sync"),
            argThat<Any> { body ->
                @Suppress("UNCHECKED_CAST")
                val envelope = body as Map<String, Any?>
                val payload = envelope["payload"] as Map<*, *>
                envelope["type"] == "sync.update" &&
                    envelope["version"] == "1.3" &&
                    (envelope["timestamp"] as Long) > 0 &&
                    payload["entities"] == listOf("favorites", "history") &&
                    payload["deviceId"] == "android-1" &&
                    payload["counts"] == mapOf("favorites" to 2, "history" to 1) &&
                    payload["syncTimestamp"] == 1_700_000_000_000L
            },
        )
    }

    @Test
    fun `send failure is swallowed with a warning`() {
        val template = mock(SimpMessagingTemplate::class.java)
        `when`(template.convertAndSend(anyString(), any<Any>())).thenThrow(IllegalStateException("broker down"))

        // 不抛：事件消费绝不被打断。
        SyncUpdateHandler(template).onSyncUpdate(SyncUpdateEvent("server", mapOf("downloads" to 1), 1L))
        assertTrue(true)
    }
}
