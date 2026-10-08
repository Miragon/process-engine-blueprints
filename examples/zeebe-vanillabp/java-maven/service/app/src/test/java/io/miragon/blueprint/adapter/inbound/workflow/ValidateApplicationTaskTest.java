package io.miragon.blueprint.adapter.inbound.workflow;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.application.port.inbound.ValidateApplicationUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationInvalidException;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.vanillabp.spi.service.TaskException;
import org.junit.jupiter.api.Test;

class ValidateApplicationTaskTest {

    private final ValidateApplicationUseCase useCase = mock(ValidateApplicationUseCase.class);
    private final ValidateApplicationTask task = new ValidateApplicationTask(useCase);

    @Test
    void completesForAValidApplication() {
        // given
        LeasingApplication application = testLeasingApplication().build();
        doNothing().when(useCase).validate(application);

        // when
        task.validateApplication(LeasingApplicationAggregate.of(application));

        // then
        verify(useCase).validate(application);
    }

    @Test
    void raisesTheApplicationInvalidBpmnErrorForAnInvalidApplication() {
        // given
        LeasingApplication application = testLeasingApplication().build();
        doThrow(new ApplicationInvalidException(application.id(), "too young")).when(useCase).validate(application);

        // when
        TaskException error = catchThrowableOfType(
                TaskException.class,
                () -> task.validateApplication(LeasingApplicationAggregate.of(application)));

        // then
        assertThat(error.getErrorCode()).isEqualTo("applicationInvalid");
        assertThat(error.getErrorName()).isEqualTo("too young");
    }
}
