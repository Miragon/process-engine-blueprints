package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.ServiceTasks
import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.application.port.inbound.SendSignatureReminderUseCase
import io.vanillabp.spi.service.WorkflowTask
import org.springframework.stereotype.Component

@Component
class SendReminderMailTask(
    private val useCase: SendSignatureReminderUseCase,
) : BikeLeasingWorkflow() {

    @WorkflowTask(taskDefinition = ServiceTasks.MIRAVELO_SEND_REMINDER_MAIL)
    fun sendReminderMail(aggregate: LeasingApplicationAggregate) {
        useCase.sendSignatureReminder(aggregate.toDomain())
    }
}
