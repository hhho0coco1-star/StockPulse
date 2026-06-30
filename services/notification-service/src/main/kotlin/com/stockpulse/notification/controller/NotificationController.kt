package com.stockpulse.notification.controller

import com.stockpulse.common.ApiResponse
import com.stockpulse.notification.dto.AlertRequest
import com.stockpulse.notification.dto.AlertUpdateRequest
import com.stockpulse.notification.dto.DeviceRequest
import com.stockpulse.notification.service.AlertService
import com.stockpulse.notification.service.DuplicateTokenException
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
class NotificationController(private val alertService: AlertService) {

    // ── N1: 규칙 목록 ─────────────────────────────────────────────────────────

    @GetMapping("/alerts")
    fun listRules(
        @RequestHeader("X-User-Id") userId: Long
    ): ResponseEntity<ApiResponse<Any>> {
        val data = alertService.listRules(userId)
        return ResponseEntity.ok(ApiResponse.ok(data))
    }

    // ── N2: 규칙 생성 ─────────────────────────────────────────────────────────

    @PostMapping("/alerts")
    fun createRule(
        @RequestHeader("X-User-Id") userId: Long,
        @Valid @RequestBody request: AlertRequest
    ): ResponseEntity<ApiResponse<Any>> {
        return try {
            val data = alertService.createRule(userId, request)
            ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.fail("VALIDATION_ERROR", e.message ?: "Invalid input"))
        }
    }

    // ── N3: 규칙 수정 ─────────────────────────────────────────────────────────

    @PutMapping("/alerts/{id}")
    fun updateRule(
        @PathVariable id: Long,
        @RequestHeader("X-User-Id") userId: Long,
        @RequestBody request: AlertUpdateRequest
    ): ResponseEntity<ApiResponse<Any>> {
        return try {
            val data = alertService.updateRule(userId, id, request)
            ResponseEntity.ok(ApiResponse.ok(data))
        } catch (e: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("NOT_FOUND", "Alert rule not found"))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail("FORBIDDEN", "Not your alert rule"))
        }
    }

    // ── N4: 규칙 삭제 ─────────────────────────────────────────────────────────

    @DeleteMapping("/alerts/{id}")
    fun deleteRule(
        @PathVariable id: Long,
        @RequestHeader("X-User-Id") userId: Long
    ): ResponseEntity<ApiResponse<Any>> {
        return try {
            alertService.deleteRule(userId, id)
            ResponseEntity.ok(ApiResponse.ok(mapOf("id" to id, "deleted" to true)))
        } catch (e: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("NOT_FOUND", "Alert rule not found"))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail("FORBIDDEN", "Not your alert rule"))
        }
    }

    // ── N5: 디바이스 토큰 등록 ────────────────────────────────────────────────

    @PostMapping("/devices")
    fun registerDevice(
        @RequestHeader("X-User-Id") userId: Long,
        @Valid @RequestBody request: DeviceRequest
    ): ResponseEntity<ApiResponse<Any>> {
        return try {
            val data = alertService.registerDevice(userId, request)
            ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data))
        } catch (e: DuplicateTokenException) {
            ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("CONFLICT", "Token already registered"))
        }
    }

    // ── N6: 토큰 해제 ─────────────────────────────────────────────────────────

    @DeleteMapping("/devices/{token}")
    fun removeDevice(
        @PathVariable token: String,
        @RequestHeader("X-User-Id") userId: Long
    ): ResponseEntity<ApiResponse<Any>> {
        return try {
            val data = alertService.removeDevice(userId, token)
            ResponseEntity.ok(ApiResponse.ok(data))
        } catch (e: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("NOT_FOUND", "Token not found"))
        }
    }

    // ── N7: 알림 이력 조회 ────────────────────────────────────────────────────

    @GetMapping("/notifications")
    fun listNotifications(
        @RequestHeader("X-User-Id") userId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ApiResponse<Any>> {
        val data = alertService.listNotifications(userId, page, size)
        return ResponseEntity.ok(ApiResponse.ok(data))
    }
}
