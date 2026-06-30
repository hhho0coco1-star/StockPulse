package com.stockpulse.trading.repository

import com.stockpulse.trading.domain.Order
import com.stockpulse.trading.domain.OrderSaga
import com.stockpulse.trading.domain.ProcessedEvent
import org.springframework.data.jpa.repository.JpaRepository

interface OrderRepository : JpaRepository<Order, Long> {
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<Order>
}

interface OrderSagaRepository : JpaRepository<OrderSaga, Long>

interface ProcessedEventRepository : JpaRepository<ProcessedEvent, String>
