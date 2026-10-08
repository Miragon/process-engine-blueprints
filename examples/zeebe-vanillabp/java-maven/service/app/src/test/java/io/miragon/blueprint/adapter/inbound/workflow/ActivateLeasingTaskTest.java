package io.miragon.blueprint.adapter.inbound.workflow;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.application.port.inbound.ActivateLeasingUseCase;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import org.junit.jupiter.api.Test;

class ActivateLeasingTaskTest {

    private final ActivateLeasingUseCase useCase = mock(ActivateLeasingUseCase.class);
    private final ActivateLeasingTask task = new ActivateLeasingTask(useCase);

    @Test
    void adoptsTheActivatedApplicationOnTheWorkflowAggregate() {
        // given
        LeasingApplication application = testLeasingApplication().status(LeasingStatus.HANDED_OVER).build();
        LeasingApplicationAggregate aggregate = LeasingApplicationAggregate.of(application);
        when(useCase.activate(application)).thenReturn(application.activate());

        // when
        task.activateLeasing(aggregate);

        // then
        assertThat(aggregate.getStatus()).isEqualTo(LeasingStatus.ACTIVE);
    }
}
