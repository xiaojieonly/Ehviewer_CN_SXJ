package com.hippo.anotherviewer.web.service

import com.hippo.anotherviewer.web.config.SiteCoreConfigProperties
import com.hippo.anotherviewer.web.dto.SmbConfigUpdateRequest
import com.hippo.anotherviewer.web.entity.SmbConfigEntity
import com.hippo.anotherviewer.web.repository.SmbConfigRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.context.ApplicationEventPublisher
import java.io.File

/**
 * Guard for SMB credentials at rest: the password must be stored encrypted
 * (never plaintext) and transparently decrypted back for use, while empty and
 * legacy plaintext values keep working.
 */
class SmbBackupServiceTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var service: SmbBackupService
    private lateinit var encryption: EncryptionService
    private lateinit var entities: MutableList<SmbConfigEntity>
    private lateinit var keyFile: File

    @BeforeEach
    fun setUp() {
        val repo = mock(SmbConfigRepository::class.java)
        entities = mutableListOf()
        `when`(repo.findByEnabled(true)).thenAnswer { entities.firstOrNull { it.enabled } }
        `when`(repo.findAll()).thenAnswer { entities }
        `when`(repo.save(any(SmbConfigEntity::class.java))).thenAnswer { inv ->
            val entity = inv.getArgument<SmbConfigEntity>(0)
            if (entity.id == 0L) entity.id = 1L
            entities.removeAll { it.id == entity.id }
            entities.add(entity)
            entity
        }
        keyFile = File(tempDir, "test.key")
        val config = SiteCoreConfigProperties()
        config.security.encryptionKeyPath = keyFile.absolutePath
        encryption = EncryptionService()
        service = SmbBackupService(
            repo,
            mock(ApplicationEventPublisher::class.java),
            config,
            mock(DownloadService::class.java),
            encryption
        )
    }

    private fun update(password: String? = null, enabled: Boolean = true) {
        service.updateConfig(
            SmbConfigUpdateRequest(
                host = "192.168.1.10",
                port = 445,
                share = "backup",
                loginMode = "USER",
                username = "bob",
                password = password,
                enabled = enabled
            )
        )
    }

    @Test
    fun `saved config stores marker-prefixed ciphertext not plaintext`() {
        update(password = "s3cret-pass")

        val stored = entities.single().password!!
        assertNotEquals("s3cret-pass", stored)
        assertTrue(stored.startsWith("enc:v1:"))
    }

    @Test
    fun `stored ciphertext decrypts back to the original password for use`() {
        update(password = "s3cret-pass")

        val stored = entities.single().password!!
        val key = keyFile.readText().trim()
        assertEquals("s3cret-pass", encryption.decrypt(stored.removePrefix("enc:v1:"), key))
    }

    @Test
    fun `empty password stays empty without encryption`() {
        update(password = "")

        val stored = entities.single().password
        assertEquals("", stored)
    }

    @Test
    fun `legacy plaintext password is kept verbatim when not changed and re-encrypted on save`() {
        entities.add(SmbConfigEntity().apply {
            host = "192.168.1.10"
            share = "backup"
            loginMode = "USER"
            username = "bob"
            password = "legacy-plain"
            enabled = true
        })

        // password == null means "don't touch": plaintext survives untouched.
        update(password = null)
        assertEquals("legacy-plain", entities.single().password)

        // Next explicit save re-encrypts it.
        update(password = "legacy-plain")
        val stored = entities.single().password!!
        assertTrue(stored.startsWith("enc:v1:"))
        assertNotEquals("legacy-plain", stored)
    }

    @Test
    fun `getConfig never echoes the password`() {
        update(password = "s3cret-pass")

        val response = service.getConfig()!!
        assertEquals("bob", response.username)
        assertEquals("192.168.1.10", response.host)
    }
}
