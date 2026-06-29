package com.stockpulse.market.dto

import java.math.BigDecimal
import java.time.Instant

data class TickEvent(
    val symbol: String,
    val price: BigDecimal,
    val volume: Long,
    val time: Instant = Instant.now()
)

data class QuoteResponse(
    val symbol: String,
    val price: BigDecimal,
    val volume: Long,
    val updatedAt: Instant
)

data class CandleResponse(
    val bucket: Instant,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: Long
)
