package com.stockpulse.portfolio.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.stockpulse.portfolio.service.PortfolioService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class PortfolioEventConsumer(
    private val portfolioService: PortfolioService,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["order.events"], groupId = "portfolio-service")
    fun consume(message: String) {
        val event = objectMapper.readTree(message)
        val type = event.path("type").asText()
        if (type != "UPDATE_HOLDINGS") return

        val orderId  = event.path("orderId").asLong()
        val userId   = event.path("userId").asLong()
        val symbol   = event.path("symbol").asText()
        val side     = event.path("side").asText()
        val quantity = event.path("quantity").asLong()
        val price    = BigDecimal(event.path("filledPrice").asText("0"))

        log.debug("보유 갱신 이벤트 수신: orderId=$orderId symbol=$symbol")
        portfolioService.updateHolding(orderId, userId, symbol, side, quantity, price)
    }
}
