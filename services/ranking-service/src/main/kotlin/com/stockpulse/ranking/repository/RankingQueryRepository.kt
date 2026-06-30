package com.stockpulse.ranking.repository

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository

/**
 * Redis 장애 시 fallback 소스로 사용하는 ranking_snapshot 테이블 조회 레포지토리.
 * ranking_snapshot 테이블은 주기적으로 Redis ZSet을 스냅샷으로 저장해 두어야 한다.
 *
 * ranking_snapshot DDL 예시:
 *   CREATE TABLE ranking_snapshot (
 *     user_id     BIGINT PRIMARY KEY,
 *     return_rate DOUBLE PRECISION NOT NULL,
 *     updated_at  TIMESTAMP WITH TIME ZONE DEFAULT now()
 *   );
 */
data class RankingSnapshotRow(val userId: Long, val returnRate: Double)

@Repository
class RankingQueryRepository(
    private val redisTemplate: StringRedisTemplate,
) {
    /**
     * ranking_snapshot 테이블이 있을 경우 JdbcTemplate / JPA로 교체할 것.
     * 현재는 ranking-service가 자체 DB를 보유하지 않는 초기 단계이므로
     * 빈 목록을 반환하는 스텁으로 구현한다.
     * Phase 6에서 DB 연동 또는 account-service 조회로 교체 예정.
     */
    fun findTopReturnRates(size: Long): List<RankingSnapshotRow> = emptyList()
}
