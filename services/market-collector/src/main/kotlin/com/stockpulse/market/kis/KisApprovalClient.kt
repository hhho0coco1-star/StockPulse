package com.stockpulse.market.kis

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Component
class KisApprovalClient(
    @Value("\${kis.app-key}") private val appKey: String,
    @Value("\${kis.app-secret}") private val appSecret: String,
    @Value("\${kis.base-url}") private val baseUrl: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val webClient = WebClient.create(baseUrl)

    suspend fun getApprovalKey(): String {
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
