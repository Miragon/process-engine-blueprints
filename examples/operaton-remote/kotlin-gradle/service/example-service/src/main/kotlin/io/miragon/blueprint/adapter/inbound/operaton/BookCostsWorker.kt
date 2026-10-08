package io.miragon.blueprint.adapter.inbound.operaton

import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase
import io.miragon.blueprint.domain.bike.OrderId
import io.miragon.blueprint.process.ServiceTasks
import io.miragon.blueprint.process.CancelBikeOrderProcessApi.FlowNodes
import org.operaton.bpm.client.spring.annotation.ExternalTaskSubscription
import org.operaton.bpm.client.task.ExternalTask
import org.operaton.bpm.client.task.ExternalTaskService
import org.springframework.stereotype.Component

@Component
@ExternalTaskSubscription(topicName = ServiceTasks.BIKE_LEASING_BOOK_COSTS)
class BookCostsWorker(
    private val useCase: BookCancellationCostsUseCase,
) : BaseExternalTaskWorker() {

    override fun executeTask(externalTask: ExternalTask, externalTaskService: ExternalTaskService) {
        // `orderId` is handed to the cancelBikeOrder sub-process by the calling activity.
        val orderId = OrderId(externalTask.getVariable(FlowNodes.StartEventCancellationRequired.Variables.ORDER_ID.value))
        useCase.bookCosts(orderId)
        externalTaskService.complete(externalTask)
    }
}
