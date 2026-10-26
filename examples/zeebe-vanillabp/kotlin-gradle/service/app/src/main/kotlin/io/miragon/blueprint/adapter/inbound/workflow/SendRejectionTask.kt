package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.ServiceTasks
import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.application.port.inbound.RejectApplicationUseCase
import io.vanillabp.spi.service.WorkflowTask
import org.springframework.stereotype.Component

@Component
class SendRejectionTask(
    private val useCase: RejectApplicationUseCase,
) : BikeLeasingWorkflow() {

    @WorkflowTask(taskDefinition = ServiceTasks.MIRAVELO_SEND_REJECTION)
    fun sendRejection(aggregate: LeasingApplicationAggregate) {
        aggregate.adopt(useCase.reject(aggregate.toDomain()))
    }
}
