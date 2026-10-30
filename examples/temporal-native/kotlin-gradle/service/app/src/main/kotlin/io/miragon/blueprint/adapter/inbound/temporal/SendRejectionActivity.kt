package io.miragon.blueprint.adapter.inbound.temporal

import io.miragon.blueprint.adapter.process.Activities
import io.miragon.blueprint.adapter.process.BIKE_LEASING_TASK_QUEUE
import io.miragon.blueprint.application.port.inbound.RejectApplicationUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.temporal.spring.boot.ActivityImpl
import org.springframework.stereotype.Component

@Component
@ActivityImpl(taskQueues = [BIKE_LEASING_TASK_QUEUE])
class SendRejectionActivity(
    private val useCase: RejectApplicationUseCase,
) : Activities.SendRejection {

    override fun sendRejection(applicationId: String) = useCase.reject(ApplicationId.of(applicationId))
}
