package com.stockpulse.notification.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.stockpulse.notification.domain.AlertRule
import com.stockpulse.notification.domain.DeviceToken
import com.stockpulse.notification.domain.NotificationLog
import com.stockpulse.notification.dto.*
import com.stockpulse.notification.repository.AlertRuleRepository
import com.stockpulse.notification.repository.DeviceTokenRepository
import com.stockpulse.notification.repository.NotificationLogRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AlertService(
    private val alertRuleRepository: AlertRuleRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val notificationLogRepository: NotificationLogRepository,
    private val objectMapper: ObjectMapper
) {

    // ── N1: 규칙 목록 ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun listRules(userId: Long): AlertListResponse {
        val rules = alertRuleRepository.findAllByUserId(userId)
            .map { it.toResponse() }
        return AlertListResponse(rules = rules)
    }

    // ── N2: 규칙 생성 ─────────────────────────────────────────────────────────

    @Transactional
    fun createRule(userId: Long, request: AlertRequest): AlertRuleResponse {
        require(request.symbol.isNotBlank()) { "symbol must not be blank" }
        require(request.type in listOf("TARGET_PRICE", "CHANGE_RATE", "NEWS")) {
            "type must be one of TARGET_PRICE, CHANGE_RATE, NEWS"
        }
        require(request.condition.isNotEmpty()) { "condition must not be empty" }

        val conditionJson = objectMapper.writeValueAsString(request.condition)
        val rule = alertRuleRepository.save(
            AlertRule(
                userId = userId,
                symbol = request.symbol.trim(),
                type = request.type,
                condition = conditionJson,
                enabled = request.enabled
            )
        )
        return rule.toResponse()
    }

    // ── N3: 규칙 수정 ─────────────────────────────────────────────────────────

    @Transactional
    fun updateRule(userId: Long, ruleId: Long, request: AlertUpdateRequest): AlertRuleResponse {
        val rule = findRuleOrThrow(ruleId)
        check(rule.userId == userId) { "FORBIDDEN" }
        request.condition?.let { rule.condition = objectMapper.writeValueAsString(it) }
        request.enabled?.let { rule.enabled = it }
        return alertRuleRepository.save(rule).toResponse()
    }

    // ── N4: 규칙 삭제 ─────────────────────────────────────────────────────────

    @Transactional
    fun deleteRule(userId: Long, ruleId: Long) {
        val rule = findRuleOrThrow(ruleId)
        check(rule.userId == userId) { "FORBIDDEN" }
        alertRuleRepository.delete(rule)
    }

    // ── N5: 디바이스 토큰 등록 ────────────────────────────────────────────────

    @Transactional
    fun registerDevice(userId: Long, request: DeviceRequest): DeviceTokenResponse {
        return try {
            val device = deviceTokenRepository.save(
                DeviceToken(userId = userId, token = request.token, platform = request.platform)
            )
            device.toResponse()
        } catch (e: DataIntegrityViolationException) {
            throw DuplicateTokenException(request.token)
        }
    }

    // ── N6: 토큰 해제 ─────────────────────────────────────────────────────────

    @Transactional
    fun removeDevice(userId: Long, token: String): Map<String, Any> {
        val device = deviceTokenRepository.findByToken(token)
            ?: throw NoSuchElementException("NOT_FOUND:$token")
        deviceTokenRepository.delete(device)
        return mapOf("token" to token, "removed" to true)
    }

    // ── N7: 알림 이력 조회 ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun listNotifications(userId: Long, page: Int, size: Int): NotificationPageResponse {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = notificationLogRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable)
        return NotificationPageResponse(
            content = result.content.map { it.toResponse() },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private fun findRuleOrThrow(ruleId: Long): AlertRule =
        alertRuleRepository.findById(ruleId).orElseThrow { NoSuchElementException("NOT_FOUND:$ruleId") }

    fun AlertRule.toResponse(): AlertRuleResponse {
        val conditionMap: Map<String, Any> = objectMapper.readValue(condition)
        return AlertRuleResponse(
            id = id, userId = userId, symbol = symbol, type = type,
            condition = conditionMap, enabled = enabled, createdAt = createdAt
        )
    }

    private fun DeviceToken.toResponse() = DeviceTokenResponse(
        id = id, userId = userId, token = token, platform = platform, createdAt = createdAt
    )

    fun NotificationLog.toResponse(): NotificationResponse {
        val dataMap: Map<String, Any> = objectMapper.readValue(data)
        return NotificationResponse(
            id = id, userId = userId, type = type, title = title, body = body,
            data = dataMap, read = isRead, createdAt = createdAt
        )
    }
}

class DuplicateTokenException(val token: String) : RuntimeException("CONFLICT:$token")
