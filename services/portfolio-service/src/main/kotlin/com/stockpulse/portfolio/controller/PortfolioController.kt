package com.stockpulse.portfolio.controller

import com.stockpulse.common.ApiResponse
import com.stockpulse.portfolio.domain.Holding
import com.stockpulse.portfolio.service.PortfolioService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/portfolio")
class PortfolioController(private val portfolioService: PortfolioService) {

    @GetMapping
    fun getHoldings(@RequestHeader("X-User-Id") userId: Long): ApiResponse<List<Map<String, Any>>> {
        val holdings = portfolioService.getHoldings(userId).filter { it.quantity > 0 }
        val result = holdings.map { h ->
            mapOf(
                "symbol"      to h.symbol,
                "quantity"    to h.quantity,
                "avgBuyPrice" to h.avgBuyPrice
            )
        }
        return ApiResponse.ok(result)
    }

    @GetMapping("/summary")
    fun getSummary(@RequestHeader("X-User-Id") userId: Long): ApiResponse<Map<String, Any>> =
        ApiResponse.ok(portfolioService.getSummary(userId))
}
