package com.stockpulse.watchlist

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class WatchlistApplication

fun main(args: Array<String>) {
    runApplication<WatchlistApplication>(*args)
}
