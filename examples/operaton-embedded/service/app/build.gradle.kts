import io.miragon.bpmn.adapter.GenerateBpmnModelsTask
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springframework)
    alias(libs.plugins.spring.dependency)
    alias(libs.plugins.bpmnToCode)
}

dependencies {
    implementation(libs.bundles.defaultService)
    implementation(libs.bundles.database)
    implementation(libs.bundles.operaton)
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

java.sourceCompatibility = JavaVersion.VERSION_21
