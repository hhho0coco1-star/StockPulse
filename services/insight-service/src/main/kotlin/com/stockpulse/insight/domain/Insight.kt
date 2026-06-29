package com.stockpulse.insight.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

enum class Grade { STRONG_BUY, POSITIVE, NEUTRAL, NEGATIVE }

data class MomentumFactor(
    val score: Int = 50,
    val return5d: Double = 0.0,
    val volumeRatio: Double = 1.0
)

data class FundamentalFactor(
    val score: Int = 50,
    val opGrowthYoY: Double = 0.0,
    val salesGrowthYoY: Double = 0.0,
    val operatingMargin: Double = 0.0
)

data class ValuationFactor(
    val score: Int = 60,
    val verdict: String = "NEUTRAL"
)

data class NewsFactor(
    val score: Int = 50,
    val positive: Int = 0,
    val negative: Int = 0,
    val total: Int = 0
)

@Document(collection = "insights")
data class Insight(
    @Id val symbol: String,
    val totalScore: Int,
    val grade: Grade,
    val momentum: MomentumFactor = MomentumFactor(),
    val fundamental: FundamentalFactor = FundamentalFactor(),
    val valuation: ValuationFactor = ValuationFactor(),
    val news: NewsFactor = NewsFactor(),
    val updatedAt: Instant = Instant.now()
)
