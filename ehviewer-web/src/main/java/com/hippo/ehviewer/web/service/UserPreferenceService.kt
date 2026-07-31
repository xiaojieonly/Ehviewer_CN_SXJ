package com.hippo.ehviewer.web.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.entity.UserPreferenceEntity
import com.hippo.ehviewer.web.repository.UserPreferenceRepository
import org.springframework.stereotype.Service

@Service
class UserPreferenceService(private val repo: UserPreferenceRepository) {

    private val mapper = jacksonObjectMapper()

    fun get(username: String): PreferenceResponse {
        val entity = repo.findByUsername(username) ?: return PreferenceResponse()
        return mapper.readValue(entity.preferences, PreferenceResponse::class.java)
    }

    fun update(username: String, request: PreferenceUpdateRequest, source: String): PreferenceResponse {
        val entity = repo.findByUsername(username)
            ?: UserPreferenceEntity().apply { this.username = username }

        // 深度合并: 只覆盖 request 中非 null 的 section
        val current = mapper.readValue(entity.preferences, PreferenceResponse::class.java)
        val merged = PreferenceResponse(
            general = request.general ?: current.general,
            reader = request.reader ?: current.reader,
            privacy = request.privacy ?: current.privacy,
        )

        entity.preferences = mapper.writeValueAsString(merged)
        entity.updatedAt = System.currentTimeMillis()
        entity.updatedBy = source
        repo.save(entity)
        return merged
    }

    /** 同步用: 全量覆盖 */
    fun replace(username: String, json: String, source: String) {
        val entity = repo.findByUsername(username)
            ?: UserPreferenceEntity().apply { this.username = username }
        entity.preferences = json
        entity.updatedAt = System.currentTimeMillis()
        entity.updatedBy = source
        repo.save(entity)
    }

    fun getRaw(username: String): String {
        return repo.findByUsername(username)?.preferences ?: "{}"
    }
}
