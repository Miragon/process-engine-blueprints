package io.miragon.blueprint.adapter.inbound.workflow;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.vanillabp.spi.service.TaskEvent;
import io.vanillabp.spi.service.TaskId;
import io.vanillabp.spi.service.WorkflowTask;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class ClarifyAlternativeTask extends BikeLeasingWorkflow {

    private final Clock clock;

    public ClarifyAlternativeTask(Clock clock) {
        this.clock = clock;
    }

    @WorkflowTask(taskDefinition = "clarifyAlternative")
    public void clarifyAlternative(
            LeasingApplicationAggregate aggregate,
            @TaskId String taskId,
            @TaskEvent TaskEvent.Event event) {
        if (event == TaskEvent.Event.CREATED) {
            aggregate.clarificationRequested(taskId, LocalDateTime.now(clock));
        } else {
            aggregate.clarificationCancelled();
        }
    }
}
