package com.hippo.anotherviewer.web.service

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hippo.anotherviewer.web.dto.*
import com.hippo.anotherviewer.web.entity.UserPreferenceEntity
import com.hippo.anotherviewer.web.repository.UserPreferenceRepository
import org.springframework.stereotype.Service

private const val SKEW_TOLERANCE = 5000L

@Service
class UserPreferenceService(private val repo: UserPreferenceRepository) {

    // 容错读取: 未知字段忽略 + 缺省填充, 异构偏好串（如 {"theme":"dark"}）不会打挂 GET/PUT
    private val mapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    // 严格模式仅用于校验/归一写入内容是否合法 JSON 对象
    private val strictMapper = jacksonObjectMapper()

    fun get(username: String): PreferenceResponse {
        val entity = repo.findByUsername(username) ?: return PreferenceResponse()
        return readStored(entity)
    }

    fun update(username: String, request: PreferenceUpdateRequest, source: String): PreferenceResponse {
        val entity = repo.findByUsername(username)
            ?: UserPreferenceEntity().apply { this.username = username }

        // 深度合并: 只覆盖 request 中非 null 的 section
        val current = readStored(entity)
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

    /** 同步用: 全量覆盖，LWW —— 仅当 incoming.lastModified 明显新于存量 updatedAt（含时钟偏差容忍）或无存量时覆盖 */
    fun replace(username: String, json: String, source: String, lastModified: Long) {
        val existing = repo.findByUsername(username)
        // 旧设备的 push 不得覆盖新值
        if (existing != null && lastModified <= existing.updatedAt + SKEW_TOLERANCE) return
        val entity = existing ?: UserPreferenceEntity().apply { this.username = username }
        entity.preferences = normalize(json)
        // E2E-8: 与实体行为一致 —— 接受谁的记录就用谁的戳（保留客户端 lastModified），
        // 不再被服务器重打戳为 serverTimestamp，否则 preferences 的高水位记账会错位。
        entity.updatedAt = lastModified
        entity.updatedBy = source
        repo.save(entity)
    }

    fun getRaw(username: String): String {
        return repo.findByUsername(username)?.preferences ?: "{}"
    }

    private fun readStored(entity: UserPreferenceEntity): PreferenceResponse = try {
        mapper.readValue(entity.preferences, PreferenceResponse::class.java)
    } catch (e: JsonProcessingException) {
        // 历史坏数据兜底: 读不出就返回全缺省, 绝不让 GET/PUT 500
        PreferenceResponse()
    }

    /** 校验/归一: 非 JSON 对象一律回退为 "{}"，合法对象原样保留（含未知字段），保证存储内容永远可读 */
    private fun normalize(json: String): String = try {
        val node = strictMapper.readTree(json)
        if (node != null && node.isObject) node.toString() else "{}"
    } catch (e: JsonProcessingException) {
        "{}"
    }
}
