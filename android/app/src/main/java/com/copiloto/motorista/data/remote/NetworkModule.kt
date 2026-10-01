package com.copiloto.motorista.data.remote

import com.copiloto.motorista.BuildConfig
import com.copiloto.motorista.data.settings.TokenStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/** Builds the configured [CopilotoApi] clients (Retrofit + OkHttp + Moshi). */
object NetworkModule {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private fun logging() = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    /** Plain client with no auth logic — used only to refresh tokens. */
    private fun createRefreshApi(baseUrl: String): CopilotoApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging())
            .build()
        return retrofit(baseUrl, client).create(CopilotoApi::class.java)
    }

    /**
     * Authenticated client: injects the bearer token and transparently refreshes
     * it on a 401 (Module 3).
     */
    fun createApi(tokenStore: TokenStore, baseUrl: String = BuildConfig.API_BASE_URL): CopilotoApi {
        val refreshApi = createRefreshApi(baseUrl)
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(tokenStore))
            .authenticator(TokenAuthenticator(tokenStore, refreshApi))
            .addInterceptor(logging())
            .build()
        return retrofit(baseUrl, client).create(CopilotoApi::class.java)
    }
}
