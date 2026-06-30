package com.stockpulse.account.controller

import com.stockpulse.common.ApiResponse
import com.stockpulse.account.service.AccountService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/account")
class AccountController(private val accountService: AccountService) {

    @GetMapping
    fun getAccount(@RequestHeader("X-User-Id") userId: Long): ApiResponse<Map<String, Any>> {
        val account = accountService.getOrCreate(userId)
        return ApiResponse.ok(mapOf(
            "cash"      to account.cash,
            "reserved"  to account.reserved,
            "available" to account.availableCash
        ))
    }

    @PostMapping("/reset")
    fun reset(@RequestHeader("X-User-Id") userId: Long): ApiResponse<Unit> {
        accountService.reset(userId)
        return ApiResponse(success = true)
    }
}
