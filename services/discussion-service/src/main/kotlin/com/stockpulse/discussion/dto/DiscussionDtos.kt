package com.stockpulse.discussion.dto

import java.time.Instant

// ── Request DTOs ──────────────────────────────────────────────────────────────

data class PostRequest(
    val content: String
)

data class CommentRequest(
    val content: String
)

data class ChatMessageRequest(
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
