package com.stockpulse.market.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "ticks")
class Tick(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val time: Instant = Instant.now(),

    @Column(nullable = false, length = 20)
    val symbol: String,

    @Column(nullable = false, precision = 12, scale = 2)
    val price: BigDecimal,

    @Column(nullable = false)
    val volume: Long
)
