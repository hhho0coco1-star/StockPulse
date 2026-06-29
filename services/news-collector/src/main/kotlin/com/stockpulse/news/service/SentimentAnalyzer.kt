package com.stockpulse.news.service

import com.stockpulse.news.domain.Sentiment
import org.springframework.stereotype.Component

@Component
class SentimentAnalyzer {

    private val positiveKeywords = listOf(
        "호실적", "상승", "목표가", "매수", "성장", "흑자", "신고가", "급등", "기대감",
        "수혜", "호재", "강세", "견조", "개선", "확대", "증가", "고성장", "돌파"
    )

    private val negativeKeywords = listOf(
        "하락", "적자", "부진", "손실", "위기", "취소", "급락", "우려", "충격",
        "악재", "약세", "감소", "축소", "위험", "폭락", "매도", "경고", "침체"
    )

    fun analyze(title: String): Sentiment {
        val text = title.lowercase()
        val positiveCount = positiveKeywords.count { text.contains(it) }
        val negativeCount = negativeKeywords.count { text.contains(it) }
        return when {
            positiveCount > negativeCount -> Sentiment.POSITIVE
            negativeCount > positiveCount -> Sentiment.NEGATIVE
            else -> Sentiment.NEUTRAL
        }
    }

    fun extractKeywords(title: String): List<String> =
        (positiveKeywords + negativeKeywords).filter { title.contains(it) }

    fun score(sentiment: Sentiment): Int = when (sentiment) {
        Sentiment.POSITIVE -> 1
        Sentiment.NEUTRAL  -> 0
        Sentiment.NEGATIVE -> -1
    }
}
