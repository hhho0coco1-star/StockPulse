// StockPulse — common 모듈 빌드 스크립트
// WBS 0-1: 공통 라이브러리 모듈 골격
//
// common은 실행형이 아닌 라이브러리 모듈이다.
// - bootJar 비활성, jar 활성 → common-0.0.1-SNAPSHOT.jar(일반 라이브러리 jar) 생성
// - 모든 서비스가 implementation("project(":common")")로 의존한다.

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

// ── bootJar 비활성 / 일반 jar 활성 ──────────────────────────────────────
// common은 실행형 Boot jar가 아닌 라이브러리 jar여야 한다.
tasks.bootJar { enabled = false }
tasks.jar     { enabled = true  }

// ── Spring BOM (버전 정렬) ──────────────────────────────────────────────
dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}")
    }
}

// ── 의존성 ──────────────────────────────────────────────────────────────
dependencies {
    // Kotlin 리플렉션 — Jackson Kotlin 모듈 요구 사항
    implementation(libs.kotlin.reflect)

    // Jackson Kotlin 모듈 — 공통 응답/에러 포맷 직렬화에 필요
    implementation(libs.jackson.module.kotlin)

    // 테스트
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotlin.test.junit5)
}

// ── 테스트 태스크: JUnit5 사용 ───────────────────────────────────────────
tasks.test {
    useJUnitPlatform()
}
