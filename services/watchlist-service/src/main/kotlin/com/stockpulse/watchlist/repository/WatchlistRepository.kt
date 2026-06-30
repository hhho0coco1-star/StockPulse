package com.stockpulse.watchlist.repository

import com.stockpulse.watchlist.domain.WatchlistItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface WatchlistRepository : JpaRepository<WatchlistItem, Long> {
    fun findAllByUserId(userId: Long): List<WatchlistItem>
    fun findByUserIdAndSymbol(userId: Long, symbol: String): WatchlistItem?
    fun existsByUserIdAndSymbol(userId: Long, symbol: String): Boolean
    fun deleteByUserIdAndSymbol(userId: Long, symbol: String)
}
