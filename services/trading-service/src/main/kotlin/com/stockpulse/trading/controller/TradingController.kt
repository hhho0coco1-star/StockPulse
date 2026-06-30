package com.stockpulse.trading.controller

import com.stockpulse.common.ApiResponse
import com.stockpulse.trading.domain.Order
import com.stockpulse.trading.service.TradingService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

data class OrderRequest(
    val symbol: String,
    val side: String,
    val type: String,
    val quantity: Long,
    val price: BigDecimal = BigDecimal("50000")
)

@RestController
@RequestMapping("/orders")
class TradingController(private val tradingService: TradingService) {

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun placeOrder(
        @RequestHeader("X-User-Id") userId: Long,
        @RequestBody req: OrderRequest
    ): ApiResponse<Map<String, Any>> {
        val order = tradingService.placeOrder(userId, req.symbol, req.side, req.type, req.quantity, req.price)
        return ApiResponse.ok(mapOf("orderId" to order.id, "status" to order.status))
    }

    @GetMapping
    fun getOrders(@RequestHeader("X-User-Id") userId: Long): ApiResponse<List<Map<String, Any>>> {
        val orders = tradingService.getOrders(userId).map { it.toSummary() }
        return ApiResponse.ok(orders)
    }

    @GetMapping("/{orderId}")
    fun getOrder(@PathVariable orderId: Long): ApiResponse<Map<String, Any>> {
        val order = tradingService.getOrder(orderId)
            ?: return ApiResponse.fail("NOT_FOUND", "주문 없음: $orderId")
        return ApiResponse.ok(order.toSummary())
    }

    private fun Order.toSummary() = mapOf(
        "orderId"     to id,
        "symbol"      to symbol,
        "side"        to side,
        "type"        to type,
        "quantity"    to quantity,
        "price"       to price,
        "filledPrice" to filledPrice,
        "status"      to status,
        "createdAt"   to createdAt
    )
}
