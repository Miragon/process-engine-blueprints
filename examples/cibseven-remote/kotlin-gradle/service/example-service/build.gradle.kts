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
    alias(libs.plugins.pitest)
}

springBoot {
    buildInfo()
}

/**
 * The remote worker + business logic (hexagonal), and — since there is no separate contract module —
 * the **owner of the process contract** too. It carries the `.bpmn`/`.dmn`/`.form` models and generates
 * the typed `*ProcessApi` objects and shared constants (flow nodes, topics, messages, variables) from
 * them with `bpmn-to-code`, keeps its own JPA/Postgres store, subscribes to the engine's external service-tasks
 * via the CIB seven external-task client, deploys the model into the engine over REST at start-up, and
 * drives the running process with a `RestClient`. The engine runs in the model-agnostic `engine-service`.
 */
dependencies {
    implementation(project(":service:common-cibseven-client"))
    implementation(libs.bpmn.to.code.runtime)
    implementation(libs.bundles.defaultService)
    implementation(libs.bundles.database)
    implementation(libs.bundles.externalTaskClient)
    implementation(libs.springdoc)
    runtimeOnly(libs.jaxb.runtime)
    testImplementation(libs.bundles.test)
    testImplementation(libs.h2)
    // Standalone in-memory engine for the process-model behaviour test (this service owns the model).
    testImplementation(libs.bundles.cib7ProcessTest)
    // Structural model validation (bpmn-to-code-testing) — this service now owns the models.
    testImplementation(libs.bpmn.to.code.testing)
    testImplementation(project(":service:common-architecture-tests"))
}

// Generates the typed `*ProcessApi` objects (flow nodes with their ids, variables and successors) and
// the shared `ServiceTasks`/`Messages`/`ProcessVariables`/`Errors`/`Escalations` from the BPMN models, so workers and tests reference process elements as compile-checked
// constants.
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
    // Isolate each test class in its own JVM so the standalone in-memory engine used by the process
    // tests never carries state between classes.
    forkEvery = 1
}

pitest {
    junit5PluginVersion.set("1.2.2")
    targetClasses.set(listOf("io.miragon.blueprint.domain.*"))
    targetTests.set(listOf("io.miragon.blueprint.*"))
    excludedTestClasses.set(
        listOf(
            "io.miragon.blueprint.adapter.process.*",
            "io.miragon.blueprint.ArchitectureTest",
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

// OCI image for the remote worker, built by Spring's Cloud Native Buildpacks integration — no
// Dockerfile to maintain (layered, non-root by default). See docs/adr/0011 and the "Run it in
// containers" section of CONTRIBUTING.md. Build with `./gradlew :service:example-service:bootBuildImage`.
tasks.named<BootBuildImage>("bootBuildImage") {
    imageName.set("miravelo/example-service:${project.version}")
    // Pin the JVM the buildpack installs to the version the code targets.
    environment.set(mapOf("BP_JVM_VERSION" to "21"))
}

java.sourceCompatibility = JavaVersion.VERSION_21
