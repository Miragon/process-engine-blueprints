package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.ServiceTasks
import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.application.port.inbound.SendCancellationConfirmationUseCase
import io.vanillabp.spi.service.WorkflowTask
import org.springframework.stereotype.Component

@Component
class SendCancellationConfirmationTask(
    private val useCase: SendCancellationConfirmationUseCase,
) : BikeLeasingWorkflow() {

    @WorkflowTask(taskDefinition = ServiceTasks.MIRAVELO_SEND_CANCELLATION_CONFIRMATION)
    fun sendCancellationConfirmation(aggregate: LeasingApplicationAggregate) {
        aggregate.adopt(useCase.sendCancellationConfirmation(aggregate.toDomain()))
    }
}
