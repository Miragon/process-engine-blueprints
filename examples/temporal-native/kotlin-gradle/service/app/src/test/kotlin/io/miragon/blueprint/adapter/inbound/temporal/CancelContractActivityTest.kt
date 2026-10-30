package io.miragon.blueprint.adapter.inbound.temporal

import io.miragon.blueprint.application.port.inbound.CancelContractUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import org.junit.jupiter.api.Test

class CancelContractActivityTest {

    private val useCase = mockk<CancelContractUseCase>(relaxed = true)
    private val activity = CancelContractActivity(useCase)
    private val applicationId = UUID.randomUUID().toString()

    @Test
    fun `delegates to the use case`() {
        // when
        activity.cancelContract(applicationId)

        // then
        verify { useCase.cancelContract(ApplicationId.of(applicationId)) }
    }
}
