package io.miragon.blueprint.adapter.inbound.workflow;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.CancelContractUseCase;
import io.vanillabp.spi.service.WorkflowTask;
import org.springframework.stereotype.Component;

@Component
public class CancelContractTask extends BikeLeasingWorkflow {

    private final CancelContractUseCase useCase;

    public CancelContractTask(CancelContractUseCase useCase) {
        this.useCase = useCase;
    }

    @WorkflowTask(taskDefinition = ServiceTasks.MIRAVELO_CANCEL_CONTRACT)
    public void cancelContract(LeasingApplicationAggregate aggregate) {
        useCase.cancelContract(aggregate.toDomain());
    }
}
