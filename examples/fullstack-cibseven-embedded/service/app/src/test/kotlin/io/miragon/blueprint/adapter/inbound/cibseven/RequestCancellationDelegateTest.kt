package io.miragon.blueprint.adapter.inbound.cibseven

import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi.FlowNodes
import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase
import io.miragon.blueprint.domain.bike.OrderId
import io.mockk.Runs
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.cibseven.bpm.engine.delegate.DelegateExecution
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class RequestCancellationDelegateTest {

    private val useCase = mockk<RequestOrderCancellationUseCase>()
    private val execution = mockk<DelegateExecution>()
    private val underTest = RequestCancellationDelegate(useCase)

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `requests the cancellation of the order and stores whether it is possible`(cancellationPossible: Boolean) {
        every { execution.getVariable(FlowNodes.StartEventCancellationRequired.Variables.ORDER_ID.value) } returns "ORDER-900"
        every { useCase.requestCancellation(OrderId("ORDER-900")) } returns cancellationPossible
        every {
            execution.setVariable(FlowNodes.ServiceTaskRequestCancellation.Variables.CANCELLATION_POSSIBLE.value, cancellationPossible)
        } just Runs

        underTest.execute(execution)

        verify { useCase.requestCancellation(OrderId("ORDER-900")) }
        verify {
            execution.setVariable(FlowNodes.ServiceTaskRequestCancellation.Variables.CANCELLATION_POSSIBLE.value, cancellationPossible)
        }
        confirmVerified(useCase)
    }
}
