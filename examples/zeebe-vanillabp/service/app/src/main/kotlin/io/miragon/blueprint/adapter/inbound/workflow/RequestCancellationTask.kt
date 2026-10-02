package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi.ServiceTasks
import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase
import io.vanillabp.spi.service.WorkflowTask
import org.springframework.stereotype.Component

@Component
class RequestCancellationTask(
    private val useCase: RequestOrderCancellationUseCase,
) : BikeLeasingWorkflow() {

    @WorkflowTask(taskDefinition = ServiceTasks.MIRAVELO_REQUEST_CANCELLATION)
    fun requestCancellation(aggregate: LeasingApplicationAggregate) {
        aggregate.cancellationPossible = useCase.requestCancellation(aggregate.placedOrderId())
    }
}
