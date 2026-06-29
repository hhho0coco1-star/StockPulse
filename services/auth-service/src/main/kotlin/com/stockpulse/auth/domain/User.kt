package com.stockpulse.auth.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "users")
class User(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(unique = true, nullable = false)
    val email: String,

    @Column(nullable = false)
    val passwordHash: String,

    @Column(nullable = false)
    val nickname: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val provider: Provider = Provider.LOCAL,

    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class Provider { LOCAL, GOOGLE, KAKAO }
