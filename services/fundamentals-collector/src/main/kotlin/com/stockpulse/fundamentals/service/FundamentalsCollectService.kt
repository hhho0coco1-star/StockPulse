package com.stockpulse.fundamentals.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.stockpulse.fundamentals.domain.Fundamentals
import com.stockpulse.fundamentals.repository.FundamentalsRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class FundamentalsCollectService(
    private val dartClient: DartClient,
    private val fundamentalsRepository: FundamentalsRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    @Value("\${collect.symbols}") private val symbols: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 매일 새벽 2시 실행
    @Scheduled(cron = "0 0 2 * * *")
    fun collectAll() {
        val currentYear = LocalDate.now().year
        symbols.split(",").map { it.trim() }.forEach { symbol ->
            try {
                collect(symbol, currentYear - 1)
            } catch (e: Exception) {
                log.error("재무 수집 실패 [$symbol]: ${e.message}")
            }
        }
    }

    fun collect(symbol: String, year: Int) {
        log.info("DART 재무 수집 시작: $symbol ($year)")
        val corpCode = dartClient.getCorpCode(symbol) ?: run {
            log.warn("법인코드 없음: $symbol")
            return
        }

        val data = dartClient.getFinancials(corpCode, year) ?: return
        if (data.path("status").asText() != "000") {
            log.warn("DART 응답 오류: ${data.path("message").asText()}")
            return
        }

        val items = data.path("list")
        val revenue = parseAmount(items, "매출액")
        val opProfit = parseAmount(items, "영업이익")
        val netIncome = parseAmount(items, "당기순이익")

        val prevYear = fundamentalsRepository.findBySymbolAndYearAndQuarter(symbol, year - 1, 4)
        val revenueGrowth = growthRate(prevYear?.revenue, revenue)
        val opGrowth = growthRate(prevYear?.operatingProfit, opProfit)
        val margin = if (revenue > 0) opProfit.toDouble() / revenue * 100 else 0.0

        val fundamentals = Fundamentals(
            symbol            = symbol,
            corpCode          = corpCode,
            year              = year,
            quarter           = 4,
            revenue           = revenue,
            operatingProfit   = opProfit,
            netIncome         = netIncome,
            revenueGrowthYoY  = revenueGrowth,
            opProfitGrowthYoY = opGrowth,
            operatingMargin   = margin
        )
        fundamentalsRepository.save(fundamentals)

        kafkaTemplate.send("fundamentals.updated", symbol, objectMapper.writeValueAsString(
            mapOf(
                "symbol"            to symbol,
                "year"              to year,
                "revenueGrowthYoY"  to revenueGrowth,
                "opProfitGrowthYoY" to opGrowth,
                "operatingMargin"   to margin
            )
        ))
        log.info("재무 수집 완료: $symbol $year → 매출 $revenue, 영업이익 $opProfit")
    }

    private fun parseAmount(items: com.fasterxml.jackson.databind.JsonNode, accountName: String): Long {
        items.forEach { item ->
            if (item.path("account_nm").asText().contains(accountName)) {
                val raw = item.path("thstrm_amount").asText().replace(",", "")
                return raw.toLongOrNull() ?: 0L
            }
        }
        return 0L
    }

    private fun growthRate(prev: Long?, current: Long): Double {
        if (prev == null || prev == 0L) return 0.0
        return (current - prev).toDouble() / prev.toDouble() * 100
    }
}
