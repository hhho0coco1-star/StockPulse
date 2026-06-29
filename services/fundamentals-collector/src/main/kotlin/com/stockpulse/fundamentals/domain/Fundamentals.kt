package com.stockpulse.fundamentals.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "fundamentals")
data class Fundamentals(
    @Id val id: String? = null,
    val symbol: String,
    val corpCode: String,
    val year: Int,
    val quarter: Int,
    val revenue: Long = 0,
    val operatingProfit: Long = 0,
    val netIncome: Long = 0,
    val revenueGrowthYoY: Double = 0.0,
    val opProfitGrowthYoY: Double = 0.0,
    val operatingMargin: Double = 0.0,
    val updatedAt: Instant = Instant.now()
)
