package io.miragon.blueprint.adapter.inbound.zeebe

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi.FlowNodes
import io.miragon.blueprint.adapter.process.ServiceTasks
import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase
import io.miragon.blueprint.domain.bike.OrderId
import org.springframework.stereotype.Component

@Component
class RequestCancellationWorker(
    private val useCase: RequestOrderCancellationUseCase,
) {

    // `orderId` is handed to the cancelBikeOrder sub-process by the calling activity.
    @ProcessEngineWorker(topic = ServiceTasks.MIRAVELO_REQUEST_CANCELLATION)
    fun handle(@Variable orderId: String): Map<String, Any> {
        val cancellationPossible = useCase.requestCancellation(OrderId(orderId))
        return mapOf(FlowNodes.ServiceTaskRequestCancellation.Variables.CANCELLATION_POSSIBLE.value to cancellationPossible)
    }
}
