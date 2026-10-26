package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.ServiceTasks
import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.application.port.inbound.CancelContractUseCase
import io.vanillabp.spi.service.WorkflowTask
import org.springframework.stereotype.Component

@Component
class CancelContractTask(
    private val useCase: CancelContractUseCase,
) : BikeLeasingWorkflow() {

    @WorkflowTask(taskDefinition = ServiceTasks.MIRAVELO_CANCEL_CONTRACT)
    fun cancelContract(aggregate: LeasingApplicationAggregate) {
        useCase.cancelContract(aggregate.toDomain())
    }
}
