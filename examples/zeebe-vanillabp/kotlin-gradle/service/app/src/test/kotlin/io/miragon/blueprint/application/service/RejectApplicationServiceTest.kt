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

class RejectApplicationServiceTest {

    private val notification = mockk<NotificationPort>()
    private val underTest = RejectApplicationService(notification = notification)

    @Test
    fun `reject notifies the customer and returns the application as REJECTED`() {

        // given: an application
        val application = testLeasingApplication()
        every { notification.send(any(), application) } just Runs

        // when: the application is rejected
        val rejected = underTest.reject(application)

        // then: the customer is notified and only the status moved to REJECTED
        assertThat(rejected).isEqualTo(application.copy(status = LeasingStatus.REJECTED))
        verify { notification.send("Your bike-leasing application was rejected", application) }
        confirmVerified(notification)
    }
}
