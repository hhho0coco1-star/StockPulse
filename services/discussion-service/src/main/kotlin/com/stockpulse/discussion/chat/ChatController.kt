package com.stockpulse.discussion.chat

import com.stockpulse.discussion.dto.ChatMessageRequest
import com.stockpulse.discussion.service.DiscussionService
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.stereotype.Controller

@Controller
class ChatController(private val discussionService: DiscussionService) {

    /**
     * STOMP 클라이언트가 /app/chat/{roomId} 로 보낸 메시지를
     * Redis Pub/Sub로 발행한다. STOMP로 직접 응답하지 않음(fan-out은 RedisRelayListener 담당).
     */
    @MessageMapping("/chat/{roomId}")
    fun handleChat(
        @DestinationVariable roomId: String,
        @Payload request: ChatMessageRequest,
        headerAccessor: SimpMessageHeaderAccessor
    ) {
        // 세션 attribute에서 userId 추출 (없으면 0 — Phase 4 STOMP 인증은 스텁)
        val userId = headerAccessor.sessionAttributes?.get("userId") as? Long ?: 0L
        discussionService.publishChat(roomId, userId, request.message)
    }
}
