package com.stockpulse.trading.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.stockpulse.trading.domain.*
import com.stockpulse.trading.kafka.OrderEventPublisher
import com.stockpulse.trading.repository.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class TradingService(
    private val orderRepository: OrderRepository,
    private val orderSagaRepository: OrderSagaRepository,
    private val processedEventRepository: ProcessedEventRepository,
    private val orderEventPublisher: OrderEventPublisher,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun placeOrder(userId: Long, symbol: String, side: String, type: String, quantity: Long, price: BigDecimal): Order {
        val filledPrice = if (type == "MARKET") price else price
        val order = orderRepository.save(Order(
            userId = userId, symbol = symbol, side = side, type = type,
            quantity = quantity, price = price, filledPrice = filledPrice
        ))
        orderSagaRepository.save(OrderSaga(orderId = order.id))

        publishOrderEvent("RESERVE_BALANCE", order.id, userId, symbol, side, quantity, filledPrice, order.totalAmount)
        log.info("주문 접수: orderId=${order.id} userId=$userId $side $symbol x$quantity")
        return order
    }

    @Transactional
    fun onBalanceReserved(eventId: String, orderId: Long) {
        if (!markProcessed(eventId)) return
        val order = orderRepository.findById(orderId).orElse(null) ?: return
        order.status = OrderStatus.RESERVED
        orderRepository.save(order)

        val saga = orderSagaRepository.findById(orderId).orElse(null) ?: return
        saga.currentStep = "UPDATE_HOLDINGS"; saga.stepStatus = "PENDING"
        orderSagaRepository.save(saga)

        publishOrderEvent("UPDATE_HOLDINGS", order.id, order.userId, order.symbol,
            order.side, order.quantity, order.filledPrice, order.totalAmount)
        log.info("잔고 예약 완료 → 보유 갱신 요청: orderId=$orderId")
    }

    @Transactional
    fun onBalanceRejected(eventId: String, orderId: Long, reason: String?) {
        if (!markProcessed(eventId)) return
        val order = orderRepository.findById(orderId).orElse(null) ?: return
        order.status = OrderStatus.REJECTED
        orderRepository.save(order)
        log.warn("주문 거절: orderId=$orderId reason=$reason")
    }

    @Transactional
    fun onHoldingsUpdated(eventId: String, orderId: Long) {
        if (!markProcessed(eventId)) return
        val order = orderRepository.findById(orderId).orElse(null) ?: return
        order.status = OrderStatus.COMPLETED

        publishOrderEvent("CONFIRM_BALANCE", order.id, order.userId, order.symbol,
            order.side, order.quantity, order.filledPrice, order.totalAmount)
        orderRepository.save(order)
        log.info("주문 완료: orderId=$orderId")
    }

    @Transactional
    fun onHoldingsFailed(eventId: String, orderId: Long, reason: String?) {
        if (!markProcessed(eventId)) return
        val order = orderRepository.findById(orderId).orElse(null) ?: return
        order.status = OrderStatus.COMPENSATING
        orderRepository.save(order)

        publishOrderEvent("RELEASE_BALANCE", order.id, order.userId, order.symbol,
            order.side, order.quantity, order.filledPrice, order.totalAmount)
        log.warn("보유 갱신 실패 → 보상(잔고 해제): orderId=$orderId reason=$reason")
    }

    @Transactional
    fun onBalanceReleased(eventId: String, orderId: Long) {
        if (!markProcessed(eventId)) return
        val order = orderRepository.findById(orderId).orElse(null) ?: return
        order.status = OrderStatus.CANCELLED
        orderRepository.save(order)
        log.info("보상 완료 → 주문 취소: orderId=$orderId")
    }

    fun getOrders(userId: Long) = orderRepository.findByUserIdOrderByCreatedAtDesc(userId)
    fun getOrder(orderId: Long) = orderRepository.findById(orderId).orElse(null)

    private fun markProcessed(eventId: String): Boolean {
        if (processedEventRepository.existsById(eventId)) {
            log.debug("중복 이벤트 무시: $eventId")
            return false
        }
        processedEventRepository.save(ProcessedEvent(eventId))
        return true
    }

    private fun publishOrderEvent(
        type: String, orderId: Long, userId: Long, symbol: String,
        side: String, quantity: Long, filledPrice: BigDecimal, amount: BigDecimal
    ) {
        val payload = objectMapper.writeValueAsString(mapOf(
            "type" to type, "orderId" to orderId, "userId" to userId,
            "symbol" to symbol, "side" to side, "quantity" to quantity,
            "filledPrice" to filledPrice, "amount" to amount
        ))
        orderEventPublisher.publish(orderId.toString(), payload)
    }
}
