package com.stockpulse.auth

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.runApplication

// DataSourceAutoConfiguration 제외: 골격 단계에서 DB 없이 기동
// TODO(Phase 1): DB 연결 설정 후 exclude 제거
@SpringBootApplication(exclude = [DataSourceAutoConfiguration::class])
class AuthApplication

fun main(args: Array<String>) {
    runApplication<AuthApplication>(*args)
}
