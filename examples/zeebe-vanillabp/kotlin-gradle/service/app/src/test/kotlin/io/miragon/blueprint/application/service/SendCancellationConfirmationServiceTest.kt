package io.miragon.blueprint.application.service

import io.miragon.blueprint.application.port.outbound.NotificationPort
import io.miragon.blueprint.domain.leasing.LeasingStatus
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import io.mockk.Runs
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SendCancellationConfirmationServiceTest {

    private val notification = mockk<NotificationPort>()
    private val underTest = SendCancellationConfirmationService(notification = notification)

    @Test
    fun `sendCancellationConfirmation notifies the customer and returns the application as CANCELLED`() {

        // given: a withdrawn application
        val application = testLeasingApplication(status = LeasingStatus.WITHDRAWN)
        every { notification.send(any(), application) } just Runs

        // when: the cancellation confirmation is sent
        val cancelled = underTest.sendCancellationConfirmation(application)

        // then: the customer is informed and only the status moved to CANCELLED
        assertThat(cancelled).isEqualTo(application.copy(status = LeasingStatus.CANCELLED))
        verify { notification.send("Your bike-leasing application has been cancelled", application) }
        confirmVerified(notification)
    }
}
