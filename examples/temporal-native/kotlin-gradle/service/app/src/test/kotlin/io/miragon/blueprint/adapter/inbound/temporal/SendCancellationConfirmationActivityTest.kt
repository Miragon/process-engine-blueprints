package io.miragon.blueprint.adapter.inbound.temporal

import io.miragon.blueprint.application.port.inbound.SendCancellationConfirmationUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import org.junit.jupiter.api.Test

class SendCancellationConfirmationActivityTest {

    private val useCase = mockk<SendCancellationConfirmationUseCase>(relaxed = true)
    private val activity = SendCancellationConfirmationActivity(useCase)
    private val applicationId = UUID.randomUUID().toString()

    @Test
    fun `delegates to the use case`() {
        // when
        activity.sendCancellationConfirmation(applicationId)

        // then
        verify { useCase.sendCancellationConfirmation(ApplicationId.of(applicationId)) }
    }
}
