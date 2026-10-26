package io.miragon.blueprint.adapter.inbound.workflow;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.vanillabp.spi.service.TaskEvent;
import io.vanillabp.spi.service.WorkflowTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ClarifyReturnTask extends BikeLeasingWorkflow {

    private static final Logger log = LoggerFactory.getLogger(ClarifyReturnTask.class);

    @WorkflowTask(taskDefinition = "clarifyReturn")
    public void clarifyReturn(LeasingApplicationAggregate aggregate, @TaskEvent TaskEvent.Event event) {
        log.info(
                "Return clarification with the supplier {} for application {}",
                event,
                aggregate.getApplicationId());
    }
}
