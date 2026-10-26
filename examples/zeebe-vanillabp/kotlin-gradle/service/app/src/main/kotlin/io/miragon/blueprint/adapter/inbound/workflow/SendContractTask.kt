package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.ServiceTasks
import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.application.port.inbound.SendContractUseCase
import io.vanillabp.spi.service.WorkflowTask
import org.springframework.stereotype.Component

@Component
class SendContractTask(
    private val useCase: SendContractUseCase,
) : BikeLeasingWorkflow() {

    @WorkflowTask(taskDefinition = ServiceTasks.MIRAVELO_SEND_CONTRACT)
    fun sendContract(aggregate: LeasingApplicationAggregate) {
        aggregate.adopt(useCase.sendContract(aggregate.toDomain()))
    }
}
