package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.web.entity.ServerConfigEntity
import com.hippo.ehviewer.web.repository.ServerConfigRepository
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service

@Service
class ServerConfigService(private val repo: ServerConfigRepository) {

    fun get(key: String, default: String = ""): String =
        repo.findById(key).map { it.value }.orElse(default)

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        get(key, default.toString()).toBoolean()

    fun getLong(key: String, default: Long = 0): Long =
        get(key, default.toString()).toLongOrNull() ?: default

    fun set(key: String, value: String) {
        val entity = repo.findById(key).orElse(ServerConfigEntity().apply { this.key = key })
        entity.value = value
        repo.save(entity)
    }

    fun setBoolean(key: String, value: Boolean) = set(key, value.toString())

    /** 启动时将 EhCoreConfigProperties 的默认值写入 DB（仅首次） */
    @PostConstruct
    fun initDefaults() {
        defaults.forEach { (k, v) ->
            if (!repo.existsById(k)) repo.save(ServerConfigEntity().apply { key = k; value = v })
        }
    }

    companion object {
        const val KEY_REQUIRE_AUTH = "security.require_auth"
        const val KEY_SESSION_TIMEOUT = "security.session_timeout"

        val defaults = mapOf(
            KEY_REQUIRE_AUTH to "false",
            KEY_SESSION_TIMEOUT to "86400",
        )
    }
}
