package io.miragon.blueprint.adapter.inbound.workflow;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.RejectApplicationUseCase;
import io.vanillabp.spi.service.WorkflowTask;
import org.springframework.stereotype.Component;

@Component
public class SendRejectionTask extends BikeLeasingWorkflow {

    private final RejectApplicationUseCase useCase;

    public SendRejectionTask(RejectApplicationUseCase useCase) {
        this.useCase = useCase;
    }

    @WorkflowTask(taskDefinition = ServiceTasks.MIRAVELO_SEND_REJECTION)
    public void sendRejection(LeasingApplicationAggregate aggregate) {
        aggregate.adopt(useCase.reject(aggregate.toDomain()));
    }
}
