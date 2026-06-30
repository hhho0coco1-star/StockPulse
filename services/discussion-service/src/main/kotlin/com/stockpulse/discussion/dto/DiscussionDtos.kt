package com.stockpulse.discussion.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

// ── Request DTOs ──────────────────────────────────────────────────────────────

data class PostRequest(
    @field:NotBlank(message = "content는 필수입니다")
    @field:Size(max = 5000, message = "content는 최대 5000자입니다")
    val content: String
)

data class CommentRequest(
    @field:NotBlank(message = "content는 필수입니다")
    @field:Size(max = 2000, message = "content는 최대 2000자입니다")
    val content: String
)

data class ChatMessageRequest(
    @field:NotBlank(message = "message는 필수입니다")
    @field:Size(max = 2000, message = "message는 최대 2000자입니다")
    val message: String
)

// ── Response DTOs ─────────────────────────────────────────────────────────────

data class PostResponse(
    val postId: Long,
    val symbol: String,
    val userId: Long,
    val content: String,
    val likeCount: Int,
    val commentCount: Int,
    val createdAt: Instant
)

data class CommentResponse(
    val commentId: Long,
    val postId: Long,
    val userId: Long,
    val content: String,
    val createdAt: Instant
)

data class LikeResponse(
    val postId: Long,
    val liked: Boolean,
    val likeCount: Int
)

data class ChatPublishResponse(
    val roomId: String,
    val published: Boolean
)

data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

// ── STOMP payload ─────────────────────────────────────────────────────────────

data class ChatBroadcast(
    val roomId: String,
    val userId: Long,
    val message: String,
    val sentAt: Instant
)
