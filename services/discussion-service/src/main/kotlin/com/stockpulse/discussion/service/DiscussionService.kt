package com.stockpulse.discussion.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.stockpulse.discussion.domain.Comment
import com.stockpulse.discussion.domain.Post
import com.stockpulse.discussion.domain.PostLike
import com.stockpulse.discussion.dto.*
import com.stockpulse.discussion.repository.CommentRepository
import com.stockpulse.discussion.repository.PostLikeRepository
import com.stockpulse.discussion.repository.PostRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class DiscussionService(
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
    private val postLikeRepository: PostLikeRepository,
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {

    // ── 게시글 목록 (D1) ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun listPosts(symbol: String, page: Int, size: Int): PageResponse<PostResponse> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = postRepository.findBySymbolOrderByCreatedAtDesc(symbol, pageable)
        return PageResponse(
            content = result.content.map { it.toResponse() },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    // ── 게시글 작성 (D2) ───────────────────────────────────────────────────────

    @Transactional
    fun createPost(symbol: String, userId: Long, request: PostRequest): PostResponse {
        require(request.content.isNotBlank()) { "content must not be blank" }
        require(request.content.length <= 2000) { "content too long (max 2000)" }
        val post = postRepository.save(Post(symbol = symbol, userId = userId, content = request.content))
        return post.toResponse()
    }

    // ── 게시글 상세 (D3) ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun getPost(postId: Long): PostResponse {
        return findPostOrThrow(postId).toResponse()
    }

    // ── 게시글 삭제 (D4) ───────────────────────────────────────────────────────

    @Transactional
    fun deletePost(postId: Long, userId: Long) {
        val post = findPostOrThrow(postId)
        check(post.userId == userId) { "FORBIDDEN" }
        postRepository.delete(post)
    }

    // ── 댓글 목록 (D5) ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun listComments(postId: Long, page: Int, size: Int): PageResponse<CommentResponse> {
        findPostOrThrow(postId)
        val pageable = PageRequest.of(page, size)
        val result = commentRepository.findByPostIdOrderByCreatedAtAsc(postId, pageable)
        return PageResponse(
            content = result.content.map { it.toResponse() },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    // ── 댓글 작성 (D6) ────────────────────────────────────────────────────────

    @Transactional
    fun createComment(postId: Long, userId: Long, request: CommentRequest): CommentResponse {
        require(request.content.isNotBlank()) { "content must not be blank" }
        require(request.content.length <= 500) { "content too long (max 500)" }
        val post = findPostOrThrow(postId)
        val comment = commentRepository.save(
            Comment(postId = postId, userId = userId, content = request.content)
        )
        post.commentCount += 1
        postRepository.save(post)
        return comment.toResponse()
    }

    // ── 좋아요 토글 (D7) ──────────────────────────────────────────────────────

    @Transactional
    fun toggleLike(postId: Long, userId: Long): LikeResponse {
        val post = findPostOrThrow(postId)
        val alreadyLiked = postLikeRepository.existsByPostIdAndUserId(postId, userId)
        val liked: Boolean
        if (alreadyLiked) {
            postLikeRepository.deleteByPostIdAndUserId(postId, userId)
            post.likeCount = maxOf(0, post.likeCount - 1)
            liked = false
        } else {
            postLikeRepository.save(PostLike(postId = postId, userId = userId))
            post.likeCount += 1
            liked = true
        }
        postRepository.save(post)
        return LikeResponse(postId = post.id, liked = liked, likeCount = post.likeCount)
    }

    // ── 채팅 발행 (D8 REST + STOMP) ──────────────────────────────────────────

    fun publishChat(roomId: String, userId: Long, message: String): ChatPublishResponse {
        val payload = ChatBroadcast(
            roomId = roomId,
            userId = userId,
            message = message,
            sentAt = Instant.now()
        )
        val json = objectMapper.writeValueAsString(payload)
        redisTemplate.convertAndSend("chat:$roomId", json)
        return ChatPublishResponse(roomId = roomId, published = true)
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────────────────────

    private fun findPostOrThrow(postId: Long): Post =
        postRepository.findById(postId).orElseThrow { NoSuchElementException("NOT_FOUND:$postId") }

    private fun Post.toResponse() = PostResponse(
        postId = id, symbol = symbol, userId = userId,
        content = content, likeCount = likeCount, commentCount = commentCount,
        createdAt = createdAt
    )

    private fun Comment.toResponse() = CommentResponse(
        commentId = id, postId = postId, userId = userId,
        content = content, createdAt = createdAt
    )
}
