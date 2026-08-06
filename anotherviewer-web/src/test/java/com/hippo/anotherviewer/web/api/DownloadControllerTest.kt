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
import org.springframework.data.domain.Sort
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

    private fun titleProj(id: Long, title: String?, titleJpn: String?, time: Long): DownloadInfoRepository.TitleProjection =
        object : DownloadInfoRepository.TitleProjection {
            override val id: Long = id
            override val title: String? = title
            override val titleJpn: String? = titleJpn
            override val time: Long = time
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
    fun `list converts row offset to page index`() {
        // A5 契约：offset 是行偏移。offset=5&limit=20 → pageIndex=0（前 20 行）；
        // offset=100&limit=100 → pageIndex=1（101..200 行）。
        val page = PageImpl(listOf(entity(1, 101)), PageRequest.of(0, 20), 1)
        `when`(downloadRepository.findAll(any(Pageable::class.java))).thenReturn(page)
        `when`(downloadRepository.count()).thenReturn(1L)
        `when`(labelRepository.findAll()).thenReturn(emptyList())

        mockMvc.perform(get("/api/v1/download/list").param("offset", "5").param("limit", "20"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.downloads.length()").value(1))

        val captor = ArgumentCaptor.forClass(Pageable::class.java)
        verify(downloadRepository).findAll(captureK(captor))
        assertEquals(0, captor.value.pageNumber)
        assertEquals(20, captor.value.pageSize)
    }

    @Test
    fun `list maps row offset 100 with limit 100 to page 1`() {
        val page = PageImpl(listOf(entity(2, 102)), PageRequest.of(1, 100), 500)
        `when`(downloadRepository.findAll(any(Pageable::class.java))).thenReturn(page)
        `when`(downloadRepository.count()).thenReturn(500L)
        `when`(labelRepository.findAll()).thenReturn(emptyList())

        mockMvc.perform(get("/api/v1/download/list").param("offset", "100").param("limit", "100"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(500))

        val captor = ArgumentCaptor.forClass(Pageable::class.java)
        verify(downloadRepository).findAll(captureK(captor))
        assertEquals(1, captor.value.pageNumber)
        assertEquals(100, captor.value.pageSize)
    }

    @Test
    fun `list defaults to time_desc sort (newest first)`() {
        val page = PageImpl(emptyList<DownloadInfoEntity>(), PageRequest.of(0, 100), 0)
        `when`(downloadRepository.findAll(any(Pageable::class.java))).thenReturn(page)
        `when`(downloadRepository.count()).thenReturn(0L)
        `when`(labelRepository.findAll()).thenReturn(emptyList())

        mockMvc.perform(get("/api/v1/download/list"))
            .andExpect(status().isOk)

        val captor = ArgumentCaptor.forClass(Pageable::class.java)
        verify(downloadRepository).findAll(captureK(captor))
        val order = captor.value.sort.getOrderFor("time")
        assertEquals(Sort.Direction.DESC, order?.direction)
    }

    @Test
    fun `list forwards the requested sort mode`() {
        val page = PageImpl(emptyList<DownloadInfoEntity>(), PageRequest.of(0, 100), 0)
        `when`(downloadRepository.findAll(any(Pageable::class.java))).thenReturn(page)
        `when`(downloadRepository.count()).thenReturn(0L)
        `when`(labelRepository.findAll()).thenReturn(emptyList())

        mockMvc.perform(get("/api/v1/download/list").param("sort", "title_asc"))
            .andExpect(status().isOk)

        val captor = ArgumentCaptor.forClass(Pageable::class.java)
        verify(downloadRepository).findAll(captureK(captor))
        val order = captor.value.sort.getOrderFor("title")
        assertEquals(Sort.Direction.ASC, order?.direction)
    }

    @Test
    fun `list falls back to time_desc for an unknown sort value`() {
        val page = PageImpl(emptyList<DownloadInfoEntity>(), PageRequest.of(0, 100), 0)
        `when`(downloadRepository.findAll(any(Pageable::class.java))).thenReturn(page)
        `when`(downloadRepository.count()).thenReturn(0L)
        `when`(labelRepository.findAll()).thenReturn(emptyList())

        mockMvc.perform(get("/api/v1/download/list").param("sort", "bogus"))
            .andExpect(status().isOk)

        val captor = ArgumentCaptor.forClass(Pageable::class.java)
        verify(downloadRepository).findAll(captureK(captor))
        assertEquals(Sort.Direction.DESC, captor.value.sort.getOrderFor("time")?.direction)
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
    fun `list forwards regex mode and invalid pattern yields 400`() {
        `when`(downloadRepository.findTitlesByLabel(any())).thenReturn(listOf(
            titleProj(1L, "Futanari Story", "フタナリ", 100L),
            titleProj(2L, "Plain", null, 200L),
            titleProj(3L, "Futa and More", "futa", 300L),
        ))
        `when`(downloadRepository.findAllById(listOf(3L, 1L))).thenReturn(listOf(entity(3, 103), entity(1, 101)))

        mockMvc.perform(get("/api/v1/download/list").param("q", "(?i)^futa").param("regex", "true"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(2))
        verify(downloadRepository).findTitlesByLabel(nullable(Int::class.java))
        verify(downloadRepository, never()).searchDownloads(any(), any(), any(Pageable::class.java))

        // 非法正则 → 400 REGEX_INVALID（service 抛 IllegalArgumentException）。
        mockMvc.perform(get("/api/v1/download/list").param("q", "(").param("regex", "true"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("REGEX_INVALID"))
    }

    @Test
    fun `regex page honours label filter and sort`() {
        `when`(downloadRepository.findTitlesByLabel(7)).thenReturn(listOf(
            titleProj(1L, "Alpha", null, 100L),
            titleProj(2L, "Beta", null, 50L),
        ))
        `when`(downloadRepository.findAllById(listOf(1L, 2L))).thenReturn(listOf(entity(1, 101), entity(2, 102)))

        // title_asc 排序 + label=7 + regex 匹配全部。
        mockMvc.perform(get("/api/v1/download/list").param("q", ".*").param("regex", "true").param("label", "7").param("sort", "title_asc"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(2))
        verify(downloadRepository).findTitlesByLabel(7)
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
    // ── batch (Android multi-select Start/Stop/Delete/Move) ─────

    @Test
    fun `start-range calls startDownloads and returns the count`() {
        val service = mock(DownloadService::class.java)
        val mvc = MockMvcBuilders.standaloneSetup(DownloadController(service))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
        `when`(service.startDownloads(listOf(1L, 2L), false, null, null, false)).thenReturn(2)

        mvc.perform(
            post("/api/v1/download/start-range")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ids":[1,2]}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").value(2))
        verify(service).startDownloads(listOf(1L, 2L), false, null, null, false)
    }

    @Test
    fun `stop-range calls pauseDownloads and returns the count`() {
        val service = mock(DownloadService::class.java)
        val mvc = MockMvcBuilders.standaloneSetup(DownloadController(service))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
        `when`(service.pauseDownloads(listOf(3L), false, null, null, false)).thenReturn(1)

        mvc.perform(
            post("/api/v1/download/stop-range")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ids":[3]}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").value(1))
        verify(service).pauseDownloads(listOf(3L), false, null, null, false)
    }

    @Test
    fun `delete-range calls deleteDownloads and returns the count`() {
        val service = mock(DownloadService::class.java)
        val mvc = MockMvcBuilders.standaloneSetup(DownloadController(service))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
        `when`(service.deleteDownloads(listOf(1L, 2L, 3L), false, null, null, false)).thenReturn(3)

        mvc.perform(
            post("/api/v1/download/delete-range")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ids":[1,2,3]}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").value(3))
        verify(service).deleteDownloads(listOf(1L, 2L, 3L), false, null, null, false)
    }

    @Test
    fun `move forwards ids and labelId`() {
        val service = mock(DownloadService::class.java)
        val mvc = MockMvcBuilders.standaloneSetup(DownloadController(service))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
        `when`(service.moveDownloads(listOf(1L, 2L), false, null, null, false, 7)).thenReturn(2)

        mvc.perform(
            post("/api/v1/download/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ids":[1,2],"labelId":7}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").value(2))
        verify(service).moveDownloads(listOf(1L, 2L), false, null, null, false, 7)
    }

    @Test
    fun `move rejects unknown label with 400 envelope`() {
        val service = mock(DownloadService::class.java)
        val mvc = MockMvcBuilders.standaloneSetup(DownloadController(service))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
        `when`(service.moveDownloads(listOf(1L), false, null, null, false, -1)).thenReturn(0)

        mvc.perform(
            post("/api/v1/download/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ids":[1],"labelId":-1}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
    }

    @Test
    fun `batch range forwards all mode with label and q filters`() {
        val service = mock(DownloadService::class.java)
        val mvc = MockMvcBuilders.standaloneSetup(DownloadController(service))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
        `when`(service.startDownloads(null, true, 7, "futa", false)).thenReturn(42)

        mvc.perform(
            post("/api/v1/download/start-range")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"all":true,"label":7,"q":"futa"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").value(42))
        verify(service).startDownloads(null, true, 7, "futa", false)
    }

    @Test
    fun `batch range rejects missing ids when all is false`() {
        val service = mock(DownloadService::class.java)
        val mvc = MockMvcBuilders.standaloneSetup(DownloadController(service))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

        mvc.perform(
            post("/api/v1/download/delete-range")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ids":[]}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        verify(service, never()).deleteDownloads(anyList(), anyBoolean(), nullable(Int::class.java), nullable(String::class.java), anyBoolean())
    }

    @Test
    fun `list forwards the search q to the search queries`() {
        `when`(downloadRepository.searchDownloads(nullable(Int::class.java), eq("futa"), any(Pageable::class.java)))
            .thenReturn(PageImpl(emptyList(), PageRequest.of(0, 100), 0))
        `when`(downloadRepository.countSearchDownloads(nullable(Int::class.java), eq("futa"))).thenReturn(0L)
        `when`(labelRepository.findAll()).thenReturn(emptyList())

        mockMvc.perform(get("/api/v1/download/list").param("q", "futa"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(0))
        verify(downloadRepository).searchDownloads(nullable(Int::class.java), eq("futa"), any(Pageable::class.java))
        verify(downloadRepository).countSearchDownloads(nullable(Int::class.java), eq("futa"))
        verify(downloadRepository, never()).findAll(any(Pageable::class.java))
    }
}
