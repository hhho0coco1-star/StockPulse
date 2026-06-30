package com.stockpulse.discussion.controller

import com.stockpulse.common.ApiResponse
import com.stockpulse.discussion.dto.ChatMessageRequest
import com.stockpulse.discussion.dto.CommentRequest
import com.stockpulse.discussion.dto.PostRequest
import com.stockpulse.discussion.service.DiscussionService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
class DiscussionController(private val service: DiscussionService) {

    // ── D1: 게시글 목록 ────────────────────────────────────────────────────────

    @GetMapping("/discussions/{symbol}/posts")
    fun listPosts(
        @PathVariable symbol: String,
        @RequestHeader("X-User-Id") userId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ApiResponse<Any>> {
        val data = service.listPosts(symbol, page, size)
        return ResponseEntity.ok(ApiResponse.ok(data))
    }

    // ── D2: 게시글 작성 ────────────────────────────────────────────────────────

    @PostMapping("/discussions/{symbol}/posts")
    fun createPost(
        @PathVariable symbol: String,
        @RequestHeader("X-User-Id") userId: Long,
        @Valid @RequestBody request: PostRequest
    ): ResponseEntity<ApiResponse<Any>> {
        return try {
            val data = service.createPost(symbol, userId, request)
            ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.fail("VALIDATION_ERROR", e.message ?: "Invalid input"))
        }
    }

    // ── D3: 게시글 상세 ────────────────────────────────────────────────────────

    @GetMapping("/discussions/posts/{postId}")
    fun getPost(
        @PathVariable postId: Long,
        @RequestHeader("X-User-Id") userId: Long
    ): ResponseEntity<ApiResponse<Any>> {
        return try {
            val data = service.getPost(postId)
            ResponseEntity.ok(ApiResponse.ok(data))
        } catch (e: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("NOT_FOUND", "Post not found"))
        }
    }

    // ── D4: 게시글 삭제 ────────────────────────────────────────────────────────

    @DeleteMapping("/discussions/posts/{postId}")
    fun deletePost(
        @PathVariable postId: Long,
        @RequestHeader("X-User-Id") userId: Long
    ): ResponseEntity<ApiResponse<Any>> {
        return try {
            service.deletePost(postId, userId)
            ResponseEntity.ok(ApiResponse.ok(mapOf("postId" to postId, "deleted" to true)))
        } catch (e: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("NOT_FOUND", "Post not found"))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail("FORBIDDEN", "Not your post"))
        }
    }

    // ── D5: 댓글 목록 ─────────────────────────────────────────────────────────

    @GetMapping("/discussions/posts/{postId}/comments")
    fun listComments(
        @PathVariable postId: Long,
        @RequestHeader("X-User-Id") userId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ApiResponse<Any>> {
        return try {
            val data = service.listComments(postId, page, size)
            ResponseEntity.ok(ApiResponse.ok(data))
        } catch (e: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("NOT_FOUND", "Post not found"))
        }
    }

    // ── D6: 댓글 작성 ─────────────────────────────────────────────────────────

    @PostMapping("/discussions/posts/{postId}/comments")
    fun createComment(
        @PathVariable postId: Long,
        @RequestHeader("X-User-Id") userId: Long,
        @Valid @RequestBody request: CommentRequest
    ): ResponseEntity<ApiResponse<Any>> {
        return try {
            val data = service.createComment(postId, userId, request)
            ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data))
        } catch (e: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("NOT_FOUND", "Post not found"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse.fail("VALIDATION_ERROR", e.message ?: "Invalid input"))
        }
    }

    // ── D7: 좋아요 토글 ────────────────────────────────────────────────────────

    @PostMapping("/discussions/posts/{postId}/like")
    fun toggleLike(
        @PathVariable postId: Long,
        @RequestHeader("X-User-Id") userId: Long
    ): ResponseEntity<ApiResponse<Any>> {
        return try {
            val data = service.toggleLike(postId, userId)
            ResponseEntity.ok(ApiResponse.ok(data))
        } catch (e: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("NOT_FOUND", "Post not found"))
        }
    }

    // ── D8: 채팅 발행 (REST 보조) ──────────────────────────────────────────────

    @PostMapping("/discussions/{roomId}/chat")
    fun publishChat(
        @PathVariable roomId: String,
        @RequestHeader("X-User-Id") userId: Long,
        @Valid @RequestBody request: ChatMessageRequest
    ): ResponseEntity<ApiResponse<Any>> {
        val data = service.publishChat(roomId, userId, request.message)
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(data))
    }
}
