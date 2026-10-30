package io.miragon.blueprint.adapter.inbound.temporal

import io.miragon.blueprint.adapter.process.BIKE_LEASING_TASK_QUEUE
import io.miragon.blueprint.adapter.process.SendReminderMail
import io.miragon.blueprint.application.port.inbound.SendSignatureReminderUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.temporal.spring.boot.ActivityImpl
import org.springframework.stereotype.Component

@Component
@ActivityImpl(taskQueues = [BIKE_LEASING_TASK_QUEUE])
class SendReminderMailActivity(
    private val useCase: SendSignatureReminderUseCase,
) : SendReminderMail {

    override fun sendReminderMail(applicationId: String) = useCase.sendSignatureReminder(ApplicationId.of(applicationId))
}
