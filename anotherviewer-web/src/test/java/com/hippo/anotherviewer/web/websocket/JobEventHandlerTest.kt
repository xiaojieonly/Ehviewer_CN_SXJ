package com.hippo.anotherviewer.web.websocket

import com.hippo.anotherviewer.web.dto.JobType
import com.hippo.anotherviewer.web.service.JobEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.messaging.simp.SimpMessagingTemplate

/**
 * JobEventHandler → STOMP 推送测试（附录 A3）：事件信封 version="1.1"，
 * 同时推 /topic/jobs/{jobId} 与 /topic/jobs/all。
 */
class JobEventHandlerTest {

    private lateinit var messagingTemplate: SimpMessagingTemplate
    private lateinit var handler: JobEventHandler

    @BeforeEach
    fun setUp() {
        messagingTemplate = mock(SimpMessagingTemplate::class.java)
        handler = JobEventHandler(messagingTemplate)
    }

    @Test
    fun `started event carries envelope version 1_1 and both topics`() {
        handler.handleJobEvent(JobEvent.Started("job-abc12345", JobType.IMPORT, "已提交", 0L))

        verify(messagingTemplate).convertAndSend(
            org.mockito.ArgumentMatchers.eq("/topic/jobs/job-abc12345"),
            org.mockito.ArgumentMatchers.any<Any>()
        )
        verify(messagingTemplate).convertAndSend(
            org.mockito.ArgumentMatchers.eq("/topic/jobs/all"),
            org.mockito.ArgumentMatchers.any<Any>()
        )
    }

    @Test
    fun `progress event carries percent processed total and stage`() {
        handler.handleJobEvent(JobEvent.Progress("job-abc12345", JobType.EXPORT, "压缩分片 1/3", 33.33, 1, 3))

        verify(messagingTemplate).convertAndSend(
            org.mockito.ArgumentMatchers.eq("/topic/jobs/all"),
            org.mockito.ArgumentMatchers.any<Any>()
        )
    }

    @Test
    fun `completed event carries result`() {
        handler.handleJobEvent(JobEvent.Completed("job-abc12345", JobType.CACHE_CLEAR, mapOf("removed" to 42)))

        verify(messagingTemplate).convertAndSend(
            org.mockito.ArgumentMatchers.eq("/topic/jobs/all"),
            org.mockito.ArgumentMatchers.any<Any>()
        )
    }

    @Test
    fun `failed event carries error message`() {
        handler.handleJobEvent(JobEvent.Failed("job-abc12345", JobType.RESTORE, "解包失败"))

        verify(messagingTemplate).convertAndSend(
            org.mockito.ArgumentMatchers.eq("/topic/jobs/all"),
            org.mockito.ArgumentMatchers.any<Any>()
        )
    }
}
