package com.stockpulse.notification.domain

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(
    name = "alert_rules",
    indexes = [
        Index(name = "idx_alert_rules_symbol_enabled", columnList = "symbol, enabled"),
        Index(name = "idx_alert_rules_user", columnList = "user_id")
    ]
)
data class AlertRule(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(nullable = false, length = 20)
    val symbol: String,

    @Column(nullable = false, length = 20)
    val type: String,  // TARGET_PRICE | CHANGE_RATE | NEWS

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var condition: String,  // JSON 문자열 저장

    @Column(nullable = false)
    var enabled: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)

@Entity
@Table(
    name = "device_tokens",
    indexes = [Index(name = "idx_device_tokens_user", columnList = "user_id")]
)
data class DeviceToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(nullable = false, length = 512, unique = true)
    val token: String,

    @Column(nullable = false, length = 10)
    val platform: String,  // ios | android | web

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)

@Entity
@Table(
    name = "notifications",
    indexes = [Index(name = "idx_notifications_user_created", columnList = "user_id, created_at DESC")]
)
data class NotificationLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(nullable = false, length = 20)
    val type: String,

    @Column(nullable = false, length = 200)
    val title: String,

    @Column(nullable = false, length = 500)
    val body: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    val data: String = "{}",  // JSON 문자열 저장

    @Column(name = "read", nullable = false)
    var isRead: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
