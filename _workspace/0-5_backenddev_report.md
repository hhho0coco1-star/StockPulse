# backend-dev 리포트: WBS 0-5

## 구현 완료 파일 목록

| 파일 | 설명 |
|------|------|
| `settings.gradle.kts` | services:api-gateway, services:auth-service 모듈 활성화 |
| `gradle/libs.versions.toml` | Spring Cloud 2023.0.3, JJWT 0.12.6, JPA/Security 의존성 추가 |
| `common/src/.../ApiResponse.kt` | 공통 응답 포맷 (ok/fail 팩토리 메서드 포함) |
| `services/api-gateway/build.gradle.kts` | Spring Cloud Gateway + Redis Reactive 의존성 |
| `services/api-gateway/.../GatewayApplication.kt` | Gateway 메인 진입점 |
| `services/api-gateway/.../filter/JwtAuthFilter.kt` | JWT 필터 골격 (public path 통과, 나머지 헤더 검사) |
| `services/api-gateway/.../application.yml` | 포트 8080, 7개 서비스 라우팅 규칙, Redis 설정 |
| `services/auth-service/build.gradle.kts` | Web + Security + JPA + JJWT 의존성 |
| `services/auth-service/.../AuthApplication.kt` | DataSource 제외 기동 (골격 단계) |
| `services/auth-service/.../dto/AuthDto.kt` | 요청/응답 DTO 5종 |
| `services/auth-service/.../domain/User.kt` | JPA 엔티티 골격 (users 테이블) |
| `services/auth-service/.../controller/AuthController.kt` | 5개 엔드포인트 stub 응답 |
| `services/auth-service/.../config/SecurityConfig.kt` | Stateless + public path permitAll |
| `services/auth-service/.../application.yml` | 포트 8081 |

## 빌드 결과
```
BUILD SUCCESSFUL in 35s
16 actionable tasks: 10 executed, 6 up-to-date
```

## 수정 이력
- `AuthController.kt:38` — `ApiResponse<Nothing>` → `ApiResponse<Unit>` 타입 수정 (컴파일 에러 자체 수정)

## 골격 단계 제약사항 (Phase 1에서 제거 예정)
- auth-service: `DataSourceAutoConfiguration` 제외 → DB 없이 기동
- 모든 엔드포인트: stub 응답 (실제 DB 저장·JWT 검증 없음)
- JwtAuthFilter: X-User-Id 헤더 "todo-extract-from-jwt" 하드코딩
