package com.stockpulse.market.repository

import com.stockpulse.market.domain.Tick
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface TickRepository : JpaRepository<Tick, Long> {

    @Query(
        "SELECT t FROM Tick t WHERE t.symbol = :symbol AND t.time BETWEEN :from AND :to ORDER BY t.time DESC"
    )
    fun findBySymbolAndTimeBetween(symbol: String, from: Instant, to: Instant): List<Tick>
}
