package io.miragon.blueprint.application.service

import io.miragon.blueprint.application.port.inbound.SendSignatureReminderUseCase
import io.miragon.blueprint.application.port.outbound.NotificationPort
import io.miragon.blueprint.domain.leasing.LeasingApplication
import org.springframework.stereotype.Service

@Service
class SendSignatureReminderService(
    private val notification: NotificationPort,
) : SendSignatureReminderUseCase {

    override fun sendSignatureReminder(application: LeasingApplication) {
        notification.send("Reminder: your leasing contract is still awaiting signature", application)
    }
}
