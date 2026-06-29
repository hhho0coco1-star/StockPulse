package com.stockpulse.insight.service

import com.stockpulse.insight.domain.*
import org.springframework.stereotype.Component

@Component
class ScoreEngine {

    fun momentumScore(return5d: Double, volumeRatio: Double): MomentumFactor {
        val returnScore = when {
            return5d > 5.0  -> 40
            return5d > 2.0  -> 25
            return5d > -2.0 -> 15
            else            -> 5
        }
        val volumeScore = when {
            volumeRatio > 2.0 -> 30
            volumeRatio > 1.5 -> 20
            else              -> 10
        }
        return MomentumFactor(
            score       = (returnScore + volumeScore + 30).coerceIn(0, 100),
            return5d    = return5d,
            volumeRatio = volumeRatio
        )
    }

    fun fundamentalScore(opGrowthYoY: Double, salesGrowthYoY: Double, margin: Double): FundamentalFactor {
        val base = when {
            opGrowthYoY > 20.0 -> 90
            opGrowthYoY > 10.0 -> 75
            opGrowthYoY > 0.0  -> 60
            else               -> 30
        }
        val bonus = if (salesGrowthYoY > 10.0) 10 else 0
        return FundamentalFactor(
            score            = (base + bonus).coerceIn(0, 100),
            opGrowthYoY      = opGrowthYoY,
            salesGrowthYoY   = salesGrowthYoY,
            operatingMargin  = margin
        )
    }

    fun valuationScore(margin: Double): ValuationFactor {
        val score = when {
            margin > 15.0 -> 80
            margin > 10.0 -> 70
            margin > 5.0  -> 60
            else          -> 40
        }
        val verdict = when {
            score >= 75 -> "UNDERVALUED"
            score >= 55 -> "NEUTRAL"
            else        -> "OVERVALUED"
        }
        return ValuationFactor(score = score, verdict = verdict)
    }

    fun newsScore(positive: Int, negative: Int, total: Int): NewsFactor {
        val score = if (total == 0) 50
                    else ((positive.toDouble() / total) * 100).toInt().coerceIn(0, 100)
        return NewsFactor(score = score, positive = positive, negative = negative, total = total)
    }

    fun totalScore(m: MomentumFactor, f: FundamentalFactor, v: ValuationFactor, n: NewsFactor): Int =
        (m.score * 0.30 + f.score * 0.30 + v.score * 0.20 + n.score * 0.20).toInt()

    fun grade(total: Int): Grade = when {
        total >= 80 -> Grade.STRONG_BUY
        total >= 65 -> Grade.POSITIVE
        total >= 45 -> Grade.NEUTRAL
        else        -> Grade.NEGATIVE
    }
}
