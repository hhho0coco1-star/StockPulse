package com.stockpulse.insight.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stockpulse.insight.domain.*
import com.stockpulse.insight.repository.InsightRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class InsightService(
    private val insightRepository: InsightRepository,
    private val scoreEngine: ScoreEngine,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 뉴스 이벤트 → 인사이트 갱신
    @KafkaListener(topics = ["news.collected"], groupId = "insight-service")
    fun onNews(message: String) {
        try {
            val event = objectMapper.readTree(message)
            val symbol = event.path("symbol").asText()
            log.debug("뉴스 이벤트 수신: $symbol")
            recalculate(symbol, newsEvent = event)
        } catch (e: Exception) {
            log.error("뉴스 이벤트 처리 오류: ${e.message}")
        }
    }

    // 재무 이벤트 → 인사이트 갱신
    @KafkaListener(topics = ["fundamentals.updated"], groupId = "insight-service")
    fun onFundamentals(message: String) {
        try {
            val event = objectMapper.readTree(message)
            val symbol = event.path("symbol").asText()
            log.debug("재무 이벤트 수신: $symbol")
            recalculate(symbol, fundamentalsEvent = event)
        } catch (e: Exception) {
            log.error("재무 이벤트 처리 오류: ${e.message}")
        }
    }

    private fun recalculate(
        symbol: String,
        newsEvent: JsonNode? = null,
        fundamentalsEvent: JsonNode? = null
    ) {
        val existing = insightRepository.findById(symbol).orElse(null)

        val newsFactor = if (newsEvent != null) {
            val sentiment = newsEvent.path("sentiment").asText()
            val prev = existing?.news ?: NewsFactor()
            val positive = prev.positive + if (sentiment == "POSITIVE") 1 else 0
            val negative = prev.negative + if (sentiment == "NEGATIVE") 1 else 0
            scoreEngine.newsScore(positive, negative, prev.total + 1)
        } else existing?.news ?: NewsFactor()

        val fundamentalFactor = if (fundamentalsEvent != null) {
            val opGrowth    = fundamentalsEvent.path("opProfitGrowthYoY").asDouble()
            val salesGrowth = fundamentalsEvent.path("revenueGrowthYoY").asDouble()
            val margin      = fundamentalsEvent.path("operatingMargin").asDouble()
            scoreEngine.fundamentalScore(opGrowth, salesGrowth, margin)
        } else existing?.fundamental ?: FundamentalFactor()

        val momentum    = existing?.momentum ?: scoreEngine.momentumScore(0.0, 1.0)
        val valuation   = scoreEngine.valuationScore(fundamentalFactor.operatingMargin)
        val total       = scoreEngine.totalScore(momentum, fundamentalFactor, valuation, newsFactor)

        val insight = Insight(
            symbol      = symbol,
            totalScore  = total,
            grade       = scoreEngine.grade(total),
            momentum    = momentum,
            fundamental = fundamentalFactor,
            valuation   = valuation,
            news        = newsFactor
        )
        insightRepository.save(insight)
        log.info("인사이트 갱신: $symbol → score=$total grade=${insight.grade}")
    }

    fun getInsight(symbol: String): Insight? = insightRepository.findById(symbol).orElse(null)

    fun getStrong(page: Int, size: Int): List<Insight> =
        insightRepository.findByTotalScoreGreaterThanEqualOrderByTotalScoreDesc(
            65, PageRequest.of(page, size)
        )
}
