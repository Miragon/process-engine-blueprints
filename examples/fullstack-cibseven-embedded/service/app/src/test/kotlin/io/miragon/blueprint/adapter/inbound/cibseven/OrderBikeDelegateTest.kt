package io.miragon.blueprint.adapter.inbound.cibseven

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes
import io.miragon.blueprint.adapter.process.Errors
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.bike.BikeUnavailableException
import io.miragon.blueprint.domain.bike.OrderId
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.mockk.Runs
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.cibseven.bpm.engine.delegate.BpmnError
import org.cibseven.bpm.engine.delegate.DelegateExecution
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OrderBikeDelegateTest {

    private val useCase = mockk<OrderBikeUseCase>()
    private val execution = mockk<DelegateExecution>()
    private val underTest = OrderBikeDelegate(useCase)

    private val applicationId = ApplicationId.new()
    private val bikeId = BikeId("BIKE-900")

    @BeforeEach
    fun givenTheApplicationAndItsBike() {
        every { execution.processBusinessKey } returns applicationId.value.toString()
        every { execution.getVariable(FlowNodes.ServiceTaskOrderBike.Variables.BIKE_ID.value) } returns bikeId.value
    }

    @Test
    fun `orders the bike the process carries and stores the order id`() {
        every { useCase.orderBike(applicationId, bikeId) } returns OrderId("ORDER-900")
        every { execution.setVariable(FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.value, "ORDER-900") } just Runs

        underTest.execute(execution)

        verify { useCase.orderBike(applicationId, bikeId) }
        verify { execution.setVariable(FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.value, "ORDER-900") }
        confirmVerified(useCase)
    }

    @Test
    fun `turns an unavailable bike into the bikeUnavailable BPMN error`() {
        every { useCase.orderBike(applicationId, bikeId) } throws BikeUnavailableException(bikeId)

        assertThatThrownBy { underTest.execute(execution) }
            .isInstanceOf(BpmnError::class.java)
            .extracting("errorCode")
            .isEqualTo(Errors.BIKE_UNAVAILABLE.code)
        verify(exactly = 0) { execution.setVariable(any<String>(), any()) }
    }
}
