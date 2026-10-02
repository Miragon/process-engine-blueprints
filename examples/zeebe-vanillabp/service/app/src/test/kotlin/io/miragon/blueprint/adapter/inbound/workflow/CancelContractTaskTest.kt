package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.application.port.inbound.CancelContractUseCase
import io.miragon.blueprint.domain.leasing.ContractId
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class CancelContractTaskTest {

    private val useCase = mockk<CancelContractUseCase>(relaxed = true)
    private val task = CancelContractTask(useCase)

    @Test
    fun `hands the application of the workflow aggregate to the use case`() {
        // given
        val application = testLeasingApplication(contractId = ContractId("CONTRACT-1"))

        // when
        task.cancelContract(LeasingApplicationAggregate.of(application))

        // then
        verify { useCase.cancelContract(application) }
    }
}
