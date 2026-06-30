package com.stockpulse.ranking.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.stockpulse.ranking.service.RankingService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class RankingEventConsumer(
    private val rankingService: RankingService,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["portfolio.events"], groupId = "ranking-service")
    fun consume(message: String) {
        val event = objectMapper.readTree(message)
        if (event.path("type").asText() != "HOLDINGS_UPDATED") return

        val userId     = event.path("userId").asLong()
        val returnRate = event.path("returnRate").asDouble(0.0)
        rankingService.updateScore(userId, returnRate)
        log.debug("랭킹 갱신: userId=$userId returnRate=$returnRate")
    }
}
