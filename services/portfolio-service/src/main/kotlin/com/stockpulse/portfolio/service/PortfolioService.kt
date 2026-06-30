package com.stockpulse.portfolio.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.stockpulse.portfolio.domain.Holding
import com.stockpulse.portfolio.repository.HoldingRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class PortfolioService(
    private val holdingRepository: HoldingRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getHoldings(userId: Long): List<Holding> = holdingRepository.findByUserId(userId)

    @Transactional
    fun updateHolding(orderId: Long, userId: Long, symbol: String, side: String, quantity: Long, price: BigDecimal) {
        try {
            val holding = holdingRepository.findByUserIdAndSymbol(userId, symbol)
                ?: holdingRepository.save(Holding(userId = userId, symbol = symbol))

            when (side) {
                "BUY"  -> holding.buy(quantity, price)
                "SELL" -> holding.sell(quantity)
                else   -> error("알 수 없는 side: $side")
            }
            holdingRepository.save(holding)

            val payload = objectMapper.writeValueAsString(mapOf(
                "type" to "HOLDINGS_UPDATED", "orderId" to orderId,
                "userId" to userId, "symbol" to symbol, "side" to side
            ))
            kafkaTemplate.send("portfolio.events", orderId.toString(), payload)
            log.info("보유 갱신 완료: userId=$userId symbol=$symbol side=$side qty=$quantity")
        } catch (e: Exception) {
            val payload = objectMapper.writeValueAsString(mapOf(
                "type" to "HOLDINGS_FAILED", "orderId" to orderId,
                "userId" to userId, "reason" to e.message
            ))
            kafkaTemplate.send("portfolio.events", orderId.toString(), payload)
            log.error("보유 갱신 실패: ${e.message}")
        }
    }

    fun getSummary(userId: Long): Map<String, Any> {
        val holdings = holdingRepository.findByUserId(userId).filter { it.quantity > 0 }
        val totalBuy  = holdings.sumOf { it.avgBuyPrice * BigDecimal(it.quantity) }
        return mapOf("totalHoldings" to holdings.size, "totalBuyAmount" to totalBuy)
    }
}
