package com.stockpulse.realtime

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class RealtimeGatewayApplication

fun main(args: Array<String>) {
    runApplication<RealtimeGatewayApplication>(*args)
}
