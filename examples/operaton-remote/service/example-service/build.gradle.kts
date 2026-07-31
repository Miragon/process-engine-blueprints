import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springframework)
    alias(libs.plugins.spring.dependency)
}

/**
 * The remote worker + business logic (hexagonal). It owns the domain, use cases and adapters, keeps its
 * own JPA/Postgres store, subscribes to the engine's external service-tasks via the CIB seven
 * external-task client, and drives the process over the engine's REST API with a `RestClient`. The
 * process contract (topics, messages, element ids) comes from `common-package`; the engine runs in
 * `engine-service`.
 */
dependencies {
    implementation(project(":service:common-package"))
    implementation(project(":service:common-cibseven-client"))
    implementation(libs.bundles.defaultService)
    implementation(libs.bundles.database)
    implementation(libs.bundles.externalTaskClient)
    runtimeOnly(libs.jaxb.runtime)
    testImplementation(libs.bundles.test)
    testImplementation(libs.h2)
    // Standalone in-memory engine for the process-model behaviour test (this service owns the model).
    testImplementation(libs.bundles.cib7ProcessTest)
    testImplementation(project(":service:common-architecture-tests"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<BootJar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

java.sourceCompatibility = JavaVersion.VERSION_21
