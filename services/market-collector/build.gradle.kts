plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":common"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.kafka)
    implementation(libs.postgresql)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.reactor)
    implementation(libs.resilience4j.spring.boot3)
    implementation(libs.resilience4j.kotlin)
    implementation(libs.spring.boot.starter.aop)
    implementation(libs.spring.boot.starter.actuator)

    // ── Phase 6 관측 ──────────────────────────────────────────
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.micrometer.tracing.bridge.otel)
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(libs.loki.logback.appender)
    implementation(libs.micrometer.context.propagation)   // reactive 컨텍스트 전파
    implementation(libs.kotlinx.coroutines.slf4j)          // 코루틴 MDC 전파

    testImplementation(libs.spring.boot.starter.test)
}
