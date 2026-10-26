package io.miragon.blueprint.adapter.inbound.workflow;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.application.port.inbound.SendCancellationConfirmationUseCase;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import org.junit.jupiter.api.Test;

class SendCancellationConfirmationTaskTest {

    private final SendCancellationConfirmationUseCase useCase = mock(SendCancellationConfirmationUseCase.class);
    private final SendCancellationConfirmationTask task = new SendCancellationConfirmationTask(useCase);

    @Test
    void adoptsTheCancelledApplicationOnTheWorkflowAggregate() {
        // given
        LeasingApplication application = testLeasingApplication().status(LeasingStatus.WITHDRAWN).build();
        LeasingApplicationAggregate aggregate = LeasingApplicationAggregate.of(application);
        when(useCase.sendCancellationConfirmation(application)).thenReturn(application.cancel());

        // when
        task.sendCancellationConfirmation(aggregate);

        // then
        assertThat(aggregate.getStatus()).isEqualTo(LeasingStatus.CANCELLED);
    }
}
