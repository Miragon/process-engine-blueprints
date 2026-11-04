package io.miragon.blueprint.adapter.inbound.zeebe

import dev.bpmcrafters.processengine.worker.ProcessEngineWorker
import dev.bpmcrafters.processengine.worker.Variable
import io.miragon.blueprint.adapter.process.ServiceTasks
import io.miragon.blueprint.application.port.inbound.SendSignatureReminderUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.springframework.stereotype.Component

@Component
class SendReminderMailWorker(
    private val useCase: SendSignatureReminderUseCase,
) {

    @ProcessEngineWorker(topic = ServiceTasks.MIRAVELO_SEND_REMINDER_MAIL)
    fun handle(@Variable applicationId: String) {
        useCase.sendSignatureReminder(ApplicationId.of(applicationId))
    }
}
