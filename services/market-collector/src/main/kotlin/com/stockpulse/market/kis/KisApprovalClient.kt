package com.stockpulse.market.kis

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.kotlin.circuitbreaker.executeSuspendFunction
import io.github.resilience4j.kotlin.retry.executeSuspendFunction
import io.github.resilience4j.kotlin.timelimiter.executeSuspendFunction
import io.github.resilience4j.retry.RetryRegistry
import io.github.resilience4j.timelimiter.TimeLimiterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Component
class KisApprovalClient(
    @Value("\${kis.app-key}") private val appKey: String,
    @Value("\${kis.app-secret}") private val appSecret: String,
    @Value("\${kis.base-url}") private val baseUrl: String,
    private val cbRegistry: CircuitBreakerRegistry,
    private val retryRegistry: RetryRegistry,
    private val timeLimiterRegistry: TimeLimiterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val webClient = WebClient.create(baseUrl)

    private val cb = cbRegistry.circuitBreaker("kisApproval")
    private val retry = retryRegistry.retry("kisApproval")
    private val timeLimiter = timeLimiterRegistry.timeLimiter("kisApproval")

    suspend fun getApprovalKey(): String =
        cb.executeSuspendFunction {
            retry.executeSuspendFunction {
                timeLimiter.executeSuspendFunction {
                    requestApprovalKey()
                }
            }
        }

    private suspend fun requestApprovalKey(): String {
        log.info("KIS Approval Key 발급 요청")
        val response = webClient.post()
            .uri("/oauth2/Approval")
            .bodyValue(
                mapOf(
                    "grant_type" to "client_credentials",
                    "appkey"     to appKey,
                    "secretkey"  to appSecret
                )
            )
            .retrieve()
            .awaitBody<Map<String, Any>>()

        val key = response["approval_key"] as? String
            ?: error("KIS Approval Key 발급 실패: $response")
        log.info("KIS Approval Key 발급 성공")
        return key
    }
}
