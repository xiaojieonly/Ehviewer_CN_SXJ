package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.web.dto.AuthResponse
import com.hippo.ehviewer.web.dto.AuthStatusResponse
import com.hippo.ehviewer.web.dto.LoginRequest
import com.hippo.ehviewer.web.dto.RegisterRequest
import com.hippo.ehviewer.web.entity.AuthConfigEntity
import com.hippo.ehviewer.web.repository.AuthConfigRepository
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class EhAuthService(
    private val authRepository: AuthConfigRepository,
    private val encryptionService: EncryptionService
) {
    private val tokenStore = ConcurrentHashMap<String, String>()

    fun register(request: RegisterRequest): AuthResponse {
        if (authRepository.existsByUsername(request.username)) {
            return AuthResponse(false, "Username already exists")
        }
        val entity = AuthConfigEntity().apply {
            username = request.username
            passwordHash = encryptionService.hashPassword(request.password)
            createdAt = System.currentTimeMillis()
        }
        authRepository.save(entity)
        val token = encryptionService.generateToken()
        tokenStore[token] = request.username
        return AuthResponse(true, "Registration successful", token, request.username)
    }

    fun login(request: LoginRequest): AuthResponse {
        val entity = authRepository.findByUsername(request.username)
            ?: return AuthResponse(false, "Invalid username or password")
        if (!encryptionService.verifyPassword(request.password, entity.passwordHash)) {
            return AuthResponse(false, "Invalid username or password")
        }
        entity.lastLoginAt = System.currentTimeMillis()
        authRepository.save(entity)
        val token = encryptionService.generateToken()
        tokenStore[token] = request.username
        return AuthResponse(true, "Login successful", token, request.username)
    }

    fun getStatus(token: String?): AuthStatusResponse {
        if (token == null) return AuthStatusResponse(false)
        val username = tokenStore[token] ?: return AuthStatusResponse(false)
        return AuthStatusResponse(true, username)
    }

    fun validateToken(token: String?): String? {
        if (token == null) return null
        return tokenStore[token]
    }

    fun logout(token: String?) {
        if (token != null) {
            tokenStore.remove(token)
        }
    }
}
