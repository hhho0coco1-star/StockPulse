package com.stockpulse.insight

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class InsightServiceApplication

fun main(args: Array<String>) {
    runApplication<InsightServiceApplication>(*args)
}
