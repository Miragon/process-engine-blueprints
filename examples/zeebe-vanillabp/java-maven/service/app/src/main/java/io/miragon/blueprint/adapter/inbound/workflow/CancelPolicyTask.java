package io.miragon.blueprint.adapter.inbound.workflow;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.CancelInsurancePolicyUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.vanillabp.spi.service.WorkflowTask;
import org.springframework.stereotype.Component;

@Component
public class CancelPolicyTask extends BikeLeasingWorkflow {

    private final CancelInsurancePolicyUseCase useCase;

    public CancelPolicyTask(CancelInsurancePolicyUseCase useCase) {
        this.useCase = useCase;
    }

    @WorkflowTask(taskDefinition = ServiceTasks.MIRAVELO_CANCEL_POLICY)
    public void cancelPolicy(LeasingApplicationAggregate aggregate) {
        useCase.cancelPolicy(ApplicationId.of(aggregate.getApplicationId()));
    }
}
