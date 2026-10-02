package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.ServiceTasks
import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase
import io.vanillabp.spi.service.WorkflowTask
import org.springframework.stereotype.Component

@Component
class OrderBikeTask(
    private val useCase: OrderBikeUseCase,
) : BikeLeasingWorkflow() {

    @WorkflowTask(taskDefinition = ServiceTasks.MIRAVELO_ORDER_BIKE)
    fun orderBike(aggregate: LeasingApplicationAggregate) {
        aggregate.adopt(useCase.orderBike(aggregate.toDomain()))
    }
}
