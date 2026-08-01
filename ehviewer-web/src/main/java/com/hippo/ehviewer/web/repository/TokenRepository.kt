package com.hippo.ehviewer.web.repository

import com.hippo.ehviewer.web.entity.TokenEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional

interface TokenRepository : JpaRepository<TokenEntity, Long> {
    fun findByTokenHash(tokenHash: String): TokenEntity?
    @Transactional
    fun deleteByTokenHash(tokenHash: String)
}
