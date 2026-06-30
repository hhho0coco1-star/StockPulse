package com.stockpulse.account.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.stockpulse.account.domain.Account
import com.stockpulse.account.domain.Ledger
import com.stockpulse.account.repository.AccountRepository
import com.stockpulse.account.repository.LedgerRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class AccountService(
    private val accountRepository: AccountRepository,
    private val ledgerRepository: LedgerRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getOrCreate(userId: Long): Account =
        accountRepository.findById(userId).orElseGet {
            accountRepository.save(Account(userId = userId))
        }

    @Transactional
    fun reserveBalance(orderId: Long, userId: Long, amount: BigDecimal) {
        val account = getOrCreate(userId)
        try {
            account.reserve(amount)
            accountRepository.save(account)
            ledgerRepository.save(Ledger(userId = userId, orderId = orderId, type = "RESERVE", amount = amount, balanceAfter = account.cash - account.reserved))
            publishAccountEvent("BALANCE_RESERVED", orderId, userId, amount)
            log.info("잔고 예약 성공: userId=$userId, orderId=$orderId, amount=$amount")
        } catch (e: IllegalArgumentException) {
            publishAccountEvent("BALANCE_REJECTED", orderId, userId, amount, e.message)
            log.warn("잔고 예약 실패: ${e.message}")
        }
    }

    @Transactional
    fun releaseBalance(orderId: Long, userId: Long, amount: BigDecimal) {
        val account = getOrCreate(userId)
        account.release(amount)
        accountRepository.save(account)
        ledgerRepository.save(Ledger(userId = userId, orderId = orderId, type = "RELEASE", amount = amount, balanceAfter = account.cash - account.reserved))
        publishAccountEvent("BALANCE_RELEASED", orderId, userId, amount)
        log.info("잔고 해제(보상): userId=$userId, orderId=$orderId")
    }

    @Transactional
    fun confirmBalance(orderId: Long, userId: Long, amount: BigDecimal) {
        val account = getOrCreate(userId)
        account.confirm(amount)
        accountRepository.save(account)
        ledgerRepository.save(Ledger(userId = userId, orderId = orderId, type = "CONFIRM", amount = amount, balanceAfter = account.cash - account.reserved))
        log.info("잔고 확정: userId=$userId, orderId=$orderId")
    }

    @Transactional
    fun reset(userId: Long) {
        val account = getOrCreate(userId)
        account.cash = BigDecimal("10000000")
        account.reserved = BigDecimal.ZERO
        accountRepository.save(account)
    }

    private fun publishAccountEvent(type: String, orderId: Long, userId: Long, amount: BigDecimal, reason: String? = null) {
        val payload = objectMapper.writeValueAsString(mapOf(
            "type" to type, "orderId" to orderId, "userId" to userId,
            "amount" to amount, "reason" to reason
        ))
        kafkaTemplate.send("account.events", orderId.toString(), payload)
    }
}
