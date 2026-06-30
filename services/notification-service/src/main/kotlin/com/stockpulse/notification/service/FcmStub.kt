package com.stockpulse.notification.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * FCM 발송 스텁 — Phase 4: 실제 Firebase 호출 없이 로그만 남긴다.
 * Phase 5+에서 Firebase Admin SDK 연동으로 교체 예정.
 */
@Component
class FcmStub {

    private val log = LoggerFactory.getLogger(javaClass)

    fun send(token: String, title: String, body: String, dataJson: String) {
        log.info("FCM 발송: token={} title={} body={} data={}", token, title, body, dataJson)
    }
}
