package com.stockpulse.trading.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.stockpulse.trading.service.TradingService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class TradingEventConsumer(
    private val tradingService: TradingService,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["account.events"], groupId = "trading-service")
    fun onAccountEvent(message: String) {
        val event   = objectMapper.readTree(message)
        val type    = event.path("type").asText()
        val orderId = event.path("orderId").asLong()
        val eventId = "${type}_${orderId}"

        log.debug("account 이벤트: $type orderId=$orderId")
        when (type) {
            "BALANCE_RESERVED" -> tradingService.onBalanceReserved(eventId, orderId)
            "BALANCE_REJECTED" -> tradingService.onBalanceRejected(eventId, orderId, event.path("reason").asText())
            "BALANCE_RELEASED" -> tradingService.onBalanceReleased(eventId, orderId)
        }
    }

    @KafkaListener(topics = ["portfolio.events"], groupId = "trading-service")
    fun onPortfolioEvent(message: String) {
        val event   = objectMapper.readTree(message)
        val type    = event.path("type").asText()
        val orderId = event.path("orderId").asLong()
        val eventId = "${type}_${orderId}"

        log.debug("portfolio 이벤트: $type orderId=$orderId")
        when (type) {
            "HOLDINGS_UPDATED" -> tradingService.onHoldingsUpdated(eventId, orderId)
            "HOLDINGS_FAILED"  -> tradingService.onHoldingsFailed(eventId, orderId, event.path("reason").asText())
        }
    }
}
