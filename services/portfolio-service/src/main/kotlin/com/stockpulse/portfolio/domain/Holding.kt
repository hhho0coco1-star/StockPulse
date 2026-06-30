package com.stockpulse.portfolio.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

@Entity
@Table(name = "holdings", uniqueConstraints = [UniqueConstraint(columnNames = ["userId", "symbol"])])
class Holding(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    val userId: Long,
    val symbol: String,
    var quantity: Long = 0,
    var avgBuyPrice: BigDecimal = BigDecimal.ZERO,
    var updatedAt: Instant = Instant.now()
) {
    fun buy(qty: Long, price: BigDecimal) {
        val totalCost = avgBuyPrice * BigDecimal(quantity) + price * BigDecimal(qty)
        quantity += qty
        avgBuyPrice = if (quantity > 0) totalCost.divide(BigDecimal(quantity), 2, RoundingMode.HALF_UP) else BigDecimal.ZERO
        updatedAt = Instant.now()
    }

    fun sell(qty: Long) {
        require(quantity >= qty) { "보유 수량 부족: 보유=$quantity, 매도=$qty" }
        quantity -= qty
        updatedAt = Instant.now()
    }

    fun evalAmount(currentPrice: BigDecimal) = currentPrice * BigDecimal(quantity)
    fun evalProfit(currentPrice: BigDecimal) = (currentPrice - avgBuyPrice) * BigDecimal(quantity)
}
