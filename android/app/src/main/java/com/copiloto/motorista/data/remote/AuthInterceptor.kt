package com.copiloto.motorista.data.remote

import com.copiloto.motorista.data.settings.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

private const val NO_AUTH_HEADER = "No-Auth"
private const val AUTHORIZATION = "Authorization"

/**
 * Attaches `Authorization: Bearer <access>` to every request except those marked
 * with the `No-Auth` header (login/register/refresh) (Module 3).
 */
class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.header(NO_AUTH_HEADER) != null) {
            return chain.proceed(request.newBuilder().removeHeader(NO_AUTH_HEADER).build())
        }

        val token = runBlocking { tokenStore.accessToken() }
        val authed = if (token != null) {
            request.newBuilder().header(AUTHORIZATION, "Bearer $token").build()
        } else {
            request
        }
        return chain.proceed(authed)
    }
}
