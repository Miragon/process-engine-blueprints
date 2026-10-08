package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.application.port.inbound.SendContractUseCase
import io.miragon.blueprint.domain.leasing.ContractId
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SendContractTaskTest {

    private val useCase = mockk<SendContractUseCase>()
    private val task = SendContractTask(useCase)

    @Test
    fun `adopts the issued contract on the workflow aggregate`() {
        // given
        val application = testLeasingApplication()
        val aggregate = LeasingApplicationAggregate.of(application)
        every { useCase.sendContract(application) } returns application.withContract(ContractId("CONTRACT-1"))

        // when
        task.sendContract(aggregate)

        // then
        assertThat(aggregate.contractId).isEqualTo("CONTRACT-1")
    }
}
