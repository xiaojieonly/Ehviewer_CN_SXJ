package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.entity.ServerConfigEntity
import com.hippo.anotherviewer.web.repository.ServerConfigRepository
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import java.io.File

@Service
class ServerConfigService(
    private val repo: ServerConfigRepository,
    private val encryptionService: EncryptionService,
    private val config: SiteCoreConfigProperties,
) {

    fun get(key: String, default: String = ""): String {
        val raw = repo.findById(key).map { it.value }.orElse(default)
        if (key == WebProxyManager.KEY_PASSWORD && raw.startsWith(ENC_PREFIX)) {
            // Encrypted at rest; decrypt transparently so consumers (WebProxyManager,
            // ProxyController, SettingsService) keep reading plaintext.
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
        val stored = if (key == WebProxyManager.KEY_PASSWORD && value.isNotEmpty()) {
            // Encrypt proxy passwords at rest; never store plaintext.
            ENC_PREFIX + encryptionService.encrypt(value, encryptionKey())
        } else {
            value
        }
        val entity = repo.findById(key).orElse(ServerConfigEntity().apply { this.key = key })
        entity.value = stored
        repo.save(entity)
    }

    fun setBoolean(key: String, value: Boolean) = set(key, value.toString())

    /** 启动时将 SiteCoreConfigProperties 的默认值写入 DB（仅首次） */
    @PostConstruct
    fun initDefaults() {
        defaults.forEach { (k, v) ->
            if (!repo.existsById(k)) repo.save(ServerConfigEntity().apply { key = k; value = v })
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
        const val KEY_REQUIRE_AUTH = "security.require_auth"
        const val KEY_SESSION_TIMEOUT = "security.session_timeout"
        const val KEY_DOWNLOAD_PATH = "download.path"
        const val KEY_CACHE_PATH = "cache.path"
        const val ENV_REQUIRE_AUTH = "ANOTHERVIEWER_REQUIRE_AUTH"
        private const val ENC_PREFIX = "enc:v1:"

        // SyncPolicy 持久化（契约 v2 §8，ADR-0003）：跨重启保存，/api/v1/sync/policy 读写。
        const val KEY_SYNC_CONFLICT_STRATEGY = "sync.conflict_strategy"
        const val KEY_SYNC_CLIENT_TIER = "sync.client_tier"
        const val KEY_SYNC_AUTO_SYNC_INTERVAL_SEC = "sync.auto_sync_interval_sec"

        // Sync 行级来源（last-writer deviceId）命名空间前缀；具体键由 SyncService 生成。
        const val KEY_PREFIX_SYNC_PROVENANCE = "sync.prov."

        // Gallery Site 站点选择（Settings.gallery_site）：0=e-hentai, 1=exhentai。
        const val KEY_SITE_GALLERY = "site.gallery_site"

        val defaults = mapOf(
            // LAN personal deployment: auth is off by default; the operator
            // opts in via ANOTHERVIEWER_REQUIRE_AUTH or a DB value.
            KEY_REQUIRE_AUTH to "false",
            KEY_SESSION_TIMEOUT to "86400",
            // 策略缺省 = 契约 §7 常量（STRATEGY_DEFAULT / CLIENT_TIER_DEFAULT / AUTO_SYNC_INTERVAL_SEC_DEFAULT）
            KEY_SYNC_CONFLICT_STRATEGY to "device_priority",
            KEY_SYNC_CLIENT_TIER to "1",
            KEY_SYNC_AUTO_SYNC_INTERVAL_SEC to "900",
            KEY_SITE_GALLERY to "0",
        )
    }
}
