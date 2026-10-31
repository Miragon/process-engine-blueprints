package io.miragon.blueprint.adapter.inbound.temporal

import io.miragon.blueprint.application.port.inbound.IssueInsurancePolicyUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import org.junit.jupiter.api.Test

class IssueInsurancePolicyActivityTest {

    private val useCase = mockk<IssueInsurancePolicyUseCase>(relaxed = true)
    private val activity = IssueInsurancePolicyActivity(useCase)
    private val applicationId = UUID.randomUUID().toString()

    @Test
    fun `delegates to the use case`() {
        // when
        activity.issueInsurancePolicy(applicationId)

        // then
        verify { useCase.issuePolicy(ApplicationId.of(applicationId)) }
    }
}
