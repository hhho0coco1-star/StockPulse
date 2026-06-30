package com.stockpulse.market.repository

import com.stockpulse.market.domain.Tick
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

@Repository
class TickRepository(private val jdbcTemplate: JdbcTemplate) {

    fun save(tick: Tick) {
        jdbcTemplate.update(
            "INSERT INTO ticks (time, symbol, price, volume) VALUES (?, ?, ?, ?)",
            Timestamp.from(tick.time), tick.symbol, tick.price, tick.volume
        )
    }

    fun findBySymbolAndTimeBetween(symbol: String, from: Instant, to: Instant): List<Tick> =
        jdbcTemplate.query(
            "SELECT time, symbol, price, volume FROM ticks WHERE symbol = ? AND time BETWEEN ? AND ? ORDER BY time DESC",
            { rs, _ -> Tick(
                time   = rs.getTimestamp("time").toInstant(),
                symbol = rs.getString("symbol"),
                price  = rs.getBigDecimal("price"),
                volume = rs.getLong("volume")
            )},
            symbol, Timestamp.from(from), Timestamp.from(to)
        )

    fun findLatestBySymbol(symbol: String): Tick? =
        jdbcTemplate.query(
            "SELECT time, symbol, price, volume FROM ticks WHERE symbol = ? ORDER BY time DESC LIMIT 1",
            { rs, _ -> Tick(
                time   = rs.getTimestamp("time").toInstant(),
                symbol = rs.getString("symbol"),
                price  = rs.getBigDecimal("price"),
                volume = rs.getLong("volume")
            )},
            symbol
        ).firstOrNull()
}
