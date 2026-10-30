package io.miragon.blueprint.adapter.inbound.temporal

import io.miragon.blueprint.adapter.process.BIKE_LEASING_TASK_QUEUE
import io.miragon.blueprint.adapter.process.RequestCancellation
import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase
import io.miragon.blueprint.domain.bike.OrderId
import io.temporal.spring.boot.ActivityImpl
import org.springframework.stereotype.Component

@Component
@ActivityImpl(taskQueues = [BIKE_LEASING_TASK_QUEUE])
class RequestCancellationActivity(
    private val useCase: RequestOrderCancellationUseCase,
) : RequestCancellation {

    override fun requestCancellation(orderId: String): Boolean = useCase.requestCancellation(OrderId(orderId))
}
