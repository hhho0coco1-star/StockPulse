package com.stockpulse.realtime

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import reactor.core.publisher.Hooks

@SpringBootApplication
class RealtimeGatewayApplication

fun main(args: Array<String>) {
    // Reactor Context ↔ MDC(traceId) 자동 전파 (Phase 6 관측)
    Hooks.enableAutomaticContextPropagation()
    runApplication<RealtimeGatewayApplication>(*args)
}
