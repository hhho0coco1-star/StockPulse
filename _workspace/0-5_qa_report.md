# QA 리포트: WBS 0-5 api-gateway + auth-service 골격

> 검증 수행: 오케스트레이터 직접 실행

## 최종 판정: **PASS** (D1~D8 전체 충족)

## DoD 항목별 결과

| # | 항목 | 결과 |
|---|------|------|
| D1 | `./gradlew build` 성공 | ✅ BUILD SUCCESSFUL in 35s |
| D2 | common 모듈 `ApiResponse.kt` 존재 | ✅ |
| D3 | settings.gradle.kts 두 모듈 include | ✅ |
| D4 | auth-service 8081 포트 기동 | ✅ Tomcat started on port 8081 |
| D5 | `POST /auth/signup` 201 응답 | ✅ `{ success: true, data: { userId: 1, email } }` |
| D6 | `POST /auth/login` 200 + 토큰 응답 | ✅ `{ accessToken, refreshToken, expiresIn: 3600 }` |
| D7 | api-gateway 8080 포트 기동 | ✅ Netty started on port 8080 |
| D8 | Gateway(8080) 통해 auth-service 라우팅 동작 | ✅ `{ success: true, data: { userId: 1, email } }` |

## 수정 이력
- `AuthApplication.kt`: `UserDetailsServiceAutoConfiguration` 추가 제외 (빈 password 에러 해결)
- `application.yml`: 빈 security.user 설정 제거

## 골격 단계 제약 (Phase 1에서 해제)
- DB 없이 기동 (`DataSourceAutoConfiguration` 제외)
- 모든 엔드포인트 stub 응답
- JwtAuthFilter X-User-Id 하드코딩
