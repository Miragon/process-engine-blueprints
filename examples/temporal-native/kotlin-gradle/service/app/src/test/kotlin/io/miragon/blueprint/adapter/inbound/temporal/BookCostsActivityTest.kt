package io.miragon.blueprint.adapter.inbound.temporal

import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase
import io.miragon.blueprint.domain.bike.OrderId
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class BookCostsActivityTest {

    private val useCase = mockk<BookCancellationCostsUseCase>(relaxed = true)
    private val activity = BookCostsActivity(useCase)

    @Test
    fun `delegates to the use case`() {
        // when
        activity.bookCosts("ORDER-1")

        // then
        verify { useCase.bookCosts(OrderId("ORDER-1")) }
    }
}
