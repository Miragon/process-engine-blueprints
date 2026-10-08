package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase
import io.miragon.blueprint.domain.bike.OrderId
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RequestCancellationTaskTest {

    private val useCase = mockk<RequestOrderCancellationUseCase>()
    private val task = RequestCancellationTask(useCase)

    @Test
    fun `records on the workflow aggregate that the dealer allows the cancellation`() {
        // given
        val aggregate = LeasingApplicationAggregate.of(testLeasingApplication(orderId = OrderId("ORDER-1")))
        every { useCase.requestCancellation(OrderId("ORDER-1")) } returns true

        // when
        task.requestCancellation(aggregate)

        // then
        assertThat(aggregate.cancellationPossible).isTrue()
    }

    @Test
    fun `records on the workflow aggregate that the dealer refuses the cancellation`() {
        // given
        val aggregate = LeasingApplicationAggregate.of(testLeasingApplication(orderId = OrderId("ORDER-1")))
        every { useCase.requestCancellation(OrderId("ORDER-1")) } returns false

        // when
        task.requestCancellation(aggregate)

        // then
        assertThat(aggregate.cancellationPossible).isFalse()
    }

    @Test
    fun `fails when no order was placed`() {
        // given
        val aggregate = LeasingApplicationAggregate.of(testLeasingApplication(orderId = null))

        // when / then
        assertThatThrownBy { task.requestCancellation(aggregate) }.isInstanceOf(IllegalStateException::class.java)
        confirmVerified(useCase)
    }
}
