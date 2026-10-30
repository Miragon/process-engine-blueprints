package io.miragon.blueprint.adapter.inbound.temporal

import io.miragon.blueprint.application.port.inbound.RejectApplicationUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import org.junit.jupiter.api.Test

class SendRejectionActivityTest {

    private val useCase = mockk<RejectApplicationUseCase>(relaxed = true)
    private val activity = SendRejectionActivity(useCase)
    private val applicationId = UUID.randomUUID().toString()

    @Test
    fun `delegates to the use case`() {
        // when
        activity.sendRejection(applicationId)

        // then
        verify { useCase.reject(ApplicationId.of(applicationId)) }
    }
}
