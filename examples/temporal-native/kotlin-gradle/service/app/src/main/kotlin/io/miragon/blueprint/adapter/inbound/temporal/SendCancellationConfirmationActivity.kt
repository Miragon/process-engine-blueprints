package io.miragon.blueprint.adapter.inbound.temporal

import io.miragon.blueprint.adapter.process.BIKE_LEASING_TASK_QUEUE
import io.miragon.blueprint.adapter.process.BikeLeasingActivities
import io.miragon.blueprint.application.port.inbound.SendCancellationConfirmationUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.temporal.spring.boot.ActivityImpl
import org.springframework.stereotype.Component

@Component
@ActivityImpl(taskQueues = [BIKE_LEASING_TASK_QUEUE])
class SendCancellationConfirmationActivity(
    private val useCase: SendCancellationConfirmationUseCase,
) : BikeLeasingActivities.SendCancellationConfirmation {

    override fun sendCancellationConfirmation(applicationId: String) = useCase.sendCancellationConfirmation(ApplicationId.of(applicationId))
}
