package com.stockpulse.ranking.controller

import com.stockpulse.common.ApiResponse
import com.stockpulse.ranking.service.RankingService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/ranking")
class RankingController(private val rankingService: RankingService) {

    @GetMapping
    fun getRanking(@RequestParam(defaultValue = "20") size: Long): ApiResponse<List<Map<String, Any>>> =
        ApiResponse.ok(rankingService.getTopRanking(size))

    @GetMapping("/me")
    fun getMyRank(@RequestHeader("X-User-Id") userId: Long): ApiResponse<Map<String, Any>> =
        ApiResponse.ok(rankingService.getMyRank(userId))
}
