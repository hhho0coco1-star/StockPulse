// StockPulse — 루트 빌드 스크립트 (공통 설정)
// WBS 0-1: Gradle 멀티모듈 골격 초기 구성
//
// ⚠️ JDK 호환성 주의:
//   - 이 PC에는 Java 24가 설치되어 있으나 Spring Boot 3.3.x는 Java 17~21 검증.
//   - Gradle toolchain을 JDK 21로 고정하여 런타임(Java 24)과 빌드(JDK 21)를 분리.
//   - JDK 21이 로컬에 없으면 settings.gradle.kts의 Foojay resolver가 자동 다운로드.

// ── 플러그인 선언 (apply false — 하위 모듈이 필요에 따라 적용) ────────────
plugins {
    alias(libs.plugins.kotlin.jvm)                  apply false
    alias(libs.plugins.kotlin.spring)               apply false
    alias(libs.plugins.spring.boot)                 apply false
    alias(libs.plugins.spring.dependency.management) apply false
}

// ── 전체 프로젝트 공통 설정 ───────────────────────────────────────────────
allprojects {
    group   = "com.stockpulse"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

// ── 하위 모듈 공통 설정 ────────────────────────────────────────────────────
subprojects {
    // ── Java Toolchain: JDK 21 고정 ─────────────────────────────────────
    // 시스템 Java(현재 24)와 무관하게 빌드는 JDK 21로 수행됨.
    // JDK 21이 없으면 Foojay resolver가 자동 프로비저닝.
    plugins.withType<org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper> {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }

        // ── Kotlin 컴파일 옵션 ─────────────────────────────────────────
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            compilerOptions {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
                freeCompilerArgs.addAll(
                    listOf(
                        "-Xjsr305=strict",   // JSR-305 null 어노테이션 엄격 적용 (Spring null 안정성)
                    )
                )
            }
        }
    }

    // ── Java toolchain도 동일하게 JDK 21로 고정 ─────────────────────────
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }
    }
}
