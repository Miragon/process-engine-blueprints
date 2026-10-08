package io.miragon.blueprint.application.service;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.miragon.blueprint.domain.leasing.ApplicationInvalidException;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import org.junit.jupiter.api.Test;

class ValidateApplicationServiceTest {

    private final ValidateApplicationService underTest = new ValidateApplicationService();

    @Test
    void validateAcceptsAWellFormedApplication() {

        // given: a valid, solvent application
        LeasingApplication application = testLeasingApplication().build();

        // when / then: validation passes
        assertThatCode(() -> underTest.validate(application)).doesNotThrowAnyException();
    }

    @Test
    void validateRejectsAnApplicationWithoutIncome() {

        // given: an application with zero monthly net income
        LeasingApplication application = testLeasingApplication().monthlyNetIncome(0.0).build();

        // when / then: validation surfaces the application as invalid
        assertThatThrownBy(() -> underTest.validate(application))
                .isInstanceOf(ApplicationInvalidException.class);
    }
}
