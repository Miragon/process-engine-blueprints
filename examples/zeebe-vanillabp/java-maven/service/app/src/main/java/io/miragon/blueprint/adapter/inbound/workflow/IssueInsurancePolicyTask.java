package io.miragon.blueprint.adapter.inbound.workflow;

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.IssueInsurancePolicyUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.vanillabp.spi.service.WorkflowTask;
import org.springframework.stereotype.Component;

@Component
public class IssueInsurancePolicyTask extends BikeLeasingWorkflow {

    private final IssueInsurancePolicyUseCase useCase;

    public IssueInsurancePolicyTask(IssueInsurancePolicyUseCase useCase) {
        this.useCase = useCase;
    }

    @WorkflowTask(taskDefinition = ServiceTasks.MIRAVELO_ISSUE_INSURANCE_POLICY)
    public void issueInsurancePolicy(LeasingApplicationAggregate aggregate) {
        useCase.issuePolicy(ApplicationId.of(aggregate.getApplicationId()));
    }
}
