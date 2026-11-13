package io.miragon.blueprint.adapter.inbound.workflow

import io.miragon.blueprint.adapter.process.LeasingApplicationAggregate
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import io.vanillabp.spi.service.TaskEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ClarifyReturnTaskTest {

    private val task = ClarifyReturnTask()

    @Test
    fun `remembers the created user task on the workflow aggregate`() {
        // given
        val aggregate = LeasingApplicationAggregate.of(testLeasingApplication())

        // when
        task.clarifyReturn(aggregate, "4712", TaskEvent.Event.CREATED)

        // then
        assertThat(aggregate.clarifyReturnTaskId).isEqualTo("4712")
    }

    @Test
    fun `forgets the user task when the engine cancels it`() {
        // given
        val aggregate = LeasingApplicationAggregate.of(testLeasingApplication())
        task.clarifyReturn(aggregate, "4712", TaskEvent.Event.CREATED)

        // when
        task.clarifyReturn(aggregate, "4712", TaskEvent.Event.CANCELED)

        // then
        assertThat(aggregate.clarifyReturnTaskId).isNull()
    }
}
