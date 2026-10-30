package io.miragon.blueprint.adapter.inbound.temporal

import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase
import io.miragon.blueprint.domain.bike.OrderId
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RequestCancellationActivityTest {

    private val useCase = mockk<RequestOrderCancellationUseCase>()
    private val activity = RequestCancellationActivity(useCase)

    @Test
    fun `returns what the use case decided`() {
        // given
        every { useCase.requestCancellation(OrderId("ORDER-1")) } returns true

        // when
        val result = activity.requestCancellation("ORDER-1")

        // then
        assertThat(result).isTrue()
    }
}
