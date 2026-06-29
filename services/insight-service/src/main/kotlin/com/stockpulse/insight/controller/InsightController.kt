package com.stockpulse.insight.controller

import com.stockpulse.common.ApiResponse
import com.stockpulse.insight.domain.Insight
import com.stockpulse.insight.service.InsightService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/insights")
class InsightController(private val insightService: InsightService) {

    @GetMapping("/{symbol}")
    fun getInsight(@PathVariable symbol: String): ApiResponse<Insight> {
        val insight = insightService.getInsight(symbol)
            ?: return ApiResponse.fail("NOT_FOUND", "인사이트 데이터 없음: $symbol")
        return ApiResponse.ok(insight)
    }

    @GetMapping("/{symbol}/factors")
    fun getFactors(@PathVariable symbol: String): ApiResponse<Map<String, Any>> {
        val insight = insightService.getInsight(symbol)
            ?: return ApiResponse.fail("NOT_FOUND", "인사이트 데이터 없음: $symbol")
        return ApiResponse.ok(mapOf(
            "symbol"      to insight.symbol,
            "totalScore"  to insight.totalScore,
            "grade"       to insight.grade,
            "momentum"    to insight.momentum,
            "fundamental" to insight.fundamental,
            "valuation"   to insight.valuation,
            "news"        to insight.news,
            "updatedAt"   to insight.updatedAt
        ))
    }

    @GetMapping("/strong")
    fun getStrong(
        @RequestParam(defaultValue = "KR") market: String,
        @RequestParam(defaultValue = "0")  page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ApiResponse<List<Insight>> =
        ApiResponse.ok(insightService.getStrong(page, size))
}
