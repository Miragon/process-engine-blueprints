package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.Errors
import io.miragon.blueprint.adapter.process.ServiceTasks
import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase
import io.miragon.blueprint.domain.bike.BikeUnavailableException
import io.vanillabp.spi.service.TaskException
import io.vanillabp.spi.service.WorkflowTask
import org.springframework.stereotype.Component

@Component
class OrderBikeTask(
    private val useCase: OrderBikeUseCase,
) : BikeLeasingWorkflow() {

    @WorkflowTask(taskDefinition = ServiceTasks.MIRAVELO_ORDER_BIKE)
    fun orderBike(aggregate: LeasingApplicationAggregate) {
        try {
            aggregate.adopt(useCase.orderBike(aggregate.toDomain()))
        } catch (unavailable: BikeUnavailableException) {
            throw TaskException(unavailable.message, Errors.BIKE_UNAVAILABLE.code)
        }
    }
}
