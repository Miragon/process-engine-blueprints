package io.miragon.blueprint.application.service

import io.miragon.blueprint.application.port.outbound.NotificationPort
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import io.mockk.Runs
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class SendSignatureReminderServiceTest {

    private val notification = mockk<NotificationPort>()
    private val underTest = SendSignatureReminderService(notification = notification)

    @Test
    fun `sendSignatureReminder reminds the customer`() {

        // given: an application awaiting signature
        val application = testLeasingApplication()
        every { notification.send(any(), application) } just Runs

        // when: the signature reminder is sent
        underTest.sendSignatureReminder(application)

        // then: the customer is reminded
        verify { notification.send("Reminder: your leasing contract is still awaiting signature", application) }
        confirmVerified(notification)
    }
}
