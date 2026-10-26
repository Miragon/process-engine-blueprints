package io.miragon.blueprint.adapter.inbound.workflow;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.ActivateLeasingUseCase;
import io.vanillabp.spi.service.WorkflowTask;
import org.springframework.stereotype.Component;

@Component
public class ActivateLeasingTask extends BikeLeasingWorkflow {

    private final ActivateLeasingUseCase useCase;

    public ActivateLeasingTask(ActivateLeasingUseCase useCase) {
        this.useCase = useCase;
    }

    @WorkflowTask(taskDefinition = ServiceTasks.MIRAVELO_ACTIVATE_LEASING)
    public void activateLeasing(LeasingApplicationAggregate aggregate) {
        aggregate.adopt(useCase.activate(aggregate.toDomain()));
    }
}
