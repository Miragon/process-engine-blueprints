package io.miragon.blueprint.adapter.inbound.temporal

import io.miragon.blueprint.application.port.inbound.ActivateLeasingUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import org.junit.jupiter.api.Test

class ActivateLeasingActivityTest {

    private val useCase = mockk<ActivateLeasingUseCase>(relaxed = true)
    private val activity = ActivateLeasingActivity(useCase)
    private val applicationId = UUID.randomUUID().toString()

    @Test
    fun `delegates to the use case`() {
        // when
        activity.activateLeasing(applicationId)

        // then
        verify { useCase.activate(ApplicationId.of(applicationId)) }
    }
}
