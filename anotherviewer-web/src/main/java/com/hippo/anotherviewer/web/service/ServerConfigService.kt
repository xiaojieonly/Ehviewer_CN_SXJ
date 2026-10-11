package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.entity.ServerConfigEntity
import com.hippo.anotherviewer.web.repository.ServerConfigRepository
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import java.io.File
import java.util.Optional

@Service
class ServerConfigService(
    private val repo: ServerConfigRepository,
    private val encryptionService: EncryptionService,
    private val config: SiteCoreConfigProperties,
) {

    // 60s 单键缓存（性能快赢）：AuthTokenFilter 与 PrivacyMaskFilter 每个请求各
    // 读一次 server_config，直接打到 SQLite 是明显的请求放大。读路径仍按原形
    // 态 findById（测试替身按此打桩），命中过的键在 TTL 内走内存（含键不存在
    // 的负缓存）；写操作清空缓存，下次读重新查库。
    private val cacheLock = Any()
    private val cache = HashMap<String, Optional<String>>()
    private var cacheResetAtMs = 0L

    fun get(key: String, default: String = ""): String {
        val raw = readCached(key).orElse(default)
        if (isSecretKey(key) && raw.startsWith(ENC_PREFIX)) {
            // Encrypted at rest; decrypt transparently so consumers keep reading plaintext.
            return runCatching { encryptionService.decrypt(raw.removePrefix(ENC_PREFIX), encryptionKey()) }
                .getOrDefault("")
        }
        return raw
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean {
        if (key == KEY_REQUIRE_AUTH) {
            System.getenv(ENV_REQUIRE_AUTH)?.let { envValue ->
                return envValue.trim().toBoolean()
            }
        }
        return get(key, default.toString()).toBoolean()
    }

    fun getLong(key: String, default: Long = 0): Long =
        get(key, default.toString()).toLongOrNull() ?: default

    fun set(key: String, value: String) {
        val stored = if (isSecretKey(key) && value.isNotEmpty()) {
            // Encrypt secrets at rest; never store plaintext.
            ENC_PREFIX + encryptionService.encrypt(value, encryptionKey())
        } else {
            value
        }
        val entity = repo.findById(key).orElse(ServerConfigEntity().apply { this.key = key })
        entity.value = stored
        repo.save(entity)
        invalidate()
    }

    fun setBoolean(key: String, value: Boolean) = set(key, value.toString())

    /** MASTER-2026-08-22 P2：前缀枚举与删除（sync.prov.* 孤儿键维护用）。 */
    fun findByKeyStartingWith(prefix: String): List<ServerConfigEntity> =
        repo.findByKeyStartingWith(prefix)

    fun delete(entity: ServerConfigEntity) {
        repo.delete(entity)
        invalidate()
    }

    /** 启动时将 SiteCoreConfigProperties 的默认值写入 DB（仅首次） */
    @PostConstruct
    fun initDefaults() {
        defaults.forEach { (k, v) ->
            if (!repo.existsById(k)) repo.save(ServerConfigEntity().apply { key = k; value = v })
        }
        invalidate()
        migrateV4SyncStrategy()
    }

    /**
     * v4（WP2）一次性策略迁移：v4 默认策略 = lww（契约 §1.4 基线调整）。
     *
     * 存量库若还留着 v1/v2 时代的 `sync.conflict_strategy = device_priority`（当初的
     * 出厂默认，而非用户显式选择），首次升级到 v4 时改写成 lww，否则老库永远停在
     * A 策略而新装是 lww，行为不一致。**直接读 repository 防缓存**：initDefaults
     * 刚写入的默认值必须被本次迁移看到（缓存 TTL 内 readCached 可能还是旧值）。
     *
     * 幂等：marker 存在即跳过——用户迁移后显式改回 device_priority 不再被改写
     * （marker 与「用户选择」的语义区分即在此：迁移只做一次）。
     */
    private fun migrateV4SyncStrategy() {
        if (repo.existsById(KEY_SYNC_V4_STRATEGY_MIGRATED)) return
        val current = repo.findById(KEY_SYNC_CONFLICT_STRATEGY).map { it.value }.orElse(null)
        if (current == "device_priority") {
            val entity = repo.findById(KEY_SYNC_CONFLICT_STRATEGY).orElse(ServerConfigEntity().apply { key = KEY_SYNC_CONFLICT_STRATEGY })
            entity.value = "lww"
            repo.save(entity)
            invalidate()
        }
        repo.save(ServerConfigEntity().apply { key = KEY_SYNC_V4_STRATEGY_MIGRATED; value = "true" })
        invalidate()
    }

    /** TTL 内命中缓存直接返回；过期或未命中则 findById 回源。synchronized：读多写少，简单为上。 */
    private fun readCached(key: String): Optional<String> {
        synchronized(cacheLock) {
            val now = System.currentTimeMillis()
            if (now - cacheResetAtMs >= CACHE_TTL_MS) {
                cache.clear()
                cacheResetAtMs = now
            }
            return cache.computeIfAbsent(key) { k -> repo.findById(k).map { it.value } }
        }
    }

    private fun invalidate() {
        synchronized(cacheLock) {
            cache.clear()
            cacheResetAtMs = System.currentTimeMillis()
        }
    }

    private fun encryptionKey(): String {
        val file = File(config.security.encryptionKeyPath)
        if (file.exists()) return file.readText().trim()
        val key = encryptionService.generateToken()
        file.parentFile?.mkdirs()
        file.writeText(key)
        return key
    }

    companion object {
        /** 单键缓存有效期：过期后各键的下一个读请求触发回源。 */
        const val CACHE_TTL_MS = 60_000L

        const val KEY_REQUIRE_AUTH = "security.require_auth"
        const val KEY_SESSION_TIMEOUT = "security.session_timeout"
        // 免码配对（task/4-auto-pair）：LAN 单用户默认开启；配置 setup key 后
        // 客户端必须携带该 key 才能自动注册设备。
        const val KEY_AUTO_PAIRING = "security.auto_pairing"
        const val KEY_SETUP_KEY = "security.setup_key"
        const val KEY_DOWNLOAD_PATH = "download.path"
        const val KEY_CACHE_PATH = "cache.path"
        // 下载数值组（下载延迟/超时、画廊与图片并发、缓存容量）：settings PUT
        // 落盘 + 启动回喂 SiteCoreConfigProperties，与 path 双键同一套管线。
        const val KEY_DOWNLOAD_DELAY = "download.download_delay"
        const val KEY_DOWNLOAD_TIMEOUT = "download.download_timeout"
        const val KEY_MAX_CONCURRENT_GALLERIES = "download.max_concurrent_galleries"
        const val KEY_MAX_CONCURRENT_IMAGES = "download.max_concurrent_images"
        const val KEY_CACHE_SIZE_MB = "cache.size_mb"
        // App 推送下载开关（openapi.yaml 上传端点要求「upload disabled → 400」）。
        const val KEY_UPLOAD_ENABLED = "download.upload_enabled"
        const val ENV_REQUIRE_AUTH = "ANOTHERVIEWER_REQUIRE_AUTH"
        private const val ENC_PREFIX = "enc:v1:"

        // SyncPolicy 持久化（契约 v2 §8，ADR-0003）：跨重启保存，/api/v1/sync/policy 读写。
        const val KEY_SYNC_CONFLICT_STRATEGY = "sync.conflict_strategy"
        const val KEY_SYNC_CLIENT_TIER = "sync.client_tier"
        const val KEY_SYNC_AUTO_SYNC_INTERVAL_SEC = "sync.auto_sync_interval_sec"
        /** v4（WP2）策略迁移完成标志：存在即表示存量 device_priority 已一次性改写为 lww。 */
        const val KEY_SYNC_V4_STRATEGY_MIGRATED = "sync.v4_strategy_migrated"

        // Sync 行级来源（last-writer deviceId）命名空间前缀；具体键由 SyncService 生成。
        const val KEY_PREFIX_SYNC_PROVENANCE = "sync.prov."

        // Gallery Site 站点选择（Settings.gallery_site）：0=e-hentai, 1=exhentai。
        const val KEY_SITE_GALLERY = "site.gallery_site"

        // 内容打码模式（2026-09-04）：服务端权威开关——开启时 PrivacyMaskFilter
        // 对内容类 JSON 响应统一脱敏（Agent 等无头客户端也拿到脱敏数据）。
        const val KEY_PRIVACY_MASK = "privacy.mask_enabled"

        // 图像处理×EntryPoint（2026-09-08）：端点/token 走 settings 双写；
        // token 与 proxy.password 同走 enc:v1: 加密落盘（见 isSecretKey）。
        const val KEY_ENTRYPOINT_URL = "processing.entrypoint_url"
        const val KEY_ENTRYPOINT_TOKEN = "processing.entrypoint_token"
        const val KEY_AUTOMATION_ENABLED = "processing.automation_enabled"
        const val KEY_PERIODIC_ENABLED = "processing.periodic_enabled"
        const val KEY_PERIODIC_INTERVAL = "processing.periodic_interval_minutes"
        // ProcessingType.name() → {"module","capability","params":{...}} 的 JSON 映射表。
        const val KEY_TYPE_MAPPING = "processing.type_mapping"
        // 定期补跑上次触发时间（epoch millis，调度器自记录，无 UI）。
        const val KEY_AUTOMATION_LAST_SCAN = "processing.automation_last_scan"

        val defaults = mapOf(
            // LAN personal deployment: auth is off by default; the operator
            // opts in via ANOTHERVIEWER_REQUIRE_AUTH or a DB value.
            KEY_REQUIRE_AUTH to "false",
            KEY_SESSION_TIMEOUT to "86400",
            // LAN 单用户部署：免码配对默认开启；setup key 默认不配置。
            KEY_AUTO_PAIRING to "true",
            // App 推送下载默认开启。
            KEY_UPLOAD_ENABLED to "true",
            // 策略缺省 = v4 基线 lww（STRATEGY_DEFAULT 更名）；CLIENT_TIER=1、
            // AUTO_SYNC=10s（ADR-0006 D7 静默节拍）对齐契约常量。
            KEY_SYNC_CONFLICT_STRATEGY to "lww",
            KEY_SYNC_CLIENT_TIER to "1",
            KEY_SYNC_AUTO_SYNC_INTERVAL_SEC to "10",
            KEY_SITE_GALLERY to "0",
            KEY_PRIVACY_MASK to "false",
            // EntryPoint 副武器（同机 141:9800）；token 默认空=未配置。
            KEY_ENTRYPOINT_URL to "http://192.168.6.141:9800",
            KEY_AUTOMATION_ENABLED to "false",
            KEY_PERIODIC_ENABLED to "false",
            KEY_PERIODIC_INTERVAL to "60",
        )

        /** 落盘需加密的键（静态加密 enc:v1:，get 时透明解密）。 */
        private fun isSecretKey(key: String): Boolean =
            key == WebProxyManager.KEY_PASSWORD || key == KEY_ENTRYPOINT_TOKEN
    }
}
