package com.stockpulse.auth.controller

import com.stockpulse.auth.dto.*
import com.stockpulse.common.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
class AuthController {

    @PostMapping("/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signup(@RequestBody request: SignupRequest): ApiResponse<SignupResponse> {
        // TODO(Phase 1): 실제 회원가입 로직 (DB 저장, 비밀번호 해시)
        return ApiResponse.ok(SignupResponse(userId = 1L, email = request.email))
    }

    @PostMapping("/auth/login")
    fun login(@RequestBody request: LoginRequest): ApiResponse<TokenResponse> {
        // TODO(Phase 1): 실제 인증 로직 (DB 조회, JWT 발급)
        return ApiResponse.ok(
            TokenResponse(
                accessToken = "stub-access-token",
                refreshToken = "stub-refresh-token"
            )
        )
    }

    @PostMapping("/auth/refresh")
    fun refresh(@RequestHeader("Authorization") authorization: String): ApiResponse<Map<String, Any>> {
        // TODO(Phase 1): Refresh 토큰 검증 + 새 Access 토큰 발급
        return ApiResponse.ok(mapOf("accessToken" to "stub-new-access-token", "expiresIn" to 3600))
    }

    @PostMapping("/auth/logout")
    fun logout(@RequestHeader("Authorization") authorization: String): ApiResponse<Unit> {
        // TODO(Phase 1): Redis에서 Refresh 토큰 무효화
        return ApiResponse(success = true)
    }

    @GetMapping("/users/me")
    fun me(@RequestHeader("X-User-Id") userId: String): ApiResponse<UserResponse> {
        // TODO(Phase 1): X-User-Id로 DB 조회
        return ApiResponse.ok(UserResponse(userId = userId.toLong(), email = "user@example.com", nickname = "테스터"))
    }
}
