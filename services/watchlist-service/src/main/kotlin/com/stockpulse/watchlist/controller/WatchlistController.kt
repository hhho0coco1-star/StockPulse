package com.stockpulse.watchlist.controller

import com.stockpulse.common.ApiResponse
import com.stockpulse.watchlist.dto.AddWatchRequest
import com.stockpulse.watchlist.service.DuplicateSymbolException
import com.stockpulse.watchlist.service.WatchlistService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/watchlist")
class WatchlistController(private val service: WatchlistService) {

    // ── W1: 목록 조회 ─────────────────────────────────────────────────────────

    @GetMapping
    fun list(
        @RequestHeader("X-User-Id") userId: Long
    ): ResponseEntity<ApiResponse<Any>> {
        val data = service.list(userId)
        return ResponseEntity.ok(ApiResponse.ok(data))
    }

    // ── W2: 종목 추가 ─────────────────────────────────────────────────────────

    @PostMapping
    fun add(
        @RequestHeader("X-User-Id") userId: Long,
        @Valid @RequestBody request: AddWatchRequest
    ): ResponseEntity<ApiResponse<Any>> {
        return try {
            val data = service.add(userId, request)
            ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.fail("VALIDATION_ERROR", e.message ?: "Invalid symbol"))
        } catch (e: DuplicateSymbolException) {
            ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("CONFLICT", "Symbol ${e.symbol} already in watchlist"))
        }
    }

    // ── W3: 종목 삭제 ─────────────────────────────────────────────────────────

    @DeleteMapping("/{symbol}")
    fun remove(
        @PathVariable symbol: String,
        @RequestHeader("X-User-Id") userId: Long
    ): ResponseEntity<ApiResponse<Any>> {
        return try {
            val data = service.remove(userId, symbol)
            ResponseEntity.ok(ApiResponse.ok(data))
        } catch (e: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("NOT_FOUND", "Symbol $symbol not in watchlist"))
        }
    }
}
