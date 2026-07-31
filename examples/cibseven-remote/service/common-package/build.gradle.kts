import io.miragon.bpmn.adapter.GenerateBpmnModelsTask
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.bpmnToCode)
}

/**
 * The shared *contract* module: it owns the BPMN/DMN/form models and the typed `*ProcessApi` objects
 * generated from them. Both the `engine-service` (which deploys the models) and the `example-service`
 * (which consumes the topics, message names and element ids) depend on it, so the process contract has
 * a single source of truth. `bpmn-to-code-runtime` is exposed via `api` so consumers get the
 * `ProcessId` / `MessageName` / `ElementId` types transitively.
 */
dependencies {
    api(libs.bpmn.to.code.runtime)
    testImplementation(libs.bpmn.to.code.testing)
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // bpmn-to-code-testing logs via kotlin-logging/slf4j; provide a binding on the test runtime.
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.18")
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

java {
    sourceCompatibility = JavaVersion.VERSION_21
}
