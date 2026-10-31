package io.miragon.blueprint.adapter.inbound.temporal

import io.miragon.blueprint.application.port.inbound.CancelInsurancePolicyUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import org.junit.jupiter.api.Test

class CancelPolicyActivityTest {

    private val useCase = mockk<CancelInsurancePolicyUseCase>(relaxed = true)
    private val activity = CancelPolicyActivity(useCase)
    private val applicationId = UUID.randomUUID().toString()

    @Test
    fun `delegates to the use case`() {
        // when
        activity.cancelPolicy(applicationId)

        // then
        verify { useCase.cancelPolicy(ApplicationId.of(applicationId)) }
    }
}
