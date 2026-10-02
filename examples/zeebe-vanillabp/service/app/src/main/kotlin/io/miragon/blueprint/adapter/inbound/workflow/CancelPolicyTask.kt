package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.ServiceTasks
import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.application.port.inbound.CancelInsurancePolicyUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.vanillabp.spi.service.WorkflowTask
import org.springframework.stereotype.Component

@Component
class CancelPolicyTask(
    private val useCase: CancelInsurancePolicyUseCase,
) : BikeLeasingWorkflow() {

    @WorkflowTask(taskDefinition = ServiceTasks.MIRAVELO_CANCEL_POLICY)
    fun cancelPolicy(aggregate: LeasingApplicationAggregate) {
        useCase.cancelPolicy(ApplicationId.of(aggregate.applicationId))
    }
}
