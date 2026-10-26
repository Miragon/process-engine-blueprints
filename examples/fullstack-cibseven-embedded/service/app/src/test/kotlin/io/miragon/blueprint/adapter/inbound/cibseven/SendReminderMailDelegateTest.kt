package io.miragon.blueprint.adapter.inbound.cibseven

import io.miragon.blueprint.application.port.inbound.SendSignatureReminderUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.mockk.Runs
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.cibseven.bpm.engine.delegate.DelegateExecution
import org.junit.jupiter.api.Test

class SendReminderMailDelegateTest {

    private val useCase = mockk<SendSignatureReminderUseCase>()
    private val execution = mockk<DelegateExecution>()
    private val underTest = SendReminderMailDelegate(useCase)

    @Test
    fun `sends the signature reminder of the application in the business key`() {
        val applicationId = ApplicationId.new()
        every { execution.processBusinessKey } returns applicationId.value.toString()
        every { useCase.sendSignatureReminder(applicationId) } just Runs

        underTest.execute(execution)

        verify { useCase.sendSignatureReminder(applicationId) }
        confirmVerified(useCase)
    }
}
