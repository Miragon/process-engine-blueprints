package io.miragon.blueprint

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import io.miragon.common.architecture.ServiceArchitectureTest
import org.junit.jupiter.api.Test

/**
 * Runs the reusable ArchUnit + Konsist suite against this service's base package.
 * The rules live in the `:service:common-architecture-tests` module.
 */
class ArchitectureTest : ServiceArchitectureTest("io.miragon.blueprint") {

    /** What this blueprint shows: the service reaches the engine through the process-engine-api alone. */
    @Test
    fun `the service does not use the Camunda client directly`() {
        val productionClasses = ClassFileImporter()
            .withImportOption(DoNotIncludeTests())
            .importPackages("io.miragon.blueprint")

        noClasses().should().dependOnClassesThat().resideInAPackage("io.camunda..").check(productionClasses)
    }
}
