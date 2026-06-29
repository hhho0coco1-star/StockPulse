package com.stockpulse.insight.repository

import com.stockpulse.insight.domain.Insight
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository

interface InsightRepository : MongoRepository<Insight, String> {
    fun findByTotalScoreGreaterThanEqualOrderByTotalScoreDesc(score: Int, pageable: Pageable): List<Insight>
}
