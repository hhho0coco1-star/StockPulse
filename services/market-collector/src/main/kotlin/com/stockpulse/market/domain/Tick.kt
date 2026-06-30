package com.stockpulse.market.domain

import java.math.BigDecimal
import java.time.Instant

data class Tick(
    val time: Instant = Instant.now(),
    val symbol: String,
    val price: BigDecimal,
    val volume: Long
)
