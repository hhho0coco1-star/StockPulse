package com.stockpulse.account.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.stockpulse.account.service.AccountService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class AccountEventConsumer(
    private val accountService: AccountService,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["order.events"], groupId = "account-service")
    fun consume(message: String) {
        val event = objectMapper.readTree(message)
        val type    = event.path("type").asText()
        val orderId = event.path("orderId").asLong()
        val userId  = event.path("userId").asLong()
        val amount  = BigDecimal(event.path("amount").asText("0"))

        log.debug("account 이벤트 수신: $type orderId=$orderId")
        when (type) {
            "RESERVE_BALANCE"  -> accountService.reserveBalance(orderId, userId, amount)
            "RELEASE_BALANCE"  -> accountService.releaseBalance(orderId, userId, amount)
            "CONFIRM_BALANCE"  -> accountService.confirmBalance(orderId, userId, amount)
        }
    }
}
