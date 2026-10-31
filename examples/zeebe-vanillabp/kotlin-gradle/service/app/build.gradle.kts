import io.miragon.bpmn.adapter.GenerateBpmnModelsTask
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage
import org.springframework.boot.gradle.tasks.bundling.BootJar
import java.math.BigDecimal

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springframework)
    alias(libs.plugins.spring.dependency)
    alias(libs.plugins.bpmnToCode)
    alias(libs.plugins.gradleRetryTesting)
    alias(libs.plugins.pitest)
}

springBoot {
    buildInfo()
}

// Konsist parses the sources with the embedded Kotlin compiler; Spring's dependency management would
// otherwise align it to the Kotlin plugin version, which Konsist does not support yet.
configurations.testRuntimeClasspath {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" && requested.name == "kotlin-compiler-embeddable") {
            useVersion(libs.versions.konsist.kotlin.compiler.version.get())
        }
    }
}

dependencies {
    implementation(libs.bundles.defaultService)
    implementation(libs.bundles.database)
    implementation(project(":service:common-zeebe"))
    implementation(libs.vanillabpSpi)
    implementation(libs.bpmnToCodeRuntime)
    implementation(libs.springdoc)
    testImplementation(libs.bundles.test)
    testImplementation(libs.zeebeSdk)
    testImplementation(libs.bundles.zeebeProcessTest)
    testImplementation(libs.bpmnToCodeTesting)
    testImplementation(project(":service:common-zeebe-test"))
    testImplementation(project(":service:common-architecture-tests"))
    testRuntimeOnly(libs.junitPlatformLauncher)
}

// Generates the typed `*ProcessApi` objects and the shared constant files (job types, messages, …) from the
// BPMN models, so workers and tests reference process elements as compile-checked constants.
tasks.register<GenerateBpmnModelsTask>("generateBpmnModels") {
    baseDir = projectDir.toString()
    filePattern = "src/main/resources/bpmn/*.bpmn"
    outputFolderPath = "$projectDir/src/main/kotlin"
    packagePath = "io.miragon.blueprint.adapter.process"
    outputLanguage = OutputLanguage.KOTLIN
    processEngine = ProcessEngine.ZEEBE
}

tasks.named("classes") {
    dependsOn("generateBpmnModels")
}

tasks.test {
    useJUnitPlatform()
    // In-memory Camunda process tests are mildly async; retry to keep CI stable.
    retry {
        maxRetries.set(3)
        maxFailures.set(3)
        failOnPassedAfterRetry.set(false)
    }
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
    imageName.set("miravelo/zeebe-example:${project.version}")
    // Pin the JVM the buildpack installs to the version the code targets.
    environment.set(mapOf("BP_JVM_VERSION" to "21"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
}
