package com.stockpulse.notification.repository

import com.stockpulse.notification.domain.AlertRule
import com.stockpulse.notification.domain.DeviceToken
import com.stockpulse.notification.domain.NotificationLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AlertRuleRepository : JpaRepository<AlertRule, Long> {
    fun findAllByUserId(userId: Long): List<AlertRule>
    fun findAllBySymbolAndEnabledTrue(symbol: String): List<AlertRule>
}

@Repository
interface DeviceTokenRepository : JpaRepository<DeviceToken, Long> {
    fun findByToken(token: String): DeviceToken?
    fun findAllByUserId(userId: Long): List<DeviceToken>
    fun existsByToken(token: String): Boolean
    fun deleteByToken(token: String)
}

@Repository
interface NotificationLogRepository : JpaRepository<NotificationLog, Long> {
    fun findAllByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): Page<NotificationLog>
}
