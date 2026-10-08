package io.miragon.blueprint.adapter.inbound.operaton

import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.bike.BikeUnavailableException
import io.miragon.blueprint.domain.bike.OrderId
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.operaton.bpm.client.task.ExternalTask
import org.operaton.bpm.client.task.ExternalTaskService
import org.junit.jupiter.api.Test
import java.util.UUID

class OrderBikeWorkerTest {

    private val useCase = mockk<OrderBikeUseCase>()
    private val underTest = OrderBikeWorker(useCase)

    private val applicationId = ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
    private val task = mockk<ExternalTask>(relaxed = true) {
        every { businessKey } returns applicationId.value.toString()
    }
    private val service = mockk<ExternalTaskService>(relaxed = true)

    @Test
    fun `completes with the order id as output variable`() {

        // given: the bike was available and an order was placed
        every { useCase.orderBike(applicationId) } returns OrderId("ORDER-1")

        // when: the worker runs
        underTest.execute(task, service)

        // then: the process continues with the `orderId` output variable and no BPMN error is raised
        verify { service.complete(task, mapOf("orderId" to "ORDER-1")) }
        verify(exactly = 0) { service.handleBpmnError(any(), any(), any()) }
    }

    @Test
    fun `raises the bikeUnavailable BPMN error when the dealer cannot deliver the bike`() {

        // given: the dealer has the bike out of stock
        every { useCase.orderBike(applicationId) } throws BikeUnavailableException(BikeId("BIKE-OOS"))

        // when: the worker runs
        underTest.execute(task, service)

        // then: the BPMN error is raised and the task is neither completed nor reported as a failure
        verify { service.handleBpmnError(task, "bikeUnavailable", "Bike BIKE-OOS is not available at the dealer") }
        verify(exactly = 0) { service.complete(any(), any()) }
        verify(exactly = 0) { service.handleFailure(any<ExternalTask>(), any(), any(), any(), any()) }
    }
}
