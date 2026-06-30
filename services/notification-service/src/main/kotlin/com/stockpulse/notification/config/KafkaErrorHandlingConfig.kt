package com.stockpulse.notification.config

import com.fasterxml.jackson.core.JsonProcessingException
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff

/**
 * Kafka 소비 실패 시 market.tick.DLT 로 라우팅하는 에러 핸들러 설정.
 * FixedBackOff(1s, 3회) 재시도 후 DLT 발행.
 * JsonProcessingException / IllegalArgumentException 은 재시도 없이 즉시 DLT.
 */
@Configuration
class KafkaErrorHandlingConfig {

    @Bean
    fun errorHandler(kafkaTemplate: KafkaTemplate<String, String>): DefaultErrorHandler {
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate) { record: ConsumerRecord<*, *>, _ ->
            TopicPartition("${record.topic()}.DLT", record.partition())
        }
        val backOff = FixedBackOff(1_000L, 3L)
        return DefaultErrorHandler(recoverer, backOff).apply {
            addNotRetryableExceptions(
                JsonProcessingException::class.java,
                IllegalArgumentException::class.java,
            )
        }
    }
}
