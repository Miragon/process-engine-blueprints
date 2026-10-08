package io.miragon.blueprint.adapter.inbound.cibseven

import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase
import io.miragon.blueprint.domain.bike.OrderId
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.cibseven.bpm.client.task.ExternalTask
import org.cibseven.bpm.client.task.ExternalTaskService
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
    fun `completes with the order id and availability as output variables`() {

        // given: the bike was available and an order was placed
        every { useCase.orderBike(applicationId) } returns
            OrderBikeUseCase.Result(orderId = OrderId("ORDER-1"), bikeAvailable = true)

        // when: the worker runs
        underTest.execute(task, service)

        // then: the process continues with the `orderId` and `bikeAvailable` output variables
        verify {
            service.complete(
                task,
                match { it["orderId"] == "ORDER-1" && it["bikeAvailable"] == true },
            )
        }
    }
}
