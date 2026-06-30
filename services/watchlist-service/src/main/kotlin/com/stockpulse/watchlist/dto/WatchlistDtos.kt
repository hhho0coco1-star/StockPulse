package com.stockpulse.watchlist.dto

import java.time.Instant

data class AddWatchRequest(
    val symbol: String
)

data class WatchlistItemResponse(
    val id: Long,
    val symbol: String,
    val createdAt: Instant
)

data class WatchlistResponse(
    val items: List<WatchlistItemResponse>
)

data class RemoveWatchResponse(
    val symbol: String,
    val removed: Boolean
)
