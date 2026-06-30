package com.stockpulse.notification.kafka

import com.stockpulse.notification.service.AlertEvaluator
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * market.tick 토픽 소비자.
 * 수신된 JSON을 AlertEvaluator에 위임한다.
 */
@Component
class TickConsumer(private val alertEvaluator: AlertEvaluator) {

    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["market.tick"], groupId = "notification-service")
    fun consume(message: String) {
        log.debug("market.tick 수신: {}", message)
        try {
            alertEvaluator.evaluate(message)
        } catch (e: Exception) {
            log.error("틱 평가 중 오류: message={} error={}", message, e.message, e)
        }
    }
}
