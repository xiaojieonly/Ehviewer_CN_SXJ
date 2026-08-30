package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.entity.DownloadInfoEntity
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import com.hippo.anotherviewer.web.repository.DownloadLabelRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional

/**
 * Pins the G4 metrics fixes and the EH-DOWN download-start semantics
 * (plan-2026-08-30 §3.2/§3.3): state counts via COUNT SQL (countByState,
 * no entity materialisation) and immediate FAILED instead of a silent
 * pending task while EH is DOWN.
 */
class DownloadServiceTest {

    private lateinit var downloadRepository: DownloadInfoRepository
    private lateinit var labelRepository: DownloadLabelRepository
    private lateinit var availability: EhAvailabilityService
    private lateinit var downloadDirIndex: DownloadDirIndex
    private lateinit var service: DownloadService

    @BeforeEach
    fun setUp() {
        downloadRepository = mock(DownloadInfoRepository::class.java)
        labelRepository = mock(DownloadLabelRepository::class.java)
        availability = mock(EhAvailabilityService::class.java)
        downloadDirIndex = mock(DownloadDirIndex::class.java)
        val sessionManager = mock(SiteSessionManager::class.java)
        service = DownloadService(
            downloadRepository,
            labelRepository,
            SiteCoreConfigProperties(),
            mock(ApplicationEventPublisher::class.java),
            mock(ImageCacheService::class.java),
            sessionManager,
            mock(GalleryLookupService::class.java),
            mock(ServerConfigService::class.java),
            availability,
            downloadDirIndex,
        )
    }

    @Test
    fun `state counts use countByState instead of loading entities`() {
        `when`(downloadRepository.countByState(3)).thenReturn(9171L)
        `when`(downloadRepository.countByState(4)).thenReturn(3L)

        assertEquals(9171L, service.getCompletedDownloadCount())
        assertEquals(3L, service.getFailedDownloadCount())

        verify(downloadRepository).countByState(3)
        verify(downloadRepository).countByState(4)
        verify(downloadRepository, never()).findByState(3)
        verify(downloadRepository, never()).findByState(4)
    }

    @Test
    fun `startDownload marks the task FAILED with EH_UNAVAILABLE error while blocked`() {
        `when`(availability.isBlocked()).thenReturn(true)
        val entity = DownloadInfoEntity().apply {
            id = 1
            gid = 42L
            token = "tok"
            state = 0
        }
        `when`(downloadRepository.findById(1L)).thenReturn(Optional.of(entity))
        `when`(downloadRepository.save(any(DownloadInfoEntity::class.java))).thenAnswer { it.getArgument(0) }

        assertFalse(service.startDownload(1L))
        assertEquals(4, entity.state, "blocked start must FAILED immediately")
        assertEquals("EH_UNAVAILABLE: EH 平台当前不可达", entity.error)
        verify(downloadRepository).save(entity)
    }

    @Test
    fun `startDownload checks availability even for resumable paused tasks`() {
        // DOWN 重新开始：paused(0) 任务不被静默改回 1（无 worker、保持可重试的 FAILED 语义）。
        `when`(availability.isBlocked()).thenReturn(true)
        val entity = DownloadInfoEntity().apply {
            id = 1
            gid = 42L
            token = "tok"
            state = 0
        }
        `when`(downloadRepository.findById(1L)).thenReturn(Optional.of(entity))
        `when`(downloadRepository.save(any(DownloadInfoEntity::class.java))).thenAnswer { it.getArgument(0) }

        service.startDownload(1L)

        assertEquals(4, entity.state)
    }
}
