package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.config.GlobalExceptionHandler
import com.hippo.anotherviewer.web.service.EhAvailabilityService
import com.hippo.anotherviewer.web.service.SiteSessionManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.concurrent.atomic.AtomicInteger

/**
 * GET /api/v1/site/availability is read-only (never probes) while POST runs a
 * manual (single-flighted) probe and reflects the resulting state —
 * docs/plan-2026-08-30-eh-circuit-breaker.md §4 items 2/3.
 */
class SiteAvailabilityControllerTest {

    private lateinit var availability: EhAvailabilityService
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        availability = EhAvailabilityService(
            "https://e-hentai.org", 5000,
            probe = { true }
        )
        mockMvc = MockMvcBuilders.standaloneSetup(SiteAvailabilityController(availability))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `GET returns UNKNOWN before any probe without triggering one`() {
        val probes = AtomicInteger(0)
        availability = EhAvailabilityService(
            "https://e-hentai.org", 5000,
            probe = { probes.incrementAndGet(); true }
        )
        mockMvc = MockMvcBuilders.standaloneSetup(SiteAvailabilityController(availability)).build()

        mockMvc.perform(get("/api/v1/site/availability"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("UNKNOWN"))
            .andExpect(jsonPath("$.downAt").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.lastReason").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.lastProbeAt").value(org.hamcrest.Matchers.nullValue()))

        assertEquals(0, probes.get(), "GET must never trigger a probe")
    }

    @Test
    fun `GET reflects DOWN + downAt + lastReason after a failure`() {
        availability.recordFailure("connect timed out")

        mockMvc.perform(get("/api/v1/site/availability"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("DOWN"))
            .andExpect(jsonPath("$.lastReason").value("connect timed out"))
            .andExpect(jsonPath("$.downAt").isNumber)
    }

    @Test
    fun `POST probes once and exposes UP with lastProbeAt`() {
        val probes = AtomicInteger(0)
        availability = EhAvailabilityService(
            "https://e-hentai.org", 5000,
            probe = { probes.incrementAndGet(); true }
        )
        mockMvc = MockMvcBuilders.standaloneSetup(SiteAvailabilityController(availability)).build()

        mockMvc.perform(post("/api/v1/site/availability"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("UP"))
            .andExpect(jsonPath("$.lastProbeAt").isNumber)

        assertEquals(1, probes.get())
    }

    @Test
    fun `POST a failing probe keeps DOWN and the subsequent GET stays DOWN without re-probing`() {
        val probes = AtomicInteger(0)
        availability = EhAvailabilityService(
            "https://e-hentai.org", 5000,
            probe = { probes.incrementAndGet(); false }
        )
        mockMvc = MockMvcBuilders.standaloneSetup(SiteAvailabilityController(availability)).build()

        mockMvc.perform(post("/api/v1/site/availability"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("DOWN"))

        mockMvc.perform(get("/api/v1/site/availability"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("DOWN"))

        assertEquals(1, probes.get(), "GET must not probe even while DOWN")
    }
}
