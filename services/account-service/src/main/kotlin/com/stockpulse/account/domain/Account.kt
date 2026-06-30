package com.stockpulse.account.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "account")
class Account(
    @Id val userId: Long,
    var cash: BigDecimal = BigDecimal("10000000"),
    var reserved: BigDecimal = BigDecimal.ZERO,
    var updatedAt: Instant = Instant.now()
) {
    val availableCash get() = cash - reserved

    fun reserve(amount: BigDecimal) {
        require(availableCash >= amount) { "잔고 부족: 가용=${availableCash}, 요청=${amount}" }
        reserved += amount
        updatedAt = Instant.now()
    }

    fun release(amount: BigDecimal) {
        reserved = (reserved - amount).coerceAtLeast(BigDecimal.ZERO)
        updatedAt = Instant.now()
    }

    fun confirm(amount: BigDecimal) {
        reserved -= amount
        cash -= amount
        updatedAt = Instant.now()
    }
}

@Entity
@Table(name = "ledger")
class Ledger(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    val userId: Long,
    val orderId: Long,
    val type: String,
    val amount: BigDecimal,
    val balanceAfter: BigDecimal,
    val createdAt: Instant = Instant.now()
)
