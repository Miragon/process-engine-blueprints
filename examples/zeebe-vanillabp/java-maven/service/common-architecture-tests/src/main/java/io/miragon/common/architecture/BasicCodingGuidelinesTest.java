package io.miragon.common.architecture;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * General coding guidelines that rely on the resolved bytecode graph (package structure, freedom of
 * cycles, no {@code println}).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BasicCodingGuidelinesTest {

    protected abstract String rootPackage();

    private JavaClasses allClasses;
    private JavaClasses productionClasses;

    private JavaClasses allClasses() {
        if (allClasses == null) {
            allClasses = new ClassFileImporter().importPackages(rootPackage());
        }
        return allClasses;
    }

    private JavaClasses productionClasses() {
        if (productionClasses == null) {
            productionClasses = new ClassFileImporter()
                    .withImportOption(new ImportOption.DoNotIncludeTests())
                    .importPackages(rootPackage());
        }
        return productionClasses;
    }

    @Test
    void eachClassHasPackageDeclaration() {
        ArchRuleDefinition.classes()
                .should().resideInAnyPackage(rootPackage() + "..")
                .because("All classes should be in the specified package structure")
                .check(allClasses());
    }

    @Test
    void classesAreFreeOfCycles() {
        SlicesRuleDefinition.slices()
                .matching(rootPackage() + ".(**)")
                .should().beFreeOfCycles()
                .because("Classes should not have circular dependencies")
                .check(productionClasses());
    }

    @Test
    void productionCodeDoesNotUsePrintlnOrSystemOut() {
        ArchRuleDefinition.noClasses()
                .should().callMethodWhere(target(name("println")).and(target(owner(assignableTo(PrintStream.class)))))
                .because("Use a logger instead of println or System.out for diagnostic output")
                .check(productionClasses());
    }
}
