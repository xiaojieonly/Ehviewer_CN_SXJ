package com.hippo.anotherviewer.web.service

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Service
class EncryptionService {
    private val passwordEncoder = BCryptPasswordEncoder()
    private val gcmTagLength = 128
    private val gcmIvLength = 12
    private val aesKeyLength = 32

    // 密文格式布局（encrypt 只产出新格式）：
    //   新格式: Base64(magic(4B "AVK2") + salt(16B) + iv(12B) + GCM 密文 + tag(16B))
    //     密钥派生: PBKDF2WithHmacSHA256(key, salt, 100_000 次) → 32 字节
    //     再迁移时改 magic 并保留 salt/迭代参数即可向后兼容解旧密文
    //   旧格式（仅 decrypt 兼容，无 magic）: Base64(iv(12B) + GCM 密文 + tag(16B))
    //     密钥派生: 旧 deriveKey 截断/补零到 32 字节
    private val magic = "AVK2".toByteArray(Charsets.US_ASCII)
    private val saltLength = 16
    private val pbkdf2Iterations = 100_000

    fun hashPassword(password: String): String {
        return passwordEncoder.encode(password)
    }

    fun verifyPassword(password: String, hash: String): Boolean {
        return passwordEncoder.matches(password, hash)
    }

    fun generateToken(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun encrypt(data: String, key: String): String {
        val salt = ByteArray(saltLength)
        SecureRandom().nextBytes(salt)
        val secretKey = deriveKeyPbkdf2(key, salt)
        val iv = ByteArray(gcmIvLength)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(gcmTagLength, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        val combined = combine(magic, salt, iv, encrypted)
        return Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(encryptedData: String, key: String): String {
        val combined = Base64.getDecoder().decode(encryptedData)
        if (hasMagic(combined)) {
            val salt = combined.copyOfRange(magic.size, magic.size + saltLength)
            val iv = combined.copyOfRange(magic.size + saltLength, magic.size + saltLength + gcmIvLength)
            val encrypted = combined.copyOfRange(magic.size + saltLength + gcmIvLength, combined.size)
            val secretKey = deriveKeyPbkdf2(key, salt)
            return gcmDecrypt(secretKey, iv, encrypted)
        }
        // 旧格式兼容路径：无 magic，iv 前缀 12B，密钥为旧截断/补零派生
        return try {
            if (combined.size < gcmIvLength) {
                throw IllegalArgumentException("密文长度不足，无法按旧格式解密")
            }
            val iv = combined.copyOfRange(0, gcmIvLength)
            val encrypted = combined.copyOfRange(gcmIvLength, combined.size)
            gcmDecrypt(deriveKeyLegacy(key), iv, encrypted)
        } catch (e: Exception) {
            throw IllegalArgumentException("旧格式密文解密失败（密钥错误或数据已损坏）", e)
        }
    }

    private fun gcmDecrypt(secretKey: SecretKeySpec, iv: ByteArray, encrypted: ByteArray): String {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(gcmTagLength, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            val decrypted = cipher.doFinal(encrypted)
            return String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            throw IllegalArgumentException("密文解密失败（GCM 认证失败或数据已损坏）", e)
        }
    }

    private fun hasMagic(combined: ByteArray): Boolean {
        if (combined.size < magic.size + saltLength + gcmIvLength) return false
        for (i in magic.indices) {
            if (combined[i] != magic[i]) return false
        }
        return true
    }

    private fun combine(magic: ByteArray, salt: ByteArray, iv: ByteArray, encrypted: ByteArray): ByteArray {
        val combined = ByteArray(magic.size + salt.size + iv.size + encrypted.size)
        var offset = 0
        magic.copyInto(combined, offset)
        offset += magic.size
        salt.copyInto(combined, offset)
        offset += salt.size
        iv.copyInto(combined, offset)
        offset += iv.size
        encrypted.copyInto(combined, offset)
        return combined
    }

    private fun deriveKeyPbkdf2(key: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(key.toCharArray(), salt, pbkdf2Iterations, aesKeyLength * 8)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    // 旧密钥派生：截断/补零到 32 字节，仅用于解密存量旧格式密文
    private fun deriveKeyLegacy(key: String): SecretKeySpec {
        val raw = key.toByteArray(Charsets.UTF_8)
        val keyBytes = if (raw.size >= aesKeyLength) {
            raw.copyOf(aesKeyLength)
        } else {
            val padded = ByteArray(aesKeyLength)
            raw.copyInto(padded)
            padded
        }
        return SecretKeySpec(keyBytes, "AES")
    }
}
