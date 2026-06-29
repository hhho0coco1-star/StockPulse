package com.stockpulse.fundamentals.service

import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class DartClient(
    @Value("\${dart.api-key}") private val apiKey: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val webClient = WebClient.builder()
        .baseUrl("https://opendart.fss.or.kr/api")
        .build()

    // 주식 종목 코드(005930) → DART 고유 법인번호 조회
    fun getCorpCode(stockCode: String): String? {
        return try {
            val resp = webClient.get()
                .uri("/company.json?crtfc_key={key}&stock_code={code}", apiKey, stockCode)
                .retrieve()
                .bodyToMono(JsonNode::class.java)
                .block()
            resp?.path("corp_code")?.asText()?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            log.warn("DART 법인코드 조회 실패 [$stockCode]: ${e.message}")
            null
        }
    }

    // 단일기업 전체 재무제표 조회 (연간)
    fun getFinancials(corpCode: String, year: Int): JsonNode? {
        return try {
            webClient.get()
                .uri(
                    "/fnlttSinglAcntAll.json?crtfc_key={key}&corp_code={corp}&bsns_year={year}&reprt_code=11011&fs_div=CFS",
                    apiKey, corpCode, year
                )
                .retrieve()
                .bodyToMono(JsonNode::class.java)
                .block()
        } catch (e: Exception) {
            log.warn("DART 재무제표 조회 실패 [$corpCode/$year]: ${e.message}")
            null
        }
    }
}
