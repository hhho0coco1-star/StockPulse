package com.stockpulse.discussion.domain

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "posts",
    indexes = [Index(name = "idx_posts_symbol_created", columnList = "symbol, created_at DESC")]
)
data class Post(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 20)
    val symbol: String,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String,

    @Column(name = "like_count", nullable = false)
    var likeCount: Int = 0,

    @Column(name = "comment_count", nullable = false)
    var commentCount: Int = 0,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)

@Entity
@Table(
    name = "comments",
    indexes = [Index(name = "idx_comments_post", columnList = "post_id, created_at")]
)
data class Comment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "post_id", nullable = false)
    val postId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "post_likes")
@IdClass(PostLikeId::class)
data class PostLike(
    @Id
    @Column(name = "post_id", nullable = false)
    val postId: Long,

    @Id
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)

data class PostLikeId(
    val postId: Long = 0,
    val userId: Long = 0
) : java.io.Serializable
