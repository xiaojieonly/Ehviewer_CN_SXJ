package com.hippo.ehviewer.web.websocket

import com.hippo.ehviewer.web.dto.DownloadProgress
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

@Component
class DownloadProgressHandler(private val messagingTemplate: SimpMessagingTemplate) {

    @EventListener
    fun handleDownloadProgress(progress: DownloadProgress) {
        messagingTemplate.convertAndSend(
            "/topic/download/${progress.gid}",
            progress
        )

        messagingTemplate.convertAndSend(
            "/topic/download/all",
            progress
        )
    }
}
