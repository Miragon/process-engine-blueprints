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
 * the typed `*ProcessApi` objects (topics, messages, element ids, variables) from them with
 * `bpmn-to-code`, keeps its own JPA/Postgres store, subscribes to the engine's external service-tasks
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

// Generates the typed `*ProcessApi` objects (element ids, messages, timers, variables, external-task
// topics, …) from the BPMN models, so workers and tests reference process elements as compile-checked
// constants.
tasks.register<GenerateBpmnModelsTask>("generateBpmnModels") {
    baseDir = projectDir.toString()
    filePattern = "src/main/resources/bpmn/*.bpmn"
    outputFolderPath = "$projectDir/src/main/kotlin"
    packagePath = "io.miragon.blueprint.process"
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

val mutationTargetClasses = (project.findProperty("mutationTargetClasses") as String?)
    ?.split(",")?.map(String::trim)?.filter(String::isNotEmpty)

pitest {
    junit5PluginVersion.set("1.2.2")
    targetClasses.set(mutationTargetClasses ?: listOf("io.miragon.blueprint.*"))
    targetTests.set(listOf("io.miragon.blueprint.*"))
    failWhenNoMutations.set(false)
    excludedClasses.set(
        listOf(
            // Generated typed process API — no behaviour of ours to mutate.
            "io.miragon.blueprint.process.*ProcessApi*",
            // Application bootstrap / wiring, outside the hexagonal layers.
            "io.miragon.blueprint.ExampleServiceApplication*",
            "io.miragon.blueprint.BikeCatalogueSeeder*",
            "io.miragon.blueprint.adapter.inbound.rest.DevCorsConfiguration*",
            // Remote engine plumbing — external-task workers and the start-up deployment adapter are
            // integration glue, exercised by the process / Bruno layers rather than by mutation.
            "io.miragon.blueprint.adapter.inbound.cibseven.*",
            "io.miragon.blueprint.adapter.outbound.engine.ProcessModelDeploymentAdapter*",
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
