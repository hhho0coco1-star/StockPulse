package com.stockpulse.watchlist.service

import com.stockpulse.watchlist.domain.WatchlistItem
import com.stockpulse.watchlist.dto.AddWatchRequest
import com.stockpulse.watchlist.dto.RemoveWatchResponse
import com.stockpulse.watchlist.dto.WatchlistItemResponse
import com.stockpulse.watchlist.dto.WatchlistResponse
import com.stockpulse.watchlist.repository.WatchlistRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WatchlistService(private val watchlistRepository: WatchlistRepository) {

    // ── W1: 목록 조회 ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun list(userId: Long): WatchlistResponse {
        val items = watchlistRepository.findAllByUserId(userId)
            .map { it.toResponse() }
        return WatchlistResponse(items = items)
    }

    // ── W2: 종목 추가 ─────────────────────────────────────────────────────────

    @Transactional
    fun add(userId: Long, request: AddWatchRequest): WatchlistItemResponse {
        require(request.symbol.isNotBlank()) { "symbol must not be blank" }
        return try {
            val item = watchlistRepository.save(
                WatchlistItem(userId = userId, symbol = request.symbol.trim())
            )
            item.toResponse()
        } catch (e: DataIntegrityViolationException) {
            throw DuplicateSymbolException(request.symbol)
        }
    }

    // ── W3: 종목 삭제 ─────────────────────────────────────────────────────────

    @Transactional
    fun remove(userId: Long, symbol: String): RemoveWatchResponse {
        val item = watchlistRepository.findByUserIdAndSymbol(userId, symbol)
            ?: throw NoSuchElementException("NOT_FOUND:$symbol")
        watchlistRepository.delete(item)
        return RemoveWatchResponse(symbol = symbol, removed = true)
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private fun WatchlistItem.toResponse() = WatchlistItemResponse(
        id = id, symbol = symbol, createdAt = createdAt
    )
}

class DuplicateSymbolException(val symbol: String) : RuntimeException("CONFLICT:$symbol")
