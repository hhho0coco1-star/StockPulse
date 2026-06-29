package com.stockpulse.news.repository

import com.stockpulse.news.domain.NewsArticle
import org.springframework.data.mongodb.repository.MongoRepository
import java.time.Instant

interface NewsRepository : MongoRepository<NewsArticle, String> {
    fun existsByUrl(url: String): Boolean
    fun findBySymbolAndPublishedAtAfter(symbol: String, after: Instant): List<NewsArticle>
}
