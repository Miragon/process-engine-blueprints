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

// Diff-scoped on PRs (via -PmutationTargetClasses), full sweep nightly. Not attached to `build`.
val mutationTargetClasses = (project.findProperty("mutationTargetClasses") as String?)
    ?.split(",")?.map(String::trim)?.filter(String::isNotEmpty)

pitest {
    junit5PluginVersion.set("1.2.2")
    targetClasses.set(mutationTargetClasses ?: listOf("io.miragon.blueprint.*"))
    targetTests.set(listOf("io.miragon.blueprint.*"))
    failWhenNoMutations.set(false)
    excludedClasses.set(
        listOf(
            // Workflows need the Temporal runtime, covered by the process test.
            "io.miragon.blueprint.adapter.process.*",
            // Application bootstrap, outside the hexagonal layers.
            "io.miragon.blueprint.BikeLeasingApplication*",
            "io.miragon.blueprint.BikeCatalogueSeeder*",
            // Dev-only CORS escape hatch.
            "io.miragon.blueprint.adapter.inbound.rest.DevCorsConfiguration*",
            // Temporal engine glue (activities, workflow client) — exercised by the process test, not
            // unit-mutated.
            "io.miragon.blueprint.adapter.inbound.temporal.*",
            "io.miragon.blueprint.adapter.outbound.temporal.*",
        ),
    )
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
