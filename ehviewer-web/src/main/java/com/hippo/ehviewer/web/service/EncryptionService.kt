package com.hippo.ehviewer.web.service

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Service
class EncryptionService {
    private val passwordEncoder = BCryptPasswordEncoder()
    private val gcmTagLength = 128
    private val gcmIvLength = 12
    private val aesKeyLength = 32

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
        val keyBytes = deriveKey(key)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val iv = ByteArray(gcmIvLength)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(gcmTagLength, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        val combined = iv + encrypted
        return Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(encryptedData: String, key: String): String {
        val keyBytes = deriveKey(key)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val combined = Base64.getDecoder().decode(encryptedData)
        val iv = combined.copyOfRange(0, gcmIvLength)
        val encrypted = combined.copyOfRange(gcmIvLength, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(gcmTagLength, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        val decrypted = cipher.doFinal(encrypted)
        return String(decrypted, Charsets.UTF_8)
    }

    private fun deriveKey(key: String): ByteArray {
        val raw = key.toByteArray(Charsets.UTF_8)
        return if (raw.size >= aesKeyLength) {
            raw.copyOf(aesKeyLength)
        } else {
            val padded = ByteArray(aesKeyLength)
            raw.copyInto(padded)
            padded
        }
    }
}
