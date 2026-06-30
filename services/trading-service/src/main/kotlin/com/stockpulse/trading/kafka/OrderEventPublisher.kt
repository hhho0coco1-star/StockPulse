package com.stockpulse.trading.kafka

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Saga 이벤트 발행 전담 컴포넌트.
 * TradingService의 self-invocation 문제를 해결하고 CB/Retry AOP가 동작하도록 분리.
 * publish() 실패 시 Retry 3회 후 최종 실패 → 호출 측 트랜잭션 롤백.
 */
@Component
class OrderEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @CircuitBreaker(name = "orderEvents")
    @Retry(name = "orderEvents")
    fun publish(key: String, payload: String) {
        log.debug("Kafka 이벤트 발행: key={}", key)
        kafkaTemplate.send("order.events", key, payload).get(3, TimeUnit.SECONDS)
    }
}
