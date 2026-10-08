import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springframework)
    alias(libs.plugins.spring.dependency)
}

configurations.all {
    // CIB seven 2.2.0 pulls in both the generic `cibseven-webclient-web` and the Spring-Boot-4
    // variant `cibseven-webclient-web-spring-boot-4`. The generic one calls
    // PathMatchConfigurer.setUseSuffixPatternMatch(...), which was removed in Spring 7 / Spring
    // Boot 4, and crashes the app on start-up. Drop it so only the SB4 variant remains.
    exclude(group = "org.cibseven.webapp", module = "cibseven-webclient-web")
}

/**
 * The engine host: a **model-agnostic** CIB seven engine. It runs the engine and exposes
 * `/engine-rest` + Cockpit/Tasklist at `/camunda`, but ships **no** process model of its own — the
 * `example-service` (which owns the process) deploys the model into it over REST at start-up.
 *
 * It ships no process model of its own — the `example-service` owns the whole contract and deploys it
 * over REST at start-up. The engine host carries only the two **execution/task-listener** beans:
 * listeners have no external-task equivalent and always run *inside* the engine. It deliberately does
 * **not** depend on the worker (that would drag the worker's business code + JPA into the engine), so
 * those listeners reference process variables by **plain string name** rather than the worker's
 * generated `*ProcessApi` contract — the price of letting a single service own the whole model.
 */
dependencies {
    implementation(libs.bundles.defaultService)
    implementation(libs.bundles.database)
    implementation(libs.bundles.cibseven)
}

tasks.withType<BootJar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

java.sourceCompatibility = JavaVersion.VERSION_21
