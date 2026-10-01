package com.copiloto.motorista.data.repository

import com.copiloto.motorista.data.remote.CopilotoApi
import com.copiloto.motorista.data.remote.dto.LoginRequest
import com.copiloto.motorista.data.remote.dto.RegisterRequest
import com.copiloto.motorista.data.settings.TokenStore
import kotlinx.coroutines.flow.Flow

/** Sign-up / sign-in and token lifecycle (Module 3 — JWT auth). */
class AuthRepository(
    private val api: CopilotoApi,
    private val tokenStore: TokenStore,
) {

    val isLoggedIn: Flow<Boolean> = tokenStore.isLoggedIn

    suspend fun register(username: String, password: String, email: String?): Result<Unit> =
        runCatching {
            val tokens = api.register(RegisterRequest(username, email?.ifBlank { null }, password))
            tokenStore.save(tokens.access, tokens.refresh)
        }

    suspend fun login(username: String, password: String): Result<Unit> =
        runCatching {
            val tokens = api.login(LoginRequest(username, password))
            tokenStore.save(tokens.access, tokens.refresh)
        }

    suspend fun logout() {
        tokenStore.clear()
    }
}
