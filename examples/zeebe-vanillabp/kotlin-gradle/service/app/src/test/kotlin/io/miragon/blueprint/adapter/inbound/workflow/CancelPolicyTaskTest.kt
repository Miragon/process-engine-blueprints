package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.application.port.inbound.CancelInsurancePolicyUseCase
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class CancelPolicyTaskTest {

    private val useCase = mockk<CancelInsurancePolicyUseCase>(relaxed = true)
    private val task = CancelPolicyTask(useCase)

    @Test
    fun `delegates to the use case for the application of the workflow aggregate`() {
        // given
        val application = testLeasingApplication()

        // when
        task.cancelPolicy(LeasingApplicationAggregate.of(application))

        // then
        verify { useCase.cancelPolicy(application.id) }
    }
}
