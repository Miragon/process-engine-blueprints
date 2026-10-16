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

// Fluxnova 3.0.1's engine-rest pins the pre-release tools.jackson.datatype:jackson-datatype-jsr310:3.0.0-rc2,
// which Spring's JSON converter auto-discovers and which breaks java.time (de)serialization on Jackson 3.1
// (NoSuchFieldError). Jackson 3 handles java.time natively, so the module is dropped — the same fix as
// upstream (https://github.com/finos/fluxnova-bpm-platform/issues/244). Remove once Fluxnova 3.1.0 is used.
configurations.all {
    exclude(group = "tools.jackson.datatype", module = "jackson-datatype-jsr310")
}

dependencies {
    implementation(libs.bundles.defaultService)
    implementation(libs.bundles.database)
    implementation(libs.bundles.fluxnova)
    implementation(libs.springdoc)
    implementation(libs.bpmn.to.code.runtime)
    testImplementation(libs.bundles.test)
    testImplementation(libs.bundles.fluxnovaProcessTest)
    testImplementation(libs.bpmn.to.code.testing)
    testImplementation(project(":service:common-architecture-tests"))
}

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

val foreignEngineGroups = listOf("org.cibseven", "org.camunda", "org.operaton")
val modelParsersOfBpmnToCodeTesting =
    listOf("org.camunda.bpm.model:camunda-bpmn-model", "org.camunda.bpm.model:camunda-xml-model")

fun externalModulesOf(classpath: NamedDomainObjectProvider<Configuration>) =
    classpath
        .flatMap { it.incoming.artifactView { componentFilter { id -> id is ModuleComponentIdentifier } }.artifacts.resolvedArtifacts }
        .map { artifacts -> artifacts.map { it.id.componentIdentifier.displayName }.toSet() }

val verifyFluxnovaIsTheOnlyEngine by tasks.registering {
    group = "verification"
    description = "Fails when a CIB seven, Camunda or Operaton artifact reaches the runtime or test classpath."
    val runtimeModules = externalModulesOf(configurations.runtimeClasspath)
    val testModules = externalModulesOf(configurations.testRuntimeClasspath)
    val foreignGroups = foreignEngineGroups
    val allowedInTests = modelParsersOfBpmnToCodeTesting
    doLast {
        fun Set<String>.fromForeignEngines() = filter { module -> foreignGroups.any { module.startsWith("$it.") } }
        val foreignRuntimeModules = runtimeModules.get().fromForeignEngines()
        val foreignTestModules =
            testModules.get().fromForeignEngines().filterNot { module -> allowedInTests.any { module.startsWith("$it:") } }
        check(foreignRuntimeModules.isEmpty() && foreignTestModules.isEmpty()) {
            "Fluxnova must be the only process engine, but found on the runtime classpath: $foreignRuntimeModules " +
                "and on the test classpath: $foreignTestModules"
        }
    }
}

tasks.check {
    dependsOn(verifyFluxnovaIsTheOnlyEngine)
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
            "io.miragon.blueprint.adapter.process.*",
            "io.miragon.blueprint.FluxnovaBikeLeasingApplication*",
            "io.miragon.blueprint.BikeCatalogueSeeder*",
            "io.miragon.blueprint.adapter.inbound.rest.DevCorsConfiguration*",
            "io.miragon.blueprint.adapter.inbound.fluxnova.*",
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
// maintain (layered, non-root by default). See docs/adr/0011 and the "Run it in containers" section of
// CONTRIBUTING.md. Build with `./gradlew :service:app:bootBuildImage`.
tasks.named<BootBuildImage>("bootBuildImage") {
    imageName.set("miravelo/fluxnova-embedded-example:${project.version}")
    // Pin the JVM the buildpack installs to the version the code targets.
    environment.set(mapOf("BP_JVM_VERSION" to "21"))
}

java.sourceCompatibility = JavaVersion.VERSION_21
