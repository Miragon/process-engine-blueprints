package io.miragon.blueprint.adapter.inbound.temporal

import io.miragon.blueprint.adapter.process.BIKE_LEASING_TASK_QUEUE
import io.miragon.blueprint.adapter.process.BookCosts
import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase
import io.miragon.blueprint.domain.bike.OrderId
import io.temporal.spring.boot.ActivityImpl
import org.springframework.stereotype.Component

@Component
@ActivityImpl(taskQueues = [BIKE_LEASING_TASK_QUEUE])
class BookCostsActivity(
    private val useCase: BookCancellationCostsUseCase,
) : BookCosts {

    override fun bookCosts(orderId: String) = useCase.bookCosts(OrderId(orderId))
}
