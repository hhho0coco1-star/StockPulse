package com.stockpulse.news.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

enum class Sentiment { POSITIVE, NEUTRAL, NEGATIVE }

@Document(collection = "news")
data class NewsArticle(
    @Id val id: String? = null,
    val symbol: String,
    val title: String,
    val url: String,
    val source: String = "naver",
    val publishedAt: Instant,
    val sentiment: Sentiment,
    val keywords: List<String> = emptyList(),
    val score: Int
)
