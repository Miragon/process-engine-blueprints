package io.miragon.blueprint.adapter.inbound.workflow;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.SendCancellationConfirmationUseCase;
import io.vanillabp.spi.service.WorkflowTask;
import org.springframework.stereotype.Component;

@Component
public class SendCancellationConfirmationTask extends BikeLeasingWorkflow {

    private final SendCancellationConfirmationUseCase useCase;

    public SendCancellationConfirmationTask(SendCancellationConfirmationUseCase useCase) {
        this.useCase = useCase;
    }

    @WorkflowTask(taskDefinition = ServiceTasks.MIRAVELO_SEND_CANCELLATION_CONFIRMATION)
    public void sendCancellationConfirmation(LeasingApplicationAggregate aggregate) {
        aggregate.adopt(useCase.sendCancellationConfirmation(aggregate.toDomain()));
    }
}
