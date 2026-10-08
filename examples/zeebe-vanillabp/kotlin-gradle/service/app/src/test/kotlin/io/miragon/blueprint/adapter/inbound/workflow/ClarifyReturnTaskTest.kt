package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.domain.bike.OrderId
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import io.vanillabp.spi.service.TaskEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ClarifyReturnTaskTest {

    private val task = ClarifyReturnTask()

    @Test
    fun `the engine-only user task leaves no trace on the workflow aggregate`() {
        // given
        val application = testLeasingApplication(orderId = OrderId("ORDER-1"))
        val aggregate = LeasingApplicationAggregate.of(application)

        // when
        task.clarifyReturn(aggregate, TaskEvent.Event.CREATED)
        task.clarifyReturn(aggregate, TaskEvent.Event.CANCELED)

        // then
        assertThat(aggregate.toDomain()).isEqualTo(application)
        assertThat(aggregate.clarifyAlternativeTaskId).isNull()
        assertThat(aggregate.cancellationPossible).isNull()
    }
}
