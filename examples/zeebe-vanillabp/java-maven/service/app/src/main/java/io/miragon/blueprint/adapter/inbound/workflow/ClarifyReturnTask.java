package io.miragon.blueprint.adapter.inbound.workflow;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.vanillabp.spi.service.TaskEvent;
import io.vanillabp.spi.service.TaskId;
import io.vanillabp.spi.service.WorkflowTask;
import org.springframework.stereotype.Component;

@Component
public class ClarifyReturnTask extends BikeLeasingWorkflow {

    @WorkflowTask(taskDefinition = "clarifyReturn")
    public void clarifyReturn(
            LeasingApplicationAggregate aggregate,
            @TaskId String taskId,
            @TaskEvent TaskEvent.Event event) {
        if (event == TaskEvent.Event.CREATED) {
            aggregate.returnClarificationRequested(taskId);
        } else {
            aggregate.returnClarificationCancelled();
        }
    }
}
