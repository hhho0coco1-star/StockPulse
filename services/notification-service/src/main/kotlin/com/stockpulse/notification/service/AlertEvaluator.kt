package com.stockpulse.notification.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.stockpulse.notification.domain.NotificationLog
import com.stockpulse.notification.repository.AlertRuleRepository
import com.stockpulse.notification.repository.DeviceTokenRepository
import com.stockpulse.notification.repository.NotificationLogRepository
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * market.tick 수신 시 알림 규칙을 평가하고,
 * 조건 충족 시 이력 저장 + FCM 스텁 발송을 수행한다.
 */
@Service
class AlertEvaluator(
    private val alertRuleRepository: AlertRuleRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val notificationLogRepository: NotificationLogRepository,
    private val fcmStub: FcmStub,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Retry(name = "alertEval")
    @Transactional
    fun evaluate(tickJson: String) {
        val tick: Map<String, Any> = objectMapper.readValue(tickJson)
        val symbol = tick["symbol"]?.toString() ?: return
        val price = (tick["price"] as? Number)?.toDouble() ?: return
        val changeRate = (tick["change"] as? Number)?.toDouble() ?: 0.0

        val rules = alertRuleRepository.findAllBySymbolAndEnabledTrue(symbol)
        if (rules.isEmpty()) return

        log.debug("틱 수신: symbol={} price={} change={} → 활성 규칙 {}건", symbol, price, changeRate, rules.size)

        for (rule in rules) {
            val condition: Map<String, Any> = objectMapper.readValue(rule.condition)
            val triggered = when (rule.type) {
                "TARGET_PRICE" -> {
                    val op = condition["op"]?.toString()
                    val targetPrice = (condition["price"] as? Number)?.toDouble()
                    when {
                        op == "GTE" && targetPrice != null -> price >= targetPrice
                        op == "LTE" && targetPrice != null -> price <= targetPrice
                        else -> false
                    }
                }
                "CHANGE_RATE" -> {
                    val op = condition["op"]?.toString()
                    val targetRate = (condition["rate"] as? Number)?.toDouble()
                    when {
                        op == "GTE" && targetRate != null -> changeRate >= targetRate
                        op == "LTE" && targetRate != null -> changeRate <= targetRate
                        else -> false
                    }
                }
                "NEWS" -> {
                    // Phase 4: 스킵 (후속 Phase에서 news.raw / insight.updated 연동)
                    log.debug("NEWS 규칙 평가 스킵 (Phase 4 미지원): ruleId={}", rule.id)
                    false
                }
                else -> {
                    log.warn("알 수 없는 규칙 타입: {}", rule.type)
                    false
                }
            }

            if (!triggered) continue

            // 이력 구성
            val (title, body) = buildNotificationText(rule.type, symbol, price, changeRate, condition)
            val dataPayload = mapOf(
                "symbol" to symbol,
                "price" to price.toLong().toString(),
                "type" to rule.type,
                "deeplink" to "stockpulse://stock/$symbol"
            )
            val dataJson = objectMapper.writeValueAsString(dataPayload)

            val notif = notificationLogRepository.save(
                NotificationLog(
                    userId = rule.userId,
                    type = rule.type,
                    title = title,
                    body = body,
                    data = dataJson
                )
            )
            log.info("알림 이력 저장: notificationId={} userId={} type={}", notif.id, rule.userId, rule.type)

            // FCM 스텁 발송
            val devices = deviceTokenRepository.findAllByUserId(rule.userId)
            for (device in devices) {
                fcmStub.send(device.token, title, body, dataJson)
            }
        }
    }

    private fun buildNotificationText(
        type: String, symbol: String, price: Double, changeRate: Double,
        condition: Map<String, Any>
    ): Pair<String, String> {
        return when (type) {
            "TARGET_PRICE" -> {
                val targetPrice = (condition["price"] as? Number)?.toLong()
                val op = condition["op"]?.toString()
                val opLabel = if (op == "GTE") "이상" else "이하"
                val title = "[$symbol] 목표가 도달"
                val body = "${targetPrice?.let { "%,d원".format(it) } ?: "?"} $opLabel (현재 ${"%.0f".format(price)}원)"
                title to body
            }
            "CHANGE_RATE" -> {
                val targetRate = condition["rate"]?.toString()
                val op = condition["op"]?.toString()
                val opLabel = if (op == "GTE") "이상" else "이하"
                val title = "[$symbol] 변동률 조건 충족"
                val body = "변동률 ${targetRate}% $opLabel (현재 ${"%.2f".format(changeRate)}%)"
                title to body
            }
            else -> "[$symbol] 알림" to "조건이 충족되었습니다."
        }
    }
}
