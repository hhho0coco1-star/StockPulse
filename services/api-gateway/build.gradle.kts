plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${libs.versions.springCloud.get()}")
    }
}

dependencies {
    implementation(project(":common"))
    implementation(libs.spring.cloud.gateway)
    implementation(libs.spring.cloud.loadbalancer)
    implementation(libs.spring.boot.starter.data.redis.reactive)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)

    // ── Phase 6 관측 ──────────────────────────────────────────
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.micrometer.tracing.bridge.otel)
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(libs.loki.logback.appender)
    implementation(libs.micrometer.context.propagation)   // reactive 컨텍스트 전파

    testImplementation(libs.spring.boot.starter.test)
}
