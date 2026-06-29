package com.stockpulse.market.controller

import com.stockpulse.common.ApiResponse
import com.stockpulse.market.dto.QuoteResponse
import com.stockpulse.market.service.TickService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/market")
class MarketController(private val tickService: TickService) {

    @GetMapping("/quote/{symbol}")
    fun getQuote(@PathVariable symbol: String): ApiResponse<QuoteResponse> {
        val quote = tickService.getQuote(symbol)
            ?: return ApiResponse.fail("NOT_FOUND", "시세 데이터 없음: $symbol")
        return ApiResponse.ok(quote)
    }

    @GetMapping("/quotes")
    fun getQuotes(@RequestParam symbols: List<String>): ApiResponse<List<QuoteResponse>> =
        ApiResponse.ok(tickService.getQuotes(symbols))

    @GetMapping("/symbols")
    fun searchSymbols(
        @RequestParam(defaultValue = "KR") market: String,
        @RequestParam(defaultValue = "") query: String
    ): ApiResponse<List<Map<String, String>>> {
        // TODO(Phase 1+): KIS 종목 마스터 연동
        val stub = listOf(
            mapOf("symbol" to "005930", "name" to "삼성전자", "market" to "KR"),
            mapOf("symbol" to "000660", "name" to "SK하이닉스", "market" to "KR")
        ).filter { it["name"]!!.contains(query) || query.isEmpty() }
        return ApiResponse.ok(stub)
    }
}
