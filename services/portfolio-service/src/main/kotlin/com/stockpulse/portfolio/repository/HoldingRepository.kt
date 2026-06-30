package com.stockpulse.portfolio.repository

import com.stockpulse.portfolio.domain.Holding
import org.springframework.data.jpa.repository.JpaRepository

interface HoldingRepository : JpaRepository<Holding, Long> {
    fun findByUserId(userId: Long): List<Holding>
    fun findByUserIdAndSymbol(userId: Long, symbol: String): Holding?
}
