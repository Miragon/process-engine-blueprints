package io.miragon.blueprint.adapter.inbound.temporal

import io.miragon.blueprint.application.port.inbound.SendSignatureReminderUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import org.junit.jupiter.api.Test

class SendReminderMailActivityTest {

    private val useCase = mockk<SendSignatureReminderUseCase>(relaxed = true)
    private val activity = SendReminderMailActivity(useCase)
    private val applicationId = UUID.randomUUID().toString()

    @Test
    fun `delegates to the use case`() {
        // when
        activity.sendReminderMail(applicationId)

        // then
        verify { useCase.sendSignatureReminder(ApplicationId.of(applicationId)) }
    }
}
