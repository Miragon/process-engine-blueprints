package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.application.port.inbound.SendSignatureReminderUseCase
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class SendReminderMailTaskTest {

    private val useCase = mockk<SendSignatureReminderUseCase>(relaxed = true)
    private val task = SendReminderMailTask(useCase)

    @Test
    fun `hands the application of the workflow aggregate to the use case`() {
        // given
        val application = testLeasingApplication()

        // when
        task.sendReminderMail(LeasingApplicationAggregate.of(application))

        // then
        verify { useCase.sendSignatureReminder(application) }
    }
}
