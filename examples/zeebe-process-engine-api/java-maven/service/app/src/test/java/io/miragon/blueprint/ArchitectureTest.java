package io.miragon.blueprint;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import io.miragon.common.architecture.ServiceArchitectureTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Runs the reusable ArchUnit suite against this service's base package.
 * The rules live in the {@code service/common-architecture-tests} module.
 */
class ArchitectureTest extends ServiceArchitectureTest {

    ArchitectureTest() {
        super("io.miragon.blueprint");
    }

    /** What this blueprint shows: the service reaches the engine through the process-engine-api alone. */
    @Test
    @DisplayName("the service does not use the Camunda client directly")
    void theServiceDoesNotUseTheCamundaClientDirectly() {
        JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(new DoNotIncludeTests())
            .importPackages("io.miragon.blueprint");

        noClasses().should().dependOnClassesThat().resideInAPackage("io.camunda..").check(productionClasses);
    }
}
