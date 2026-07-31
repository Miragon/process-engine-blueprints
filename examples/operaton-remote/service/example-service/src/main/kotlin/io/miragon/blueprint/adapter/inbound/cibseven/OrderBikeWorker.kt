package io.miragon.blueprint.adapter.inbound.cibseven

import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.process.BikeLeasingProcessProcessApi.ServiceTasks
import io.miragon.blueprint.process.BikeLeasingProcessProcessApi.Variables
import org.cibseven.bpm.client.spring.annotation.ExternalTaskSubscription
import org.cibseven.bpm.client.task.ExternalTask
import org.cibseven.bpm.client.task.ExternalTaskService
import org.springframework.stereotype.Component

@Component
@ExternalTaskSubscription(topicName = ServiceTasks.BIKE_LEASING_ORDER_BIKE)
class OrderBikeWorker(
    private val useCase: OrderBikeUseCase,
) : BaseExternalTaskWorker() {

    override fun executeTask(externalTask: ExternalTask, externalTaskService: ExternalTaskService) {
        val result = useCase.orderBike(ApplicationId.of(externalTask.businessKey))
        // Output variables the process routes on (`bikeAvailable`) and later reuses (`orderId`).
        externalTaskService.complete(
            externalTask,
            mapOf(
                Variables.ServiceTaskOrderBike.ORDER_ID.value to result.orderId?.value,
                Variables.ServiceTaskOrderBike.BIKE_AVAILABLE.value to result.bikeAvailable,
            ),
        )
    }
}
