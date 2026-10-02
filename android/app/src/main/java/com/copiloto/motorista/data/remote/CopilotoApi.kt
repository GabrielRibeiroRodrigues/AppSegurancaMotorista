package com.copiloto.motorista.data.remote

import com.copiloto.motorista.data.remote.dto.AlertResponse
import com.copiloto.motorista.data.remote.dto.CreateAlertRequest
import com.copiloto.motorista.data.remote.dto.DriverProfileDto
import com.copiloto.motorista.data.remote.dto.LoginRequest
import com.copiloto.motorista.data.remote.dto.RefreshRequest
import com.copiloto.motorista.data.remote.dto.RefreshResponse
import com.copiloto.motorista.data.remote.dto.RegisterRequest
import com.copiloto.motorista.data.remote.dto.RideHistoryDto
import com.copiloto.motorista.data.remote.dto.TokenPair
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT

/**
 * Django REST endpoints. Auth endpoints carry a `No-Auth` marker so the
 * [AuthInterceptor] does not attach a bearer token to them; every other endpoint
 * is authenticated with the JWT access token (Module 3).
 */
interface CopilotoApi {

    @Headers("No-Auth: true")
    @POST("api/auth/register/")
    suspend fun register(@Body body: RegisterRequest): TokenPair

    @Headers("No-Auth: true")
    @POST("api/auth/login/")
    suspend fun login(@Body body: LoginRequest): TokenPair

    @Headers("No-Auth: true")
    @POST("api/auth/refresh/")
    suspend fun refresh(@Body body: RefreshRequest): RefreshResponse

    @POST("api/rides/")
    suspend fun createRide(@Body ride: RideHistoryDto): RideHistoryDto

    @GET("api/profile/")
    suspend fun getProfile(): DriverProfileDto

    @PUT("api/profile/")
    suspend fun updateProfile(@Body profile: DriverProfileDto): DriverProfileDto

    @POST("api/alerts/")
    suspend fun createAlert(@Body body: CreateAlertRequest): AlertResponse
}
