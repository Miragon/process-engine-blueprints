import org.springframework.boot.gradle.tasks.bundling.BootBuildImage
import org.springframework.boot.gradle.tasks.bundling.BootJar
import java.math.BigDecimal

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springframework)
    alias(libs.plugins.spring.dependency)
    alias(libs.plugins.pitest)
}

springBoot {
    buildInfo()
}

dependencies {
    implementation(libs.bundles.defaultService)
    implementation(libs.bundles.database)
    implementation(project(":service:common-temporal"))
    implementation(libs.springdoc)
    testImplementation(libs.bundles.test)
    testImplementation(libs.bundles.temporalTesting)
    testImplementation(project(":service:common-temporal-test"))
    testImplementation(project(":service:common-architecture-tests"))
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.test {
    useJUnitPlatform()
}

pitest {
    junit5PluginVersion.set("1.2.2")
    targetClasses.set(listOf("io.miragon.blueprint.domain.*"))
    targetTests.set(listOf("io.miragon.blueprint.*"))
    excludedTestClasses.set(
        listOf(
            "io.miragon.blueprint.adapter.process.*",
            "io.miragon.blueprint.ArchitectureTest*",
        ),
    )
    threads.set(Runtime.getRuntime().availableProcessors())
    timeoutFactor.set(BigDecimal("2.0"))
    avoidCallsTo.set(listOf("kotlin.jvm.internal", "mu", "org.slf4j", "io.github.oshai"))
    mutators.set(listOf("DEFAULTS"))
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
    mutationThreshold.set(80)
}

tasks.withType<BootJar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// OCI image for the backend, built by Spring's Cloud Native Buildpacks integration — no Dockerfile to
// maintain (layered, non-root by default). See docs/adr and the "Run it in containers" section of
// CONTRIBUTING.md. Build with `./gradlew :service:app:bootBuildImage`.
tasks.named<BootBuildImage>("bootBuildImage") {
    imageName.set("miravelo/temporal-example:${project.version}")
    // Pin the JVM the buildpack installs to the version the code targets.
    environment.set(mapOf("BP_JVM_VERSION" to "21"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
}
