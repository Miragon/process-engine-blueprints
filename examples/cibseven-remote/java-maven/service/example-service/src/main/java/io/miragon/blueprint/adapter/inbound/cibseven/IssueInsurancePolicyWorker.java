package io.miragon.blueprint.adapter.inbound.cibseven;

import io.miragon.blueprint.application.port.inbound.IssueInsurancePolicyUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.process.ServiceTasks;
import org.cibseven.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.cibseven.bpm.client.task.ExternalTask;
import org.cibseven.bpm.client.task.ExternalTaskService;
import org.springframework.stereotype.Component;

@Component
@ExternalTaskSubscription(topicName = ServiceTasks.BIKE_LEASING_ISSUE_INSURANCE_POLICY)
public class IssueInsurancePolicyWorker extends BaseExternalTaskWorker {

    private final IssueInsurancePolicyUseCase useCase;

    public IssueInsurancePolicyWorker(IssueInsurancePolicyUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public void executeTask(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        useCase.issuePolicy(ApplicationId.of(externalTask.getBusinessKey()));
        externalTaskService.complete(externalTask);
    }
}
