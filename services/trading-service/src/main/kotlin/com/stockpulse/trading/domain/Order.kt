package com.stockpulse.trading.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

enum class OrderStatus {
    RECEIVED, RESERVED, EXECUTED, COMPLETED, REJECTED, COMPENSATING, CANCELLED
}

@Entity
@Table(name = "orders")
class Order(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    val userId: Long,
    val symbol: String,
    val side: String,          // BUY | SELL
    val type: String,          // MARKET | LIMIT
    val quantity: Long,
    val price: BigDecimal,
    @Enumerated(EnumType.STRING) var status: OrderStatus = OrderStatus.RECEIVED,
    var filledPrice: BigDecimal = BigDecimal.ZERO,
    val createdAt: Instant = Instant.now()
) {
    val totalAmount get() = (if (filledPrice > BigDecimal.ZERO) filledPrice else price) * BigDecimal(quantity)
}

@Entity
@Table(name = "order_saga")
class OrderSaga(
    @Id val orderId: Long,
    var currentStep: String = "RESERVE_BALANCE",
    var stepStatus: String = "PENDING",
    var compensationReason: String? = null,
    var updatedAt: Instant = Instant.now()
)

@Entity
@Table(name = "processed_event")
class ProcessedEvent(
    @Id val eventId: String,
    val processedAt: Instant = Instant.now()
)
