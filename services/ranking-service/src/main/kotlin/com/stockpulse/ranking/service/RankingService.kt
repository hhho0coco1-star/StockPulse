package com.stockpulse.ranking.service

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

@Service
class RankingService(private val redisTemplate: StringRedisTemplate) {

    private val KEY_ALL = "ranking:all"

    fun updateScore(userId: Long, returnRate: Double) {
        redisTemplate.opsForZSet().add(KEY_ALL, userId.toString(), returnRate)
    }

    fun getTopRanking(size: Long): List<Map<String, Any>> {
        val entries = redisTemplate.opsForZSet()
            .reverseRangeWithScores(KEY_ALL, 0, size - 1) ?: return emptyList()
        return entries.mapIndexed { idx, e ->
            mapOf<String, Any>("rank" to idx + 1, "userId" to (e.value ?: ""), "returnRate" to (e.score ?: 0.0))
        }
    }

    fun getMyRank(userId: Long): Map<String, Any> {
        val rank  = redisTemplate.opsForZSet().reverseRank(KEY_ALL, userId.toString())
        val score = redisTemplate.opsForZSet().score(KEY_ALL, userId.toString())
        return mapOf("rank" to (if (rank != null) rank + 1 else -1), "returnRate" to (score ?: 0.0))
    }
}
