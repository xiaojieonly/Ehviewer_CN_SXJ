package com.hippo.anotherviewer.web.service

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hippo.anotherviewer.web.dto.*
import com.hippo.anotherviewer.web.entity.UserPreferenceEntity
import com.hippo.anotherviewer.web.repository.UserPreferenceRepository
import org.springframework.stereotype.Service

@Service
class UserPreferenceService(
    private val repo: UserPreferenceRepository,
    private val serverConfig: ServerConfigService,
    // v4（WP2）：preferences 的 updatedAt 由服务器统一打戳（syncClock.next()）。
    // 可空缺省仅为既有直构测试的源兼容保留——null 时回退墙钟。
    private val syncClock: SyncClock? = null,
) {

    /** v4 打戳助手：有 SyncClock 走服务器单调时钟，直构测试场景回落墙钟。 */
    private fun stamp(): Long = syncClock?.next() ?: System.currentTimeMillis()

    // 容错读取: 未知字段忽略 + 缺省填充, 异构偏好串（如 {"theme":"dark"}）不会打挂 GET/PUT
    private val mapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    // 严格模式仅用于校验/归一写入内容是否合法 JSON 对象
    private val strictMapper = jacksonObjectMapper()

    fun get(username: String): PreferenceResponse {
        val entity = repo.findByUsername(username) ?: return withLivePrivacyMask(PreferenceResponse())
        return withLivePrivacyMask(readStored(entity))
    }

    fun update(username: String, request: PreferenceUpdateRequest, source: String): PreferenceResponse {
        val entity = repo.findByUsername(username)
            ?: UserPreferenceEntity().apply { this.username = username }

        // 深度合并: 只覆盖 request 中非 null 的 section。
        // privacyMask 不在此处写穿透：DTO 缺省无法区分「字段缺省」与「显式 false」，
        // WebUI 的节级合并会把缺省值误当显式值回写打码开关；WebUI 面板走
        // /privacy/mask 端点，App 走 replace() 的显式字段写穿透。存储副本可能
        // 过期——所有读取路径（get/getRaw）都以 ServerConfig 实时值投影覆盖。
        val current = readStored(entity)
        val merged = PreferenceResponse(
            general = request.general ?: current.general,
            reader = request.reader ?: current.reader,
            privacy = request.privacy ?: current.privacy,
        )

        entity.preferences = mapper.writeValueAsString(merged)
        // v4（WP2）：updatedAt 由服务器打戳（统一水位，增量 pull 的 since 由此推进）。
        entity.updatedAt = stamp()
        entity.updatedBy = source
        repo.save(entity)
        return merged
    }

    /**
     * 同步用: 全量覆盖，LWW —— v4 判据：仅当 incoming.lastModified >= 存量 updatedAt
     * （同戳后到胜）或无存量时覆盖；服务器打戳覆盖后 updatedAt = syncClock.next()。
     * @return true = 本次写入生效（SyncService 用它聚合 preferences 的 sync.update 计数）。
     */
    fun replace(username: String, json: String, source: String, lastModified: Long): Boolean {
        val existing = repo.findByUsername(username)
        // 旧设备的 push 不得覆盖新值；同戳放行（后到胜）。
        if (existing != null && lastModified < existing.updatedAt) return false
        val entity = existing ?: UserPreferenceEntity().apply { this.username = username }
        entity.preferences = normalize(json)
        // v4（WP2）：服务器统一打戳——不再保留客户端 lastModified（v4 语义基线：客户端
        // 上送值不落库），pull 回显的水位即本次服务器戳，增量 pull 不会漏发。
        entity.updatedAt = stamp()
        entity.updatedBy = source
        repo.save(entity)
        // App push 写穿透内容打码开关：App 导出的隐私节总是携带 privacyMask（全量文档），
        // 仅在字段显式出现时生效——旧版 App 的 push 没有该字段，绝不触碰打码开关
        // （Jackson 缺省无法区分「缺字段」与「显式 false」，必须查原始树）。
        val root = runCatching { strictMapper.readTree(json) }.getOrNull()
        val maskNode = root?.path("privacy")?.path("privacyMask")
        if (maskNode != null && maskNode.isBoolean) {
            val current = serverConfig.getBoolean(ServerConfigService.KEY_PRIVACY_MASK)
            if (maskNode.booleanValue() != current) {
                serverConfig.setBoolean(ServerConfigService.KEY_PRIVACY_MASK, maskNode.booleanValue())
            }
        }
        return true
    }

    fun getRaw(username: String): String {
        val raw = repo.findByUsername(username)?.preferences ?: "{}"
        // sync/pull 读取路径：把 privacyMask 投影为 ServerConfig 的实时值，
        // 让 WebUI 面板（/privacy/mask）侧的变更能到达 App。
        val root = runCatching { strictMapper.readTree(raw) }.getOrNull() as? ObjectNode ?: return raw
        val privacy = root.get("privacy") as? ObjectNode ?: root.putObject("privacy")
        privacy.put("privacyMask", serverConfig.getBoolean(ServerConfigService.KEY_PRIVACY_MASK))
        return root.toString()
    }

    /** 读取投影：privacy.privacyMask 一律以打码开关的权威实时值为准。 */
    private fun withLivePrivacyMask(response: PreferenceResponse): PreferenceResponse =
        response.copy(
            privacy = response.privacy.copy(
                privacyMask = serverConfig.getBoolean(ServerConfigService.KEY_PRIVACY_MASK),
            ),
        )

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
