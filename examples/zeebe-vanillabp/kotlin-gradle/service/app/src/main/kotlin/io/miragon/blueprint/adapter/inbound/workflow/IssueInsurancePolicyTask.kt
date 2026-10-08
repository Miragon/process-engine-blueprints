package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.ServiceTasks
import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.application.port.inbound.IssueInsurancePolicyUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.vanillabp.spi.service.WorkflowTask
import org.springframework.stereotype.Component

@Component
class IssueInsurancePolicyTask(
    private val useCase: IssueInsurancePolicyUseCase,
) : BikeLeasingWorkflow() {

    @WorkflowTask(taskDefinition = ServiceTasks.MIRAVELO_ISSUE_INSURANCE_POLICY)
    fun issueInsurancePolicy(aggregate: LeasingApplicationAggregate) {
        useCase.issuePolicy(ApplicationId.of(aggregate.applicationId))
    }
}
