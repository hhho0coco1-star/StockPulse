package com.stockpulse.notification.kafka

import com.stockpulse.notification.service.AlertEvaluator
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * market.tick 토픽 소비자.
 * 예외는 KafkaErrorHandlingConfig의 DefaultErrorHandler → DLT로 위임한다.
 */
@Component
class TickConsumer(private val alertEvaluator: AlertEvaluator) {

    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["market.tick"], groupId = "notification-service")
    fun consume(message: String) {
        log.debug("market.tick 수신: {}", message)
        alertEvaluator.evaluate(message)   // 예외는 ErrorHandler → DLT 로 위임
    }
}
