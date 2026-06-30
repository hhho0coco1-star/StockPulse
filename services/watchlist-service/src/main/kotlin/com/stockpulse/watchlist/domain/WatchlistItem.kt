package com.stockpulse.watchlist.domain

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "watchlist",
    uniqueConstraints = [UniqueConstraint(name = "uq_watchlist_user_symbol", columnNames = ["user_id", "symbol"])],
    indexes = [Index(name = "idx_watchlist_user", columnList = "user_id")]
)
data class WatchlistItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(nullable = false, length = 20)
    val symbol: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
