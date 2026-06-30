package com.stockpulse.discussion.chat

import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

/**
 * Redis Pub/Sub 구독자 (PatternTopic "chat:*").
 * 수신된 JSON 페이로드를 /topic/chat/{roomId} 로 STOMP fan-out한다.
 */
@Component
class ChatRelayListener(
    private val messagingTemplate: SimpMessagingTemplate
) : MessageListener {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun onMessage(message: Message, pattern: ByteArray?) {
        val channel = String(message.channel)           // e.g. "chat:005930"
        val payload = String(message.body)
        val roomId = channel.removePrefix("chat:")
        log.debug("Redis 수신 채널={} payload={}", channel, payload)
        messagingTemplate.convertAndSend("/topic/chat/$roomId", payload)
    }
}
