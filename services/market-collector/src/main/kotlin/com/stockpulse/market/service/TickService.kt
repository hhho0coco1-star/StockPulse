package com.stockpulse.market.service

import com.stockpulse.market.domain.Tick
import com.stockpulse.market.dto.QuoteResponse
import com.stockpulse.market.dto.TickEvent
import com.stockpulse.market.kafka.TickProducer
import com.stockpulse.market.repository.TickRepository
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant

@Service
class TickService(
    private val tickRepository: TickRepository,
    private val tickProducer: TickProducer,
    private val redisTemplate: StringRedisTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun process(symbol: String, price: BigDecimal, volume: Long) {
        val now = Instant.now()

        // 1. TimescaleDB 저장
        tickRepository.save(Tick(symbol = symbol, price = price, volume = volume, time = now))

        // 2. Redis 현재가 캐시 갱신
        val key = "quote:$symbol"
        redisTemplate.opsForHash<String, String>().putAll(
            key, mapOf(
                "price"     to price.toPlainString(),
                "volume"    to volume.toString(),
                "updatedAt" to now.toString()
            )
        )

        // 3. Kafka 발행
        tickProducer.send(TickEvent(symbol = symbol, price = price, volume = volume, time = now))
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "getQuoteFromDb")
    fun getQuote(symbol: String): QuoteResponse? {
        val key = "quote:$symbol"
        val hash = redisTemplate.opsForHash<String, String>().entries(key)
        if (hash.isEmpty()) return null
        return QuoteResponse(
            symbol    = symbol,
            price     = BigDecimal(hash["price"] ?: "0"),
            volume    = hash["volume"]?.toLong() ?: 0L,
            updatedAt = Instant.parse(hash["updatedAt"] ?: Instant.now().toString())
        )
    }

    fun getQuoteFromDb(symbol: String, t: Throwable): QuoteResponse? {
        log.warn("Redis 장애 → DB fallback: symbol={} cause={}", symbol, t.message)
        return tickRepository.findLatestBySymbol(symbol)?.let { tick ->
            QuoteResponse(
                symbol    = tick.symbol,
                price     = tick.price,
                volume    = tick.volume,
                updatedAt = tick.time
            )
        }
    }

    fun getQuotes(symbols: List<String>): List<QuoteResponse> =
        symbols.mapNotNull { getQuote(it) }
}
