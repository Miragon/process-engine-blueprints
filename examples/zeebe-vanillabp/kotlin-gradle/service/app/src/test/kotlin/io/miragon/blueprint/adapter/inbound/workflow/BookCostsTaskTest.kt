package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase
import io.miragon.blueprint.domain.bike.OrderId
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class BookCostsTaskTest {

    private val useCase = mockk<BookCancellationCostsUseCase>(relaxed = true)
    private val task = BookCostsTask(useCase)

    @Test
    fun `books the costs for the order recorded on the workflow aggregate`() {
        // given
        val aggregate = LeasingApplicationAggregate.of(testLeasingApplication(orderId = OrderId("ORDER-1")))

        // when
        task.bookCosts(aggregate)

        // then
        verify { useCase.bookCosts(OrderId("ORDER-1")) }
    }

    @Test
    fun `fails when no order was placed`() {
        // given
        val aggregate = LeasingApplicationAggregate.of(testLeasingApplication(orderId = null))

        // when / then
        assertThatThrownBy { task.bookCosts(aggregate) }.isInstanceOf(IllegalStateException::class.java)
        confirmVerified(useCase)
    }
}
