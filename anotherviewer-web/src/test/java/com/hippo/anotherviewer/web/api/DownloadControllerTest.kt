package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.any
import com.hippo.anotherviewer.web.argThatK
import com.hippo.anotherviewer.web.captureK
import com.hippo.anotherviewer.web.eq
import com.hippo.anotherviewer.web.config.GlobalExceptionHandler
import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.entity.DownloadInfoEntity
import com.hippo.anotherviewer.web.entity.DownloadLabelEntity
import com.hippo.anotherviewer.web.repository.DownloadInfoRepository
import com.hippo.anotherviewer.web.repository.DownloadLabelRepository
import com.hippo.anotherviewer.web.service.DownloadService
import com.hippo.anotherviewer.web.service.GalleryLookupService
import com.hippo.anotherviewer.web.service.ImageCacheService
import com.hippo.anotherviewer.web.service.SiteSessionManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.io.File

class DownloadControllerTest {

    private lateinit var downloadRepository: DownloadInfoRepository
    private lateinit var labelRepository: DownloadLabelRepository
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        downloadRepository = mock(DownloadInfoRepository::class.java)
        labelRepository = mock(DownloadLabelRepository::class.java)
        val config = SiteCoreConfigProperties().apply {
            download.path = File(System.getProperty("java.io.tmpdir"), "av-dl-test-${System.nanoTime()}").absolutePath
        }
        val downloadService = DownloadService(
            downloadRepository,
            labelRepository,
            config,
            mock(ApplicationEventPublisher::class.java),
            mock(ImageCacheService::class.java),
            mock(SiteSessionManager::class.java),
            mock(GalleryLookupService::class.java)
        )
        mockMvc = MockMvcBuilders.standaloneSetup(DownloadController(downloadService))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    // ── fixtures ────────────────────────────────────────────────

    private fun entity(id: Long, gid: Long, label: Int = 0): DownloadInfoEntity {
        val e = DownloadInfoEntity()
        e.id = id
        e.gid = gid
        e.token = "token$gid"
        e.title = "Title $gid"
        e.state = 3
        e.total = 10
        e.done = 10
        e.label = label
        e.time = gid
        return e
    }

    private fun labelEntity(id: Long, name: String): DownloadLabelEntity {
        val e = DownloadLabelEntity()
        e.id = id
        e.label = name
        e.time = id
        return e
    }

    // ── add ─────────────────────────────────────────────────────

    @Test
    fun `add forwards the request and saves the entity`() {
        `when`(downloadRepository.findByGid(123L)).thenReturn(null)

        mockMvc.perform(
            post("/api/v1/download/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"gid":123,"token":"a1b2c3d4e5","title":"T","thumb":null,"label":0}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").value(true))
        verify(downloadRepository).save(
            argThatK { it.gid == 123L && it.token == "a1b2c3d4e5" && it.title == "T" && it.label == 0 }
        )
    }

    @Test
    fun `add rejects blank token with 400 envelope`() {
        mockMvc.perform(
            post("/api/v1/download/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"gid":123,"token":"","title":"T"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.status").value(400))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("token is required"))
            .andExpect(jsonPath("$.error.traceId").exists())
        verify(downloadRepository, never()).save(any(DownloadInfoEntity::class.java))
    }

    @Test
    fun `add rejects non-positive gid with 400 envelope`() {
        mockMvc.perform(
            post("/api/v1/download/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"gid":0,"token":"a1b2c3d4e5"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        verify(downloadRepository, never()).save(any(DownloadInfoEntity::class.java))
    }

    // ── labels ──────────────────────────────────────────────────

    @Test
    fun `createLabel accepts a valid label`() {
        `when`(labelRepository.findByLabel("MyLabel")).thenReturn(null)

        mockMvc.perform(
            post("/api/v1/download/label")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"label":"MyLabel"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").value(true))
        verify(labelRepository).save(argThatK { it.label == "MyLabel" })
    }

    @Test
    fun `createLabel rejects blank label with 400 envelope`() {
        mockMvc.perform(
            post("/api/v1/download/label")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"label":""}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        verify(labelRepository, never()).save(any(DownloadLabelEntity::class.java))
    }

    // ── list pagination ─────────────────────────────────────────

    @Test
    fun `list defaults to offset 0 limit 100 and returns total`() {
        val page = PageImpl(listOf(entity(1, 101), entity(2, 102)), PageRequest.of(0, 100), 7)
        `when`(downloadRepository.findAll(any(Pageable::class.java))).thenReturn(page)
        `when`(downloadRepository.count()).thenReturn(7L)
        `when`(labelRepository.findAll()).thenReturn(listOf(labelEntity(1, "L1")))

        mockMvc.perform(get("/api/v1/download/list"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.downloads.length()").value(2))
            .andExpect(jsonPath("$.downloads[0].gid").value(101))
            .andExpect(jsonPath("$.labels.length()").value(1))
            .andExpect(jsonPath("$.labels[0].label").value("L1"))
            .andExpect(jsonPath("$.total").value(7))
        val captor = ArgumentCaptor.forClass(Pageable::class.java)
        verify(downloadRepository).findAll(captureK(captor))
        verify(downloadRepository).count()
        verify(downloadRepository, never()).findByLabel(anyInt(), any(Pageable::class.java))
        verify(downloadRepository, never()).countByLabel(anyInt())
        assertEquals(0, captor.value.pageNumber)
        assertEquals(100, captor.value.pageSize)
    }

    @Test
    fun `list forwards offset and limit to the paginated query`() {
        val page = PageImpl(listOf(entity(1, 101)), PageRequest.of(5, 20), 1)
        `when`(downloadRepository.findAll(any(Pageable::class.java))).thenReturn(page)
        `when`(downloadRepository.count()).thenReturn(1L)
        `when`(labelRepository.findAll()).thenReturn(emptyList())

        mockMvc.perform(get("/api/v1/download/list").param("offset", "5").param("limit", "20"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.downloads.length()").value(1))

        val captor = ArgumentCaptor.forClass(Pageable::class.java)
        verify(downloadRepository).findAll(captureK(captor))
        assertEquals(5, captor.value.pageNumber)
        assertEquals(20, captor.value.pageSize)
    }

    @Test
    fun `list filters by label with countByLabel total`() {
        val page = PageImpl(listOf(entity(3, 103, label = 7)), PageRequest.of(0, 20), 4)
        `when`(downloadRepository.findByLabel(eq(7), any(Pageable::class.java))).thenReturn(page)
        `when`(downloadRepository.countByLabel(7)).thenReturn(4L)
        `when`(labelRepository.findAll()).thenReturn(emptyList())

        mockMvc.perform(get("/api/v1/download/list").param("label", "7").param("limit", "20"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.downloads.length()").value(1))
            .andExpect(jsonPath("$.downloads[0].label").value(7))
            .andExpect(jsonPath("$.total").value(4))
        verify(downloadRepository).findByLabel(eq(7), any(Pageable::class.java))
        verify(downloadRepository).countByLabel(7)
        verify(downloadRepository, never()).findAll(any(Pageable::class.java))
        verify(downloadRepository, never()).count()
    }

    @Test
    fun `list clamps limit above 500 to 500`() {
        `when`(downloadRepository.findAll(any(Pageable::class.java)))
            .thenReturn(PageImpl(emptyList(), PageRequest.of(0, 500), 0))
        `when`(downloadRepository.count()).thenReturn(0L)
        `when`(labelRepository.findAll()).thenReturn(emptyList())

        mockMvc.perform(get("/api/v1/download/list").param("limit", "9999"))
            .andExpect(status().isOk)
        val captor = ArgumentCaptor.forClass(Pageable::class.java)
        verify(downloadRepository).findAll(captureK(captor))
        assertEquals(500, captor.value.pageSize)
    }

    @Test
    fun `list clamps non-positive limit to 1`() {
        `when`(downloadRepository.findAll(any(Pageable::class.java)))
            .thenReturn(PageImpl(emptyList(), PageRequest.of(0, 1), 0))
        `when`(downloadRepository.count()).thenReturn(0L)
        `when`(labelRepository.findAll()).thenReturn(emptyList())

        mockMvc.perform(get("/api/v1/download/list").param("limit", "0"))
            .andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/download/list").param("limit", "-8"))
            .andExpect(status().isOk)

        val captor = ArgumentCaptor.forClass(Pageable::class.java)
        verify(downloadRepository, times(2)).findAll(captureK(captor))
        assertEquals(1, captor.value.pageSize)
    }

    @Test
    fun `list clamps negative offset to 0`() {
        `when`(downloadRepository.findAll(any(Pageable::class.java)))
            .thenReturn(PageImpl(emptyList(), PageRequest.of(0, 100), 0))
        `when`(downloadRepository.count()).thenReturn(0L)
        `when`(labelRepository.findAll()).thenReturn(emptyList())

        mockMvc.perform(get("/api/v1/download/list").param("offset", "-3"))
            .andExpect(status().isOk)
        val captor = ArgumentCaptor.forClass(Pageable::class.java)
        verify(downloadRepository).findAll(captureK(captor))
        assertEquals(0, captor.value.pageNumber)
    }
}
