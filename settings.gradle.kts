// StockPulse — Gradle 멀티모듈 루트 설정
// WBS 0-1: Gradle 멀티모듈 골격 초기 구성

// ── Foojay Toolchain Resolver ─────────────────────────────────────────────
// JDK 21 toolchain 선언 시 로컬에 JDK 21이 없으면 Gradle이 자동 다운로드한다.
// 로컬 환경: Java 24만 설치되어 있어 auto-provisioning 플러그인이 필요함.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "stockpulse"

// ── 등록된 모듈 ─────────────────────────────────────────────────────────────
include("common")
include(
    "services:api-gateway",
    "services:auth-service",
    "services:market-collector",
    "services:realtime-gateway",
    "services:news-collector",
    "services:fundamentals-collector",
    "services:insight-service"
)

// ── 미등록 서비스 (이후 서비스별로 순차 추가 예정) ─────────────────
// 서비스가 실제 구현될 때 아래 형태로 추가:
// include(
//     "services:api-gateway",     // 완료 (WBS 0-5)
//     "services:auth-service",    // 완료 (WBS 0-5)
//     "services:user-service",
//     "services:stock-service",
//     "services:portfolio-service",
//     "services:trading-service",
//     "services:notification-service",
//     "services:feed-service",
//     "services:community-service",
//     "services:search-service",
//     "services:ai-service",
//     "services:data-collector",
//     "services:admin-service"
// )
