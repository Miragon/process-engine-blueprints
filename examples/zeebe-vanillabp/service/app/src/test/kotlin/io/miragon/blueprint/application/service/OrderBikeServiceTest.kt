package io.miragon.blueprint.application.service

import io.miragon.blueprint.application.port.outbound.BikeDealerPort
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.bike.OrderId
import io.miragon.blueprint.domain.leasing.LeasingStatus
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OrderBikeServiceTest {

    private val bikeDealer = mockk<BikeDealerPort>()
    private val underTest = OrderBikeService(bikeDealer = bikeDealer)

    @Test
    fun `orderBike places an order when the dealer has the bike in stock`() {

        // given: an application whose bike is available at the dealer
        val application = testLeasingApplication(bikeId = BikeId("BIKE-900"))
        every { bikeDealer.checkAvailability(application.bikeId) } returns true
        every { bikeDealer.order(application.bikeId) } returns OrderId("ORDER-900")

        // when: the bike is ordered
        val ordered = underTest.orderBike(application)

        // then: the order id is recorded and the application moves to ORDERED
        assertThat(ordered)
            .isEqualTo(application.copy(orderId = OrderId("ORDER-900"), status = LeasingStatus.ORDERED))
        verify { bikeDealer.checkAvailability(application.bikeId) }
        verify { bikeDealer.order(application.bikeId) }
        confirmVerified(bikeDealer)
    }

    @Test
    fun `orderBike leaves an out-of-stock bike unordered`() {

        // given: an application whose bike is out of stock at the dealer
        val application = testLeasingApplication(bikeId = BikeId("BIKE-OOS"))
        every { bikeDealer.checkAvailability(application.bikeId) } returns false

        // when: the bike is ordered
        val unordered = underTest.orderBike(application)

        // then: no order is placed and the application is untouched
        assertThat(unordered).isEqualTo(application)
        verify { bikeDealer.checkAvailability(application.bikeId) }
        confirmVerified(bikeDealer)
    }

    @Test
    fun `orderBike does not order a second bike when the task is delivered again`() {

        // given: an application that already carries the order of an earlier delivery
        val application = testLeasingApplication(orderId = OrderId("ORDER-900"), status = LeasingStatus.ORDERED)

        // when: the bike is ordered again
        val ordered = underTest.orderBike(application)

        // then: the application is untouched and the dealer is not called
        assertThat(ordered).isEqualTo(application)
        confirmVerified(bikeDealer)
    }
}
