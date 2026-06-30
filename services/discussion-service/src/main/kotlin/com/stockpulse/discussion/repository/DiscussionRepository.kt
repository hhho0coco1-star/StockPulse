package com.stockpulse.discussion.repository

import com.stockpulse.discussion.domain.Comment
import com.stockpulse.discussion.domain.Post
import com.stockpulse.discussion.domain.PostLike
import com.stockpulse.discussion.domain.PostLikeId
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PostRepository : JpaRepository<Post, Long> {
    fun findBySymbolOrderByCreatedAtDesc(symbol: String, pageable: Pageable): Page<Post>
}

@Repository
interface CommentRepository : JpaRepository<Comment, Long> {
    fun findByPostIdOrderByCreatedAtAsc(postId: Long, pageable: Pageable): Page<Comment>
}

@Repository
interface PostLikeRepository : JpaRepository<PostLike, PostLikeId> {
    fun existsByPostIdAndUserId(postId: Long, userId: Long): Boolean
    fun deleteByPostIdAndUserId(postId: Long, userId: Long)
}
