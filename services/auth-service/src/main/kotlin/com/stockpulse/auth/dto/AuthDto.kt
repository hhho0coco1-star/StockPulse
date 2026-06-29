package com.stockpulse.auth.dto

data class SignupRequest(
    val email: String,
    val password: String,
    val nickname: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long = 3600
)

data class SignupResponse(
    val userId: Long,
    val email: String
)

data class UserResponse(
    val userId: Long,
    val email: String,
    val nickname: String
)
