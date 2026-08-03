package com.hippo.anotherviewer.web.service

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class EncryptionServiceTest {

    private val service = EncryptionService()

    // ── 新格式（PBKDF2 + salt）────────────────────────────────

    @Test
    fun `新格式加密解密回环`() {
        val data = "proxy-password-中文-密码"
        val key = "master-key-1234567890"

        val encrypted = service.encrypt(data, key)

        assertEquals(data, service.decrypt(encrypted, key))
    }

    @Test
    fun `新格式密文布局含 magic salt iv 与迭代参数`() {
        val data = "secret"
        val key = "k"

        val encrypted = service.encrypt(data, key)
        val decoded = Base64.getDecoder().decode(encrypted)

        // magic(4B "AVK2") + salt(16B) + iv(12B) + 密文 + GCM tag(16B)
        assertEquals(4 + 16 + 12 + data.toByteArray(Charsets.UTF_8).size + 16, decoded.size)
        assertArrayEquals("AVK2".toByteArray(Charsets.US_ASCII), decoded.copyOfRange(0, 4))
        // salt 16B 紧随 magic 之后，随机 salt 保证相同明文每次密文不同
        assertEquals(16, decoded.copyOfRange(4, 20).size)
        // 两次加密同一数据产出不同密文（随机 salt + iv）
        val again = Base64.getDecoder().decode(service.encrypt(data, key))
        assertTrue(!decoded.contentEquals(again))
    }

    // ── 旧格式兼容（复刻旧 deriveKey 截断/补零 + iv 前缀 + 无 magic）──

    @Test
    fun `旧格式密文兼容解密-长密钥截断路径`() {
        val data = "legacy-secret"
        // 32 字节以上密钥走旧截断路径
        val key = "legacy-master-key-0123456789abcdefghijklmnop"

        val encrypted = legacyEncrypt(data, key)

        assertEquals(data, service.decrypt(encrypted, key))
    }

    @Test
    fun `旧格式密文兼容解密-短密钥补零路径`() {
        val data = "legacy-secret-short-key"
        val key = "short" // < 32 字节走旧补零路径

        val encrypted = legacyEncrypt(data, key)

        assertEquals(data, service.decrypt(encrypted, key))
    }

    // ── 错误 key 拒绝 ──────────────────────────────────────────

    @Test
    fun `错误 key 解密新格式密文失败`() {
        val encrypted = service.encrypt("secret", "correct-key")

        assertThrows(IllegalArgumentException::class.java) {
            service.decrypt(encrypted, "wrong-key")
        }
    }

    @Test
    fun `错误 key 解密旧格式密文失败`() {
        val key = "legacy-master-key-0123456789abcdefghijklmnop"
        val encrypted = legacyEncrypt("secret", key)

        assertThrows(IllegalArgumentException::class.java) {
            service.decrypt(encrypted, "another-wrong-key")
        }
    }

    // ── 测试辅助：按旧实现产出旧格式密文 ────────────────────────

    private fun legacyEncrypt(data: String, key: String): String {
        val keyBytes = legacyDeriveKey(key)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        // 旧格式：iv 前缀 12B + 密文，无 magic
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    private fun legacyDeriveKey(key: String): ByteArray {
        val raw = key.toByteArray(Charsets.UTF_8)
        return if (raw.size >= 32) {
            raw.copyOf(32)
        } else {
            val padded = ByteArray(32)
            raw.copyInto(padded)
            padded
        }
    }
}
