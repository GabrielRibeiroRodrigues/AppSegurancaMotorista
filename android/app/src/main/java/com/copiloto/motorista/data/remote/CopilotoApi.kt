package com.copiloto.motorista.data.remote

import com.copiloto.motorista.data.remote.dto.DriverProfileDto
import com.copiloto.motorista.data.remote.dto.RideHistoryDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT

/**
 * Django REST endpoints (Module F). A device id header identifies the driver
 * without requiring a login for the MVP.
 */
interface CopilotoApi {

    @POST("api/rides/")
    suspend fun createRide(
        @Header("X-Device-Id") deviceId: String,
        @Body ride: RideHistoryDto,
    ): RideHistoryDto

    @GET("api/profile/")
    suspend fun getProfile(
        @Header("X-Device-Id") deviceId: String,
    ): DriverProfileDto

    @PUT("api/profile/")
    suspend fun updateProfile(
        @Header("X-Device-Id") deviceId: String,
        @Body profile: DriverProfileDto,
    ): DriverProfileDto
}
