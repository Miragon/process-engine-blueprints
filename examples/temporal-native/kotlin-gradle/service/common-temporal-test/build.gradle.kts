plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springframework)
    alias(libs.plugins.spring.dependency)
}

group = "io.miragon.blueprint"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.temporalTesting)
    implementation(libs.spring.test)
}

tasks.bootJar {
    enabled = false
}

tasks.jar {
    enabled = true
}
