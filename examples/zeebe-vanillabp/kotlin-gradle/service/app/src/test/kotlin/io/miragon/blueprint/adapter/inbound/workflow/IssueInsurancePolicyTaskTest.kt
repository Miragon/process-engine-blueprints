package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.application.port.inbound.IssueInsurancePolicyUseCase
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class IssueInsurancePolicyTaskTest {

    private val useCase = mockk<IssueInsurancePolicyUseCase>(relaxed = true)
    private val task = IssueInsurancePolicyTask(useCase)

    @Test
    fun `delegates to the use case for the application of the workflow aggregate`() {
        // given
        val application = testLeasingApplication()

        // when
        task.issueInsurancePolicy(LeasingApplicationAggregate.of(application))

        // then
        verify { useCase.issuePolicy(application.id) }
    }
}
