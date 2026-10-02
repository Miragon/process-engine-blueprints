package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.outbound.dealer.BikeDealerAdapter.DealerUnavailableException
import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.bike.OrderId
import io.miragon.blueprint.domain.leasing.LeasingStatus
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class OrderBikeTaskTest {

    private val useCase = mockk<OrderBikeUseCase>()
    private val task = OrderBikeTask(useCase)

    @Test
    fun `adopts the placed order so the engine sees an available bike`() {
        // given
        val application = testLeasingApplication()
        val aggregate = LeasingApplicationAggregate.of(application)
        every { useCase.orderBike(application) } returns application.documentOrder(OrderId("ORDER-1"))

        // when
        task.orderBike(aggregate)

        // then
        assertThat(aggregate.orderId).isEqualTo("ORDER-1")
        assertThat(aggregate.status).isEqualTo(LeasingStatus.ORDERED)
        assertThat(aggregate.bikeAvailable).isTrue()
    }

    @Test
    fun `reports an unavailable bike when no order was placed`() {
        // given
        val application = testLeasingApplication()
        val aggregate = LeasingApplicationAggregate.of(application)
        every { useCase.orderBike(application) } returns application

        // when
        task.orderBike(aggregate)

        // then
        assertThat(aggregate.orderId).isNull()
        assertThat(aggregate.bikeAvailable).isFalse()
    }

    @Test
    fun `lets the dealer outage escape so VanillaBP fails the job and its retries count down to an incident`() {
        // given: the simulated dealer outage (BIKE-FAIL) propagates out of the use case
        val aggregate = LeasingApplicationAggregate.of(testLeasingApplication(bikeId = BikeId("BIKE-FAIL")))
        every { useCase.orderBike(any()) } throws DealerUnavailableException(BikeId("BIKE-FAIL"))

        // when / then: the outage is not swallowed and the workflow aggregate stays unordered
        assertThatThrownBy { task.orderBike(aggregate) }.isInstanceOf(DealerUnavailableException::class.java)
        assertThat(aggregate.orderId).isNull()
        assertThat(aggregate.status).isEqualTo(LeasingStatus.RECEIVED)
    }
}
