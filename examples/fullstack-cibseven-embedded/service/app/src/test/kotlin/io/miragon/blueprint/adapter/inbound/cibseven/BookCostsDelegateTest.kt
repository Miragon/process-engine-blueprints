package io.miragon.blueprint.adapter.inbound.cibseven

import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi.FlowNodes
import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase
import io.miragon.blueprint.domain.bike.OrderId
import io.mockk.Runs
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.cibseven.bpm.engine.delegate.DelegateExecution
import org.junit.jupiter.api.Test

class BookCostsDelegateTest {

    private val useCase = mockk<BookCancellationCostsUseCase>()
    private val execution = mockk<DelegateExecution>()
    private val underTest = BookCostsDelegate(useCase)

    @Test
    fun `books the costs of the order handed to the sub-process`() {
        every { execution.getVariable(FlowNodes.StartEventCancellationRequired.Variables.ORDER_ID.value) } returns "ORDER-900"
        every { useCase.bookCosts(OrderId("ORDER-900")) } just Runs

        underTest.execute(execution)

        verify { useCase.bookCosts(OrderId("ORDER-900")) }
        confirmVerified(useCase)
    }
}
