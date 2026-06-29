plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":common"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.mongodb)
    implementation(libs.spring.kafka)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)

    testImplementation(libs.spring.boot.starter.test)
}
