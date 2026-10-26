package io.miragon.blueprint.adapter.inbound.workflow;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.application.port.inbound.RejectApplicationUseCase;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import org.junit.jupiter.api.Test;

class SendRejectionTaskTest {

    private final RejectApplicationUseCase useCase = mock(RejectApplicationUseCase.class);
    private final SendRejectionTask task = new SendRejectionTask(useCase);

    @Test
    void adoptsTheRejectedApplicationOnTheWorkflowAggregate() {
        // given
        LeasingApplication application = testLeasingApplication().build();
        LeasingApplicationAggregate aggregate = LeasingApplicationAggregate.of(application);
        when(useCase.reject(application)).thenReturn(application.reject());

        // when
        task.sendRejection(aggregate);

        // then
        assertThat(aggregate.getStatus()).isEqualTo(LeasingStatus.REJECTED);
    }
}
