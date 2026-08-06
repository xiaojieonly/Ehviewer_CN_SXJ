package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.config.GlobalExceptionHandler
import com.hippo.anotherviewer.web.dto.JobState
import com.hippo.anotherviewer.web.dto.JobType
import com.hippo.anotherviewer.web.service.Job
import com.hippo.anotherviewer.web.service.JobService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/** GET /api/v1/jobs/{jobId} 与 GET /api/v1/jobs/active?type= 契约测试（附录 A2）。 */
class JobControllerTest {

    private lateinit var jobService: JobService
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        jobService = mock(JobService::class.java)
        mockMvc = MockMvcBuilders.standaloneSetup(JobController(jobService))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    private fun completedJob(jobId: String, type: JobType): Job =
        Job(jobId = jobId, type = type).apply {
            state = JobState.COMPLETED
            stage = "完成"
            processed = 3
            total = 3
            startedAt = 1L
            completedAt = 2L
            result = mapOf("success" to true)
        }

    @Test
    fun `getJob returns the job DTO with computed percent`() {
        `when`(jobService.getJob("job-abc12345")).thenReturn(completedJob("job-abc12345", JobType.IMPORT))

        mockMvc.perform(get("/api/v1/jobs/job-abc12345"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.jobId").value("job-abc12345"))
            .andExpect(jsonPath("$.type").value("IMPORT"))
            .andExpect(jsonPath("$.state").value("COMPLETED"))
            .andExpect(jsonPath("$.percent").value(100.0))
            .andExpect(jsonPath("$.processed").value(3))
            .andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.result.success").value(true))
    }

    @Test
    fun `getJob returns 404 uniform envelope for unknown job`() {
        `when`(jobService.getJob("job-nope0000")).thenReturn(null)

        mockMvc.perform(get("/api/v1/jobs/job-nope0000"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"))
            .andExpect(jsonPath("$.error.status").value(404))
    }

    @Test
    fun `activeJob returns the running job for the type`() {
        `when`(jobService.activeJob(JobType.EXPORT)).thenReturn(completedJob("job-export01", JobType.EXPORT))

        mockMvc.perform(get("/api/v1/jobs/active").param("type", "EXPORT"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.jobId").value("job-export01"))
            .andExpect(jsonPath("$.type").value("EXPORT"))
    }

    @Test
    fun `activeJob returns 404 when nothing is running`() {
        `when`(jobService.activeJob(JobType.CACHE_CLEAR)).thenReturn(null)

        mockMvc.perform(get("/api/v1/jobs/active").param("type", "CACHE_CLEAR"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("NO_ACTIVE_JOB"))
    }
}
