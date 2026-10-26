package io.miragon.blueprint.adapter.inbound.workflow;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.SendContractUseCase;
import io.vanillabp.spi.service.WorkflowTask;
import org.springframework.stereotype.Component;

@Component
public class SendContractTask extends BikeLeasingWorkflow {

    private final SendContractUseCase useCase;

    public SendContractTask(SendContractUseCase useCase) {
        this.useCase = useCase;
    }

    @WorkflowTask(taskDefinition = ServiceTasks.MIRAVELO_SEND_CONTRACT)
    public void sendContract(LeasingApplicationAggregate aggregate) {
        aggregate.adopt(useCase.sendContract(aggregate.toDomain()));
    }
}
