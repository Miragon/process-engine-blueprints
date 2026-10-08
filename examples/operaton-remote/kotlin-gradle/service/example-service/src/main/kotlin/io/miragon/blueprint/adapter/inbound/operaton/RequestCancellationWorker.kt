package io.miragon.blueprint.adapter.inbound.operaton

import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase
import io.miragon.blueprint.domain.bike.OrderId
import io.miragon.blueprint.process.ServiceTasks
import io.miragon.blueprint.process.CancelBikeOrderProcessApi.FlowNodes
import org.operaton.bpm.client.spring.annotation.ExternalTaskSubscription
import org.operaton.bpm.client.task.ExternalTask
import org.operaton.bpm.client.task.ExternalTaskService
import org.springframework.stereotype.Component

@Component
@ExternalTaskSubscription(topicName = ServiceTasks.BIKE_LEASING_REQUEST_CANCELLATION)
class RequestCancellationWorker(
    private val useCase: RequestOrderCancellationUseCase,
) : BaseExternalTaskWorker() {

    override fun executeTask(externalTask: ExternalTask, externalTaskService: ExternalTaskService) {
        val orderId = OrderId(externalTask.getVariable(FlowNodes.StartEventCancellationRequired.Variables.ORDER_ID.value))
        val cancellationPossible = useCase.requestCancellation(orderId)
        externalTaskService.complete(
            externalTask,
            mapOf(FlowNodes.ServiceTaskRequestCancellation.Variables.CANCELLATION_POSSIBLE.value to cancellationPossible),
        )
    }
}
