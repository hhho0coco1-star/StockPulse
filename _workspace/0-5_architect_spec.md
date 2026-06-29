# WBS 0-5 구현 스펙: api-gateway + auth-service Kotlin 골격

> 근거 문서: docs/02_시스템아키텍처.md §2, docs/03_API설계.md §1·§2.1, docs/04_DB설계.md §2.1
> 작성자: architect (오케스트레이터 직접 수행)

---

## 1. 범위 확정

| 서비스 | 포트 | 역할 |
|--------|------|------|
| api-gateway | 8080 | JWT 필터 · 라우팅 · Redis Rate Limit |
| auth-service | 8081 | 회원가입 · 로그인 · JWT 발급 · OAuth2 골격 |

**골격 단계 원칙**: 빌드 성공 + 엔드포인트 응답 확인까지만. 실제 DB 연동·JWT 검증 로직·Redis 연동은 Phase 1 이후 살 붙임.

---

## 2. 디렉토리 구조

```
StockPulse/
├── services/
│   ├── api-gateway/
│   │   ├── build.gradle.kts
│   │   └── src/main/
│   │       ├── kotlin/com/stockpulse/gateway/
│   │       │   ├── GatewayApplication.kt
│   │       │   └── filter/
│   │       │       └── JwtAuthFilter.kt        (골격 — 실제 검증 로직 미구현)
│   │       └── resources/
│   │           └── application.yml
│   └── auth-service/
│       ├── build.gradle.kts
│       └── src/main/
│           ├── kotlin/com/stockpulse/auth/
│           │   ├── AuthApplication.kt
│           │   ├── controller/
│           │   │   └── AuthController.kt
│           │   ├── dto/
│           │   │   ├── SignupRequest.kt
│           │   │   ├── LoginRequest.kt
│           │   │   └── TokenResponse.kt
│           │   └── domain/
│           │       └── User.kt                 (JPA 엔티티 골격)
│           └── resources/
│               └── application.yml
└── common/                                     (기존 — ApiResponse 추가)
    └── src/main/kotlin/com/stockpulse/common/
        └── ApiResponse.kt
```

---

## 3. 의존성 확정

### 3.1 libs.versions.toml 추가 항목

```toml
[versions]
springCloud = "2023.0.3"   # Spring Boot 3.3.x 호환

[libraries]
# Gateway
spring-cloud-gateway    = { module = "org.springframework.cloud:spring-cloud-starter-gateway" }
spring-cloud-loadbalancer = { module = "org.springframework.cloud:spring-cloud-starter-loadbalancer" }
spring-boot-starter-data-redis-reactive = { module = "org.springframework.boot:spring-boot-starter-data-redis-reactive" }

# Auth
spring-boot-starter-web      = { module = "org.springframework.boot:spring-boot-starter-web" }
spring-boot-starter-security = { module = "org.springframework.boot:spring-boot-starter-security" }
spring-boot-starter-data-jpa = { module = "org.springframework.boot:spring-boot-starter-data-jpa" }
postgresql                   = { module = "org.postgresql:postgresql" }
jjwt-api                     = { module = "io.jsonwebtoken:jjwt-api",  version = "0.12.6" }
jjwt-impl                    = { module = "io.jsonwebtoken:jjwt-impl", version = "0.12.6" }
jjwt-jackson                 = { module = "io.jsonwebtoken:jjwt-jackson", version = "0.12.6" }

[plugins]
kotlin-jpa = { id = "org.jetbrains.kotlin.plugin.jpa", version.ref = "kotlin" }
```

### 3.2 api-gateway/build.gradle.kts 의존성
- spring-cloud-gateway (reactive, WebFlux 기반)
- spring-boot-starter-data-redis-reactive (Rate Limit)
- spring-cloud-loadbalancer
- kotlin-reflect, jackson-module-kotlin

### 3.3 auth-service/build.gradle.kts 의존성
- spring-boot-starter-web
- spring-boot-starter-security
- spring-boot-starter-data-jpa + postgresql
- jjwt-api/impl/jackson
- kotlin-reflect, jackson-module-kotlin, kotlin-jpa 플러그인

---

## 4. 인터페이스 계약

### 4.1 api-gateway 라우팅 규칙 (application.yml)
```yaml
spring.cloud.gateway.routes:
  - id: auth-service
    uri: http://localhost:8081
    predicates: Path=/auth/**, /users/**
  - id: market-service          # Phase 1에서 실제 서비스 뜰 때 활성화
    uri: http://localhost:8082
    predicates: Path=/market/**
```

### 4.2 auth-service REST 계약 (docs/03_API설계.md §2.1 그대로)
| 메서드 | 경로 | 골격 응답 |
|--------|------|----------|
| POST | `/auth/signup` | 201 Created + `{ success: true, data: { userId, email } }` |
| POST | `/auth/login` | 200 OK + `{ success: true, data: { accessToken, refreshToken, expiresIn } }` |
| POST | `/auth/refresh` | 200 OK + `{ success: true, data: { accessToken, expiresIn } }` |
| POST | `/auth/logout` | 200 OK + `{ success: true, data: null }` |
| GET | `/users/me` | 200 OK + `{ success: true, data: { userId, email, nickname } }` |

**골격 단계**: 실제 DB 저장·JWT 검증 없이 하드코딩 응답 반환. 컴파일 + 엔드포인트 연결 확인이 목표.

### 4.3 공통 응답 포맷 (common/ApiResponse.kt)
```kotlin
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null
)
data class ApiError(val code: String, val message: String)
```

### 4.4 User JPA 엔티티 (골격)
```kotlin
@Entity @Table(name = "users")
class User(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(unique = true) val email: String,
    val passwordHash: String,
    val nickname: String,
    @Enumerated(EnumType.STRING) val provider: Provider = Provider.LOCAL,
    val createdAt: LocalDateTime = LocalDateTime.now()
)
enum class Provider { LOCAL, GOOGLE, KAKAO }
```

---

## 5. 완료 기준 (DoD) — 사용자 검증 항목

| # | 검증 항목 | 검증 방법 |
|---|-----------|----------|
| D1 | `settings.gradle.kts`에 두 모듈 include | 파일 확인 |
| D2 | `./gradlew build` 성공 (컴파일 에러 없음) | 터미널 실행 |
| D3 | common 모듈에 `ApiResponse.kt` 존재 | 파일 확인 |
| D4 | api-gateway: 8080 포트 정상 기동 | `bootRun` 후 로그 확인 |
| D5 | auth-service: 8081 포트 정상 기동 | `bootRun` 후 로그 확인 |
| D6 | `POST /auth/signup` 호출 시 201 응답 | curl 또는 Postman |
| D7 | `POST /auth/login` 호출 시 200 + 토큰 응답 | curl 또는 Postman |
| D8 | api-gateway 통해 auth-service 라우팅 동작 | 8080 포트로 /auth/signup 호출 |

---

## 6. 구현 시 주의사항 (backend-dev 전달)

1. **api-gateway는 WebFlux(reactive) 기반** — Spring MVC 혼용 불가. `spring-boot-starter-web` 넣으면 충돌
2. **auth-service는 Spring MVC** — `spring-boot-starter-web` 사용
3. **골격 단계에서 DB 연결 없이 기동** — `application.yml`에 `spring.jpa.hibernate.ddl-auto: none`, `spring.datasource.url` 없이도 기동되도록 `@SpringBootTest` 아닌 단순 기동 확인
   - datasource 자동 구성 비활성화: `@SpringBootApplication(exclude = [DataSourceAutoConfiguration::class])` 임시 적용 (Phase 1에서 제거)
4. **settings.gradle.kts** 주석 처리된 `services:api-gateway`, `services:auth-service` 활성화
5. **공통 응답**: `common` 모듈 의존성 두 서비스 모두 추가
