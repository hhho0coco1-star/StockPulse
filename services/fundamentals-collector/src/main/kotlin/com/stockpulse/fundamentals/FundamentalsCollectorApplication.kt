package com.stockpulse.fundamentals

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class FundamentalsCollectorApplication

fun main(args: Array<String>) {
    runApplication<FundamentalsCollectorApplication>(*args)
}
