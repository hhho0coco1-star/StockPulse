package com.stockpulse.ranking.service

import com.stockpulse.ranking.repository.RankingQueryRepository
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

@Service
class RankingService(
    private val redisTemplate: StringRedisTemplate,
    private val rankingQueryRepository: RankingQueryRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val KEY_ALL = "ranking:all"

    fun updateScore(userId: Long, returnRate: Double) {
        redisTemplate.opsForZSet().add(KEY_ALL, userId.toString(), returnRate)
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "getTopRankingFromDb")
    fun getTopRanking(size: Long): List<Map<String, Any>> {
        val entries = redisTemplate.opsForZSet()
            .reverseRangeWithScores(KEY_ALL, 0, size - 1) ?: return emptyList()
        return entries.mapIndexed { idx, e ->
            mapOf<String, Any>("rank" to idx + 1, "userId" to (e.value ?: ""), "returnRate" to (e.score ?: 0.0))
        }
    }

    fun getTopRankingFromDb(size: Long, t: Throwable): List<Map<String, Any>> {
        log.warn("Redis 장애 → DB fallback ranking: cause={}", t.message)
        return rankingQueryRepository.findTopReturnRates(size)
            .mapIndexed { idx, r ->
                mapOf<String, Any>("rank" to idx + 1, "userId" to r.userId.toString(), "returnRate" to r.returnRate)
            }
    }

    fun getMyRank(userId: Long): Map<String, Any> {
        val rank  = redisTemplate.opsForZSet().reverseRank(KEY_ALL, userId.toString())
        val score = redisTemplate.opsForZSet().score(KEY_ALL, userId.toString())
        return mapOf("rank" to (if (rank != null) rank + 1 else -1), "returnRate" to (score ?: 0.0))
    }
}
