package com.stockpulse.notification.dto

import jakarta.validation.constraints.NotBlank
import java.time.Instant

// ── Request DTOs ──────────────────────────────────────────────────────────────

data class AlertRequest(
    @field:NotBlank(message = "symbol은 필수입니다")
    val symbol: String,
    @field:NotBlank(message = "type은 필수입니다")
    val type: String,
    val condition: Map<String, Any>,
    val enabled: Boolean = true
)

data class AlertUpdateRequest(
    val condition: Map<String, Any>?,
    val enabled: Boolean?
)

data class DeviceRequest(
    @field:NotBlank(message = "token은 필수입니다")
    val token: String,
    @field:NotBlank(message = "platform은 필수입니다")
    val platform: String
)

// ── Response DTOs ─────────────────────────────────────────────────────────────

data class AlertRuleResponse(
    val id: Long,
    val userId: Long,
    val symbol: String,
    val type: String,
    val condition: Map<String, Any>,
    val enabled: Boolean,
    val createdAt: Instant
)

data class AlertListResponse(
    val rules: List<AlertRuleResponse>
)

data class DeviceTokenResponse(
    val id: Long,
    val userId: Long,
    val token: String,
    val platform: String,
    val createdAt: Instant
)

data class RemoveDeviceResponse(
    val token: String,
    val removed: Boolean
)

data class NotificationResponse(
    val id: Long,
    val userId: Long,
    val type: String,
    val title: String,
    val body: String,
    val data: Map<String, Any>,
    val read: Boolean,
    val createdAt: Instant
)

data class NotificationPageResponse(
    val content: List<NotificationResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)
