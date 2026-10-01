package com.copiloto.motorista.data.remote

import com.copiloto.motorista.data.remote.dto.RefreshRequest
import com.copiloto.motorista.data.settings.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * When an authenticated request returns 401, tries once to mint a new access token
 * from the stored refresh token and replays the request. If the refresh fails, the
 * stored tokens are cleared so the app falls back to the login screen (Module 3).
 *
 * [refreshApi] is a plain client with no authenticator, to avoid recursion.
 */
class TokenAuthenticator(
    private val tokenStore: TokenStore,
    private val refreshApi: CopilotoApi,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Give up after a single retry to avoid an infinite 401 loop.
        if (responseCount(response) >= 2) return null

        val refresh = runBlocking { tokenStore.refreshToken() } ?: return null

        val newAccess = runBlocking {
            runCatching { refreshApi.refresh(RefreshRequest(refresh)).access }.getOrNull()
        }

        if (newAccess == null) {
            runBlocking { tokenStore.clear() }
            return null
        }

        runBlocking { tokenStore.updateAccess(newAccess) }
        return response.request.newBuilder()
            .header("Authorization", "Bearer $newAccess")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
