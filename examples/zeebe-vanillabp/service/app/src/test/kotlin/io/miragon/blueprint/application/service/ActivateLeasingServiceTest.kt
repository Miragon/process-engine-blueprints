package io.miragon.blueprint.application.service

import io.miragon.blueprint.domain.leasing.LeasingStatus
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ActivateLeasingServiceTest {

    private val underTest = ActivateLeasingService()

    @Test
    fun `activate returns the application with the ACTIVE status`() {

        // given: a handed-over application
        val application = testLeasingApplication(status = LeasingStatus.HANDED_OVER)

        // when: the leasing is activated
        val activated = underTest.activate(application)

        // then: only the status moved to ACTIVE
        assertThat(activated).isEqualTo(application.copy(status = LeasingStatus.ACTIVE))
    }
}
