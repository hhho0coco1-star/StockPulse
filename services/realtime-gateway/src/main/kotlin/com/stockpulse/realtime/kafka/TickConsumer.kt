package com.stockpulse.realtime.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

@Component
class TickConsumer(
    private val messagingTemplate: SimpMessagingTemplate,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["market.tick"], groupId = "realtime-gateway")
    fun consume(message: String) {
        try {
            val tick = objectMapper.readValue(message, Map::class.java)
            val symbol = tick["symbol"] as? String ?: return
            messagingTemplate.convertAndSend("/topic/market/$symbol", tick)
            log.debug("WebSocket 푸시: /topic/market/$symbol")
        } catch (e: Exception) {
            log.error("틱 소비 오류: ${e.message}")
        }
    }
}
