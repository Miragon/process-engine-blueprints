package io.miragon.blueprint.adapter.inbound.zeebe

import dev.bpmcrafters.processengine.worker.BpmnErrorOccurred
import io.miragon.blueprint.adapter.outbound.dealer.BikeDealerAdapter.DealerUnavailableException
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.bike.BikeUnavailableException
import io.miragon.blueprint.domain.bike.OrderId
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class OrderBikeWorkerTest {

    private val useCase = mockk<OrderBikeUseCase>()
    private val worker = OrderBikeWorker(useCase)

    @Test
    fun `returns the order id as output variable`() {
        // given
        val id = UUID.randomUUID()
        every { useCase.orderBike(ApplicationId.of(id.toString()), BikeId("BIKE-900")) } returns OrderId("ORDER-1")

        // when
        val variables = worker.handle(id.toString(), "BIKE-900")

        // then
        assertThat(variables).isEqualTo(mapOf("orderId" to "ORDER-1"))
    }

    @Test
    fun `throws the bikeUnavailable BPMN error when the dealer cannot deliver the bike`() {
        // given
        val id = UUID.randomUUID()
        every { useCase.orderBike(any(), any()) } throws BikeUnavailableException(BikeId("BIKE-OOS"))

        // when / then
        assertThatThrownBy { worker.handle(id.toString(), "BIKE-OOS") }
            .isInstanceOfSatisfying(BpmnErrorOccurred::class.java) { error ->
                assertThat(error.errorCode).isEqualTo("bikeUnavailable")
                assertThat(error.message).isEqualTo("Bike BIKE-OOS is not available at the dealer")
            }
    }

    @Test
    fun `lets the dealer outage through so the worker library fails the job and retries count down to an incident`() {
        // given: the simulated dealer outage (BIKE-FAIL) propagates out of the use case
        val id = UUID.randomUUID()
        every { useCase.orderBike(any(), any()) } throws DealerUnavailableException(BikeId("BIKE-FAIL"))

        // when / then
        assertThatThrownBy { worker.handle(id.toString(), "BIKE-FAIL") }
            .isInstanceOf(DealerUnavailableException::class.java)
    }
}
