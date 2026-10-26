package io.miragon.blueprint.adapter.inbound.cibseven

import io.miragon.blueprint.application.port.inbound.CancelContractUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.mockk.Runs
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.cibseven.bpm.engine.delegate.DelegateExecution
import org.junit.jupiter.api.Test

class CancelContractDelegateTest {

    private val useCase = mockk<CancelContractUseCase>()
    private val execution = mockk<DelegateExecution>()
    private val underTest = CancelContractDelegate(useCase)

    @Test
    fun `cancels the contract of the application in the business key`() {
        val applicationId = ApplicationId.new()
        every { execution.processBusinessKey } returns applicationId.value.toString()
        every { useCase.cancelContract(applicationId) } just Runs

        underTest.execute(execution)

        verify { useCase.cancelContract(applicationId) }
        confirmVerified(useCase)
    }
}
