package com.stockpulse.market.kis

import com.fasterxml.jackson.databind.ObjectMapper
import com.stockpulse.market.service.TickService
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.*
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.net.URI

@Component
class KisWebSocketClient(
    private val approvalClient: KisApprovalClient,
    private val tickService: TickService,
    private val objectMapper: ObjectMapper,
    @Value("\${kis.ws-url}") private val wsUrl: String,
    @Value("\${kis.subscribe-symbols}") private val symbols: List<String>
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @PostConstruct
    fun connect() {
        scope.launch { connectWithRetry() }
    }

    private suspend fun connectWithRetry() {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            attempt++
            try {
                log.info("KIS WebSocket 연결 시도 #$attempt")
                val approvalKey = approvalClient.getApprovalKey()
                startSession(approvalKey)
            } catch (e: Exception) {
                log.error("KIS WebSocket 연결 실패: ${e.message}. 5초 후 재시도")
                delay(5_000)
            }
        }
    }

    private suspend fun startSession(approvalKey: String) {
        val client = ReactorNettyWebSocketClient()
        val subscribeMsg = objectMapper.writeValueAsString(
            buildSubscribeMessage(approvalKey, symbols.first())
        )

        log.info("KIS WebSocket 연결 중: $wsUrl")
        client.execute(URI(wsUrl)) { session ->
            log.info("KIS WebSocket 세션 수립 완료")
            val sends = session.send(
                Mono.just(session.textMessage(subscribeMsg))
            )
            val receives = session.receive()
                .filter { it.type == WebSocketMessage.Type.TEXT }
                .doOnNext { msg -> parseTick(msg.payloadAsText) }
                .then()

            sends.then(receives)
        }.awaitSingleOrNull()
    }

    private fun buildSubscribeMessage(approvalKey: String, symbol: String): Map<String, Any> = mapOf(
        "header" to mapOf(
            "approval_key" to approvalKey,
            "custtype"     to "P",
            "tr_type"      to "1",
            "content-type" to "utf-8"
        ),
        "body" to mapOf(
            "input" to mapOf(
                "tr_id"  to "H0STCNT0",
                "tr_key" to symbol
            )
        )
    )

    private fun parseTick(raw: String) {
        try {
            if (raw.startsWith("{")) return  // 구독 확인 JSON 응답
            val parts = raw.split("|")
            if (parts.size < 4) return
            val data = parts[3].split("^")
            if (data.size < 13) return
            val symbol = data[0]
            val price  = BigDecimal(data[2])
            val volume = data[12].toLongOrNull() ?: 0L
            log.info("틱 수신: $symbol price=$price volume=$volume")
            scope.launch { tickService.process(symbol, price, volume) }
        } catch (e: Exception) {
            log.debug("틱 파싱 스킵: ${e.message}")
        }
    }
}
