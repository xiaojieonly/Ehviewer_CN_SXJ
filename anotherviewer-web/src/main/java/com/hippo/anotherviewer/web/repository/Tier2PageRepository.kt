package com.hippo.anotherviewer.web.repository

import com.hippo.anotherviewer.web.entity.Tier2PageEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Persistent EH page cache rows, keyed by normalized URL ([Tier2PageEntity.url]).
 * The refresh job reads its oldest-first batch through
 * [findByFetchedAtLessThanOrderByFetchedAtAsc]; capacity trimming goes through
 * JpaRepository's paged `findAll` + `deleteAllInBatch`.
 */
interface Tier2PageRepository : JpaRepository<Tier2PageEntity, String> {

    /** Cached pages last fetched before [fetchedAt], oldest first (refresh candidates). */
    fun findByFetchedAtLessThanOrderByFetchedAtAsc(fetchedAt: Long, pageable: Pageable): List<Tier2PageEntity>
}
