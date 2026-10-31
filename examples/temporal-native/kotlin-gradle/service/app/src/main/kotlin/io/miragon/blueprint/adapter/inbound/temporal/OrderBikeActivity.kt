package io.miragon.blueprint.adapter.inbound.temporal

import io.miragon.blueprint.adapter.process.BIKE_LEASING_TASK_QUEUE
import io.miragon.blueprint.adapter.process.BIKE_UNAVAILABLE_FAILURE
import io.miragon.blueprint.adapter.process.BikeLeasingActivities
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.bike.BikeUnavailableException
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.temporal.failure.ApplicationFailure
import io.temporal.spring.boot.ActivityImpl
import org.springframework.stereotype.Component

@Component
@ActivityImpl(taskQueues = [BIKE_LEASING_TASK_QUEUE])
class OrderBikeActivity(
    private val useCase: OrderBikeUseCase,
) : BikeLeasingActivities.OrderBike {

    override fun orderBike(applicationId: String, bikeId: String): String =
        try {
            useCase.orderBike(ApplicationId.of(applicationId), BikeId(bikeId)).value
        } catch (e: BikeUnavailableException) {
            throw ApplicationFailure.newNonRetryableFailure(e.message, BIKE_UNAVAILABLE_FAILURE)
        }
}
