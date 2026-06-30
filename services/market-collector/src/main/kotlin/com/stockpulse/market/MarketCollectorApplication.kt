package com.stockpulse.market

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import reactor.core.publisher.Hooks

@SpringBootApplication
@EnableScheduling
class MarketCollectorApplication

fun main(args: Array<String>) {
    // Reactor Context ↔ MDC(traceId) 자동 전파 (Phase 6 관측)
    Hooks.enableAutomaticContextPropagation()
    runApplication<MarketCollectorApplication>(*args)
}
