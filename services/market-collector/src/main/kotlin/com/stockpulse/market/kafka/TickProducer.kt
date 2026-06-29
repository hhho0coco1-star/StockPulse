package com.stockpulse.market.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.stockpulse.market.dto.TickEvent
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class TickProducer(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    companion object {
        const val TOPIC = "market.tick"
    }

    fun send(event: TickEvent) {
        val payload = objectMapper.writeValueAsString(event)
        kafkaTemplate.send(TOPIC, event.symbol, payload)
    }
}
