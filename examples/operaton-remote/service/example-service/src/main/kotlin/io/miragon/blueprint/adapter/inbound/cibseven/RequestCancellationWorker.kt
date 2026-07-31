package io.miragon.blueprint.adapter.inbound.cibseven

import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase
import io.miragon.blueprint.domain.bike.OrderId
import io.miragon.blueprint.process.CancelBikeOrderProcessApi.ServiceTasks
import io.miragon.blueprint.process.CancelBikeOrderProcessApi.Variables
import org.cibseven.bpm.client.spring.annotation.ExternalTaskSubscription
import org.cibseven.bpm.client.task.ExternalTask
import org.cibseven.bpm.client.task.ExternalTaskService
import org.springframework.stereotype.Component

@Component
@ExternalTaskSubscription(topicName = ServiceTasks.BIKE_LEASING_REQUEST_CANCELLATION)
class RequestCancellationWorker(
    private val useCase: RequestOrderCancellationUseCase,
) : BaseExternalTaskWorker() {

    override fun executeTask(externalTask: ExternalTask, externalTaskService: ExternalTaskService) {
        val orderId = OrderId(externalTask.getVariable(Variables.StartEventCancellationRequired.ORDER_ID.value))
        val cancellationPossible = useCase.requestCancellation(orderId)
        externalTaskService.complete(
            externalTask,
            mapOf(Variables.ServiceTaskRequestCancellation.CANCELLATION_POSSIBLE.value to cancellationPossible),
        )
    }
}
