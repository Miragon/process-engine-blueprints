package io.miragon.blueprint.adapter.inbound.workflow;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.vanillabp.spi.service.TaskEvent;
import org.junit.jupiter.api.Test;

class ClarifyReturnTaskTest {

    private final ClarifyReturnTask task = new ClarifyReturnTask();

    @Test
    void theEngineOnlyUserTaskLeavesNoTraceOnTheWorkflowAggregate() {
        // given
        LeasingApplication application = testLeasingApplication().orderId(new OrderId("ORDER-1")).build();
        LeasingApplicationAggregate aggregate = LeasingApplicationAggregate.of(application);

        // when
        task.clarifyReturn(aggregate, TaskEvent.Event.CREATED);
        task.clarifyReturn(aggregate, TaskEvent.Event.CANCELED);

        // then
        assertThat(aggregate.toDomain()).isEqualTo(application);
        assertThat(aggregate.getClarifyAlternativeTaskId()).isNull();
        assertThat(aggregate.getCancellationPossible()).isNull();
    }
}
