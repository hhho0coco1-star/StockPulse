package com.stockpulse.auth

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.runApplication

// 골격 단계 제외 항목 (TODO Phase 1: 실제 구현 후 제거)
// - DataSourceAutoConfiguration: DB 없이 기동
// - UserDetailsServiceAutoConfiguration: 커스텀 UserDetailsService 구현 전까지 제외
@SpringBootApplication(exclude = [DataSourceAutoConfiguration::class, UserDetailsServiceAutoConfiguration::class])
class AuthApplication

fun main(args: Array<String>) {
    runApplication<AuthApplication>(*args)
}
