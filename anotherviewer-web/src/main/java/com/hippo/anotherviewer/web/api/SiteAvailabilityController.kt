package com.hippo.anotherviewer.web.api

import com.hippo.anotherviewer.web.service.AvailabilityStatus
import com.hippo.anotherviewer.web.service.EhAvailabilityService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * EH availability status (docs/plan-2026-08-30-eh-circuit-breaker.md §4 items 2/3):
 *
 * - `GET /api/v1/site/availability` — read the current state machine snapshot
 *   only; NEVER triggers a probe (no network I/O).
 * - `POST /api/v1/site/availability` — executes one (single-flighted, ≤5s)
 *   manual probe and returns the resulting snapshot. Manual-recovery semantics:
 *   user actions only; no auto-recovery will ever happen.
 *
 * Auth: API routes are authenticated by default (anonymous when login is
 * disabled, same as every other API route) — no SecurityConfig change needed.
 */
@RestController
@RequestMapping("/api/v1/site/availability")
class SiteAvailabilityController(
    private val availability: EhAvailabilityService,
) {

    @GetMapping
    fun status(): AvailabilityStatus = availability.status()

    @PostMapping
    fun probe(): AvailabilityStatus {
        availability.probeNow()
        return availability.status()
    }
}
