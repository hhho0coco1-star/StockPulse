package com.stockpulse.news

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class NewsCollectorApplication

fun main(args: Array<String>) {
    runApplication<NewsCollectorApplication>(*args)
}
