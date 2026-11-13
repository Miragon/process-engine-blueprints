package io.miragon.blueprint.adapter.inbound.workflow;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.vanillabp.spi.service.TaskEvent;
import org.junit.jupiter.api.Test;

class ClarifyReturnTaskTest {

    private final ClarifyReturnTask task = new ClarifyReturnTask();

    @Test
    void remembersTheCreatedUserTaskOnTheWorkflowAggregate() {
        // given
        LeasingApplicationAggregate aggregate = LeasingApplicationAggregate.of(testLeasingApplication().build());

        // when
        task.clarifyReturn(aggregate, "4712", TaskEvent.Event.CREATED);

        // then
        assertThat(aggregate.getClarifyReturnTaskId()).isEqualTo("4712");
    }

    @Test
    void forgetsTheUserTaskWhenTheEngineCancelsIt() {
        // given
        LeasingApplicationAggregate aggregate = LeasingApplicationAggregate.of(testLeasingApplication().build());
        task.clarifyReturn(aggregate, "4712", TaskEvent.Event.CREATED);

        // when
        task.clarifyReturn(aggregate, "4712", TaskEvent.Event.CANCELED);

        // then
        assertThat(aggregate.getClarifyReturnTaskId()).isNull();
    }
}
