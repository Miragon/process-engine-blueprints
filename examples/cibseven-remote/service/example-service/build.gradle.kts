import io.miragon.bpmn.adapter.GenerateBpmnModelsTask
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springframework)
    alias(libs.plugins.spring.dependency)
    alias(libs.plugins.bpmnToCode)
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
}

tasks.withType<BootJar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

java.sourceCompatibility = JavaVersion.VERSION_21
