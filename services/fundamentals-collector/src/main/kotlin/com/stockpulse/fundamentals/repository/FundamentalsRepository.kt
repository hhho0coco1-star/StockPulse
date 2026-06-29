package com.stockpulse.fundamentals.repository

import com.stockpulse.fundamentals.domain.Fundamentals
import org.springframework.data.mongodb.repository.MongoRepository

interface FundamentalsRepository : MongoRepository<Fundamentals, String> {
    fun findTopBySymbolOrderByYearDescQuarterDesc(symbol: String): Fundamentals?
    fun findBySymbolAndYearAndQuarter(symbol: String, year: Int, quarter: Int): Fundamentals?
}
