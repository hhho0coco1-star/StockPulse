package com.stockpulse.news.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stockpulse.news.domain.NewsArticle
import com.stockpulse.news.repository.NewsRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.Instant

@Service
class NewsCollectService(
    private val newsRepository: NewsRepository,
    private val sentimentAnalyzer: SentimentAnalyzer,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    @Value("\${naver.client-id}") private val clientId: String,
    @Value("\${naver.client-secret}") private val clientSecret: String,
    @Value("\${collect.symbols}") private val symbols: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val webClient = WebClient.builder()
        .baseUrl("https://openapi.naver.com")
        .defaultHeader("X-Naver-Client-Id", clientId)
        .defaultHeader("X-Naver-Client-Secret", clientSecret)
        .build()

    private val symbolNames = mapOf(
        "005930" to "삼성전자",
        "000660" to "SK하이닉스",
        "035420" to "NAVER",
        "051910" to "LG화학"
    )

    @Scheduled(fixedDelayString = "\${collect.interval-ms:1800000}", initialDelay = 5000)
    fun collectAll() {
        symbols.split(",").map { it.trim() }.forEach { symbol ->
            try {
                collect(symbol)
            } catch (e: Exception) {
                log.error("뉴스 수집 실패 [$symbol]: ${e.message}")
            }
        }
    }

    fun collect(symbol: String) {
        val query = symbolNames[symbol] ?: symbol
        log.info("뉴스 수집 시작: $symbol ($query)")

        val response = webClient.get()
            .uri("/v1/search/news.json?query={q}&display=20&sort=date", query)
            .retrieve()
            .bodyToMono(JsonNode::class.java)
            .block() ?: return

        val items = response.path("items")
        var saved = 0
        items.forEach { item ->
            val url   = item.path("link").asText()
            val title = item.path("title").asText().replace(Regex("<[^>]*>"), "")
            if (newsRepository.existsByUrl(url)) return@forEach

            val sentiment = sentimentAnalyzer.analyze(title)
            val keywords  = sentimentAnalyzer.extractKeywords(title)
            val article = NewsArticle(
                symbol      = symbol,
                title       = title,
                url         = url,
                publishedAt = Instant.now(),
                sentiment   = sentiment,
                keywords    = keywords,
                score       = sentimentAnalyzer.score(sentiment)
            )
            newsRepository.save(article)
            kafkaTemplate.send("news.collected", symbol, objectMapper.writeValueAsString(
                mapOf("symbol" to symbol, "sentiment" to sentiment.name, "title" to title, "publishedAt" to article.publishedAt)
            ))
            saved++
        }
        log.info("뉴스 수집 완료: $symbol → ${saved}건 저장")
    }
}
