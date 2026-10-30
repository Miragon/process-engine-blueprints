package io.miragon.blueprint.adapter.inbound.temporal

import io.miragon.blueprint.adapter.outbound.dealer.BikeDealerAdapter.DealerUnavailableException
import io.miragon.blueprint.adapter.process.BIKE_UNAVAILABLE_FAILURE
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.bike.BikeUnavailableException
import io.miragon.blueprint.domain.bike.OrderId
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.mockk.every
import io.mockk.mockk
import io.temporal.failure.ApplicationFailure
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class OrderBikeActivityTest {

    private val useCase = mockk<OrderBikeUseCase>()
    private val activity = OrderBikeActivity(useCase)
    private val applicationId = UUID.randomUUID().toString()

    @Test
    fun `returns the order id`() {
        // given
        every { useCase.orderBike(ApplicationId.of(applicationId), BikeId("BIKE-900")) } returns OrderId("ORDER-1")

        // when
        val orderId = activity.orderBike(applicationId, "BIKE-900")

        // then
        assertThat(orderId).isEqualTo("ORDER-1")
    }

    @Test
    fun `reports an unavailable bike as a non-retryable business failure`() {
        // given
        every { useCase.orderBike(any(), any()) } throws BikeUnavailableException(BikeId("BIKE-OOS"))

        // when, then
        assertThatThrownBy { activity.orderBike(applicationId, "BIKE-OOS") }
            .isInstanceOfSatisfying(ApplicationFailure::class.java) { failure ->
                assertThat(failure.type).isEqualTo(BIKE_UNAVAILABLE_FAILURE)
                assertThat(failure.isNonRetryable).isTrue()
            }
    }

    @Test
    fun `lets a dealer outage through so that Temporal retries`() {
        // given
        every { useCase.orderBike(any(), any()) } throws DealerUnavailableException(BikeId("BIKE-FAIL"))

        // when, then
        assertThatThrownBy { activity.orderBike(applicationId, "BIKE-FAIL") }
            .isInstanceOf(DealerUnavailableException::class.java)
    }
}
