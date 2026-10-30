package io.miragon.blueprint.adapter.inbound.temporal

import io.miragon.blueprint.application.port.inbound.SendContractUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import org.junit.jupiter.api.Test

class SendContractActivityTest {

    private val useCase = mockk<SendContractUseCase>(relaxed = true)
    private val activity = SendContractActivity(useCase)
    private val applicationId = UUID.randomUUID().toString()

    @Test
    fun `delegates to the use case`() {
        // when
        activity.sendContract(applicationId)

        // then
        verify { useCase.sendContract(ApplicationId.of(applicationId)) }
    }
}
