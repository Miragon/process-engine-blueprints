import io.miragon.bpmn.adapter.GenerateBpmnModelsTask
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage
import org.springframework.boot.gradle.tasks.bundling.BootJar
import java.math.BigDecimal

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springframework)
    alias(libs.plugins.spring.dependency)
    alias(libs.plugins.bpmnToCode)
    alias(libs.plugins.pitest)
}

springBoot {
    buildInfo()
}

dependencies {
    implementation(libs.bundles.defaultService)
    implementation(libs.bundles.database)
    implementation(libs.bundles.operaton)
    implementation(libs.springdoc)
    implementation(libs.bpmn.to.code.runtime)
    testImplementation(libs.bundles.test)
    testImplementation(libs.bundles.operatonProcessTest)
    testImplementation(libs.bpmn.to.code.testing)
    testImplementation(project(":service:common-architecture-tests"))
}

// Generates the typed `*ProcessApi` objects (element ids, messages, timers, variables, …) from the
// BPMN models, so workers and tests reference process elements as compile-checked constants.
tasks.register<GenerateBpmnModelsTask>("generateBpmnModels") {
    baseDir = projectDir.toString()
    filePattern = "src/main/resources/bpmn/*.bpmn"
    outputFolderPath = "$projectDir/src/main/kotlin"
    packagePath = "io.miragon.blueprint.adapter.process"
    outputLanguage = OutputLanguage.KOTLIN
    processEngine = ProcessEngine.CAMUNDA_7
}

tasks.named("classes") {
    dependsOn("generateBpmnModels")
}

tasks.test {
    useJUnitPlatform()
    forkEvery = 1
}

tasks.withType<BootJar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Mutation testing (PIT). PR runs are diff-scoped via -PmutationTargetClasses; the nightly sweep runs
// the full default target set. Bootstrap/config/generated/engine-adapter classes are excluded — they
// hold no domain logic worth mutating. Gate: 80% mutation coverage.
val mutationTargetClasses = (project.findProperty("mutationTargetClasses") as String?)
    ?.split(",")?.map(String::trim)?.filter(String::isNotEmpty)

pitest {
    junit5PluginVersion.set("1.2.2")
    targetClasses.set(mutationTargetClasses ?: listOf("io.miragon.blueprint.*"))
    targetTests.set(listOf("io.miragon.blueprint.*"))
    failWhenNoMutations.set(false)
    excludedClasses.set(
        listOf(
            "io.miragon.blueprint.adapter.process.*ProcessApi*",
            "io.miragon.blueprint.adapter.process.HistoryCleanupConfiguration*",
            "io.miragon.blueprint.OperatonBikeLeasingApplication*",
            "io.miragon.blueprint.BikeCatalogueSeeder*",
            "io.miragon.blueprint.adapter.inbound.rest.DevCorsConfiguration*",
            "io.miragon.blueprint.adapter.inbound.operaton.*",
        ),
    )
    excludedTestClasses.set(
        listOf(
            "io.miragon.blueprint.process.*",
            "io.miragon.blueprint.architecture.*",
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

// OCI image for the backend, built by Spring's Cloud Native Buildpacks integration — no Dockerfile to
// maintain (layered, non-root by default). Build with `./gradlew :service:app:bootBuildImage`.
tasks.named<BootBuildImage>("bootBuildImage") {
    imageName.set("miravelo/app:${project.version}")
    // Pin the JVM the buildpack installs to the version the code targets.
    environment.set(mapOf("BP_JVM_VERSION" to "21"))
}

java.sourceCompatibility = JavaVersion.VERSION_21
