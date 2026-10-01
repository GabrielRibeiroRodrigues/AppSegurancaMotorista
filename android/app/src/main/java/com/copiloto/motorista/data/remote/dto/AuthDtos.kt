package com.copiloto.motorista.data.remote.dto

import com.squareup.moshi.Json

/** Request bodies and responses for the JWT auth endpoints (Module 3). */

data class RegisterRequest(
    @Json(name = "username") val username: String,
    @Json(name = "email") val email: String? = null,
    @Json(name = "password") val password: String,
)

data class LoginRequest(
    @Json(name = "username") val username: String,
    @Json(name = "password") val password: String,
)

data class RefreshRequest(
    @Json(name = "refresh") val refresh: String,
)

/** Access + refresh pair returned by login and register. */
data class TokenPair(
    @Json(name = "access") val access: String,
    @Json(name = "refresh") val refresh: String,
)

/** The refresh endpoint returns a new access token (refresh stays the same). */
data class RefreshResponse(
    @Json(name = "access") val access: String,
)
