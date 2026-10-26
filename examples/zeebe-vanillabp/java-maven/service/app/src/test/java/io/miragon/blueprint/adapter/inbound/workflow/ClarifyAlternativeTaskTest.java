package io.miragon.blueprint.adapter.inbound.workflow;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.vanillabp.spi.service.TaskEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ClarifyAlternativeTaskTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-01T08:00:00Z"), ZoneOffset.UTC);
    private final ClarifyAlternativeTask task = new ClarifyAlternativeTask(clock);

    @Test
    void remembersTheCreatedUserTaskOnTheWorkflowAggregate() {
        // given
        LeasingApplicationAggregate aggregate = LeasingApplicationAggregate.of(testLeasingApplication().build());

        // when
        task.clarifyAlternative(aggregate, "4711", TaskEvent.Event.CREATED);

        // then
        assertThat(aggregate.getClarifyAlternativeTaskId()).isEqualTo("4711");
        assertThat(aggregate.getClarificationRequestedAt()).isEqualTo(LocalDateTime.of(2026, 3, 1, 8, 0));
    }

    @Test
    void forgetsTheUserTaskWhenTheEngineCancelsIt() {
        // given
        LeasingApplicationAggregate aggregate = LeasingApplicationAggregate.of(testLeasingApplication().build());
        task.clarifyAlternative(aggregate, "4711", TaskEvent.Event.CREATED);

        // when
        task.clarifyAlternative(aggregate, "4711", TaskEvent.Event.CANCELED);

        // then
        assertThat(aggregate.getClarifyAlternativeTaskId()).isNull();
        assertThat(aggregate.getClarificationRequestedAt()).isNull();
    }
}
