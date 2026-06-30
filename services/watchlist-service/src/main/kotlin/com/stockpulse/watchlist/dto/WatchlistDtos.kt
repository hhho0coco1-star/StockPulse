package com.stockpulse.watchlist.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.time.Instant

data class AddWatchRequest(
    @field:NotBlank(message = "symbol은 필수입니다")
    @field:Pattern(regexp = "\\d{6}", message = "symbol은 6자리 숫자여야 합니다")
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
